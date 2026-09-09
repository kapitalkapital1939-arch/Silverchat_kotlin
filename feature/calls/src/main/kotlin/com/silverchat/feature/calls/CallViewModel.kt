package com.silverchat.feature.calls

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.model.CallEndReason
import com.silverchat.core.model.CallId
import com.silverchat.core.model.CallSession
import com.silverchat.core.model.CallState
import com.silverchat.core.model.CallType
import com.silverchat.core.model.ConnectionQuality
import com.silverchat.core.model.PremiumPerkCode
import com.silverchat.core.model.User
import com.silverchat.core.model.labelRu
import com.silverchat.core.webrtc.CallController
import com.silverchat.core.webrtc.CallUiState
import com.silverchat.core.webrtc.WebRtcClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel экрана звонка.
 *
 * Важное архитектурное решение: **вся логика звонка живёт в
 * [CallController] (`:core:webrtc`), а не здесь.** Контроллер — `@Singleton`,
 * поэтому звонок переживает пересоздание экрана: при повороте, переходе
 * в mini-player и возврате на полный экран состояние не теряется.
 *
 * ViewModel делает только три вещи:
 *  1. транслирует `CallUiState`, `elapsed` и качество связи в UI-состояние;
 *  2. пробрасывает команды пользователя в контроллер;
 *  3. определяет доступность HD-качества по статусу Premium.
 *
 * Дублировать машину состояний звонка здесь нельзя: два источника истины
 * о состоянии PeerConnection гарантированно разойдутся, и UI покажет
 * «идёт звонок» после фактического завершения.
 */
