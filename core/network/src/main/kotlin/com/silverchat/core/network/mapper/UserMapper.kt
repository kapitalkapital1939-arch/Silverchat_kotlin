package com.silverchat.core.network.mapper

import com.silverchat.core.model.AvatarAnimationType
import com.silverchat.core.model.AvatarMedia
import com.silverchat.core.model.BannerMedia
import com.silverchat.core.model.DaySchedule
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.PremiumStatus
import com.silverchat.core.model.PremiumTierId
import com.silverchat.core.model.PrivacySettings
import com.silverchat.core.model.User
import com.silverchat.core.model.UserBadges
import com.silverchat.core.model.UserFlags
import com.silverchat.core.model.UserId
import com.silverchat.core.model.UserPresence
import com.silverchat.core.model.VisibilityRule
import com.silverchat.core.model.WorkingHours
import com.silverchat.core.network.dto.AvatarDto
import com.silverchat.core.network.dto.BannerDto
import com.silverchat.core.network.dto.LocationRequest
import com.silverchat.core.network.dto.PremiumStatusDto
import com.silverchat.core.network.dto.PrivacyDto
import com.silverchat.core.network.dto.UserDto
import com.silverchat.core.network.dto.WorkingHoursRequest

/* =========================================================================
   МАППЕРЫ: DTO (сеть) -> модель (домен)
   ------------------------------------------------------------------------
   Все преобразования сосредоточены в одном пакете. Правила:
    - мапперы НЕ бросают исключений: неизвестное значение -> значение по
      умолчанию, иначе одно новое поле на бэкенде ломает весь клиент;
    - маппер детерминирован и не ходит в сеть/БД -> тестируется тривиально.
   ========================================================================= */

fun UserDto.toDomain(): User = User(
    id = UserId(id),
    firstName = firstName,
    lastName = lastName,
    username = username,
    phone = phone,
    bio = bio,
    avatar = avatar?.toDomain(),
    banner = banner?.toDomain(),
    badges = badges.toDomain(),
    flags = flags.toDomain(),
    presence = presence?.toDomain() ?: UserPresence.Offline(0L),
    location = location?.toDomain(),
    workingHours = workingHours?.toDomain(),
    premium = premium.toDomain(),
    createdAt = createdAt,
)

fun AvatarDto.toDomain(): AvatarMedia = AvatarMedia(
    staticUrl = staticUrl,
    animatedUrl = animatedUrl,
    animationType = animationType.toAnimationType(),
    dominantColor = dominantColor,
)

fun BannerDto.toDomain(): BannerMedia = BannerMedia(
    staticUrl = staticUrl,
    animatedUrl = animatedUrl,
    animationType = animationType.toAnimationType(),
    blurHash = blurHash,
)

private fun String.toAnimationType(): AvatarAnimationType = when (this) {
    "lottie" -> AvatarAnimationType.LOTTIE
    "video" -> AvatarAnimationType.VIDEO
    "gif" -> AvatarAnimationType.GIF
    else -> AvatarAnimationType.NONE
}

fun com.silverchat.core.network.dto.BadgesDto.toDomain(): UserBadges = UserBadges(
    verified = verified,
    developer = developer,
    premium = premium,
    admin = admin,
    owner = owner,
)

fun com.silverchat.core.network.dto.FlagsDto.toDomain(): UserFlags = UserFlags(
    banned = banned,
    restricted = restricted,
    bot = bot,
    isMasterAccount = isMasterAccount,
    deleted = deleted,
)

fun com.silverchat.core.network.dto.PresenceDto.toDomain(): UserPresence = when (kind) {
    "online" -> UserPresence.Online
    "recently" -> UserPresence.Recently
    "within_week" -> UserPresence.WithinWeek
    "within_month" -> UserPresence.WithinMonth
    "long_ago" -> UserPresence.LongAgo
    "typing" -> chatId?.let { UserPresence.Typing(com.silverchat.core.model.ChatId(it)) }
        ?: UserPresence.Offline(lastSeen ?: 0L)
    else -> UserPresence.Offline(lastSeen ?: 0L)
}

fun PrivacyDto.toDomain(): PrivacySettings = PrivacySettings(
    lastSeenVisibility = lastSeenVisibility.toVisibility(),
    phoneVisibility = phoneVisibility.toVisibility(),
    avatarVisibility = avatarVisibility.toVisibility(),
    callsAllowed = callsAllowed.toVisibility(),
    storiesAllowed = storiesAllowed.toVisibility(),
    addToGroups = addToGroups.toVisibility(),
    readReceiptsEnabled = readReceipts,
    exceptions = exceptions.map { UserId(it) },
)

private fun String.toVisibility(): VisibilityRule = when (this) {
    "contacts" -> VisibilityRule.CONTACTS
    "nobody" -> VisibilityRule.NOBODY
    else -> VisibilityRule.EVERYBODY
}

fun LocationRequest.toDomain(): LocationInfo = LocationInfo(
    latitude = latitude,
    longitude = longitude,
    address = address,
    title = title,
    visible = visible,
)

fun LocationInfo.toDto(): LocationRequest = LocationRequest(
    latitude = latitude,
    longitude = longitude,
    address = address,
    title = title,
    visible = visible,
)

fun WorkingHoursRequest.toDomain(): WorkingHours = WorkingHours(
    timeZone = timezone,
    schedule = schedule.map { DaySchedule(it.dayOfWeek, it.openMinute, it.closeMinute, it.closed) },
    alwaysOpen = alwaysOpen,
    visible = visible,
)

fun WorkingHours.toDto(): WorkingHoursRequest = WorkingHoursRequest(
    timezone = timeZone,
    alwaysOpen = alwaysOpen,
    schedule = schedule.map {
        com.silverchat.core.network.dto.DayScheduleDto(it.dayOfWeek, it.openMinute, it.closeMinute, it.closed)
    },
    visible = visible,
)

fun PremiumStatusDto.toDomain(): PremiumStatus = when (kind) {
    "active" -> PremiumStatus.Active(
        tier = PremiumTierId(tier ?: "premium_month"),
        expiresAt = expiresAt ?: 0L,
        autoRenew = autoRenew,
        giftedBy = giftedBy?.let { UserId(it) },
    )

    "expired" -> PremiumStatus.Expired(expiredAt ?: 0L)
    "lifetime" -> PremiumStatus.Lifetime
    else -> PremiumStatus.None
}
