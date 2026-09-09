package com.silverchat.core.data.repository

import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.data.mapper.toDomain
import com.silverchat.core.data.mapper.toEntity
import com.silverchat.core.data.realtime.RealtimeBus
import com.silverchat.core.database.dao.CallHistoryDao
import com.silverchat.core.domain.repository.CallRepository
import com.silverchat.core.model.CallId
import com.silverchat.core.model.CallSession
import com.silverchat.core.model.CallSignal
import com.silverchat.core.model.CallState
import com.silverchat.core.model.CallType
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.ConnectionState
import com.silverchat.core.model.IceServer
import com.silverchat.core.model.SocketOps
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.dto.CameraRequest
import com.silverchat.core.network.dto.MuteRequest
import com.silverchat.core.network.dto.ScreenRequest
import com.silverchat.core.network.dto.StartCallRequest
import com.silverchat.core.network.mapper.apiCall
import com.silverchat.core.network.mapper.toDomain as dtoToDomain
import com.silverchat.core.network.ws.RealtimeSocket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Звонки: сигналинг, состояние активной сессии, история.
 *
 * Разделение ответственности здесь принципиальное:
 *  - REST создаёт и завершает сессию (это транзакции с аудиторией и биллингом);
 *  - WebSocket доставляет SDP/ICE-кандидатов — через REST они не ходят,
 *    потому что медиа-данные идут напрямую P2P, сервер видит только сигналинг;
 *  - сам медиапоток — это :core:webrtc, репозиторий его не касается.
 *
 * Активная сессия держится в [activeCall] как `StateFlow`, а не читается из БД:
 * звонок живёт секунды, и писать его в Room означало бы лишние транзакции
 * на каждое изменение состояния mute/camera.
 */
