package com.silverchat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Пользователь SilverChat.
 *
 * @property id        внутренний UUID (никогда не показывается в UI)
 * @property username  публичный @username; может быть null до покупки/занятия
 * @property badges    верификация / разработчик / премиум — см. [UserBadges]
 * @property flags     админ-признаки, недоступные обычному клиенту на запись
 */
@Serializable
data class User(
    @SerialName("id") val id: UserId,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("bio") val bio: String? = null,
    @SerialName("avatar") val avatar: AvatarMedia? = null,
    @SerialName("banner") val banner: BannerMedia? = null,
    @SerialName("badges") val badges: UserBadges = UserBadges(),
    @SerialName("flags") val flags: UserFlags = UserFlags(),
    @SerialName("presence") val presence: UserPresence = UserPresence.Offline(0L),
    @SerialName("location") val location: LocationInfo? = null,
    @SerialName("working_hours") val workingHours: WorkingHours? = null,
    @SerialName("premium") val premium: PremiumStatus = PremiumStatus.None,
    @SerialName("created_at") val createdAt: Long = 0L,
) {
    val fullName: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { "@$username" }

    val displayName: String
        get() = fullName

    /** Ники в UI всегда показываются с «@» — см. SearchRepository. */
    val handle: String?
        get() = username?.let { "@$it" }

    val initials: String
        get() = buildString {
            append(firstName.firstOrNull()?.uppercaseChar() ?: '?')
            lastName?.firstOrNull()?.uppercaseChar()?.let(::append)
        }
}

@Serializable
@JvmInline
value class UserId(val raw: String) {
    override fun toString(): String = raw
}

/** Верификационная галочка, статус разработчика, премиум-звезда. Выдаются из админки. */
@Serializable
data class UserBadges(
    @SerialName("verified") val verified: Boolean = false,
    @SerialName("developer") val developer: Boolean = false,
    @SerialName("premium") val premium: Boolean = false,
    @SerialName("admin") val admin: Boolean = false,
    @SerialName("owner") val owner: Boolean = false,
) {
    val isEmpty: Boolean get() = !(verified || developer || premium || admin || owner)
}

/**
 * Системные флаги. `isMasterAccount` — признак супер-аккаунта `@silver`:
 * именно он открывает доступ к AdminPanel (проверяется и на клиенте, и на бэке).
 */
@Serializable
data class UserFlags(
    @SerialName("banned") val banned: Boolean = false,
    @SerialName("restricted") val restricted: Boolean = false,
    @SerialName("bot") val bot: Boolean = false,
    @SerialName("is_master_account") val isMasterAccount: Boolean = false,
    @SerialName("deleted") val deleted: Boolean = false,
)

/**
 * Онлайн-статус. LAST_SEEN скрывается, если пользователь ограничил видимость
 * в настройках приватности — тогда сервер присылает [Recently]/[WithinWeek].
 */
@Serializable
sealed interface UserPresence {
    @Serializable
    @SerialName("online")
    data object Online : UserPresence

    @Serializable
    @SerialName("offline")
    data class Offline(@SerialName("last_seen") val lastSeenAt: Long) : UserPresence

    @Serializable
    @SerialName("recently")
    data object Recently : UserPresence

    @Serializable
    @SerialName("within_week")
    data object WithinWeek : UserPresence

    @Serializable
    @SerialName("within_month")
    data object WithinMonth : UserPresence

    @Serializable
    @SerialName("long_ago")
    data object LongAgo : UserPresence

    @Serializable
    @SerialName("typing")
    data class Typing(@SerialName("chat_id") val chatId: ChatId) : UserPresence
}

/**
 * Аватарка.
 * [ANIMATION_LOTTIE] — векторная анимация (JSON Lottie), доступна всем.
 * [ANIMATION_VIDEO]  — зацикленное видео/WebP; для непрeмиум показывается [staticUrl].
 */
@Serializable
data class AvatarMedia(
    @SerialName("static_url") val staticUrl: String? = null,
    @SerialName("animated_url") val animatedUrl: String? = null,
    @SerialName("animation_type") val animationType: AvatarAnimationType = AvatarAnimationType.NONE,
    @SerialName("dominant_color") val dominantColor: Long? = null,
) {
    val isAnimated: Boolean get() = animationType != AvatarAnimationType.NONE && !animatedUrl.isNullOrBlank()
}

@Serializable
enum class AvatarAnimationType {
    @SerialName("none") NONE,
    @SerialName("lottie") LOTTIE,
    @SerialName("video") VIDEO,
    @SerialName("gif") GIF,
}

/** Баннер профиля/канала. Лимит анимированных баннеров — привилегия Premium. */
@Serializable
data class BannerMedia(
    @SerialName("static_url") val staticUrl: String? = null,
    @SerialName("animated_url") val animatedUrl: String? = null,
    @SerialName("animation_type") val animationType: AvatarAnimationType = AvatarAnimationType.NONE,
    @SerialName("blur_hash") val blurHash: String? = null,
)

/** Кастомный блок «Местоположение» в профиле и в каналах. */
@Serializable
data class LocationInfo(
    @SerialName("lat") val latitude: Double,
    @SerialName("lng") val longitude: Double,
    @SerialName("address") val address: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("visible") val visible: Boolean = true,
)

/** Кастомный блок «Часы работы». */
@Serializable
data class WorkingHours(
    @SerialName("timezone") val timeZone: String = "Europe/Chisinau",
    @SerialName("schedule") val schedule: List<DaySchedule> = emptyList(),
    @SerialName("always_open") val alwaysOpen: Boolean = false,
    @SerialName("visible") val visible: Boolean = true,
) {
    /** true, если прямо сейчас открыто (считается на клиенте по timezone). */
    fun isOpenNow(epochMillis: Long, dayOfWeek: Int, minuteOfDay: Int): Boolean {
        if (alwaysOpen) return true
        val day = schedule.firstOrNull { it.dayOfWeek == dayOfWeek } ?: return false
        return minuteOfDay in day.openMinute..day.closeMinute
    }
}

@Serializable
data class DaySchedule(
    /** 1 = понедельник … 7 = воскресенье (ISO-8601) */
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("open_minute") val openMinute: Int,
    @SerialName("close_minute") val closeMinute: Int,
    @SerialName("closed") val closed: Boolean = false,
)

@Serializable
data class PrivacySettings(
    @SerialName("last_seen_visibility") val lastSeenVisibility: VisibilityRule = VisibilityRule.EVERYBODY,
    @SerialName("phone_visibility") val phoneVisibility: VisibilityRule = VisibilityRule.CONTACTS,
    @SerialName("avatar_visibility") val avatarVisibility: VisibilityRule = VisibilityRule.EVERYBODY,
    @SerialName("calls_allowed") val callsAllowed: VisibilityRule = VisibilityRule.EVERYBODY,
    @SerialName("stories_allowed") val storiesAllowed: VisibilityRule = VisibilityRule.EVERYBODY,
    @SerialName("add_to_groups") val addToGroups: VisibilityRule = VisibilityRule.EVERYBODY,
    @SerialName("read_receipts") val readReceiptsEnabled: Boolean = true,
    @SerialName("exceptions") val exceptions: List<UserId> = emptyList(),
)

@Serializable
enum class VisibilityRule {
    @SerialName("everybody") EVERYBODY,
    @SerialName("contacts") CONTACTS,
    @SerialName("nobody") NOBODY,
}
