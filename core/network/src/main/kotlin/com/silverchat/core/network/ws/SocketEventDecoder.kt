package com.silverchat.core.network.ws

import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.model.SocketOps
import com.silverchat.core.network.dto.ChatDto
import com.silverchat.core.network.dto.LedgerDto
import com.silverchat.core.network.dto.MarketListingDto
import com.silverchat.core.network.dto.MessageDto
import com.silverchat.core.network.dto.PremiumStatusDto
import com.silverchat.core.network.dto.StoryDto
import com.silverchat.core.network.dto.StreakDto
import com.silverchat.core.network.dto.WalletDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Декодер входящих кадров в типизированные события.
 *
 * Здесь единственное место, где строковый `op` превращается в sealed-класс
 * [SocketEvent]. Ниже по стеку (репозитории, ViewModel) строк уже нет —
 * только типы, поэтому `when` проверяется компилятором на полноту.
 *
 * Неизвестный op НЕ роняет приложение: он уходит в [SocketEvent.Unknown],
 * что позволяет серверу выкатывать новые события раньше, чем клиент обновится.
 */
@Singleton
class SocketEventDecoder @Inject constructor(private val json: Json) {

    fun decode(frame: IncomingFrame): SocketEvent = try {
        when (frame.op) {
            // ── Сообщения ────────────────────────────────────────────────
            SocketOps.MESSAGE_NEW -> SocketEvent.MessageNew(payload<MessageDto>(frame))
            SocketOps.MESSAGE_SENT_ACK -> SocketEvent.MessageAck(payload<MessageDto>(frame))
            SocketOps.MESSAGE_EDITED -> SocketEvent.MessageEdited(payload<MessageDto>(frame))
            SocketOps.MESSAGE_DELETED -> SocketEvent.MessageDeleted(payload<MessageDeletedPayload>(frame))
            SocketOps.MESSAGE_REACTION_UPDATED -> SocketEvent.ReactionUpdated(payload<MessageDto>(frame))
            SocketOps.MESSAGE_DELIVERED -> SocketEvent.DeliveryUpdated(payload<DeliveryPayload>(frame))
            SocketOps.MESSAGE_READ -> SocketEvent.ReadUpdated(payload<DeliveryPayload>(frame))
            SocketOps.MESSAGE_TYPING -> SocketEvent.Typing(payload<TypingPayload>(frame))

            // ── Чаты ─────────────────────────────────────────────────────
            SocketOps.CHAT_UPDATED -> SocketEvent.ChatUpdated(payload<ChatDto>(frame))
            SocketOps.CHAT_CREATED -> SocketEvent.ChatCreated(payload<ChatDto>(frame))
            SocketOps.CHAT_LIST_SYNC -> SocketEvent.ChatListSync(payload<ChatListSyncPayload>(frame))
            SocketOps.CHAT_DRAFT_UPDATED -> SocketEvent.DraftUpdated(payload<DraftPayload>(frame))
            SocketOps.CHAT_PINNED_UPDATED -> SocketEvent.PinnedUpdated(payload<ChatDto>(frame))
            SocketOps.CHAT_MEMBER_JOINED -> SocketEvent.MemberChanged(payload<MemberPayload>(frame))
            SocketOps.CHAT_MEMBER_LEFT -> SocketEvent.MemberChanged(payload<MemberPayload>(frame))
            SocketOps.CHAT_MEMBER_ROLE_CHANGED -> SocketEvent.MemberChanged(payload<MemberPayload>(frame))
            SocketOps.CHAT_INVITE_CREATED -> SocketEvent.InviteUpdated(payload<InvitePayload>(frame))
            SocketOps.CHAT_INVITE_REVOKED -> SocketEvent.InviteUpdated(payload<InvitePayload>(frame))

            // ── Presence ─────────────────────────────────────────────────
            SocketOps.PRESENCE_UPDATE -> SocketEvent.PresenceChanged(payload<PresencePayload>(frame))
            SocketOps.PRESENCE_READ -> SocketEvent.ReadUpdated(payload<DeliveryPayload>(frame))

            // ── Сторис ───────────────────────────────────────────────────
            SocketOps.STORY_NEW -> SocketEvent.StoryNew(payload<StoryDto>(frame))
            SocketOps.STORY_VIEWED -> SocketEvent.StoryViewed(payload<StoryViewedPayload>(frame))
            SocketOps.STORY_EXPIRED -> SocketEvent.StoryExpired(payload<StoryIdPayload>(frame))
            SocketOps.STORY_DELETED -> SocketEvent.StoryDeleted(payload<StoryIdPayload>(frame))

            // ── Звонки ───────────────────────────────────────────────────
            SocketOps.CALL_INCOMING -> SocketEvent.CallIncoming(payload<CallPayload>(frame))
            SocketOps.CALL_STATE -> SocketEvent.CallStateChanged(payload<CallPayload>(frame))
            SocketOps.CALL_SIGNAL -> SocketEvent.CallSignalReceived(payload<CallSignalPayload>(frame))
            SocketOps.CALL_ICE_SERVERS -> SocketEvent.CallIceServers(payload<IceServersPayload>(frame))

            // ── Маркет и экономика ───────────────────────────────────────
            SocketOps.WALLET_UPDATED -> SocketEvent.WalletUpdated(payload<WalletDto>(frame))
            SocketOps.WALLET_LEDGER_APPEND -> SocketEvent.LedgerAppended(payload<LedgerDto>(frame))
            SocketOps.STREAK_UPDATED -> SocketEvent.StreakUpdated(payload<StreakDto>(frame))
            SocketOps.PREMIUM_UPDATED -> SocketEvent.PremiumUpdated(payload<PremiumStatusDto>(frame))
            SocketOps.PREMIUM_GRANTED -> SocketEvent.PremiumGranted(payload<PremiumGrantedPayload>(frame))
            SocketOps.MARKET_LISTING_NEW -> SocketEvent.ListingNew(payload<MarketListingDto>(frame))
            SocketOps.MARKET_LISTING_UPDATED -> SocketEvent.ListingUpdated(payload<MarketListingDto>(frame))
            SocketOps.MARKET_PURCHASE_RESULT -> SocketEvent.PurchaseCompleted(payload<PurchasePayload>(frame))

            // ── Профиль ──────────────────────────────────────────────────
            SocketOps.PROFILE_UPDATED -> SocketEvent.ProfileUpdated(payload<com.silverchat.core.network.dto.UserDto>(frame))
            SocketOps.PROFILE_AVATAR_UPDATED -> SocketEvent.AvatarUpdated(payload<com.silverchat.core.network.dto.UserDto>(frame))
            SocketOps.PROFILE_BANNER_UPDATED -> SocketEvent.BannerUpdated(payload<com.silverchat.core.network.dto.UserDto>(frame))

            // ── Админка ──────────────────────────────────────────────────
            SocketOps.ADMIN_LOG_APPEND -> SocketEvent.AdminLogAppended(payload<com.silverchat.core.network.dto.AdminLogDto>(frame))
            SocketOps.ADMIN_BROADCAST -> SocketEvent.AdminBroadcast(payload<BroadcastPayload>(frame))

            // ── Системные ────────────────────────────────────────────────
            SocketOps.ERROR -> SocketEvent.ServerError(payload<ErrorPayload>(frame))
            SocketOps.ACK -> SocketEvent.Ack(payload<AckPayload>(frame))

            else -> SocketEvent.Unknown(frame.op, frame.payload)
        }
    } catch (e: Exception) {
        ScLogger.e(LogTag.WS_FRAME, "Не удалось декодировать ${frame.op}", e)
        SocketEvent.DecodeFailure(frame.op, e.message ?: "decode error")
    }

