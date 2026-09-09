package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.ReactionKind
import com.silverchat.core.model.Story
import com.silverchat.core.model.StoryCluster
import com.silverchat.core.model.StoryId
import com.silverchat.core.model.StoryPrivacy
import com.silverchat.core.model.User
import kotlinx.coroutines.flow.Flow

/** Лента сторис: публикация, просмотр, реакции, ответы, статистика. */
interface StoryRepository {

    /** Лента, сгруппированная по авторам — одна ячейка = один кластер. */
    fun observeFeed(): Flow<List<StoryCluster>>

    fun observeStory(storyId: StoryId): Flow<Story?>

    fun observeMyStories(): Flow<List<Story>>

    /** Архив сторис в профиле (закреплённые + истёкшие). */
    fun observeArchive(): Flow<List<Story>>

    suspend fun publish(
        localUri: String?,
        caption: String?,
        privacy: StoryPrivacy,
        backgroundGradient: List<Long>?,
        overlayText: String?,
        location: LocationInfo?,
        durationMs: Long = 5_000L,
    ): ScResult<Story>

    suspend fun markViewed(storyId: StoryId): ScResult<Unit>

    suspend fun react(storyId: StoryId, kind: ReactionKind): ScResult<Unit>

    suspend fun reply(storyId: StoryId, text: String): ScResult<Unit>

    suspend fun delete(storyId: StoryId): ScResult<Unit>

    suspend fun pinToProfile(storyId: StoryId, pinned: Boolean): ScResult<Unit>

    suspend fun viewers(storyId: StoryId): ScResult<List<User>>

    /** Скрытный просмотр — привилегия Premium (STORY_STEALTH). */
    suspend fun viewStealth(storyId: StoryId): ScResult<Unit>
}
