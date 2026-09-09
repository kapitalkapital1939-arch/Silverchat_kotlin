package com.silverchat.feature.stories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.domain.repository.AuthRepository
import com.silverchat.core.domain.repository.StoryRepository
import com.silverchat.core.domain.usecase.story.ObserveStoriesFeedUseCase
import com.silverchat.core.model.ReactionKind
import com.silverchat.core.model.Story
import com.silverchat.core.model.StoryCluster
import com.silverchat.core.model.StoryId
import com.silverchat.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Полноэкранный просмотрщик сторис.
 *
 * Навигация внутри просмотрщика трёхмерная, и это главное отличие от
 * обычного списка:
 *  - **вперёд/назад** по сторис одного автора (тап по правой/левой трети);
 *  - **следующий/предыдущий автор** — когда сегменты автора закончились;
 *  - **пауза** — удержание пальца или уход приложения в фон.
 *
 * Таймер сегмента реализован через [delay] в корутине, а не через
 * `animateFloatAsState`:
 *  1. длительность видео-сторис берётся из метаданных (`media.durationMs`),
 *     поэтому целевое значение анимации менялось бы на лету;
 *  2. пауза должна останавливать отсчёт точно, без дрейфа;
 *  3. отмена корутины при уходе с экрана происходит вместе с `viewModelScope`.
 *
 * Кластер автора ищется по `authorId` на каждой сборке состояния, а не
 * хранится индексом: лента пересортировывается после каждого просмотра
 * (непросмотренные уходят вниз), и сохранённый индекс стал бы указывать
 * на другого автора.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StoryViewerViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
    observeFeed: ObserveStoriesFeedUseCase,
    authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val authorId: String = savedStateHandle[Routes.Args.STORY_AUTHOR_ID] ?: ""
    private val initialIndex: Int = savedStateHandle[Routes.Args.STORY_INDEX] ?: 0

    private val storyIndex = MutableStateFlow(initialIndex.coerceAtLeast(0))
    private val progress = MutableStateFlow(0f)
    private val paused = MutableStateFlow(false)
    private val showViewers = MutableStateFlow(false)
    private val replyDraft = MutableStateFlow("")
    private val viewers = MutableStateFlow<List<User>>(emptyList())

    private val _events = MutableStateFlow<StoryViewerEvent?>(null)
    val events: StateFlow<StoryViewerEvent?> = _events

    /** Активная корутина таймера; отменяется при смене сторис и в onCleared. */
    private var tickerJob: Job? = null

    /** Отправленные отметки — защита от дублей при перемотке назад. */
    private val viewedSent = mutableSetOf<String>()

    /**
     * ID текущего пользователя.
     *
     * `AuthRepository.currentUserId` — поток, а не значение: сессия
     * восстанавливается из EncryptedSharedPreferences асинхронно и может
     * прийти позже первого кадра. Пустая строка означает «сессии нет».
     */
    private val currentUserId: StateFlow<String> = authRepository.currentUserId
        .map { it?.raw.orEmpty() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    /** Лента кластеров; пустая до появления сессии. */
    private val clusters: StateFlow<List<StoryCluster>> = currentUserId
        .flatMapLatest { id ->
            if (id.isEmpty()) flowOf(emptyList()) else observeFeed(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Изменяемые поля экрана, собранные в один объект.
     *
     * `combine` типизирован до пяти потоков, а здесь их шесть, поэтому
     * позиция/прогресс/пауза/зрители/черновик объединяются в [Controls],
     * а список посмотревших добавляется вторым `combine`.
     */
    private data class Controls(
        val index: Int,
        val progress: Float,
        val paused: Boolean,
        val viewersOpen: Boolean,
        val draft: String,
    )

    private val controls: Flow<Controls> = combine(
        storyIndex,
        progress,
        paused,
        showViewers,
        replyDraft,
    ) { index, prog, isPaused, viewersOpen, draft ->
        Controls(index, prog, isPaused, viewersOpen, draft)
    }

    val uiState: StateFlow<StoryViewerUiState> = combine(
        clusters,
        currentUserId,
        controls,
        viewers,
    ) { list, me, c, viewerList ->
        build(list, me, c, viewerList)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = StoryViewerUiState(),
    )

    private fun build(
        list: List<StoryCluster>,
        me: String,
        c: Controls,
        viewerList: List<User>,
    ): StoryViewerUiState {
        val resolved = list.indexOfFirst { it.authorId.raw == authorId }
        val cluster = list.getOrNull(resolved)
        val stories = cluster?.stories.orEmpty().sortedBy { it.createdAt }
        val safeIndex = if (stories.isEmpty()) 0 else c.index.coerceIn(0, stories.lastIndex)
        val story = stories.getOrNull(safeIndex)

        return StoryViewerUiState(
            cluster = cluster,
            stories = stories,
            currentStory = story,
            currentIndex = safeIndex,
            segmentCount = stories.size,
            progress = c.progress,
            isPaused = c.paused,
            hasNextAuthor = resolved in 0 until list.lastIndex,
            hasPreviousAuthor = resolved > 0,
            author = cluster?.author,
            authorName = cluster?.author?.fullName.orEmpty(),
            // «Мои» — сравнение автора с текущим пользователем, а не с
            // authorId из навигации: иначе чужие сторис получили бы меню автора
            isMine = cluster?.authorId?.raw == me && me.isNotEmpty(),
            showViewers = c.viewersOpen,
            viewers = viewerList,
            replyDraft = c.draft,
            canReply = story?.repliesEnabled == true && story.authorId.raw != me,
        )
    }

    init {
        // Смена текущей сторис запускает таймер и отметку о просмотре.
        // Слушаем собственное состояние, а не ждём команды от экрана:
        // иначе первый кадр показывал бы сторис без движения прогресса.
        viewModelScope.launch {
            uiState
                .map { it.currentStory?.id?.raw }
                .distinctUntilChanged()
                .collect { id ->
                    if (id != null) {
                        markViewedOnce(StoryId(id))
                        restartTicker()
                    }
                }
        }
    }

    /* ── Навигация по сегментам ───────────────────────────────────────── */

    /** Тап по правой трети: следующая сторис, следующий автор или выход. */
    fun next() {
        val state = uiState.value
        when {
            state.stories.isEmpty() -> finish()
            state.currentIndex < state.stories.lastIndex -> storyIndex.value = state.currentIndex + 1
            state.hasNextAuthor -> _events.value = StoryViewerEvent.NextAuthor
            else -> finish()
        }
    }

    /** Тап по левой трети: предыдущая сторис или предыдущий автор. */
    fun previous() {
        val state = uiState.value
        when {
            state.currentIndex > 0 -> storyIndex.value = state.currentIndex - 1
            state.hasPreviousAuthor -> _events.value = StoryViewerEvent.PreviousAuthor
        }
    }

    /** Переход к конкретной сторис (тап по полосе сегментов). */
    fun seekTo(index: Int) {
        storyIndex.value = index
    }

    fun pause() { paused.value = true }

    fun resume() { paused.value = false }

    /* ── Таймер сегмента ──────────────────────────────────────────────── */

    /**
     * Отсчёт прогресса текущего сегмента.
     *
     * Шаг 16 мс ≈ 60 кадров/сек: полоса движется плавно, но `delay`
     * не удерживает кадр и не нагружает главный поток.
     */
    private fun restartTicker() {
        tickerJob?.cancel()
        progress.value = 0f

        val story = uiState.value.currentStory ?: return
        val duration = story.media.durationMs.coerceIn(MIN_SEGMENT_MS, MAX_SEGMENT_MS)

        tickerJob = viewModelScope.launch {
            var elapsed = 0L
            while (isActive && elapsed < duration) {
                delay(TICKER_STEP_MS)
                // На паузе время не начисляется: удержание пальца должно
                // «замораживать» сторис, а не замедлять её
                if (paused.value) continue
                elapsed += TICKER_STEP_MS
                progress.value = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            }
            if (isActive) next()
        }
    }

    /* ── Отметка о просмотре ──────────────────────────────────────────── */

    /**
     * Отмечает сторис просмотренной ровно один раз.
     *
     * Ошибка намеренно не пробрасывается в UI: кольцо останется цветным —
     * заметный, но некритичный дефект, а прерывать просмотр нельзя.
     * Повторная отметка идемпотентна на сервере.
     */
    private fun markViewedOnce(storyId: StoryId) {
        if (!viewedSent.add(storyId.raw)) return
        viewModelScope.launch { storyRepository.markViewed(storyId) }
    }

    /* ── Реакции и ответы ─────────────────────────────────────────────── */

    fun onReplyDraftChanged(text: String) { replyDraft.value = text }

    fun sendReply() = viewModelScope.launch {
        val story = uiState.value.currentStory ?: return@launch
        val text = replyDraft.value.trim()
        if (text.isEmpty()) return@launch
        when (storyRepository.reply(story.id, text)) {
            is ScResult.Success -> replyDraft.value = ""
            is ScResult.Failure -> _events.value = StoryViewerEvent.Error("Не удалось отправить ответ")
            ScResult.Loading -> Unit
        }
    }

    /** Быстрая реакция на сторис — отправляется без текстового ответа. */
    fun react(kind: ReactionKind) = viewModelScope.launch {
        val story = uiState.value.currentStory ?: return@launch
        storyRepository.react(story.id, kind)
    }

    /**
     * Скрытный просмотр — привилегия Premium (STORY_STEALTH).
     *
     * Результат приходит событием: право проверяет сервер, и показывать
     * «просмотрено анонимно» до подтверждения нельзя.
     */
    fun viewStealth() = viewModelScope.launch {
        val story = uiState.value.currentStory ?: return@launch
        _events.value = when (storyRepository.viewStealth(story.id)) {
            is ScResult.Success -> StoryViewerEvent.StealthGranted
            is ScResult.Failure -> StoryViewerEvent.Error("Скрытный просмотр доступен в Premium")
            ScResult.Loading -> null
        }
    }

    /* ── Список посмотревших ──────────────────────────────────────────── */

    /** Доступно только автору: чужой список зрителей — утечка приватности. */
    fun toggleViewers() {
        if (!uiState.value.isMine) return
        showViewers.value = !showViewers.value
        if (showViewers.value) loadViewers()
    }

    private fun loadViewers() = viewModelScope.launch {
        val story = uiState.value.currentStory ?: return@launch
        viewers.value = when (val result = storyRepository.viewers(story.id)) {
            is ScResult.Success -> result.data
            else -> emptyList()
        }
    }

    /* ── Управление публикацией ───────────────────────────────────────── */

    fun deleteCurrent() = viewModelScope.launch {
        val story = uiState.value.currentStory ?: return@launch
        when (storyRepository.delete(story.id)) {
            is ScResult.Success -> _events.value = StoryViewerEvent.Close
            is ScResult.Failure -> _events.value = StoryViewerEvent.Error("Не удалось удалить сторис")
            ScResult.Loading -> Unit
        }
    }

    /** Закреплённые сторис не исчезают из профиля после истечения срока. */
    fun pinCurrent() = viewModelScope.launch {
        val story = uiState.value.currentStory ?: return@launch
        storyRepository.pinToProfile(story.id, pinned = !story.pinnedToProfile)
    }

    fun consumeEvent() {
        _events.value = null
    }

    private fun finish() {
        tickerJob?.cancel()
        _events.value = StoryViewerEvent.Close
    }

    override fun onCleared() {
        tickerJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICKER_STEP_MS = 16L

        /** Фото без метаданных показываем не быстрее секунды на сегмент. */
        const val MIN_SEGMENT_MS = 1_000L

        /** Видео длиннее минуты режем: удержание внимания падает, трафик растёт. */
        const val MAX_SEGMENT_MS = 60_000L
    }
}

/** Одноразовые команды экрану: навигация между авторами и закрытие. */
sealed interface StoryViewerEvent {
    data object NextAuthor : StoryViewerEvent
    data object PreviousAuthor : StoryViewerEvent
    data object Close : StoryViewerEvent
    data object StealthGranted : StoryViewerEvent
    data class Error(val message: String) : StoryViewerEvent
}

data class StoryViewerUiState(
    val cluster: StoryCluster? = null,
    val stories: List<Story> = emptyList(),
    val currentStory: Story? = null,
    val currentIndex: Int = 0,
    val segmentCount: Int = 0,
    val progress: Float = 0f,
    val isPaused: Boolean = false,
    val hasNextAuthor: Boolean = false,
    val hasPreviousAuthor: Boolean = false,
    val author: User? = null,
    val authorName: String = "",
    val isMine: Boolean = false,
    val showViewers: Boolean = false,
    val viewers: List<User> = emptyList(),
    val replyDraft: String = "",
    val canReply: Boolean = false,
)