@Singleton
class CallRepositoryImpl @Inject constructor(
    private val api: SilverChatApi,
    private val socket: RealtimeSocket,
    private val bus: RealtimeBus,
    private val callHistoryDao: CallHistoryDao,
    private val json: Json,
) : CallRepository {

    private val _activeCall = MutableStateFlow<CallSession?>(null)

    private val scope = CoroutineScope(SupervisorJob())

    init {
        // Входящий звонок: кадр `call.incoming` несёт только id, поэтому
        // полная сессия добирается одним REST-запросом. Без этого UI звонка
        // не знал бы ни типа (аудио/видео), ни инициатора.
        bus.callEvents
            .onEach { event ->
                when (event.state) {
                    STATE_ENDED -> finishCall(event.callId, event.reason)
                    else -> if (_activeCall.value?.id != event.callId) {
                        runCatching { api.callSession(event.callId).dtoToDomain() }
                            .onSuccess { _activeCall.value = it }
                    } else {
                        _activeCall.value = _activeCall.value?.let { current ->
                            current.copy(
                                state = parseState(event.state) ?: current.state,
                            )
                        }
                    }
                }
            }
            .launchIn(scope)

        // ICE-серверы приходят асинхронно уже после создания PeerConnection.
        bus.iceServers
            .onEach { servers ->
                _activeCall.value = _activeCall.value?.copy(iceServers = servers)
            }
            .launchIn(scope)
    }

    override fun observeActiveCall(): Flow<CallSession?> = _activeCall.asStateFlow()

    override fun observeSignals(): Flow<CallSignal> = bus.callSignals

    override fun observeCallHistory(): Flow<List<com.silverchat.core.model.CallHistoryEntry>> =
        callHistoryDao.observe(limit = HISTORY_LIMIT).map { entities ->
            entities.mapNotNull { it.toDomain() }
        }

    override fun observeConnectionState(): Flow<ConnectionState> = socket.state

    /* ── Жизненный цикл звонка ─────────────────────────────────────────── */

    override suspend fun startCall(chatId: ChatId, type: CallType): ScResult<CallSession> =
        apiCall {
            val session = api.startCall(
                StartCallRequest(chatId = chatId, type = type.name.lowercase()),
            ).dtoToDomain()
            _activeCall.value = session.copy(state = CallState.OUTGOING_RINGING)
            session
        }

    override suspend fun acceptCall(callId: CallId): ScResult<CallSession> = apiCall {
        val session = api.acceptCall(callId).dtoToDomain()
        _activeCall.value = session.copy(state = CallState.CONNECTING)
        session
    }

    override suspend fun declineCall(callId: CallId): ScResult<Unit> = apiCall {
        api.declineCall(callId)
        finishCall(callId, reason = "declined")
    }

    override suspend fun endCall(callId: CallId): ScResult<Unit> = apiCall {
        api.endCall(callId)
        finishCall(callId, reason = "hangup")
    }

    /* ── Состояние медиа ───────────────────────────────────────────────── */

    override suspend fun setMuted(callId: CallId, muted: Boolean): ScResult<Unit> = apiCall {
        api.setCallMuted(callId, MuteRequest(muted))
        // Локально сразу: иконка микрофона обязана отреагировать в тот же кадр,
        // даже если ответ сервера задержится.
        _activeCall.value = _activeCall.value?.copy(muted = muted)
    }

    override suspend fun setCamera(callId: CallId, enabled: Boolean): ScResult<Unit> = apiCall {
        api.setCallCamera(callId, CameraRequest(enabled))
        _activeCall.value = _activeCall.value?.copy(cameraOff = !enabled)
    }

    override suspend fun switchCamera(callId: CallId): ScResult<Unit> = apiCall {
        api.switchCallCamera(callId)
    }

    override suspend fun setScreenSharing(callId: CallId, enabled: Boolean): ScResult<Unit> =
        apiCall {
            api.setCallScreenSharing(callId, ScreenRequest(enabled))
            _activeCall.value = _activeCall.value?.copy(screenSharing = enabled)
        }

    override suspend fun requestIceServers(callId: CallId): ScResult<List<IceServer>> = apiCall {
        val servers = api.iceServers(callId).servers.map { it.dtoToDomain() }
        _activeCall.value = _activeCall.value?.copy(iceServers = servers)
        servers
    }

    /* ── Сигналинг WebRTC ──────────────────────────────────────────────── */

    /**
     * Отправка SDP/ICE через WebSocket.
     *
     * Именно через сокет, а не REST: кандидат может прийти сотни раз за
     * секунду в момент установки соединения, и HTTP-запрос на каждый
     * убил бы и батарею, и порядок доставки.
     */
    override suspend fun sendSignal(signal: CallSignal): ScResult<Unit> = apiCall {
        if (!socket.isConnected) {
            return ScResult.Failure(ScError.Network("Нет соединения для сигналинга"))
        }
        socket.send(
            SocketOps.CALL_SIGNAL,
            buildJsonObject {
                put("signal", json.encodeToString(CallSignal.serializer(), signal))
            },
        )
        Unit
    }

    /* ── Внутреннее ────────────────────────────────────────────────────── */

    /**
     * Завершает звонок и пишет запись в историю.
     *
     * История сохраняется локально сразу, не дожидаясь `GET /calls/history`:
     * пользователь видит пропущенный звонок в тот же момент, как кладёт трубку.
     */
    private suspend fun finishCall(callId: CallId, reason: String?) {
        val finished = _activeCall.value?.copy(
            state = CallState.ENDED,
            endedAt = System.currentTimeMillis(),
            endReason = parseEndReason(reason),
        )
        _activeCall.value = null

        if (finished != null) {
            val durationMs = finished.startedAt?.let { finished.endedAt!! - it } ?: 0L
            val peer = finished.participants.firstOrNull { it.userId != finished.initiatorId }?.user
            if (peer != null) {
                callHistoryDao.upsert(
                    com.silverchat.core.model.CallHistoryEntry(
                        callId = finished.id,
                        chatId = finished.chatId,
                        peer = peer,
                        type = finished.type,
                        missed = durationMs == 0L && finished.initiatorId != peer.id,
                        startedAt = finished.startedAt ?: System.currentTimeMillis(),
                        durationMs = durationMs,
                    ).toEntity(),
                )
            }
        }
    }

    private fun parseState(raw: String?): CallState? =
        CallState.entries.firstOrNull { it.name.equals(raw, true) }

    private fun parseEndReason(raw: String?): com.silverchat.core.model.CallEndReason? =
        raw?.let { value ->
            com.silverchat.core.model.CallEndReason.entries.firstOrNull {
                it.name.equals(value, true) ||
                    // Сервер шлёт wire-имена из @SerialName ("network", "media").
                    it.name.lowercase() == value.removeSuffix("_error")
            }
        }

    private companion object {
        const val STATE_ENDED = "ended"
        const val HISTORY_LIMIT = 100
    }
}
