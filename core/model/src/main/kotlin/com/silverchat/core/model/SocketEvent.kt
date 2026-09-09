package com.silverchat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* =========================================================================
   ПРОТОКОЛ WEBSOCKET (клиент <-> Node.js backend)
   ------------------------------------------------------------------------
   Один и тот же контракт используют:
     - Android-клиент (этот файл);
     - веб-версия (бесшовная синхронизация — одинаковые имена событий);
     - Node.js сервер (backend/src/realtime/protocol.ts).

   Формат кадра — JSON:
   { "v": 1, "id": "uuid", "op": "chat.message.new", "ts": 1730000000000, "payload": {...} }

   `id` — correlation id: ответ сервера на команду несёт тот же id,
   поэтому клиент сопоставляет ack с отправленной командой без состояний.
   ========================================================================= */

@Serializable
data class SocketFrame(
    @SerialName("v") val version: Int = PROTOCOL_VERSION,
    @SerialName("id") val id: String,
    @SerialName("op") val op: String,
    @SerialName("ts") val timestamp: Long,
    @SerialName("payload") val payload: kotlinx.serialization.json.JsonElement? = null,
) {
    companion object {
        const val PROTOCOL_VERSION = 1
    }
}

/**
 * Все операции протокола в одном месте.
 *
 * Префиксы:
 *  - `session.*`  — подключение, авторизация, heartbeat
 *  - `chat.*`     — чаты, участники, приглашения
 *  - `message.*`  — сообщения, реакции, редактирование, удаление, печать
 *  - `story.*`    — сторис
 *  - `call.*`     — сигналинг звонков
 *  - `market.*`   — маркет юзернеймов и подарки
 *  - `wallet.*`   — сильверы и стрики
 *  - `premium.*`  — подписка
 *  - `profile.*`  — профиль, аватар/баннер, кастомные блоки
 *  - `admin.*`    — супер-права (@silver)
 *  - `presence.*` — онлайн-статусы
 */
object SocketOps {
    // session
    const val SESSION_HELLO = "session.hello"
    const val SESSION_AUTH = "session.auth"
    const val SESSION_AUTH_OK = "session.auth.ok"
    const val SESSION_AUTH_FAIL = "session.auth.fail"
    const val SESSION_PING = "session.ping"
    const val SESSION_PONG = "session.pong"
    const val SESSION_KICK = "session.kick"
    const val SESSION_SUBSCRIBE = "session.subscribe"

    // presence
    const val PRESENCE_UPDATE = "presence.update"
    const val PRESENCE_TYPING = "presence.typing"
    const val PRESENCE_READ = "presence.read"

    // chat
    const val CHAT_LIST_SYNC = "chat.list.sync"
    const val CHAT_UPDATED = "chat.updated"
    const val CHAT_CREATED = "chat.created"
    const val CHAT_MEMBER_JOINED = "chat.member.joined"
    const val CHAT_MEMBER_LEFT = "chat.member.left"
    const val CHAT_MEMBER_ROLE_CHANGED = "chat.member.role.changed"
    const val CHAT_INVITE_CREATED = "chat.invite.created"
    const val CHAT_INVITE_REVOKED = "chat.invite.revoked"
    const val CHAT_DRAFT_UPDATED = "chat.draft.updated"
    const val CHAT_PINNED_UPDATED = "chat.pinned.updated"

    // message
    const val MESSAGE_SEND = "message.send"
    const val MESSAGE_SENT_ACK = "message.sent.ack"
    const val MESSAGE_NEW = "message.new"
    const val MESSAGE_EDIT = "message.edit"
    const val MESSAGE_EDITED = "message.edited"
    const val MESSAGE_DELETE = "message.delete"
    const val MESSAGE_DELETED = "message.deleted"
    const val MESSAGE_REACT = "message.react"
    const val MESSAGE_REACTION_UPDATED = "message.reaction.updated"
    const val MESSAGE_READ = "message.read"
    const val MESSAGE_DELIVERED = "message.delivered"
    const val MESSAGE_TYPING = "message.typing"
    const val MESSAGE_UPLOAD_TICKET = "message.upload.ticket"

    // story
    const val STORY_NEW = "story.new"
    const val STORY_VIEW = "story.view"
    const val STORY_VIEWED = "story.viewed"
    const val STORY_REPLY = "story.reply"
    const val STORY_EXPIRED = "story.expired"
    const val STORY_DELETED = "story.deleted"

