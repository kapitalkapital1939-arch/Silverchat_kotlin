package com.silverchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* =========================================================================
   ЗАПРОСЫ К БЭКЕНДУ (Node.js)
   ------------------------------------------------------------------------
   Все имена полей — snake_case, как в протоколе WebSocket, чтобы клиент,
   веб-версия и сервер использовали один словарь.
   ========================================================================= */

@Serializable data class SendOtpRequest(@SerialName("phone") val phone: String, @SerialName("device_id") val deviceId: String)
@Serializable
data class VerifyOtpRequest(
    @SerialName("phone") val phone: String,
    @SerialName("code") val code: String,
    @SerialName("device_name") val deviceName: String,
)
@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class UpdateProfileRequest(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("bio") val bio: String? = null,
)

@Serializable
data class LocationRequest(
    @SerialName("lat") val latitude: Double?,
    @SerialName("lng") val longitude: Double?,
    @SerialName("address") val address: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("visible") val visible: Boolean = true,
)

@Serializable
data class WorkingHoursRequest(
    @SerialName("timezone") val timezone: String,
    @SerialName("always_open") val alwaysOpen: Boolean = false,
    @SerialName("schedule") val schedule: List<DayScheduleDto> = emptyList(),
    @SerialName("visible") val visible: Boolean = true,
)

@Serializable
data class DayScheduleDto(
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("open_minute") val openMinute: Int,
    @SerialName("close_minute") val closeMinute: Int,
    @SerialName("closed") val closed: Boolean = false,
)

@Serializable
data class PrivacyRequest(
    @SerialName("last_seen_visibility") val lastSeenVisibility: String,
    @SerialName("phone_visibility") val phoneVisibility: String,
    @SerialName("avatar_visibility") val avatarVisibility: String = "everybody",
    @SerialName("calls_allowed") val callsAllowed: String,
    @SerialName("stories_allowed") val storiesAllowed: String,
    @SerialName("add_to_groups") val addToGroups: String,
    @SerialName("read_receipts") val readReceipts: Boolean = true,
    @SerialName("exceptions") val exceptions: List<String> = emptyList(),
)

@Serializable data class OpenPersonalChatRequest(@SerialName("user_id") val userId: String)

