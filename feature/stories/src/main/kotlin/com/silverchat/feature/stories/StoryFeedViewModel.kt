package com.silverchat.feature.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.domain.repository.AuthRepository
import com.silverchat.core.domain.repository.StoryRepository
import com.silverchat.core.domain.usecase.story.ObserveStoriesFeedUseCase
import com.silverchat.core.model.Story
import com.silverchat.core.model.StoryCluster
import com.silverchat.core.model.StoryId
import com.silverchat.core.model.StoryPrivacy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Лента сторис и архив собственных публикаций.
 *
 * Ключевые решения:
 *  1. **Сортировка делегирована [ObserveStoriesFeedUseCase]**: «мои» первыми,
 *     затем непросмотренные по свежести, затем просмотренные. Держать этот
 *     порядок в UI означало бы пересчитывать его на каждую recomposition.
 *  2. **`currentUserId` — это поток, а не значение.** Сессия может появиться
 *     позже первого кадра (восстановление токена из EncryptedSharedPreferences),
 *     поэтому use case переключается через `flatMapLatest`, а не вызывается
 *     один раз с пустым ID.
 *  3. **`WhileSubscribed(5000)`** — каждый просмотр меняет `seenByMe` и
 *     пересобирает ленту; держать поток живым при свёрнутом приложении
 *     незачем, а 5 секунд покрывают поворот экрана и переход в просмотрщик.
 *  4. **Отметка о просмотре уходит сразу**, а не «при выходе»: иначе после
 *     убийства процесса кольцо останется цветным и пользователь увидит
 *     уже просмотренные сторис как новые.
 *  5. **Режим архива — отдельная ветка потока.** В архиве показываются только
 *     свои публикации (включая истёкшие), в ленте — только активные чужие.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StoryFeedViewModel @Inject constructor(
    private val observeFeed: ObserveStoriesFeedUseCase,
    private val storyRepository: StoryRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** Автор, чьи сторис открыты в просмотрщике — для подсветки ячейки трея. */
    private val openedAuthorId = MutableStateFlow<String?>(null)

    /** Режим архива: только собственные публикации. */
    private val archiveMode = MutableStateFlow(false)

    val uiState: StateFlow<StoryFeedUiState> = combine(
        authRepository.currentUserId.map { it?.raw.orEmpty() },
        archiveMode,
        openedAuthorId,
    ) { userId, archive, opened -> FeedParams(userId, archive, opened) }
        .flatMapLatest { params ->
            when {
                params.userId.isEmpty() -> flowOf(emptyUiState(params))
                params.archiveMode -> storyRepository.observeArchive().map { archive ->
                    StoryFeedUiState(
                        clusters = emptyList(),
                        myStories = archive.sortedByDescending { it.createdAt },
                        archiveMode = true,
                        openedAuthorId = params.openedAuthorId,
                        totalUnseen = 0,
                        expiredCount = archive.count { it.isExpired },
                    )
                }

                else -> combine(
                    observeFeed(params.userId),
                    storyRepository.observeMyStories(),
                ) { clusters, mine ->
                    StoryFeedUiState(
                        clusters = clusters,
                        myStories = mine.sortedByDescending { it.createdAt },
                        archiveMode = false,
                        openedAuthorId = params.openedAuthorId,
                        totalUnseen = clusters.sumOf { it.unseenCount },
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = StoryFeedUiState(),
        )

    /** Внутренние параметры, чтобы не плодить вложенные `combine`. */
    private data class FeedParams(
        val userId: String,
        val archiveMode: Boolean,
        val openedAuthorId: String?,
    )

    private fun emptyUiState(params: FeedParams) = StoryFeedUiState(
        archiveMode = params.archiveMode,
        openedAuthorId = params.openedAuthorId,
    )

    /** Открыт просмотрщик — запоминаем автора для подсветки в трее. */
    fun onViewerOpened(authorId: String) {
        openedAuthorId.value = authorId
    }

    fun onViewerClosed() {
        openedAuthorId.value = null
    }

    fun toggleArchive() {
        archiveMode.value = !archiveMode.value
    }

    /**
     * Отметка о просмотре.
     *
     * Ошибка намеренно не пробрасывается в UI: кольцо останется цветным —
     * заметный, но некритичный дефект, а прерывать просмотр из-за него
     * нельзя. Повторная отметка идемпотентна на сервере.
     */
    fun markViewed(storyId: StoryId) = viewModelScope.launch {
        storyRepository.markViewed(storyId)
    }

    /**
     * Скрытный просмотр — привилегия Premium (STORY_STEALTH).
     *
     * Возвращает `Job` в состоянии, а не `Boolean`: вызывающий экран ждёт
     * результат через `await()`, потому что синхронный ответ из корутины
     * невозможен, а показывать сторис до подтверждения права нельзя.
     */
    fun viewStealth(storyId: StoryId) = viewModelScope.launch {
        storyRepository.viewStealth(storyId)
    }

    fun deleteStory(storyId: StoryId) = viewModelScope.launch {
        storyRepository.delete(storyId)
    }

    /** Закрепление сторис в профиле: закреплённые не исчезают после истечения. */
    fun togglePin(story: Story) = viewModelScope.launch {
        storyRepository.pinToProfile(story.id, pinned = !story.pinnedToProfile)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class StoryFeedUiState(
    val clusters: List<StoryCluster> = emptyList(),
    val myStories: List<Story> = emptyList(),
    val archiveMode: Boolean = false,
    val openedAuthorId: String? = null,
    val totalUnseen: Int = 0,
    val expiredCount: Int = 0,
) {
    val isEmpty: Boolean get() = clusters.isEmpty() && myStories.isEmpty()
}

/** Русская подпись уровня приватности для экрана публикации. */
fun StoryPrivacy.titleRu(): String = when (this) {
    StoryPrivacy.EVERYONE -> "Все"
    StoryPrivacy.CONTACTS -> "Мои контакты"
    StoryPrivacy.CLOSE_FRIENDS -> "Близкие друзья"
    StoryPrivacy.SELECTED -> "Выбранные"
    StoryPrivacy.PRIVATE -> "Только я"
}

/** Русское пояснение: кто именно увидит публикацию. */
fun StoryPrivacy.hintRu(): String = when (this) {
    StoryPrivacy.EVERYONE -> "Сторис увидят все, включая незнакомых людей"
    StoryPrivacy.CONTACTS -> "Только те, у кого сохранён ваш номер"
    StoryPrivacy.CLOSE_FRIENDS -> "Только список близких друзей"
    StoryPrivacy.SELECTED -> "Только отмеченные вами пользователи"
    StoryPrivacy.PRIVATE -> "Никто не увидит; сторис попадёт в ваш архив"
}
