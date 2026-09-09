package com.silverchat.core.domain.usecase.profile

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.format.UsernameFormatter
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.base.SuspendUseCase
import com.silverchat.core.domain.repository.ProfileRepository
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.model.AvatarAnimationType
import com.silverchat.core.model.AvatarMedia
import com.silverchat.core.model.BannerMedia
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.PremiumPerkCode
import com.silverchat.core.model.User
import com.silverchat.core.model.WorkingHours
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Обновление username с полной валидацией до запроса к серверу. */
class UpdateUsernameUseCase @Inject constructor(
    private val repository: ProfileRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<String?, User>(dispatchers) {

    override suspend fun execute(params: String?): ScResult<User> {
        if (params.isNullOrBlank()) return repository.updateUsername(null)

        return when (val validation = UsernameFormatter.validate(params)) {
            is com.silverchat.core.common.format.UsernameValidation.Valid ->
                repository.updateUsername(validation.value)

            is com.silverchat.core.common.format.UsernameValidation.Invalid ->
                ScResult.Failure(ScError.Validation(validation.reason, field = "username"))

            is com.silverchat.core.common.format.UsernameValidation.Reserved ->
                ScResult.Failure(ScError.Forbidden(validation.reason, requiredPermission = "username.reserved"))
        }
    }
}

/**
 * Загрузка аватарки с поддержкой анимации.
 *
 * Анимированная аватарка типа VIDEO — Premium-перк. LOTTIE и GIF доступны всем:
 * Lottie дешевле по трафику и масштабируется без потери качества, поэтому
 * базовая анимация не отдаётся в премиум.
 */
class UploadAvatarUseCase @Inject constructor(
    private val repository: ProfileRepository,
    private val wallet: WalletRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<UploadAvatarParams, User>(dispatchers) {

    override suspend fun execute(params: UploadAvatarParams): ScResult<User> {
        if (params.animationType == AvatarAnimationType.VIDEO) {
            val allowed = wallet.observePerkAvailable(PremiumPerkCode.ANIMATED_AVATAR).first()
            if (!allowed) {
                return ScResult.Failure(
                    ScError.PremiumRequired(
                        message = "Видео-аватарки доступны в SilverChat Premium",
                        perk = PremiumPerkCode.ANIMATED_AVATAR.name,
                    ),
                )
            }
        }
        return repository.uploadAvatar(
            localUri = params.localUri,
            animated = params.animationType != AvatarAnimationType.NONE,
        )
    }
}

data class UploadAvatarParams(
    val localUri: String,
    val animationType: AvatarAnimationType = AvatarAnimationType.NONE,
)

/** Загрузка баннера профиля/канала (анимированный — Premium). */
class UploadBannerUseCase @Inject constructor(
    private val repository: ProfileRepository,
    private val wallet: WalletRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<UploadBannerParams, User>(dispatchers) {

    override suspend fun execute(params: UploadBannerParams): ScResult<User> {
        if (params.animationType == AvatarAnimationType.VIDEO ||
            params.animationType == AvatarAnimationType.LOTTIE
        ) {
            val allowed = wallet.observePerkAvailable(PremiumPerkCode.ANIMATED_BANNER).first()
            if (!allowed) {
                return ScResult.Failure(
                    ScError.PremiumRequired(
                        message = "Анимированные баннеры доступны в SilverChat Premium",
                        perk = PremiumPerkCode.ANIMATED_BANNER.name,
                    ),
                )
            }
        }
        return repository.uploadBanner(
            localUri = params.localUri,
            animated = params.animationType != AvatarAnimationType.NONE,
        )
    }
}

data class UploadBannerParams(
    val localUri: String,
    val animationType: AvatarAnimationType = AvatarAnimationType.NONE,
)

/**
 * Кастомные блоки профиля: местоположение и часы работы.
 * Валидирует координаты и корректность интервалов (open < close).
 */
class UpdateProfileBlocksUseCase @Inject constructor(
    private val repository: ProfileRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<UpdateProfileBlocksParams, User>(dispatchers) {

    override suspend fun execute(params: UpdateProfileBlocksParams): ScResult<User> {
        params.location?.let { loc ->
            if (loc.latitude !in -90.0..90.0 || loc.longitude !in -180.0..180.0) {
                return ScResult.Failure(ScError.Validation("Некорректные координаты", "location"))
            }
        }

        params.workingHours?.let { hours ->
            hours.schedule.forEach { day ->
                if (!day.closed && day.openMinute >= day.closeMinute) {
                    return ScResult.Failure(
                        ScError.Validation(
                            "Время закрытия должно быть позже времени открытия",
                            "working_hours",
                        ),
                    )
                }
                if (day.dayOfWeek !in 1..7) {
                    return ScResult.Failure(ScError.Validation("Некорректный день недели", "working_hours"))
                }
            }
        }

        val locationResult = repository.setLocation(params.location)
        if (locationResult.isFailure) return locationResult.map { it }

        val hoursResult = repository.setWorkingHours(params.workingHours)
        if (hoursResult.isFailure) return hoursResult.map { it }

        return locationResult
    }
}

data class UpdateProfileBlocksParams(
    val location: LocationInfo?,
    val workingHours: WorkingHours?,
)

/** Сборка карточки пользователя для показа в bottom sheet / на экране профиля. */
data class UserProfileCard(
    val user: User,
    val isMe: Boolean,
    val sharedMediaCount: Int,
    val commonChatsCount: Int,
    val isOpenNow: Boolean?,
    val premiumPerks: List<PremiumPerkCode>,
)

class BuildUserProfileCardUseCase @Inject constructor(
    private val wallet: WalletRepository,
) {
    suspend operator fun invoke(
        user: User,
        currentUserId: String,
        sharedMediaCount: Int = 0,
        commonChatsCount: Int = 0,
    ): UserProfileCard {
        val isOpenNow = user.workingHours?.let { hours ->
            val cal = java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone(hours.timeZone),
            )
            val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK).let {
                // Calendar: SUNDAY=1 -> ISO: MON=1..SUN=7
                if (it == java.util.Calendar.SUNDAY) 7 else it - 1
            }
            val minuteOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            hours.isOpenNow(System.currentTimeMillis(), dayOfWeek, minuteOfDay)
        }

        return UserProfileCard(
            user = user,
            isMe = user.id.raw == currentUserId,
            sharedMediaCount = sharedMediaCount,
            commonChatsCount = commonChatsCount,
            isOpenNow = isOpenNow,
            premiumPerks = if (user.badges.premium) PremiumPerkCode.entries else emptyList(),
        )
    }
}

/** Заглушка для превью аватара до загрузки (blurhash/доминирующий цвет). */
fun AvatarMedia?.placeholderColor(): Long =
    this?.dominantColor ?: 0xFF3E82F7

fun BannerMedia?.placeholderGradient(): List<Long> =
    listOf(0xFF3E82F7, 0xFF8B5CF6)