    private inline fun <reified T> payload(frame: IncomingFrame): T {
        val element: JsonElement = frame.payload
            ?: error("Кадр ${frame.op} пришёл без payload")
        return json.decodeFromJsonElement(element)
    }
}

/* ── Полезные нагрузки ───────────────────────────────────────────────────── */

@kotlinx.serialization.Serializable
data class MessageDeletedPayload(
    @kotlinx.serialization.SerialName("chat_id") val chatId: String,
    @kotlinx.serialization.SerialName("message_ids") val messageIds: List<String>,
    @kotlinx.serialization.SerialName("for_everyone") val forEveryone: Boolean,
)

@kotlinx.serialization.Serializable
data class DeliveryPayload(
    @kotlinx.serialization.SerialName("chat_id") val chatId: String,
    @kotlinx.serialization.SerialName("message_ids") val messageIds: List<String>,
    @kotlinx.serialization.SerialName("user_id") val userId: String? = null,
)

@kotlinx.serialization.Serializable
data class TypingPayload(
    @kotlinx.serialization.SerialName("chat_id") val chatId: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    @kotlinx.serialization.SerialName("typing") val typing: Boolean,
)

@kotlinx.serialization.Serializable
data class ChatListSyncPayload(
    @kotlinx.serialization.SerialName("chats") val chats: List<ChatDto>,
    @kotlinx.serialization.SerialName("server_time") val serverTime: Long,
)

