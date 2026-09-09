package com.silverchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* =========================================================================
   ОТВЕТЫ БЭКЕНДА
   ========================================================================= */

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("user") val user: UserDto,
    @SerialName("session_id") val sessionId: String,
    @SerialName("ws_url") val wsUrl: String? = null,
)

@Serializable
data class SendOtpResponse(
    @SerialName("phone") val phone: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("retry_after") val retryAfter: Int,
    @SerialName("is_new_user") val isNewUser: Boolean,
)

@Serializable
data class SessionDto(
    @SerialName("id") val id: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("platform") val platform: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("last_active_at") val lastActiveAt: Long,
    @SerialName("ip") val ip: String? = null,
    @SerialName("location_hint") val locationHint: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false,
)

@Serializable
data class ResolveResponse(
    @SerialName("kind") val kind: String, // user | chat | listing | not_found
    @SerialName("user") val user: UserDto? = null,
    @SerialName("chat") val chat: ChatDto? = null,
    @SerialName("listing") val listing: MarketListingDto? = null,
)

@Serializable
data class ChatListResponse(
    @SerialName("chats") val chats: List<ChatDto>,
    @SerialName("folders") val folders: List<ChatFolderDto> = emptyList(),
    @SerialName("total_unread") val totalUnread: Int = 0,
    @SerialName("server_time") val serverTime: Long,
)

@Serializable
data class MembersResponse(
    @SerialName("members") val members: List<ChatMemberDto>,
    @SerialName("total") val total: Int,
    @SerialName("next_offset") val nextOffset: Int? = null,
)

@Serializable
data class MessageListResponse(
    @SerialName("messages") val messages: List<MessageDto>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("chat") val chat: ChatDto? = null,
)

@Serializable
data class InviteLinkDto(
    @SerialName("token") val token: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("name") val name: String? = null,
    @SerialName("max_uses") val maxUses: Int? = null,
    @SerialName("used_count") val usedCount: Int = 0,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("requires_approval") val requiresApproval: Boolean = false,
    @SerialName("revoked") val revoked: Boolean = false,
)

/** Пресigned-URL: медиа грузится напрямую в object storage, минуя Node.js. */
@Serializable
data class UploadTicketResponse(
    @SerialName("upload_id") val uploadId: String,
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("method") val method: String = "PUT",
    @SerialName("headers") val headers: Map<String, String> = emptyMap(),
    @SerialName("public_url") val publicUrl: String,
    @SerialName("thumb_url") val thumbUrl: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
data class IceServersResponse(
    @SerialName("servers") val servers: List<IceServerDto>,
    @SerialName("ttl_seconds") val ttlSeconds: Int,
)

@Serializable
data class IceServerDto(
    @SerialName("urls") val urls: List<String>,
    @SerialName("username") val username: String? = null,
    @SerialName("credential") val credential: String? = null,
)

@Serializable
data class CallSessionDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("initiator_id") val initiatorId: String,
    @SerialName("participants") val participants: List<CallParticipantDto> = emptyList(),
    @SerialName("state") val state: String,
    @SerialName("started_at") val startedAt: Long? = null,
    @SerialName("ended_at") val endedAt: Long? = null,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("is_encrypted") val isEncrypted: Boolean = true,
)

@Serializable
data class CallParticipantDto(
    @SerialName("user_id") val userId: String,
    @SerialName("state") val state: String,
    @SerialName("muted") val muted: Boolean = false,
    @SerialName("camera_off") val cameraOff: Boolean = false,
)

@Serializable
data class CallHistoryDto(
    @SerialName("call_id") val callId: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("peer") val peer: UserDto,
    @SerialName("type") val type: String,
    @SerialName("missed") val missed: Boolean,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("duration_ms") val durationMs: Long = 0L,
)

@Serializable
data class BuyResponse(
    @SerialName("status") val status: String, // success | insufficient_funds | taken | reserved | forbidden
    @SerialName("listing") val listing: MarketListingDto? = null,
    @SerialName("wallet") val wallet: WalletDto? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("reserved_until") val reservedUntil: Long? = null,
    @SerialName("required_silver") val requiredSilver: Long? = null,
    @SerialName("available_silver") val availableSilver: Long? = null,
)

@Serializable
data class MarketFeedDto(
    @SerialName("listings") val listings: List<MarketListingDto>,
    @SerialName("gifts") val gifts: List<GiftDto> = emptyList(),
    @SerialName("premium_tiers") val premiumTiers: List<PremiumTierDto> = emptyList(),
    @SerialName("trending") val trending: List<String> = emptyList(),
    @SerialName("volume_24h") val volume24h: Long = 0L,
    @SerialName("total") val total: Int = 0,
)

/** Серверные настройки, которые влияют на клиент (лимиты, feature flags). */
@Serializable
data class ServerSettingsDto(
    @SerialName("max_message_length") val maxMessageLength: Int = 4096,
    @SerialName("max_upload_mb") val maxUploadMb: Int = 100,
    @SerialName("premium_max_upload_mb") val premiumMaxUploadMb: Int = 4096,
    @SerialName("story_max_duration_sec") val storyMaxDurationSec: Int = 60,
    @SerialName("circle_max_duration_sec") val circleMaxDurationSec: Int = 60,
    @SerialName("min_username_length") val minUsernameLength: Int = 4,
    @SerialName("max_group_members") val maxGroupMembers: Int = 200_000,
    @SerialName("market_fee_percent") val marketFeePercent: Int = 5,
    @SerialName("streak_base_reward") val streakBaseReward: Long = 50,
    @SerialName("features") val features: Map<String, Boolean> = emptyMap(),
)

/**
 * Ответ конвертации подарка в сильверы.
 *
 * Отдельный тип вместо `WalletDto` нужен потому, что UI показывает именно
 * начисленную сумму («+250 серебра»), а не итоговый баланс: баланс мог
 * измениться параллельной транзакцией, и показывать его как «получено» нельзя.
 */
@Serializable
data class ConvertGiftResponse(
    @SerialName("silver_gained") val silverGained: Long,
    @SerialName("wallet") val wallet: WalletDto,
)