    // call
    const val CALL_INITIATE = "call.initiate"
    const val CALL_INCOMING = "call.incoming"
    const val CALL_ACCEPT = "call.accept"
    const val CALL_DECLINE = "call.decline"
    const val CALL_HANGUP = "call.hangup"
    const val CALL_SIGNAL = "call.signal"
    const val CALL_STATE = "call.state"
    const val CALL_ICE_SERVERS = "call.ice.servers"

    // market
    const val MARKET_LISTING_NEW = "market.listing.new"
    const val MARKET_LISTING_UPDATED = "market.listing.updated"
    const val MARKET_PURCHASE = "market.purchase"
    const val MARKET_PURCHASE_RESULT = "market.purchase.result"
    const val MARKET_OFFER = "market.offer"
    const val MARKET_SELL = "market.sell"
    const val MARKET_GIFT_SEND = "market.gift.send"

    // wallet / streak
    const val WALLET_UPDATED = "wallet.updated"
    const val WALLET_LEDGER_APPEND = "wallet.ledger.append"
    const val STREAK_CLAIM = "streak.claim"
    const val STREAK_UPDATED = "streak.updated"

    // premium
    const val PREMIUM_PURCHASE = "premium.purchase"
    const val PREMIUM_UPDATED = "premium.updated"
    const val PREMIUM_GRANTED = "premium.granted"

    // profile
    const val PROFILE_UPDATED = "profile.updated"
    const val PROFILE_AVATAR_UPDATED = "profile.avatar.updated"
    const val PROFILE_BANNER_UPDATED = "profile.banner.updated"

    // admin
    const val ADMIN_GRANT = "admin.grant"
    const val ADMIN_CREDIT = "admin.credit"
    const val ADMIN_LOG_APPEND = "admin.log.append"
    const val ADMIN_BROADCAST = "admin.broadcast"

    // system
    const val ERROR = "error"
    const val ACK = "ack"
}

/** Результат ack/ошибки сервера. */
@Serializable
data class SocketAck(
    @SerialName("op") val op: String,
    @SerialName("ok") val ok: Boolean,
    @SerialName("code") val errorCode: String? = null,
    @SerialName("message") val errorMessage: String? = null,
)

@Serializable
data class SocketError(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
    @SerialName("retryable") val retryable: Boolean = false,
    @SerialName("retry_after_ms") val retryAfterMs: Long? = null,
)

/* =========================================================================
   СОСТОЯНИЕ СОЕДИНЕНИЯ / СИНХРОНИЗАЦИИ
   ========================================================================= */

/**
 * Состояние WebSocket-канала. Показывает бейдж «Подключение…» в шапке
 * списка чатов и управляет очередью отправки сообщений.
 */
@Serializable
sealed interface ConnectionState {
    @Serializable @SerialName("disconnected")
    data object Disconnected : ConnectionState

    @Serializable @SerialName("connecting")
    data object Connecting : ConnectionState

    @Serializable @SerialName("authenticating")
    data object Authenticating : ConnectionState

    @Serializable @SerialName("connected")
    data class Connected(
        @SerialName("session_id") val sessionId: String,
        @SerialName("latency_ms") val latencyMs: Long = 0L,
    ) : ConnectionState

    @Serializable @SerialName("reconnecting")
    data class Reconnecting(
        @SerialName("attempt") val attempt: Int,
        @SerialName("next_retry_in_ms") val nextRetryInMs: Long,
        @SerialName("reason") val reason: String? = null,
    ) : ConnectionState

    @Serializable @SerialName("failed")
    data class Failed(@SerialName("error") val error: String) : ConnectionState

    val isOnline: Boolean get() = this is Connected
}

/** Фаза первичной синхронизации после подключения (offline-first). */
enum class SyncPhase {
    IDLE,
    AUTH,
    CHATS,
    MESSAGES,
    STORIES,
    WALLET,
    COMPLETE,
    FAILED,
}

/** Полный снимок состояния подключения для UI. */
data class ConnectionUiState(
    val state: ConnectionState = ConnectionState.Disconnected,
    val phase: SyncPhase = SyncPhase.IDLE,
    val pendingOutgoing: Int = 0,
    val lastSyncAt: Long? = null,
) {
    val bannerText: String?
        get() = when (state) {
            is ConnectionState.Reconnecting -> "Переподключение… попытка ${state.attempt}"
            ConnectionState.Connecting, ConnectionState.Authenticating -> "Подключение…"
            is ConnectionState.Failed -> "Нет соединения с сервером"
            ConnectionState.Disconnected -> "Офлайн — сообщения отправятся позже"
            else -> null
        }
}
