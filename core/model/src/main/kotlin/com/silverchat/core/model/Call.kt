package com.silverchat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* =========================================================================
   ГОЛОСОВЫЕ И ВИДЕОЗВОНКИ
   ------------------------------------------------------------------------
   Транспорт — WebRTC (P2P), сигналинг — наш WebSocket-канал.
   Сервер НЕ участвует в медиа-потоке, только сводит пиры и выдаёт
   ICE/TURN-креденшелы с коротким TTL.
   ========================================================================= */

@Serializable
data class CallSession(
    @SerialName("id") val id: CallId,
    @SerialName("type") val type: CallType,
    @SerialName("chat_id") val chatId: ChatId,
    @SerialName("initiator_id") val initiatorId: UserId,
    @SerialName("participants") val participants: List<CallParticipant> = emptyList(),
    @SerialName("state") val state: CallState = CallState.IDLE,
    @SerialName("started_at") val startedAt: Long? = null,
    @SerialName("answered_at") val answeredAt: Long? = null,
    @SerialName("ended_at") val endedAt: Long? = null,
    @SerialName("end_reason") val endReason: CallEndReason? = null,
    @SerialName("ice_servers") val iceServers: List<IceServer> = emptyList(),
    @SerialName("is_encrypted") val isEncrypted: Boolean = true,
    @SerialName("video_enabled") val videoEnabled: Boolean = false,
    @SerialName("muted") val muted: Boolean = false,
    @SerialName("camera_off") val cameraOff: Boolean = false,
    @SerialName("screen_sharing") val screenSharing: Boolean = false,
) {
    val durationMs: Long?
        get() = if (answeredAt != null && endedAt != null) endedAt - answeredAt else null
}

@Serializable
@JvmInline
value class CallId(val raw: String) {
    override fun toString(): String = raw
}

@Serializable
enum class CallType {
    @SerialName("audio") AUDIO,
    @SerialName("video") VIDEO,
    @SerialName("group_audio") GROUP_AUDIO,
    @SerialName("group_video") GROUP_VIDEO,
}

/**
 * Жизненный цикл звонка — единственный источник истины для UI.
 * Каждый переход валидируется в [com.silverchat.core.webrtc.CallStateMachine].
 */
@Serializable
enum class CallState {
    @SerialName("idle") IDLE,
    @SerialName("preparing") PREPARING,      // собираем PeerConnection/ICE
    @SerialName("outgoing_ringing") OUTGOING_RINGING,
    @SerialName("incoming_ringing") INCOMING_RINGING,
    @SerialName("connecting") CONNECTING,    // ICE connected, медиа ещё нет
    @SerialName("active") ACTIVE,
    @SerialName("reconnecting") RECONNECTING,
    @SerialName("on_hold") ON_HOLD,
    @SerialName("ended") ENDED,
    @SerialName("failed") FAILED,
}

@Serializable
enum class CallEndReason {
    @SerialName("hangup") HANGUP,
    @SerialName("declined") DECLINED,
    @SerialName("missed") MISSED,
    @SerialName("busy") BUSY,
    @SerialName("timeout") TIMEOUT,
    @SerialName("network") NETWORK_ERROR,
    @SerialName("media") MEDIA_ERROR,
    @SerialName("forbidden") FORBIDDEN,     // пользователь запретил звонки в приватности
}

@Serializable
data class CallParticipant(
    @SerialName("user_id") val userId: UserId,
    @SerialName("user") val user: User? = null,
    @SerialName("state") val state: CallState = CallState.IDLE,
    @SerialName("muted") val muted: Boolean = false,
    @SerialName("camera_off") val cameraOff: Boolean = false,
    @SerialName("hand_raised") val handRaised: Boolean = false,
    @SerialName("is_speaking") val isSpeaking: Boolean = false,
    @SerialName("connection_quality") val quality: ConnectionQuality = ConnectionQuality.UNKNOWN,
)

@Serializable
enum class ConnectionQuality {
    @SerialName("unknown") UNKNOWN,
    @SerialName("excellent") EXCELLENT,
    @SerialName("good") GOOD,
    @SerialName("fair") FAIR,
    @SerialName("poor") POOR,
    @SerialName("lost") LOST,
}

/**
 * ICE/TURN серверы. Креденшелы выдаются бэкендом на время звонка
 * (TTL 5 мин) и НЕ сохраняются в EncryptedSharedPreferences.
 */
@Serializable
data class IceServer(
    @SerialName("urls") val urls: List<String>,
    @SerialName("username") val username: String? = null,
    @SerialName("credential") val credential: String? = null,
)

/** Запись в истории звонков (отображается как service-сообщение в чате). */
@Serializable
data class CallHistoryEntry(
    @SerialName("call_id") val callId: CallId,
    @SerialName("chat_id") val chatId: ChatId,
    @SerialName("peer") val peer: User,
    @SerialName("type") val type: CallType,
    @SerialName("missed") val missed: Boolean,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("duration_ms") val durationMs: Long = 0L,
)

/* =========================================================================
   WEBRTC-СИГНАЛИНГ: полезная нагрузка событий WebSocket
   ========================================================================= */

@Serializable
sealed interface CallSignal {
    @SerialName("offer")
    @Serializable
    data class Offer(@SerialName("sdp") val sdp: String, @SerialName("call_id") val callId: CallId) : CallSignal

    @SerialName("answer")
    @Serializable
    data class Answer(@SerialName("sdp") val sdp: String, @SerialName("call_id") val callId: CallId) : CallSignal

    @SerialName("candidate")
    @Serializable
    data class Candidate(
        @SerialName("call_id") val callId: CallId,
        @SerialName("candidate") val candidate: String,
        @SerialName("sdp_mid") val sdpMid: String?,
        @SerialName("sdp_m_line_index") val sdpMLineIndex: Int,
    ) : CallSignal

    @SerialName("busy")
    @Serializable
    data class Busy(@SerialName("call_id") val callId: CallId) : CallSignal

    @SerialName("hangup")
    @Serializable
    data class Hangup(
        @SerialName("call_id") val callId: CallId,
        @SerialName("reason") val reason: CallEndReason,
    ) : CallSignal

    @SerialName("media_state")
    @Serializable
    data class MediaState(
        @SerialName("call_id") val callId: CallId,
        @SerialName("muted") val muted: Boolean,
        @SerialName("camera_off") val cameraOff: Boolean,
    ) : CallSignal
}
