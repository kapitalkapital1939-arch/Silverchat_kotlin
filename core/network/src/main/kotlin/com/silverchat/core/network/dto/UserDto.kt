package com.silverchat.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* =========================================================================
   DTO ПОЛЬЗОВАТЕЛЯ + МАППЕР В ДОМЕННУЮ МОДЕЛЬ
   ========================================================================= */

@Serializable
data class UserDto(
    @SerialName("id") val id: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("bio") val bio: String? = null,
    @SerialName("avatar") val avatar: AvatarDto? = null,
    @SerialName("banner") val banner: BannerDto? = null,
    @SerialName("badges") val badges: BadgesDto = BadgesDto(),
    @SerialName("flags") val flags: FlagsDto = FlagsDto(),
    @SerialName("presence") val presence: PresenceDto? = null,
    @SerialName("location") val location: LocationRequest? = null,
    @SerialName("working_hours") val workingHours: WorkingHoursRequest? = null,
    @SerialName("premium") val premium: PremiumStatusDto = PremiumStatusDto("none"),
    @SerialName("created_at") val createdAt: Long = 0L,
)

@Serializable
data class AvatarDto(
    @SerialName("static_url") val staticUrl: String? = null,
    @SerialName("animated_url") val animatedUrl: String? = null,
    @SerialName("animation_type") val animationType: String = "none",
    @SerialName("dominant_color") val dominantColor: Long? = null,
)

@Serializable
data class BannerDto(
    @SerialName("static_url") val staticUrl: String? = null,
    @SerialName("animated_url") val animatedUrl: String? = null,
    @SerialName("animation_type") val animationType: String = "none",
    @SerialName("blur_hash") val blurHash: String? = null,
)

@Serializable
data class BadgesDto(
    @SerialName("verified") val verified: Boolean = false,
    @SerialName("developer") val developer: Boolean = false,
    @SerialName("premium") val premium: Boolean = false,
    @SerialName("admin") val admin: Boolean = false,
    @SerialName("owner") val owner: Boolean = false,
)

@Serializable
data class FlagsDto(
    @SerialName("banned") val banned: Boolean = false,
    @SerialName("restricted") val restricted: Boolean = false,
    @SerialName("bot") val bot: Boolean = false,
    @SerialName("is_master_account") val isMasterAccount: Boolean = false,
    @SerialName("deleted") val deleted: Boolean = false,
)

@Serializable
data class PresenceDto(
    @SerialName("kind") val kind: String = "offline", // online | offline | recently | within_week | within_month | long_ago | typing
    @SerialName("last_seen") val lastSeen: Long? = null,
    @SerialName("chat_id") val chatId: String? = null,
)

@Serializable
data class PrivacyDto(
    @SerialName("last_seen_visibility") val lastSeenVisibility: String = "everybody",
    @SerialName("phone_visibility") val phoneVisibility: String = "contacts",
    @SerialName("avatar_visibility") val avatarVisibility: String = "everybody",
    @SerialName("calls_allowed") val callsAllowed: String = "everybody",
    @SerialName("stories_allowed") val storiesAllowed: String = "everybody",
    @SerialName("add_to_groups") val addToGroups: String = "everybody",
    @SerialName("read_receipts") val readReceipts: Boolean = true,
    @SerialName("exceptions") val exceptions: List<String> = emptyList(),
)
