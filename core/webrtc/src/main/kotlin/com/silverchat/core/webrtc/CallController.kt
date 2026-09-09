package com.silverchat.core.webrtc

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.domain.repository.CallRepository
import com.silverchat.core.model.CallId
import com.silverchat.core.model.CallSignal
import com.silverchat.core.model.CallState
import com.silverchat.core.model.CallType
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.PremiumPerkCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * Оркестратор звонка — единственный класс, знающий полный жизненный цикл.
 *
 * Последовательность исходящего звонка:
 *  1. [CallRepository.startCall]       -> сервер создаёт сессию, шлёт peer'у `call.incoming`
 *  2. [CallRepository.requestIceServers] -> получаем STUN/TURN с TTL 5 минут
 *  3. [WebRtcClient.createPeerConnection] + захват медиа
 *  4. createOffer -> local SDP -> `call.signal{offer}` через WebSocket
 *  5. получаем `call.signal{answer}` -> applyRemoteDescription
 *  6. обмен ICE-кандидатами в обе стороны (trickle ICE)
 *  7. [CallState.ACTIVE] -> запускаем таймер и контроль качества
 *
 * Все переходы валидируются через [com.silverchat.core.domain.usecase.call.CallStateMachine],
 * поэтому UI никогда не отрисует «звонок идёт» после «звонок завершён».
 */
