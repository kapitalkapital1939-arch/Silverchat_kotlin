package com.silverchat.feature.stories.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.chat.QuickReactionPanel
import com.silverchat.core.designsystem.navigation.StoriesNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.QuickReactions
import com.silverchat.core.model.Story
import com.silverchat.core.model.StoryMediaType
import com.silverchat.feature.stories.StoryViewerEvent
import com.silverchat.feature.stories.StoryViewerViewModel

/**
 * Полноэкранный просмотрщик сторис.
 *
 * Управление жестами (как в Instagram/Telegram):
 *  - тап по **правой** трети — следующая сторис;
 *  - тап по **левой** трети — предыдущая;
 *  - **удержание** — пауза (палец убран — продолжение);
 *  - свайп вниз — закрытие.
 *
 * Зоны тапа реализованы двумя прозрачными `Box` поверх контента, а не
 * `detectTapGestures` на весь экран: так проще гарантировать, что
 * поля ответа и кнопки не перехватываются жестом ленты.
 *
 * Фон — градиент из метаданных медиа, а не чёрный: пока грузится
 * изображение, кадр не «мигает» чёрным, и текстовые сторис сразу
 * показываются в авторском оформлении.
 */
@Composable
fun StoryViewerScreen(
    navigator: StoriesNavigator,
    modifier: Modifier = Modifier,
    viewModel: StoryViewerViewModel = hiltViewModel(),
    onAuthorChange: (Int) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val viewers by viewModel.viewersState.collectAsStateWithLifecycle()

    // Пауза при уходе приложения в фон: сторис не должна «докрутиться»,
    // пока пользователь смотрит на другой экран
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.pause()

                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.resume()

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        awaitDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Одноразовые события: переход между авторами и закрытие
    LaunchedEffect(events) {
        when (events) {
            StoryViewerEvent.NextAuthor -> onAuthorChange(+1)
            StoryViewerEvent.PreviousAuthor -> onAuthorChange(-1)
            StoryViewerEvent.Close -> navigator.back()
            StoryViewerEvent.StealthGranted -> Unit
            is StoryViewerEvent.Error -> Unit
            null -> Unit
        }
        if (events != null) viewModel.consumeEvent()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(storyBackground(state.currentStory)),
    ) {
        // ── Контент сторис ───────────────────────────────────────────────
        StoryContent(state.currentStory)

        // Затемнение сверху и снизу: текст и кнопки читаются на любом кадре
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.22f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.6f),
                    ),
                ),
        )

        Column(Modifier.fillMaxSize()) {
            SegmentProgressRow(
                count = state.segmentCount,
                currentIndex = state.currentIndex,
                progress = state.progress,
                onSegmentClick = viewModel::seekTo,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = ScSpacing.sm),
            )

            StoryHeader(
                authorName = state.authorName,
                author = state.author,
                createdAt = state.currentStory?.createdAt ?: 0L,
                isMine = state.isMine,
                isPaused = state.isPaused,
                onAvatarClick = { state.author?.let { navigator.openProfile(it.id.raw) } },
                onClose = navigator::back,
                onPauseToggle = { if (state.isPaused) viewModel.resume() else viewModel.pause() },
                onStealth = viewModel::viewStealth,
                onViewers = viewModel::toggleViewers,
            )

            // Центральная зона: жесты «вперёд/назад» и удержание для паузы
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .weight(LEFT_TAP_FRACTION)
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { viewModel.previous() },
                                    onLongPress = { viewModel.pause() },
                                    onPress = {
                                        tryAwaitRelease().let { released ->
                                            if (released) viewModel.resume()
                                        }
                                    },
                                )
                            },
                    )
                    Box(
                        Modifier
                            .weight(1f - LEFT_TAP_FRACTION)
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { viewModel.next() },
                                    onLongPress = { viewModel.pause() },
                                    onPress = {
                                        tryAwaitRelease().let { released ->
                                            if (released) viewModel.resume()
                                        }
                                    },
                                )
                            },
                    )
                }

                if (state.isPaused) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Пауза",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.Center).size(56.dp),
                    )
                }
            }

            // ── Низ: реакции, ответ, меню автора ─────────────────────────
            StoryFooter(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
            )
        }

        // ── Список посмотревших (только для своих сторис) ────────────────
        AnimatedVisibility(
            visible = state.showViewers,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ViewersSheet(
                viewers = viewers,
                count = state.currentStory?.viewersCount ?: 0,
                onClose = viewModel::toggleViewers,
                onOpenProfile = { userId -> navigator.openProfile(userId) },
            )
        }
    }
}

