package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.Message
import com.silverchat.core.model.PrivacySettings
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import com.silverchat.core.model.VisibilityRule
import com.silverchat.core.model.WorkingHours
import kotlinx.coroutines.flow.Flow

/**
 * Профиль и карточка пользователя.
 *
 * Покрывает полностью переработанный экран профиля:
 * анимированные аватар/баннер, кастомные блоки «местоположение» и
 * «часы работы», приватность, общие медиа.
 */
interface ProfileRepository {

    fun observeMe(): Flow<User?>

    fun observeUser(userId: UserId): Flow<User?>

    fun observeUserByUsername(username: String): Flow<User?>

    suspend fun updateName(firstName: String, lastName: String?): ScResult<User>

    suspend fun updateBio(bio: String): ScResult<User>

    suspend fun updateUsername(username: String?): ScResult<User>

    /**
     * @param animated true — загружаем видео/Lottie-аватарку.
     *   Анимированный аватар в формате видео доступен только Premium;
     *   при отсутствии подписки вернёт [com.silverchat.core.common.result.ScError.PremiumRequired].
     */
    suspend fun uploadAvatar(localUri: String, animated: Boolean): ScResult<User>

    suspend fun uploadBanner(localUri: String, animated: Boolean): ScResult<User>

    suspend fun setLocation(location: LocationInfo?): ScResult<User>

    suspend fun setWorkingHours(hours: WorkingHours?): ScResult<User>

    fun observePrivacy(): Flow<PrivacySettings>

    suspend fun updatePrivacy(settings: PrivacySettings): ScResult<Unit>

    suspend fun setLastSeenVisibility(rule: VisibilityRule): ScResult<Unit>

    /** Общие медиа в диалоге — вкладка в карточке пользователя. */
    fun observeSharedMedia(chatId: ChatId, kind: SharedMediaKind): Flow<List<Message>>

    suspend fun reportUser(userId: UserId, reason: String, comment: String?): ScResult<Unit>

    suspend fun blockUser(userId: UserId): ScResult<Unit>

    suspend fun unblockUser(userId: UserId): ScResult<Unit>

    fun observeBlockedUsers(): Flow<List<User>>
}

enum class SharedMediaKind { PHOTO, VIDEO, VOICE, FILE, LINK, MUSIC, CIRCLE }