@Serializable
data class CreateGroupRequest(
    @SerialName("title") val title: String,
    @SerialName("member_ids") val memberIds: List<String>,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class CreateChannelRequest(
    @SerialName("title") val title: String,
    @SerialName("about") val about: String? = null,
    @SerialName("broadcast") val broadcast: Boolean = false,
    @SerialName("join_by_invite_only") val joinByInviteOnly: Boolean = true,
)

@Serializable
data class UpdateChatRequest(
    @SerialName("title") val title: String? = null,
    @SerialName("about") val about: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("slow_mode_seconds") val slowModeSeconds: Int? = null,
)

@Serializable data class AddMembersRequest(@SerialName("user_ids") val userIds: List<String>)

@Serializable
data class UpdateMemberRequest(
    @SerialName("role") val role: String? = null,
    @SerialName("restricted_until") val restrictedUntil: Long? = null,
)

@Serializable
data class InviteLinkRequest(
    @SerialName("max_uses") val maxUses: Int? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("requires_approval") val requiresApproval: Boolean = false,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class DraftRequest(
    @SerialName("text") val text: String,
    @SerialName("reply_to") val replyTo: String? = null,
)

@Serializable
data class SendMessageRequest(
    @SerialName("client_message_id") val clientMessageId: String,
    @SerialName("content") val content: MessageContentDto,
    @SerialName("reply_to") val replyTo: String? = null,
    @SerialName("silent") val silent: Boolean = false,
    @SerialName("scheduled_at") val scheduledAt: Long? = null,
)

@Serializable
data class EditMessageRequest(
    @SerialName("content") val content: MessageContentDto,
)

@Serializable data class ReactionRequest(@SerialName("kind") val kind: String)

@Serializable data class PinRequest(@SerialName("notify") val notifyMembers: Boolean = true)

@Serializable
data class ForwardRequest(
    @SerialName("message_ids") val messageIds: List<String>,
    @SerialName("to_chat_ids") val toChatIds: List<String>,
)

@Serializable data class VoteRequest(@SerialName("options") val optionIndexes: List<Int>)

@Serializable
data class UploadTicketRequest(
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("chat_id") val chatId: String? = null,
    @SerialName("kind") val kind: String, // photo | video | voice | circle | file | avatar | banner | story
)

@Serializable
data class PublishStoryRequest(
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("media_type") val mediaType: String,
    @SerialName("caption") val caption: String? = null,
    @SerialName("privacy") val privacy: String,
    @SerialName("background_gradient") val backgroundGradient: List<Long> = emptyList(),
    @SerialName("overlay_text") val overlayText: String? = null,
    @SerialName("location") val location: LocationRequest? = null,
    @SerialName("duration_ms") val durationMs: Long = 5_000L,
)

@Serializable
data class StoryReplyRequest(
    @SerialName("text") val text: String? = null,
    @SerialName("reaction") val reaction: String? = null,
)

@Serializable
data class StartCallRequest(
    @SerialName("chat_id") val chatId: String,
    @SerialName("type") val type: String,
)

/* ── Маркет ──────────────────────────────────────────────────────────────── */

@Serializable
data class BuyRequest(
    @SerialName("listing_id") val listingId: String,
    @SerialName("offer_silver") val offerSilver: Long? = null,
)

@Serializable
data class SellRequest(
    @SerialName("username") val username: String,
    @SerialName("price_silver") val priceSilver: Long,
    @SerialName("accept_offers") val acceptOffers: Boolean = true,
    @SerialName("min_offer_silver") val minOfferSilver: Long? = null,
    @SerialName("auction") val auction: Boolean = false,
    @SerialName("duration_hours") val durationHours: Int? = null,
)

@Serializable data class OfferRequest(@SerialName("amount_silver") val amountSilver: Long)

@Serializable
data class SendGiftRequest(
    @SerialName("gift_id") val giftId: String,
    @SerialName("receiver_id") val receiverId: String,
    @SerialName("message") val message: String? = null,
    @SerialName("anonymous") val anonymous: Boolean = false,
)

@Serializable
data class TransferRequest(
    @SerialName("to_user_id") val toUserId: String,
    @SerialName("amount") val amount: Long,
    @SerialName("comment") val comment: String? = null,
)

@Serializable
data class PurchasePremiumRequest(
    @SerialName("tier_id") val tierId: String,
)

/* ── Админка ─────────────────────────────────────────────────────────────── */

@Serializable
data class AdminGrantDto(
    @SerialName("target_user_id") val targetUserId: String,
    @SerialName("action") val action: String,
    @SerialName("duration_days") val durationDays: Int? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("amount_silver") val amountSilver: Long? = null,
    @SerialName("reason") val reason: String,
)

@Serializable
data class AdminWalletDto(
    @SerialName("target_user_id") val targetUserId: String,
    @SerialName("amount") val amount: Long,
    @SerialName("reason") val reason: String,
)

@Serializable
data class AdminBanDto(
    @SerialName("target_user_id") val targetUserId: String,
    @SerialName("reason") val reason: String,
    @SerialName("permanent") val permanent: Boolean = false,
)

@Serializable
data class ResolveReportDto(
    @SerialName("status") val status: String,
    @SerialName("comment") val comment: String,
)

@Serializable
data class BroadcastDto(
    @SerialName("text") val text: String,
    @SerialName("audience") val audience: String,
)

/* =========================================================================
   ЗАПРОСЫ «ВТОРОЙ ВОЛНЫ»
   ------------------------------------------------------------------------
   Доменные интерфейсы (:core:domain) описывают продуктовое поведение целиком,
   а базовый REST-набор покрывал лишь его часть. Здесь — недостающие тела
   запросов, чтобы у каждого метода репозитория был честный серверный
   контракт, а не заглушка на клиенте.
   ========================================================================= */

/* ── Чаты ──────────────────────────────────────────────────────────────── */

@Serializable
data class FolderRequest(
    @SerialName("title") val title: String,
    @SerialName("include_types") val includeTypes: List<String> = emptyList(),
)

@Serializable data class ChatTypeRequest(@SerialName("type") val type: String)

@Serializable data class ApproveRequest(@SerialName("approve") val approve: Boolean)

@Serializable data class PinChatRequest(@SerialName("pinned") val pinned: Boolean)

@Serializable data class UnpinAllRequest(@SerialName("chat_id") val chatId: String)

@Serializable
data class DeleteChatRequest(
    @SerialName("for_everyone") val forEveryone: Boolean = false,
)

/* ── Сообщения ─────────────────────────────────────────────────────────── */

@Serializable
data class DeliveryRequest(
    @SerialName("chat_id") val chatId: String,
    @SerialName("message_ids") val messageIds: List<String>,
)

/* ── Сторис ────────────────────────────────────────────────────────────── */

@Serializable data class StoryPinRequest(@SerialName("pinned") val pinned: Boolean)

/* ── Звонки ────────────────────────────────────────────────────────────── */

@Serializable data class MuteRequest(@SerialName("muted") val muted: Boolean)

@Serializable data class CameraRequest(@SerialName("enabled") val enabled: Boolean)

@Serializable data class ScreenRequest(@SerialName("enabled") val enabled: Boolean)

/* ── Маркет и свопы ────────────────────────────────────────────────────── */

@Serializable
data class SwapRequest(
    @SerialName("my_username") val myUsername: String,
    @SerialName("their_username") val theirUsername: String,
    @SerialName("their_user_id") val theirUserId: String,
)

@Serializable data class SwapRespondRequest(@SerialName("accept") val accept: Boolean)

@Serializable
data class SwapDto(
    @SerialName("id") val id: String,
    @SerialName("my_username") val myUsername: String,
    @SerialName("their_username") val theirUsername: String,
    @SerialName("their_user_id") val theirUserId: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("expires_at") val expiresAt: Long,
)

/** Ответ проверки занятости юзернейма. */
@Serializable
data class AvailabilityDto(
    @SerialName("username") val username: String,
    @SerialName("available") val available: Boolean,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("listing_id") val listingId: String? = null,
    @SerialName("reserved_reason") val reservedReason: String? = null,
    @SerialName("invalid_reason") val invalidReason: String? = null,
)

/* ── Кошелёк и Premium ─────────────────────────────────────────────────── */

@Serializable
data class GiftPremiumRequest(
    @SerialName("to_user_id") val toUserId: String,
    @SerialName("tier_id") val tierId: String,
)

/* ── Профиль ───────────────────────────────────────────────────────────── */

@Serializable
data class ReportUserRequest(
    @SerialName("reason") val reason: String,
    @SerialName("comment") val comment: String? = null,
)

/* ── Админка: точечные действия ────────────────────────────────────────── */

@Serializable
data class VerifiedRequest(
    @SerialName("verified") val verified: Boolean,
    @SerialName("reason") val reason: String,
)

@Serializable
data class PremiumGrantRequest(
    @SerialName("days") val days: Int? = null,
    @SerialName("reason") val reason: String,
)

@Serializable
data class DeveloperRequest(
    @SerialName("developer") val developer: Boolean,
    @SerialName("reason") val reason: String,
)

@Serializable
data class RoleRequest(
    @SerialName("role") val role: String,
    @SerialName("reason") val reason: String,
)

/** Универсальное тело «причина действия» — бан, разбан, рефанд, блокировка. */
@Serializable data class ReasonRequest(@SerialName("reason") val reason: String)

@Serializable
data class RestrictRequest(
    @SerialName("reason") val reason: String,
    @SerialName("until_ts") val untilTs: Long,
)

@Serializable
data class ListingPriceRequest(
    @SerialName("price_silver") val priceSilver: Long,
    @SerialName("reason") val reason: String,
)

@Serializable
data class DeleteAnywhereRequest(
    @SerialName("reason") val reason: String,
)

/**
 * Мьют чата.
 *
 * `muted_until == null` при `muted == false` — снять мьют. При
 * `muted == true` и `null` — заглушить навсегда (как в Telegram).
 */
@Serializable
data class MuteChatRequest(
    @SerialName("muted") val muted: Boolean,
    @SerialName("muted_until") val mutedUntil: Long? = null,
)

/** Архив: клиентское состояние, но реплицируется на сервер для веб-версии. */
@Serializable
data class ArchiveChatRequest(@SerialName("archived") val archived: Boolean)