@HiltViewModel
class CallViewModel @Inject constructor(
    private val callController: CallController,
    private val webrtc: WebRtcClient,
    private val walletRepository: WalletRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val callIdArg: String = savedStateHandle[Routes.Args.CALL_ID] ?: ""
    private val wantsVideo: Boolean = savedStateHandle[Routes.Args.CALL_VIDEO] ?: false

    /** Локальные переключатели: контроллер хранит их в сессии асинхронно. */
    private val localMuted = MutableStateFlow(false)
    private val localCameraOn = MutableStateFlow(wantsVideo)
    private val localSpeakerOn = MutableStateFlow(true)

    /** Идёт ли демонстрация экрана (требует отдельного разрешения). */
    private val screenSharing = MutableStateFlow(false)

    val uiState: StateFlow<CallUiState> = combine(
        callController.uiState,
        callController.elapsed,
        webrtc.quality,
        walletRepository.observePerkAvailable(PremiumPerkCode.HD_CALLS),
        combine(localMuted, localCameraOn, localSpeakerOn, screenSharing) { m, c, s, sh ->
            LocalControls(muted = m, cameraOn = c, speakerOn = s, screenSharing = sh)
        },
    ) { controllerState, elapsed, quality, hdAllowed, controls ->
        CallUi(
            controllerState = controllerState,
            session = controllerState.sessionOrNull(),
            elapsedMs = elapsed,
            quality = quality,
            hdAvailable = hdAllowed,
            isMuted = controls.muted,
            isCameraOn = controls.cameraOn,
            isSpeakerOn = controls.speakerOn,
            isScreenSharing = controls.screenSharing,
            subtitle = subtitleFor(controllerState, elapsed),
            isVideo = controllerState.sessionOrNull()?.isVideoCall() ?: wantsVideo,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = CallUi(),
    )

    /** Внутренняя группировка локальных переключателей (лимит `combine` — 5). */
    private data class LocalControls(
        val muted: Boolean,
        val cameraOn: Boolean,
        val speakerOn: Boolean,
        val screenSharing: Boolean,
    )

    /* ── Команды пользователя ─────────────────────────────────────────── */

    /**
     * Старт исходящего звонка.
     *
     * Вызывается один раз при первом кадре экрана. Повторный вызов
     * игнорируется контроллером, но мы дополнительно проверяем состояние,
     * чтобы не отправлять лишний запрос на сервер.
     */
    fun startOutgoing(chatId: String, video: Boolean, isPremium: Boolean) {
        if (callController.uiState.value.isActive) return
        callController.startCall(
            chatId = com.silverchat.core.model.ChatId(chatId),
            type = if (video) CallType.VIDEO else CallType.AUDIO,
            isPremium = isPremium,
        )
    }

    fun accept() {
        val session = uiState.value.session ?: return
        callController.accept(session.id)
        localCameraOn.value = session.type.isVideo()
    }

    fun decline() {
        val session = uiState.value.session ?: return
        callController.decline(session.id)
    }

    fun hangUp() {
        val session = uiState.value.session ?: return
        callController.hangUp(session.id)
    }

    /**
     * Микрофон.
     *
     * Локальный флаг обновляем сразу: `CallController.toggleMute` уходит
     * в репозиторий и signaling асинхронно, а задержка иконки на 200–500 мс
     * ощущается как неотзывчивость.
     */
    fun toggleMute() {
        val session = uiState.value.session ?: return
        val next = !localMuted.value
        localMuted.value = next
        callController.toggleMute(session.id, muted = next)
    }

    /**
     * Камера.
     *
     * Для голосового звонка включение камеры переводит звонок в видео:
     * это отдельное действие, требующее согласия собеседника на сервере,
     * поэтому здесь просто переключаем локальный поток.
     */
    fun toggleCamera() {
        val session = uiState.value.session ?: return
        val next = !localCameraOn.value
        localCameraOn.value = next
        callController.toggleCamera(session.id, enabled = next)
    }

    fun switchCamera() = webrtc.switchCamera()

    /** Громкая связь переключается на уровне аудиосессии, не WebRTC. */
    fun toggleSpeaker() {
        localSpeakerOn.value = !localSpeakerOn.value
    }

    /**
     * Демонстрация экрана.
     *
     * Локально гасим камеру: два видеопотока одновременно делят кодировщик,
     * и битрейт падает ниже порога читаемости текста на экране.
     */
    fun toggleScreenSharing() {
        val session = uiState.value.session ?: return
        val next = !screenSharing.value
        screenSharing.value = next
        if (next) localCameraOn.value = false
        callController.toggleScreenSharing(session.id, enabled = next)
    }

    /**
     * Завершение звонка с причиной.
     *
     * Причина пишется в историю и в служебное сообщение чата, поэтому
     * «отклонён» и «пропущен» — разные записи, а не один «завершён».
     */
    fun endWithReason(reason: CallEndReason) {
        val session = uiState.value.session ?: return
        when (reason) {
            CallEndReason.DECLINED -> callController.decline(session.id)
            else -> callController.hangUp(session.id)
        }
    }

    /* ── Вспомогательное ──────────────────────────────────────────────── */

    /** Текстовое состояние под именем собеседника. */
    private fun subtitleFor(state: CallUiState, elapsedMs: Long): String = when (state) {
        CallUiState.Idle -> "Завершён"
        is CallUiState.Error -> state.message
        is CallUiState.Incoming -> "Входящий звонок"
        is CallUiState.Active -> when (state.session.state) {
            CallState.PREPARING -> "Подключение…"
            CallState.OUTGOING_RINGING -> "Вызов…"
            CallState.INCOMING_RINGING -> "Входящий звонок"
            CallState.CONNECTING -> "Соединение…"
            CallState.ACTIVE -> TimeFormatter.callTimer(elapsedMs)
            CallState.RECONNECTING -> "Переподключение…"
            CallState.ON_HOLD -> "Звонок на удержании"
            CallState.ENDED -> "Завершён"
            CallState.FAILED -> "Не удалось соединиться"
            CallState.IDLE -> "Подключение…"
        }
    }

    /**
     * Качество связи текстом.
     *
     * Показываем только при проблемах: «отличное соединение» — шум,
     * а «слабая сеть» требует действия пользователя (перейти на Wi-Fi).
     */
    fun qualityHint(quality: ConnectionQuality): String? = when (quality) {
        ConnectionQuality.POOR -> "Слабая сеть: возможны пропадания звука"
        ConnectionQuality.LOST -> "Соединение потеряно, переподключаемся"
        ConnectionQuality.FAIR -> "Среднее качество связи"
        else -> null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/* ── Расширения для компактности ───────────────────────────────────────── */

private fun CallUiState.sessionOrNull(): CallSession? = when (this) {
    is CallUiState.Active -> session
    is CallUiState.Incoming -> session
    else -> null
}

private fun CallType.isVideo(): Boolean = this == CallType.VIDEO || this == CallType.GROUP_VIDEO

private fun CallSession.isVideoCall(): Boolean = type.isVideo() || videoEnabled

/** Второй участник звонка 1:1 (для аватара и имени в шапке). */
fun CallSession.peerUser(currentUserId: String): User? =
    participants.firstOrNull { it.userId.raw != currentUserId }?.user

data class CallUi(
    val controllerState: CallUiState = CallUiState.Idle,
    val session: CallSession? = null,
    val elapsedMs: Long = 0L,
    val quality: ConnectionQuality = ConnectionQuality.UNKNOWN,
    val hdAvailable: Boolean = false,
    val isMuted: Boolean = false,
    val isCameraOn: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isScreenSharing: Boolean = false,
    val isVideo: Boolean = false,
    val subtitle: String = "",
) {
    val isActive: Boolean get() = controllerState.isActive
    val isIncoming: Boolean get() = controllerState is CallUiState.Incoming
    val isEnded: Boolean
        get() = session?.state == CallState.ENDED || session?.state == CallState.FAILED

    /** Кнопки управления доступны только в активном звонке. */
    val canControl: Boolean
        get() = session?.state == CallState.ACTIVE ||
            session?.state == CallState.CONNECTING ||
            session?.state == CallState.ON_HOLD

    val callId: CallId? get() = session?.id

    /** Шифрование звонка показываем явно: это требование безопасности. */
    val isEncrypted: Boolean get() = session?.isEncrypted ?: true

    val endReasonRu: String? get() = session?.endReason?.labelRu
}

/** Русская подпись состояния звонка для уведомлений и истории. */
val CallState.titleRu: String
    get() = when (this) {
        CallState.IDLE -> "Ожидание"
        CallState.PREPARING -> "Подготовка"
        CallState.OUTGOING_RINGING -> "Вызов"
        CallState.INCOMING_RINGING -> "Входящий"
        CallState.CONNECTING -> "Соединение"
        CallState.ACTIVE -> "В эфире"
        CallState.RECONNECTING -> "Переподключение"
        CallState.ON_HOLD -> "На удержании"
        CallState.ENDED -> "Завершён"
        CallState.FAILED -> "Ошибка"
    }
