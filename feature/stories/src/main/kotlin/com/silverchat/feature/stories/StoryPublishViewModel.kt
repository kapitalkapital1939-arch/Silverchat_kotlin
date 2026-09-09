package com.silverchat.feature.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.MediaRepository
import com.silverchat.core.domain.repository.VideoQuality
import com.silverchat.core.domain.usecase.story.PublishStoryParams
import com.silverchat.core.domain.usecase.story.PublishStoryUseCase
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.StoryPrivacy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Публикация сторис.
 *
 * Порядок операций фиксирован и важен:
 *  1. **сжатие** — видео уходит в [VideoQuality.HIGH] (1080p / 3.5 Мбит/с),
 *     иначе сторис загружается минутами и не помещается в лимит трафика;
 *  2. **публикация** — `PublishStoryUseCase` сам загружает медиа и создаёт
 *     запись, поэтому отдельного `upload` здесь нет: двойная загрузка
 *     удвоила бы трафик и оставила сиротские файлы в хранилище.
 *
 * Текстовая сторис — отдельный режим: `localUri == null`, а содержимое
 * задаётся градиентом и наложенным текстом. Это не «заглушка», а
 * полноценный тип контента (см. `StoryMedia.backgroundGradient`).
 */
@HiltViewModel
class StoryPublishViewModel @Inject constructor(
    private val publishStory: PublishStoryUseCase,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryPublishUiState())
    val uiState: StateFlow<StoryPublishUiState> = _uiState.asStateFlow()

    /* ── Выбор содержимого ────────────────────────────────────────────── */

    /**
     * URI приходит из экрана (системный пикер или камера).
     *
     * ViewModel не держит `Context` и не знает про `ActivityResultContract`:
     * это позволяет тестировать его без Robolectric.
     */
    fun onMediaPicked(uri: String, isVideo: Boolean) {
        _uiState.update {
            it.copy(
                localUri = uri,
                isVideo = isVideo,
                isTextMode = false,
                errorText = null,
            )
        }
        if (isVideo) compressVideo(uri)
    }

    /** Текстовая сторис: медиа нет, есть градиент и надпись. */
    fun switchToTextMode() {
        _uiState.update {
            it.copy(localUri = null, isVideo = false, isTextMode = true, errorText = null)
        }
    }

    fun switchToMediaMode() {
        _uiState.update { it.copy(isTextMode = false) }
    }

    fun onCaptionChanged(text: String) {
        _uiState.update { it.copy(caption = text.take(MAX_CAPTION)) }
    }

    fun onOverlayTextChanged(text: String) {
        _uiState.update { it.copy(overlayText = text.take(MAX_OVERLAY)) }
    }

    fun onPrivacyChanged(privacy: StoryPrivacy) {
        _uiState.update { it.copy(privacy = privacy) }
    }

    fun onGradientSelected(gradientArgb: List<Long>) {
        _uiState.update { it.copy(backgroundGradient = gradientArgb) }
    }

    /** Геометка — фирменный блок SilverChat; видна только если включена. */
    fun onLocationChanged(location: LocationInfo?) {
        _uiState.update { it.copy(location = location) }
    }

    fun onDurationChanged(durationMs: Long) {
        _uiState.update { it.copy(durationMs = durationMs) }
    }

    /** Ответы на сторис можно отключить: не все хотят получать сообщения. */
    fun toggleRepliesEnabled() {
        _uiState.update { it.copy(repliesEnabled = !it.repliesEnabled) }
    }

    /* ── Сжатие видео ─────────────────────────────────────────────────── */

    /**
     * Сжатие видео до параметров сторис.
     *
     * Прогресс показываем отдельным полем, а не общей `isPublishing`:
     * сжатие занимает десятки секунд, и без индикации экран выглядит зависшим.
     */
    private fun compressVideo(uri: String) = viewModelScope.launch {
        _uiState.update { it.copy(isCompressing = true, errorText = null) }
        when (val result = mediaRepository.compressVideo(uri, VideoQuality.HIGH)) {
            is ScResult.Success -> _uiState.update {
                it.copy(localUri = result.data.uri, isCompressing = false)
            }

            is ScResult.Failure -> _uiState.update {
                it.copy(
                    isCompressing = false,
                    // Публикуем исходник: лучше большое видео, чем потеря сторис
                    errorText = "Не удалось сжать видео, отправим как есть",
                )
            }

            ScResult.Loading -> _uiState.update { it.copy(isCompressing = true) }
        }
    }

    /* ── Публикация ───────────────────────────────────────────────────── */

    fun publish() {
        val state = _uiState.value
        if (state.isPublishing || state.isCompressing) return

        // Валидация на клиенте: сервер тоже проверит, но ждать ответа
        // ради очевидной ошибки — лишняя секунда и лишний трафик
        if (!state.isTextMode && state.localUri == null) {
            _uiState.update { it.copy(errorText = "Выберите фото или видео") }
            return
        }
        if (state.isTextMode && state.overlayText.isNullOrBlank()) {
            _uiState.update { it.copy(errorText = "Добавьте текст сторис") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true, errorText = null) }

            val params = PublishStoryParams(
                localUri = if (state.isTextMode) null else state.localUri,
                caption = state.caption.trim().ifBlank { null },
                privacy = state.privacy,
                backgroundGradient = if (state.isTextMode) state.backgroundGradient else null,
                overlayText = if (state.isTextMode) state.overlayText?.trim() else null,
                location = state.location?.takeIf { it.visible },
                durationMs = state.durationMs,
            )

            when (val result = publishStory(params)) {
                is ScResult.Success -> _uiState.update {
                    it.copy(isPublishing = false, publishedStoryId = result.data.id.raw)
                }

                is ScResult.Failure -> _uiState.update {
                    it.copy(isPublishing = false, errorText = result.error.message)
                }

                ScResult.Loading -> Unit
            }
        }
    }

    /** Экран забрал результат публикации — сбрасываем одноразовое поле. */
    fun consumePublished() {
        _uiState.update { it.copy(publishedStoryId = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorText = null) }
    }

    private companion object {
        /** Подпись видна под сторис целиком — длиннее просто обрежется. */
        const val MAX_CAPTION = 200

        /** Наложение на медиа: крупный текст по центру, лимит жёстче. */
        const val MAX_OVERLAY = 120
    }
}

data class StoryPublishUiState(
    val localUri: String? = null,
    val isVideo: Boolean = false,
    val isTextMode: Boolean = false,
    val caption: String = "",
    val overlayText: String? = null,
    val backgroundGradient: List<Long> = DEFAULT_GRADIENT,
    val privacy: StoryPrivacy = StoryPrivacy.CONTACTS,
    val location: LocationInfo? = null,
    val durationMs: Long = DEFAULT_DURATION_MS,
    val repliesEnabled: Boolean = true,
    val isCompressing: Boolean = false,
    val isPublishing: Boolean = false,
    val publishedStoryId: String? = null,
    val errorText: String? = null,
) {
    val canPublish: Boolean
        get() = !isPublishing && !isCompressing &&
            (if (isTextMode) !overlayText.isNullOrBlank() else localUri != null)

    companion object {
        /** Стандартная длительность фото-сторис. */
        const val DEFAULT_DURATION_MS = 5_000L

        /** Серебряный градиент — фирменный цвет приложения. */
        val DEFAULT_GRADIENT = listOf(0xFF1B1F2A, 0xFF3A4356, 0xFF8E9BB3)
    }
}
