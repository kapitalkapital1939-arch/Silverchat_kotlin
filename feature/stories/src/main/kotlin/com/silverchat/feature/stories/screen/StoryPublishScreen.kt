package com.silverchat.feature.stories.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.LoadingOverlay
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.SilverTextField
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.navigation.StoriesNavigator
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.StoryPrivacy
import com.silverchat.feature.stories.StoryPublishViewModel
import com.silverchat.feature.stories.hintRu
import com.silverchat.feature.stories.titleRu

/**
 * Экран публикации сторис.
 *
 * Два режима в одном экране:
 *  - **медиа** — фото или видео из галереи/камеры, сжатие перед отправкой;
 *  - **текст** — градиентная подложка и наложенная надпись.
 *
 * Разделение на два экрана было бы ошибкой: переключение режима происходит
 * нажатием одной кнопки, а состояние (приватность, геометка, подпись)
 * общее для обоих.
 *
 * Сжатие видео показывается отдельным оверлеем [LoadingOverlay], а не
 * кнопкой в состоянии `loading`: оно занимает десятки секунд, и пользователь
 * должен понимать, что именно сейчас происходит.
 */
@Composable
fun StoryPublishScreen(
    navigator: StoriesNavigator,
    modifier: Modifier = Modifier,
    onPickMedia: (fromCamera: Boolean) -> Unit = {},
    onPickLocation: () -> Unit = {},
    viewModel: StoryPublishViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Публикация завершилась — закрываем экран и показываем результат
    LaunchedEffect(state.publishedStoryId) {
        state.publishedStoryId?.let {
            viewModel.consumePublished()
            navigator.back()
        }
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SilverTopBar(
                title = if (state.isTextMode) "Текстовая сторис" else "Новая сторис",
                onBack = navigator::back,
                actions = {
                    // Переключение режима — главная кнопка шапки
                    SilverIconButton(
                        icon = if (state.isTextMode) Icons.Filled.PhotoLibrary else Icons.Filled.Title,
                        contentDescription = if (state.isTextMode) {
                            "Выбрать фото или видео"
                        } else {
                            "Создать текстовую сторис"
                        },
                        onClick = {
                            if (state.isTextMode) onPickMedia(false) else viewModel.switchToTextMode()
                        },
                        tint = ScTheme.textPrimary,
                    )
                },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = ScSpacing.md),
            ) {
                // ── Превью ───────────────────────────────────────────────
                StoryPreview(state = state, onPickMedia = { onPickMedia(false) })

                Spacer(Modifier.height(ScSpacing.md))

                // ── Градиенты для текстового режима ──────────────────────
                if (state.isTextMode) {
                    GradientPicker(
                        selected = state.backgroundGradient,
                        onSelect = viewModel::onGradientSelected,
                    )
                    Spacer(Modifier.height(ScSpacing.sm))
                    SilverTextField(
                        value = state.overlayText.orEmpty(),
                        onValueChange = viewModel::onOverlayTextChanged,
                        label = "Текст сторис",
                        placeholder = "Что происходит?",
                        maxLength = MAX_OVERLAY_LENGTH,
                        singleLine = false,
                    )
                    Spacer(Modifier.height(ScSpacing.sm))
                }

                // ── Подпись ──────────────────────────────────────────────
                SilverTextField(
                    value = state.caption,
                    onValueChange = viewModel::onCaptionChanged,
                    label = "Подпись",
                    placeholder = "Добавьте описание",
                    maxLength = MAX_CAPTION_LENGTH,
                    singleLine = false,
                )

                Spacer(Modifier.height(ScSpacing.md))

                // ── Приватность ──────────────────────────────────────────
                PrivacyPicker(
                    selected = state.privacy,
                    onSelect = viewModel::onPrivacyChanged,
                )

                Spacer(Modifier.height(ScSpacing.sm))

                // ── Геометка и ответы ────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
                    OptionChip(
                        icon = Icons.Filled.LocationOn,
                        label = state.location?.let { it.title ?: it.address } ?: "Геометка",
                        selected = state.location != null,
                        onClick = onPickLocation,
                        modifier = Modifier.weight(1f),
                    )
                    OptionChip(
                        icon = Icons.Filled.Visibility,
                        label = if (state.repliesEnabled) "Ответы вкл." else "Ответы выкл.",
                        selected = state.repliesEnabled,
                        onClick = viewModel::toggleRepliesEnabled,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.weight(1f))

                SilverButton(
                    text = when {
                        state.isCompressing -> "Сжимаем видео…"
                        state.isPublishing -> "Публикуем…"
                        else -> "Опубликовать сторис"
                    },
                    onClick = viewModel::publish,
                    enabled = state.canPublish,
                    loading = state.isPublishing,
                    modifier = Modifier.padding(bottom = ScSpacing.md),
                )
            }
        }

        // Сжатие видео — блокирующий процесс с внятным сообщением
        if (state.isCompressing) {
            Box(
                Modifier.fillMaxSize().background(ScTheme.background.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingOverlay(message = "Сжимаем видео до параметров сторис…")
            }
        }

        // У снекбара нет собственного таймаута: гасим ошибку действием,
        // иначе текст закрыл бы кнопку публикации до конца сессии экрана
        state.errorText?.let { error ->
            InlineSnackbar(
                message = error,
                actionLabel = "Скрыть",
                onAction = viewModel::dismissError,
                modifier = Modifier.align(Alignment.BottomCenter).padding(ScSpacing.md),
            )
        }
    }
}