@kotlinx.serialization.Serializable
data class DraftPayload(
    @kotlinx.serialization.SerialName("chat_id") val chatId: String,
    @kotlinx.serialization.SerialName("text") val text: String,
    @kotlinx.serialization.SerialName("reply_to") val replyTo: String? = null,
)

@kotlinx.serialization.Serializable
data class MemberPayload(
    @kotlinx.serialization.SerialName("chat_id") val chatId: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    @kotlinx.serialization.SerialName("role") val role: String? = null,
)

@kotlinx.serialization.Serializable
data class InvitePayload(
    @kotlinx.serialization.SerialName("chat_id") val chatId: String,
    @kotlinx.serialization.SerialName("token") val token: String,
)

@kotlinx.serialization.Serializable
data class PresencePayload(
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    @kotlinx.serialization.SerialName("kind") val kind: String,
    @kotlinx.serialization.SerialName("last_seen") val lastSeen: Long? = null,
)

@kotlinx.serialization.Serializable
data class StoryViewedPayload(
    @kotlinx.serialization.SerialName("story_id") val storyId: String,
    @kotlinx.serialization.SerialName("viewer_id") val viewerId: String,
)

@kotlinx.serialization.Serializable
data class StoryIdPayload(
    @kotlinx.serialization.SerialName("story_id") val storyId: String,
)

@kotlinx.serialization.Serializable
data class CallPayload(
    @kotlinx.serialization.SerialName("call_id") val callId: String,
    @kotlinx.serialization.SerialName("chat_id") val chatId: String? = null,
    @kotlinx.serialization.SerialName("type") val type: String? = null,
    @kotlinx.serialization.SerialName("state") val state: String? = null,
    @kotlinx.serialization.SerialName("from_user_id") val fromUserId: String? = null,
    @kotlinx.serialization.SerialName("reason") val reason: String? = null,
)

@kotlinx.serialization.Serializable
data class CallSignalPayload(
    @kotlinx.serialization.SerialName("call_id") val callId: String,
    @kotlinx.serialization.SerialName("kind") val kind: String, // offer | answer | candidate
    @kotlinx.serialization.SerialName("sdp") val sdp: String? = null,
    @kotlinx.serialization.SerialName("candidate") val candidate: String? = null,
    @kotlinx.serialization.SerialName("sdp_mid") val sdpMid: String? = null,
    @kotlinx.serialization.SerialName("sdp_m_line_index") val sdpMLineIndex: Int? = null,
)

@kotlinx.serialization.Serializable
data class IceServersPayload(
    @kotlinx.serialization.SerialName("call_id") val callId: String,
    @kotlinx.serialization.SerialName("servers") val servers: List<com.silverchat.core.network.dto.IceServerDto>,
)

@kotlinx.serialization.Serializable
data class PremiumGrantedPayload(
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    @kotlinx.serialization.SerialName("days") val days: Int? = null,
    @kotlinx.serialization.SerialName("granted_by") val grantedBy: String,
)

@kotlinx.serialization.Serializable
data class PurchasePayload(
    @kotlinx.serialization.SerialName("listing") val listing: MarketListingDto,
    @kotlinx.serialization.SerialName("wallet") val wallet: WalletDto,
    @kotlinx.serialization.SerialName("transaction_id") val transactionId: String,
)

@kotlinx.serialization.Serializable
data class BroadcastPayload(
    @kotlinx.serialization.SerialName("text") val text: String,
    @kotlinx.serialization.SerialName("audience") val audience: String,
)

@kotlinx.serialization.Serializable
data class ErrorPayload(
    @kotlinx.serialization.SerialName("code") val code: String,
    @kotlinx.serialization.SerialName("message") val message: String,
    @kotlinx.serialization.SerialName("retryable") val retryable: Boolean = false,
)

@kotlinx.serialization.Serializable
data class AckPayload(
    @kotlinx.serialization.SerialName("op") val op: String,
    @kotlinx.serialization.SerialName("ok") val ok: Boolean,
)
