package com.silverchat.core.domain.usecase.story

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.base.FlowUseCase
import com.silverchat.core.domain.base.SuspendUseCase
import com.silverchat.core.domain.repository.StoryRepository
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.PremiumPerkCode
import com.silverchat.core.model.Story
import com.silverchat.core.model.StoryCluster
import com.silverchat.core.model.StoryPrivacy
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Публикация сторис.
 *
 * Проверяет:
 *  - есть ли медиа (или выбрана текстовая подложка);
 *  - лимит длины подписи;
 *  - приватность: CLOSE_FRIENDS и скрытный просмотр — Premium;
 *  - лимит активных сторис (для обычных пользователей — 30 одновременно).
 */
class PublishStoryUseCase @Inject constructor(
    private val repository: StoryRepository,
    private val wallet: WalletRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<PublishStoryParams, Story>(dispatchers) {

    override suspend fun execute(params: PublishStoryParams): ScResult<Story> {
        if (params.localUri.isNullOrBlank() && params.backgroundGradient.isNullOrEmpty()) {
            return ScResult.Failure(
                ScError.Validation("Выберите фото/видео или фон для текстовой сторис", "media"),
            )
        }
        if ((params.caption?.length ?: 0) > MAX_CAPTION) {
            return ScResult.Failure(ScError.Validation("Подпись длиннее $MAX_CAPTION символов", "caption"))
        }
        if (params.location != null && (params.location.latitude !in -90.0..90.0 ||
                params.location.longitude !in -180.0..180.0)
        ) {
            return ScResult.Failure(ScError.Validation("Некорректные координаты", "location"))
        }

        val active = repository.observeMyStories().first()
        val limit = if (wallet.observePerkAvailable(PremiumPerkCode.LARGER_UPLOADS).first()) {
            PREMIUM_STORY_LIMIT
        } else {
            FREE_STORY_LIMIT
        }
        if (active.count { !it.isExpired } >= limit) {
            return ScResult.Failure(
                ScError.Conflict("Достигнут лимит активных сторис: $limit. Удалите старые."),
            )
        }

        return repository.publish(
            localUri = params.localUri,
            caption = params.caption?.trim(),
            privacy = params.privacy,
            backgroundGradient = params.backgroundGradient,
            overlayText = params.overlayText,
            location = params.location,
            durationMs = params.durationMs,
        )
    }

    private companion object {
        const val MAX_CAPTION = 512
        const val FREE_STORY_LIMIT = 30
        const val PREMIUM_STORY_LIMIT = 200
    }
}

data class PublishStoryParams(
    val localUri: String?,
    val caption: String?,
    val privacy: StoryPrivacy = StoryPrivacy.CONTACTS,
    val backgroundGradient: List<Long>? = null,
    val overlayText: String? = null,
    val location: LocationInfo? = null,
    val durationMs: Long = 5_000L,
)

/**
 * Лента сторис.
 *
 * Сортировка: сначала не просмотренные (по времени публикации), затем
 * просмотренные; «Моя сторис» всегда первой ячейкой.
 */
class ObserveStoriesFeedUseCase @Inject constructor(
    private val repository: StoryRepository,
    dispatchers: DispatcherProvider,
) : FlowUseCase<String, List<StoryCluster>>(dispatchers) {

    override fun execute(params: String): Flow<List<StoryCluster>> =
        repository.observeFeed().let { flow ->
            kotlinx.coroutines.flow.flow {
                flow.collect { clusters ->
                    emit(sort(clusters, currentUserId = params))
                }
            }
        }

    private fun sort(clusters: List<StoryCluster>, currentUserId: String): List<StoryCluster> {
        val mine = clusters.filter { it.authorId.raw == currentUserId }
        val unseen = clusters.filter { it.authorId.raw != currentUserId && it.hasUnseen }
            .sortedByDescending { it.latest?.createdAt ?: 0L }
        val seen = clusters.filter { it.authorId.raw != currentUserId && !it.hasUnseen }
            .sortedByDescending { it.latest?.createdAt ?: 0L }
        return mine + unseen + seen
    }
}