/* =========================================================================
   ПРЕВЬЮ
   ========================================================================= */

@Composable
private fun StoryPreview(
    state: com.silverchat.feature.stories.StoryPublishUiState,
    onPickMedia: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(STORY_ASPECT_RATIO)
            .clip(ScShapes.cardLarge)
            .background(
                if (state.isTextMode) {
                    gradientBrush(state.backgroundGradient)
                } else {
                    Brush.linearGradient(listOf(ScTheme.surfaceGlass, ScTheme.surfaceElevated))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // Текстовый режим: показываем надпись так, как её увидят зрители
            state.isTextMode -> Text(
                text = state.overlayText?.takeIf { it.isNotBlank() } ?: "Ваш текст",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 33.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(ScSpacing.xl),
            )

            state.localUri != null -> AsyncImage(
                model = state.localUri,
                contentDescription = "Предпросмотр сторис",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(ScShapes.cardLarge),
            )

            // Пустое состояние — крупная зона тапа, а не маленькая кнопка:
            // выбрать медиа должно быть проще всего
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(ScShapes.card)
                    .clickable(onClick = onPickMedia)
                    .padding(ScSpacing.xl),
            ) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(ScTheme.surfaceGlass),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = ScTheme.accent,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(ScSpacing.sm))
                Text(
                    text = "Выберите фото или видео",
                    color = ScTheme.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Или создайте текстовую сторис кнопкой в шапке",
                    color = ScTheme.textTertiary,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Подпись поверх превью — так видно итоговую композицию
        if (!state.isTextMode && state.caption.isNotBlank()) {
            Text(
                text = state.caption,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(ScSpacing.md),
            )
        }
    }
}

/** Соотношение сторон сторис 9:16 — стандарт вертикального контента. */
private const val STORY_ASPECT_RATIO = 9f / 16f

private fun gradientBrush(argb: List<Long>): Brush =
    Brush.linearGradient(argb.map { Color(it.toULong().toLong()) })

/* =========================================================================
   ГРАДИЕНТЫ
   ========================================================================= */

@Composable
private fun GradientPicker(
    selected: List<Long>,
    onSelect: (List<Long>) -> Unit,
) {
    Column {
        Text(
            text = "Подложка",
            color = ScTheme.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = ScSpacing.xs),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
            contentPadding = PaddingValues(vertical = ScSpacing.xs),
        ) {
            items(GRADIENT_PRESETS, key = { it.name }) { preset ->
                val isSelected = preset.argb == selected
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(gradientBrush(preset.argb))
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, ScTheme.accent, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelect(preset.argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Пресеты подложек: серебряный фирменный, тёплый, холодный, тёмный. */
private val GRADIENT_PRESETS = listOf(
    GradientPreset("silver", listOf(0xFF1B1F2A, 0xFF3A4356, 0xFF8E9BB3)),
    GradientPreset("sunset", listOf(0xFF3A1C1C, 0xFF8C3B2E, 0xFFD98A4A)),
    GradientPreset("ocean", listOf(0xFF0E2438, 0xFF1E5C8A, 0xFF63B3D9)),
    GradientPreset("forest", listOf(0xFF12261C, 0xFF2F6B45, 0xFF86C08E)),
    GradientPreset("plum", listOf(0xFF241230, 0xFF5B2A73, 0xFFB07CC6)),
    GradientPreset("night", listOf(0xFF0A0C10, 0xFF161A22, 0xFF2A3140)),
)

private data class GradientPreset(val name: String, val argb: List<Long>)

/* =========================================================================
   ПРИВАТНОСТЬ
   ========================================================================= */

/**
 * Выбор аудитории сторис.
 *
 * Показываем не только название, но и пояснение: «Близкие друзья» и
 * «Выбранные» звучат похоже, а последствия разные. Ошибка приватности —
 * самая болезненная в сторис, поэтому подсказка важнее компактности.
 */
@Composable
private fun PrivacyPicker(
    selected: StoryPrivacy,
    onSelect: (StoryPrivacy) -> Unit,
) {
    Column {
        Text(
            text = "Кто увидит",
            color = ScTheme.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = ScSpacing.xs),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
            items(StoryPrivacy.entries.toList(), key = { it.name }) { privacy ->
                val isSelected = privacy == selected
                Text(
                    text = privacy.titleRu(),
                    color = if (isSelected) Color.White else ScTheme.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(ScShapes.chip)
                        .background(if (isSelected) ScTheme.accent else ScTheme.surfaceGlass)
                        .clickable { onSelect(privacy) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(ScSpacing.xs))
        Text(
            text = selected.hintRu(),
            color = ScTheme.textTertiary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun OptionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(ScShapes.chip)
            .background(if (selected) ScTheme.accentContainer else ScTheme.surfaceGlass)
            .clickable(onClick = onClick)
            .padding(horizontal = ScSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) ScTheme.accent else ScTheme.textTertiary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = if (selected) ScTheme.textPrimary else ScTheme.textSecondary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private const val MAX_CAPTION_LENGTH = 200
private const val MAX_OVERLAY_LENGTH = 120