/** Доля экрана под жест «назад»: меньше половины, чтобы «вперёд» было проще. */
private const val LEFT_TAP_FRACTION = 0.35f

/**
 * Градиент-подложка сторис.
 *
 * Для текстовых сторис берём авторский градиент из метаданных; для медиа —
 * тёмный нейтральный, чтобы изображение не конфликтовало с фоном.
 */
private fun storyBackground(story: Story?): Brush {
    val gradient = story?.media?.backgroundGradient?.map { Color(it.toULong().toLong()) }
    if (!gradient.isNullOrEmpty() && story.media.type == StoryMediaType.TEXT) {
        return Brush.linearGradient(gradient)
    }
    return Brush.linearGradient(listOf(Color(0xFF0E1116), Color(0xFF1B1F2A)))
}

/* =========================================================================
   ПОЛОСА СЕГМЕНТОВ
   ========================================================================= */

/**
 * Индикатор прогресса по сегментам.
 *
 * Каждая сторис автора — отдельная полоса; текущая заполняется по мере
 * отсчёта. Тап по полосе переключает на конкретный сегмент.
 */
@Composable
private fun SegmentProgressRow(
    count: Int,
    currentIndex: Int,
    progress: Float,
    onSegmentClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    Row(
        modifier.padding(vertical = ScSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(count) { index ->
            val fraction = when {
                index < currentIndex -> 1f
                index == currentIndex -> progress.coerceIn(0f, 1f)
                else -> 0f
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.3f))
                    .clickable { onSegmentClick(index) },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White),
                )
            }
        }
    }
}

/* =========================================================================
   ШАПКА
   ========================================================================= */

@Composable
private fun StoryHeader(
    authorName: String,
    author: com.silverchat.core.model.User?,
    createdAt: Long,
    isMine: Boolean,
    isPaused: Boolean,
    onAvatarClick: () -> Unit,
    onClose: () -> Unit,
    onPauseToggle: () -> Unit,
    onStealth: () -> Unit,
    onViewers: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScSpacing.sm, vertical = ScSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            user = author,
            size = ScAvatarSize.small,
            onClick = onAvatarClick,
            allowAnimation = false,
        )
        Spacer(Modifier.width(ScSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = authorName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (createdAt > 0) {
                Text(
                    text = TimeFormatter.ago(System.currentTimeMillis() - createdAt),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }

        // Скрытный просмотр — привилегия Premium, доступна зрителю
        if (!isMine) {
            SilverIconButton(
                icon = Icons.Filled.VisibilityOff,
                contentDescription = "Скрытный просмотр (Premium)",
                onClick = onStealth,
                tint = Color.White,
            )
        }
        SilverIconButton(
            icon = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            contentDescription = if (isPaused) "Продолжить" else "Пауза",
            onClick = onPauseToggle,
            tint = Color.White,
        )
        if (isMine) {
            SilverIconButton(
                icon = Icons.Filled.MoreVert,
                contentDescription = "Посмотревшие и управление",
                onClick = onViewers,
                tint = Color.White,
            )
        }
        SilverIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Закрыть просмотрщик",
            onClick = onClose,
            tint = Color.White,
        )
    }
}

/* =========================================================================
   СОДЕРЖИМОЕ
   ========================================================================= */

