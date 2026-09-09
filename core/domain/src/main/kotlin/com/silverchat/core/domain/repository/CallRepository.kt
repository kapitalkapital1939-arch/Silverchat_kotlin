package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.CallHistoryEntry
import com.silverchat.core.model.CallId
import com.silverchat.core.model.CallSession
import com.silverchat.core.model.CallSignal
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.ConnectionState
import com.silverchat.core.model.IceServer
import kotlinx.coroutines.flow.Flow

/**
 * Голосовые и видеозвонки (WebRTC + сигналинг через WebSocket).
 *
 * Репозиторий отвечает за СИГНАЛИНГ и состояние сессии.
 * Сам медиа-транспорт живёт в :core:webrtc — домен не знает про PeerConnection.
 */
interface CallRepository {

    /** Активный звонок (null, если звонка нет). */
    fun observeActiveCall(): Flow<CallSession?>

    /** Входящие сигналинг-пакеты из WebSocket-канала. */
    fun observeSignals(): Flow<CallSignal>

    fun observeCallHistory(): Flow<List<CallHistoryEntry>>

    fun observeConnectionState(): Flow<ConnectionState>

    suspend fun startCall(chatId: ChatId, type: com.silverchat.core.model.CallType): ScResult<CallSession>

    suspend fun acceptCall(callId: CallId): ScResult<CallSession>

    suspend fun declineCall(callId: CallId): ScResult<Unit>

    suspend fun endCall(callId: CallId): ScResult<Unit>

    suspend fun setMuted(callId: CallId, muted: Boolean): ScResult<Unit>

    suspend fun setCamera(callId: CallId, enabled: Boolean): ScResult<Unit>

    suspend fun switchCamera(callId: CallId): ScResult<Unit>

    suspend fun setScreenSharing(callId: CallId, enabled: Boolean): ScResult<Unit>

    /** TURN/STUN кредитеншелы с коротким TTL — запрашиваются на каждый звонок. */
    suspend fun requestIceServers(callId: CallId): ScResult<List<IceServer>>

    /** Клиент шлёт SDP/ICE через WebSocket; транспорт — :core:webrtc. */
    suspend fun sendSignal(signal: CallSignal): ScResult<Unit>
}