@Singleton
class CallController @Inject constructor(
    private val repository: CallRepository,
    private val webrtc: WebRtcClient,
    private val dispatchers: DispatcherProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.realtime)

    private val _uiState = MutableStateFlow(CallUiState.Idle)
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val _elapsed = MutableStateFlow(0L)
    val elapsed: StateFlow<Long> = _elapsed.asStateFlow()

    private var timerJob: Job? = null
    private var signalJob: Job? = null
    private var timeoutJob: Job? = null
    private var ringingJob: Job? = null

    init {
        observeIncomingSignals()
    }

    /* ── Исходящий звонок ───────────────────────────────────────────────── */

    fun startCall(chatId: ChatId, type: CallType, isPremium: Boolean) {
        if (_uiState.value is CallUiState.Active) {
            ScLogger.w(LogTag.CALL, "Звонок уже активен — игнорируем startCall")
            return
        }
        transition(CallState.PREPARING)

        scope.launch {
            val session = repository.startCall(chatId, type).getOrNull()
            if (session == null) {
                transition(CallState.FAILED)
                _uiState.value = CallUiState.Error("Не удалось начать звонок")
                return@launch
            }

            val ice = repository.requestIceServers(session.id).getOrNull().orEmpty()
            prepareMedia(session.id, type, ice, isPremium, isInitiator = true)

            _uiState.value = CallUiState.Active(session.copy(state = CallState.OUTGOING_RINGING))
            transition(CallState.OUTGOING_RINGING)
            startRingingTimeout(session.id)
        }
    }

    /* ── Входящий звонок ────────────────────────────────────────────────── */

    private fun observeIncomingSignals() {
        signalJob = scope.launch {
            repository.observeSignals().collect { signal ->
                when (signal) {
                    is CallSignal.Offer -> onRemoteOffer(signal)
                    is CallSignal.Answer -> onRemoteAnswer(signal)
                    is CallSignal.Candidate -> webrtc.addIceCandidate(
                        IceCandidate(signal.sdpMid, signal.sdpMLineIndex, signal.candidate),
                    )

                    is CallSignal.Busy -> {
                        _uiState.value = CallUiState.Error("Абонент занят")
                        finishCall(signal.callId, CallState.ENDED)
                    }

                    is CallSignal.Hangup -> finishCall(signal.callId, CallState.ENDED)
                    is CallSignal.MediaState -> Unit
                }
            }
        }
    }

    private fun onRemoteOffer(signal: CallSignal.Offer) {
        scope.launch {
            val session = repository.acceptCall(signal.callId).getOrNull() ?: return@launch
            val ice = repository.requestIceServers(signal.callId).getOrNull().orEmpty()
            prepareMedia(signal.callId, session.type, ice, isPremium = false, isInitiator = false)

            webrtc.applyRemoteDescription(
                SessionDescription(SessionDescription.Type.OFFER, signal.sdp),
            ) {
                webrtc.createAnswer(
                    isPremium = false,
                    onSuccess = { answer ->
                        scope.launch {
                            repository.sendSignal(CallSignal.Answer(answer.description, signal.callId))
                            transition(CallState.CONNECTING)
                        }
                    },
                    onFailure = { error ->
                        ScLogger.e(LogTag.CALL, "createAnswer: $error")
                        finishCall(signal.callId, CallState.FAILED)
                    },
                )
            }
        }
    }

    private fun onRemoteAnswer(signal: CallSignal.Answer) {
        webrtc.applyRemoteDescription(
            SessionDescription(SessionDescription.Type.ANSWER, signal.sdp),
        ) {
            transition(CallState.CONNECTING)
        }
    }

    /* ── Общая подготовка медиа ─────────────────────────────────────────── */

    private suspend fun prepareMedia(
        callId: CallId,
        type: CallType,
        ice: List<com.silverchat.core.model.IceServer>,
        isPremium: Boolean,
        isInitiator: Boolean,
    ) {
        webrtc.createPeerConnection(callId, ice) ?: run {
            transition(CallState.FAILED)
            return
        }
        webrtc.createLocalMedia(
            audio = true,
            video = type == CallType.VIDEO || type == CallType.GROUP_VIDEO,
        )
        webrtc.applyBitrateLimit(
            if (isPremium) PREMIUM_BITRATE_KBPS else DEFAULT_BITRATE_KBPS,
        )

        // Локальные кандидаты сразу уходят собеседнику (trickle ICE)
        scope.launch {
            webrtc.iceCandidates.collect { candidate ->
                repository.sendSignal(
                    CallSignal.Candidate(
                        callId = callId,
                        candidate = candidate.sdp,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex,
                    ),
                )
            }
        }

        if (isInitiator) {
            webrtc.createOffer(
                isPremium = isPremium,
                onSuccess = { offer ->
                    scope.launch {
                        repository.sendSignal(CallSignal.Offer(offer.description, callId))
                    }
                },
                onFailure = { error ->
                    ScLogger.e(LogTag.CALL, "createOffer: $error")
                    finishCall(callId, CallState.FAILED)
                },
            )
        }
    }

    /* ── Управление во время звонка ─────────────────────────────────────── */

    fun accept(callId: CallId) {
        scope.launch { repository.acceptCall(callId) }
    }

    fun decline(callId: CallId) {
        scope.launch {
            repository.declineCall(callId)
            finishCall(callId, CallState.ENDED)
        }
    }

    fun hangUp(callId: CallId) {
        scope.launch {
            repository.endCall(callId)
            repository.sendSignal(CallSignal.Hangup(callId, com.silverchat.core.model.CallEndReason.HANGUP))
            finishCall(callId, CallState.ENDED)
        }
    }

    fun toggleMute(callId: CallId, muted: Boolean) {
        webrtc.setMicrophoneEnabled(!muted)
        scope.launch {
            repository.setMuted(callId, muted)
            repository.sendSignal(CallSignal.MediaState(callId, muted, cameraOff = currentCameraOff))
        }
    }

    fun toggleCamera(callId: CallId, enabled: Boolean) {
        currentCameraOff = !enabled
        webrtc.setCameraEnabled(enabled)
        scope.launch {
            repository.setCamera(callId, enabled)
        }
    }

    /**
     * Демонстрация экрана.
     *
     * Сервер обязательно уведомляется: у собеседника меняется раскладка
     * (основной поток — экран, а не камера), и без сигнала он продолжил бы
     * показывать видео с камеры. Локально гасим камеру, иначе два потока
     * конфликтуют за кодировщик, и битрейт падает ниже порога читаемости.
     */
    fun toggleScreenSharing(callId: CallId, enabled: Boolean) {
        if (enabled) {
            currentCameraOff = true
            webrtc.setCameraEnabled(false)
        }
        scope.launch { repository.setScreenSharing(callId, enabled) }
    }

    fun switchCamera() = webrtc.switchCamera()

    @Volatile private var currentCameraOff = false

    /* ── Состояние и таймеры ────────────────────────────────────────────── */

    private fun transition(to: CallState) {
        val current = _uiState.value
        val from = (current as? CallUiState.Active)?.session?.state ?: CallState.IDLE

        if (!com.silverchat.core.domain.usecase.call.CallStateMachine.canTransition(from, to)) {
            ScLogger.w(LogTag.CALL, "Недопустимый переход $from -> $to, игнорируем")
            return
        }

        if (current is CallUiState.Active) {
            _uiState.value = current.copy(session = current.session.copy(state = to))
        }

        when (to) {
            CallState.ACTIVE -> startTimer()
            CallState.ENDED, CallState.FAILED -> stopTimer()
            else -> Unit
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _elapsed.value = 0L
        timerJob = scope.launch {
            while (true) {
                delay(1000L)
                _elapsed.value += 1000L
            }
        }
        ScLogger.i(LogTag.CALL, "Звонок активен")
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Если абонент не ответил за [RINGING_TIMEOUT_SEC] секунд — завершаем
     * и пишем в историю как пропущенный.
     */
    private fun startRingingTimeout(callId: CallId) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(RINGING_TIMEOUT_SEC * 1000L)
            if (_uiState.value.let { it is CallUiState.Active && it.session.state == CallState.OUTGOING_RINGING }) {
                ScLogger.i(LogTag.CALL, "Таймаут дозвона")
                repository.sendSignal(
                    CallSignal.Hangup(callId, com.silverchat.core.model.CallEndReason.TIMEOUT),
                )
                finishCall(callId, CallState.ENDED)
            }
        }
    }

    private fun finishCall(callId: CallId, finalState: CallState) {
        stopTimer()
        timeoutJob?.cancel()
        ringingJob?.cancel()
        transition(finalState)
        webrtc.dispose()
        _uiState.value = CallUiState.Idle
        _elapsed.value = 0L
        ScLogger.i(LogTag.CALL, "Звонок завершён: ${callId.raw}")
    }

    fun release() {
        signalJob?.cancel()
        stopTimer()
        webrtc.shutdown()
    }

    private companion object {
        const val RINGING_TIMEOUT_SEC = 45L
        const val DEFAULT_BITRATE_KBPS = 1200
        const val PREMIUM_BITRATE_KBPS = 2500
    }
}

/** Состояние звонка для UI (полный экран, mini-player, уведомление). */
sealed interface CallUiState {
    data object Idle : CallUiState
    data class Active(val session: com.silverchat.core.model.CallSession) : CallUiState
    data class Incoming(val session: com.silverchat.core.model.CallSession) : CallUiState
    data class Error(val message: String) : CallUiState

    val isActive: Boolean get() = this is Active || this is Incoming
}

/** Перк, дающий HD-качество звонка. Используется UI для бейджа «HD». */
val HD_CALL_PERK: PremiumPerkCode = PremiumPerkCode.HD_CALLS