@Composable
private fun StoryContent(story: Story?) {
    if (story == null) return
    val media = story.media

    when (media.type) {
        StoryMediaType.PHOTO, StoryMediaType.VIDEO -> {
            AsyncImage(
                model = media.url,
                contentDescription = story.caption ?: "Сторис",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        StoryMediaType.TEXT -> {
            Box(Modifier.fillMaxSize().padding(ScSpacing.xl), contentAlignment = Alignment.Center) {
                Text(
                    text = media.overlayText.orEmpty(),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 38.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        else -> Unit
    }

    // Подпись показываем поверх любого типа медиа
    story.caption?.takeIf { it.isNotBlank() && media.type != StoryMediaType.TEXT }?.let { caption ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(ScSpacing.lg),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = caption,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* =========================================================================
   НИЖНЯЯ ПАНЕЛЬ
   ========================================================================= */

@Composable
private fun StoryFooter(
    state: com.silverchat.feature.stories.StoryViewerUiState,
    viewModel: StoryViewerViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = ScSpacing.sm, vertical = ScSpacing.sm)) {
        // Быстрые реакции — всегда: это самый частый отклик на сторис
        QuickReactionPanel(
            // Тот же набор, что в сообщениях: реакции должны быть предсказуемы
            kinds = QuickReactions.PANEL,
            selected = state.currentStory?.reactions?.firstOrNull()?.kind,
            onSelect = viewModel::react,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(ScSpacing.sm))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
        ) {
            if (state.canReply) {
                ReplyField(
                    value = state.replyDraft,
                    onValueChange = viewModel::onReplyDraftChanged,
                    onSend = viewModel::sendReply,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = if (state.isMine) {
                        "Это ваша сторис · ${state.currentStory?.viewersCount ?: 0} просмотров"
                    } else {
                        "Автор отключил ответы"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f),
                )
            }

            // Управление своей публикацией
            if (state.isMine) {
                SilverIconButton(
                    icon = Icons.Filled.PushPin,
                    contentDescription = if (state.currentStory?.pinnedToProfile == true) {
                        "Открепить от профиля"
                    } else {
                        "Закрепить в профиле"
                    },
                    onClick = viewModel::pinCurrent,
                    tint = if (state.currentStory?.pinnedToProfile == true) {
                        ScTheme.accent
                    } else {
                        Color.White
                    },
                )
                SilverIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Удалить сторис",
                    onClick = viewModel::deleteCurrent,
                    tint = ScTheme.danger,
                )
            }
        }
    }
}

@Composable
private fun ReplyField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(ScShapes.chip)
            .background(Color.White.copy(alpha = 0.14f))
            .padding(start = ScSpacing.md, end = ScSpacing.xs, top = ScSpacing.xs, bottom = ScSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            singleLine = true,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text("Ответить…", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                    inner()
                }
            },
        )
        SilverIconButton(
            icon = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Отправить ответ",
            onClick = onSend,
            enabled = value.isNotBlank(),
            tint = Color.White,
            size = 36,
        )
    }
}

/* =========================================================================
   СПИСОК ПОСМОТРЕВШИХ
   ========================================================================= */

@Composable
private fun ViewersSheet(
    viewers: List<com.silverchat.core.model.User>,
    count: Int,
    onClose: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ScShapes.sheet)
            .background(ScTheme.surfaceElevated)
            .navigationBarsPadding()
            .padding(vertical = ScSpacing.md),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Просмотрели · $count",
                color = ScTheme.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            SilverIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Закрыть список",
                onClick = onClose,
                tint = ScTheme.textPrimary,
            )
        }

        if (viewers.isEmpty()) {
            Text(
                text = "Пока никто не посмотрел",
                color = ScTheme.textTertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(ScSpacing.lg),
            )
        } else {
            Column(Modifier.fillMaxWidth().height(240.dp)) {
                androidx.compose.foundation.lazy.LazyColumn {
                    androidx.compose.foundation.lazy.items(viewers, key = { it.id.raw }) { user ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProfile(user.id.raw) }
                                .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(user = user, size = ScAvatarSize.small)
                            Spacer(Modifier.width(ScSpacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = user.fullName,
                                    color = ScTheme.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                user.handle?.let {
                                    Text(it, color = ScTheme.textTertiary, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Освобождение ресурса в `LaunchedEffect`.
 *
 * Отдельная suspend-функция вместо прямого `awaitCancellation`:
 * читаемее и не требует импорта `kotlinx.coroutines.awaitCancellation`
 * в каждом экране, где есть наблюдатель жизненного цикла.
 */
private suspend fun awaitDispose(onDispose: () -> Unit) {
    try {
        kotlinx.coroutines.awaitCancellation()
    } finally {
        onDispose()
    }
}
