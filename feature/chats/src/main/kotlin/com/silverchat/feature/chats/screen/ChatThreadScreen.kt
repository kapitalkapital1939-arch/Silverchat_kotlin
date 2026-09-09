package com.silverchat.feature.chats.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.UserBadgesRow
import com.silverchat.core.designsystem.component.VerifiedBadge
import com.silverchat.core.designsystem.component.chat.DaySeparator
import com.silverchat.core.designsystem.component.chat.MessageBubble
import com.silverchat.core.designsystem.component.chat.PinnedMessageBar
import com.silverchat.core.designsystem.component.chat.QuickReactionPanel
import com.silverchat.core.designsystem.component.chat.RecordingIndicator
import com.silverchat.core.designsystem.component.chat.ReplyQuote
import com.silverchat.core.designsystem.component.chat.ServiceMessage
import com.silverchat.core.designsystem.component.chat.TypingIndicator
import com.silverchat.core.designsystem.glass.GlassState
import com.silverchat.core.designsystem.glass.glassSource
import com.silverchat.core.designsystem.glass.glassSurface
import com.silverchat.core.designsystem.navigation.ChatsNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.domain.usecase.chat.snippet
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.User
import com.silverchat.core.model.UserPresence
import com.silverchat.feature.chats.BubbleUi
import com.silverchat.feature.chats.ChatThreadUiState
import com.silverchat.feature.chats.ChatThreadViewModel
import com.silverchat.feature.chats.QuickReactionKinds
import com.silverchat.feature.chats.ThreadEvent
import kotlinx.coroutines.launch

/**
 * Экран диалога.
 *
 * Слои (снизу вверх):
 *  1. фон чата — градиент (не сплошной цвет: на однотонном фоне пузыри
 *     «сливаются», и стекло шапки не читается);
 *  2. лента сообщений — помечена [glassSource], чтобы шапка и поле ввода
 *     размывали именно её;
 *  3. стеклянная шапка с аватаром, именем, статусом и кнопками звонка;
 *  4. стеклянное поле ввода с ответом/редактированием и записью голоса;
 *  5. плавающая кнопка «вниз» и панель выделения поверх ленты.
 *
 * Производительность ленты:
 *  - `reverseLayout = true`: новые сообщения добавляются в позицию 0,
 *    и Compose не пересчитывает смещения всех элементов;
 *  - `key = message.id` — обязательное условие корректной анимации вставки;
 *  - `contentType` разделяет текстовые, медиа- и служебные элементы,
 *    чтобы узлы не пересоздавались в смешанной ленте;
 *  - отметка о прочтении уходит батчем по факту видимости, а не на каждый кадр.
 */
@Composable
fun ChatThreadScreen(
    viewModel: ChatThreadViewModel,
    navigator: ChatsNavigator,
    glassState: GlassState,
    modifier: Modifier = Modifier,
    onOpenMessageMenu: (Message) -> Unit = {},
    onPickFile: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Черновик сохраняется при уходе с экрана, а не в onCleared ViewModel:
    // там viewModelScope уже отменён, и suspend-запись в Room не выполнится
    DisposableEffect(viewModel) {
        onDispose { viewModel.flushDraft() }
    }

    // Пересылка требует навигации — обрабатываем событие и сразу гасим его
    LaunchedEffect(events) {
        when (val event = events) {
            is ThreadEvent.Forward -> navigator.forwardMessages(event.messageIds)
            else -> Unit
        }
        if (events != null) viewModel.consumeEvent()
    }

    // Автоскролл к новому сообщению только если пользователь уже внизу:
    // иначе чтение истории прерывалось бы каждым входящим сообщением
    LaunchedEffect(state.bubbles.size) {
        if (listState.firstVisibleItemIndex <= 1 && state.bubbles.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Подгрузка истории при приближении к «потолку» ленты
    LaunchedEffect(listState, state.bubbles) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - PREFETCH_THRESHOLD) {
                // В reverseLayout последний видимый = самый старый
                state.bubbles.firstOrNull()?.message?.id?.let(viewModel::loadOlder)
            }
        }
    }

    // Отметка о прочтении: батч видимых входящих сообщений
    LaunchedEffect(listState, state.bubbles) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
        }.collect { visibleKeys ->
            val incoming = state.bubbles
                .asSequence()
                .filter { !it.isOutgoing }
                .map { it.message.id }
                .filter { it.raw in visibleKeys }
                .toList()
            if (incoming.isNotEmpty()) viewModel.markVisibleRead(incoming)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ScTheme.chatBackground, ScTheme.background))),
    ) {
        Column(Modifier.fillMaxSize()) {
            ThreadTopBar(
                state = state,
                onBack = navigator::back,
                onInfoClick = { state.chat?.let { navigator.openChatInfo(it.id.raw) } },
                onVoiceCall = { state.chat?.let { navigator.startCall(it.id.raw, video = false) } },
                onVideoCall = { state.chat?.let { navigator.startCall(it.id.raw, video = true) } },
            )

            // Закреплённое сообщение: клик скроллит к нему
            state.chat?.pinned?.firstOrNull()?.let { pinnedId ->
                val index = state.bubbles.indexOfFirst { it.message.id == pinnedId }
                val pinnedMessage = state.bubbles.getOrNull(index)?.message
                if (pinnedMessage != null) {
                    PinnedMessageBar(
                        snippet = pinnedMessage.snippet(),
                        onClick = { if (index >= 0) scope.launch { listState.animateScrollToItem(index) } },
                        onClose = { viewModel.unpinMessage(pinnedMessage) },
                    )
                }
            }

            // ── Лента сообщений ──────────────────────────────────────────
            Box(Modifier.weight(1f).glassSource(glassState)) {
                if (state.bubbles.isEmpty() && !state.isLoading) {
                    EmptyThreadHint(isChannel = state.isChannel)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(vertical = ScSpacing.sm),
                    ) {
                        items(
                            // asReversed: в reverseLayout позиция 0 = самое новое
                            items = state.bubbles.asReversed(),
                            key = { it.message.id.raw },
                            contentType = { bubbleContentType(it) },
                        ) { bubble ->
                            BubbleRow(
                                bubble = bubble,
                                state = state,
                                viewModel = viewModel,
                                onLongPress = { message ->
                                    viewModel.openReactionPanel(message)
                                    onOpenMessageMenu(message)
                                },
                                onSenderClick = { user -> navigator.openProfile(user.id.raw) },
                                onQuoteClick = { message ->
                                    val targetId = message.replyTo?.messageId
                                    val targetIndex = state.bubbles.indexOfLast { it.message.id == targetId }
                                    if (targetIndex >= 0) {
                                        scope.launch { listState.animateScrollToItem(targetIndex) }
                                    }
                                },
                            )
                        }

                        if (state.typingUsers.isNotEmpty()) {
                            item(key = "__typing__", contentType = "typing") {
                                TypingIndicator(senderName = state.typingNames.firstOrNull())
                            }
                        }
                    }
                }

                // Панель быстрых реакций поверх ленты
                state.reactionPanelFor?.let { targetId ->
                    val target = state.bubbles.firstOrNull { it.message.id == targetId }?.message
                    if (target != null) {
                        QuickReactionPanel(
                            kinds = QuickReactionKinds,
                            selected = target.myReaction,
                            onSelect = { kind -> viewModel.react(target, kind) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(ScSpacing.md),
                        )
                    }
                }

                val showScrollButton by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > SCROLL_BUTTON_THRESHOLD }
                }
                ScrollToBottomButton(
                    visible = showScrollButton,
                    unreadCount = state.unreadDividerIndex.coerceAtLeast(0),
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = ScSpacing.md, bottom = ScSpacing.md),
                )
            }

            // ── Поле ввода ───────────────────────────────────────────────
            MessageComposer(
                state = state,
                viewModel = viewModel,
                glassState = glassState,
                onAttachClick = onPickFile,
            )
        }

        // ── Режим выделения ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.selectionMode,
            enter = slideInVertically(tween(200)) { -it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SelectionActionBar(
                count = state.selectedIds.size,
                onCancel = viewModel::clearSelection,
                onForward = viewModel::forwardSelected,
                onDelete = { viewModel.deleteSelected(forEveryone = true) },
            )
        }
    }
}

/** Тип контента для переиспользования узлов списка. */
private fun bubbleContentType(bubble: BubbleUi): String = when {
    bubble.message.content is MessageContent.Service -> "service"
    bubble.message.isStickerOnly -> "sticker"
    else -> bubble.message.content::class.java.simpleName
}

private const val PREFETCH_THRESHOLD = 6
private const val SCROLL_BUTTON_THRESHOLD = 3

/* =========================================================================
   ШАПКА
   ========================================================================= */

@Composable
private fun ThreadTopBar(
    state: ChatThreadUiState,
    onBack: () -> Unit,
    onInfoClick: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
) {
    val chat = state.chat
    val isOnline = chat?.peer?.presence is UserPresence.Online

    SilverTopBar(
        title = state.title,
        onBack = onBack,
        leadingContent = {
            UserAvatar(
                user = chat?.peer,
                avatarOverride = chat?.avatar,
                fallbackTitle = chat?.title,
                fallbackId = chat?.id?.raw ?: "chat",
                size = ScAvatarSize.chatHeader,
                showOnline = isOnline,
                onClick = onInfoClick,
            )
        },
        titleContent = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onInfoClick),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = state.title,
                        color = ScTheme.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (chat?.verified == true) VerifiedBadge(size = 15)
                    chat?.peer?.badges?.let { badges -> UserBadgesRow(badges, iconSize = 14) }
                }
                Text(
                    text = state.onlineSubtitle.orEmpty(),
                    color = if (isOnline || state.onlineSubtitle == "печатает…") {
                        ScTheme.accent
                    } else {
                        ScTheme.textTertiary
                    },
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        },
        actions = {
            // В канале звонки бессмысленны: нет собеседника
            if (chat?.isChannel != true && state.canWrite) {
                SilverIconButton(
                    icon = Icons.Filled.Call,
                    contentDescription = "Голосовой звонок",
                    onClick = onVoiceCall,
                    tint = ScTheme.textPrimary,
                )
                SilverIconButton(
                    icon = Icons.Filled.Videocam,
                    contentDescription = "Видеозвонок",
                    onClick = onVideoCall,
                    tint = ScTheme.textPrimary,
                )
            }
            SilverIconButton(
                icon = Icons.Filled.MoreVert,
                contentDescription = "Информация о чате",
                onClick = onInfoClick,
                tint = ScTheme.textPrimary,
            )
        },
    )
}

/* =========================================================================
   ПУЗЫРЬ С РАЗДЕЛИТЕЛЯМИ
   ========================================================================= */

@Composable
private fun BubbleRow(
    bubble: BubbleUi,
    state: ChatThreadUiState,
    viewModel: ChatThreadViewModel,
    onLongPress: (Message) -> Unit,
    onSenderClick: (User) -> Unit,
    onQuoteClick: (Message) -> Unit,
) {
    val message = bubble.message
    val isPersonal = state.chat?.isPersonal == true

    Column {
        if (bubble.showDaySeparator) {
            DaySeparator(TimeFormatter.daySeparator(message.sentAt))
        }

        if (message.content is MessageContent.Service) {
            ServiceMessage((message.content as MessageContent.Service).title)
            return@Column
        }

        val isSelected = message.id in state.selectedIds
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) Modifier.background(ScTheme.accentContainer) else Modifier,
                ),
        ) {
            MessageBubble(
                message = message,
                isOutgoing = bubble.isOutgoing,
                isGroupedWithPrevious = bubble.groupedWithPrevious,
                isGroupedWithNext = bubble.groupedWithNext,
                // Аватар и имя отправителя нужны только в группах и каналах
                showSenderAvatar = !isPersonal,
                showSenderName = !isPersonal,
                isPremiumSender = message.sender?.premium?.isActive == true,
                onLongPress = { msg ->
                    if (state.selectionMode) viewModel.toggleSelection(msg) else onLongPress(msg)
                },
                onDoubleTap = viewModel::doubleTapReact,
                onReactionClick = viewModel::react,
                onSenderClick = onSenderClick,
                onQuoteClick = onQuoteClick,
                onMediaClick = { msg ->
                    if (state.selectionMode) viewModel.toggleSelection(msg)
                },
                onRetryClick = viewModel::retryFailed,
            )
        }
    }
}

/* =========================================================================
   ПОЛЕ ВВОДА
   ========================================================================= */

@Composable
private fun MessageComposer(
    state: ChatThreadUiState,
    viewModel: ChatThreadViewModel,
    glassState: GlassState,
    onAttachClick: () -> Unit,
) {
    // Режим записи голоса заменяет поле ввода целиком
    state.recording?.let { recording ->
        RecordingIndicator(
            elapsedMs = recording.elapsedMs,
            amplitude = recording.amplitude,
            liveWaveform = recording.waveform,
            onCancel = viewModel::cancelVoiceRecording,
            onSend = viewModel::stopVoiceRecordingAndSend,
        )
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .glassSurface(state = glassState, shape = ScShapes.banner, blurRadius = 22.dp)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = ScSpacing.sm, vertical = ScSpacing.sm),
    ) {
        // Цитата ответа ИЛИ метка редактирования — одновременно их не бывает
        val composerContext: Message? = state.editingMessage ?: state.replyTo
        AnimatedVisibility(
            visible = composerContext != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            composerContext?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReplyQuote(
                        senderName = if (state.editingMessage != null) {
                            "Редактирование"
                        } else {
                            message.sender?.fullName ?: "Ответ"
                        },
                        snippet = message.snippet(),
                        isOutgoing = false,
                        modifier = Modifier.weight(1f),
                    )
                    SilverIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Отменить",
                        onClick = viewModel::cancelComposer,
                        size = 34,
                    )
                }
                Spacer(Modifier.height(ScSpacing.xs))
            }
        }

        // Медленный режим показываем явно: молчаливая блокировка ввода
        // воспринимается как баг
        if (state.slowModeSeconds > 0) {
            Text(
                text = "Медленный режим: ${state.slowModeSeconds} с между сообщениями",
                color = ScTheme.textTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = ScSpacing.sm, vertical = 2.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.xs),
        ) {
            SilverIconButton(
                icon = Icons.Filled.AttachFile,
                contentDescription = "Прикрепить файл",
                onClick = onAttachClick,
                enabled = state.canWrite,
            )

            Box(
                Modifier
                    .weight(1f)
                    .clip(ScShapes.field)
                    .background(ScTheme.surface)
                    .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
            ) {
                BasicTextField(
                    value = state.inputText,
                    onValueChange = viewModel::onInputChanged,
                    enabled = state.canWrite,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = ScTheme.textPrimary, fontSize = 15.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(ScTheme.accent),
                    maxLines = COMPOSER_MAX_LINES,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.inputText.isEmpty()) {
                                Text(
                                    text = state.composerHint,
                                    color = ScTheme.textTertiary,
                                    fontSize = 15.sp,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            // Одна кнопка с двумя состояниями: микрофон при пустом поле,
            // отправка при тексте. Так поле ввода не «прыгает» по ширине.
            val hasText = state.inputText.isNotBlank()
            SilverIconButton(
                icon = if (hasText) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                contentDescription = if (hasText) "Отправить" else "Записать голосовое",
                onClick = {
                    if (hasText) viewModel.sendText() else viewModel.startVoiceRecording()
                },
                enabled = state.canWrite,
                tint = if (hasText) Color.White else ScTheme.textSecondary,
                background = if (hasText) ScTheme.accent else Color.Transparent,
                size = 44,
            )
        }
    }
}

private const val COMPOSER_MAX_LINES = 6

/* =========================================================================
   ВСПОМОГАТЕЛЬНЫЕ ЭЛЕМЕНТЫ
   ========================================================================= */

@Composable
private fun ScrollToBottomButton(
    visible: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ScTheme.surfaceGlass)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "К последним сообщениям",
                tint = ScTheme.textPrimary,
                modifier = Modifier.size(22.dp),
            )
            if (unreadCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(ScTheme.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    onCancel: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ScTheme.surfaceElevated)
            .padding(horizontal = ScSpacing.sm, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SilverIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Выйти из режима выделения",
            onClick = onCancel,
            tint = ScTheme.textPrimary,
        )
        Text(
            text = "Выбрано: $count",
            color = ScTheme.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = ScSpacing.sm),
        )
        SilverIconButton(
            icon = Icons.Filled.Forward,
            contentDescription = "Переслать",
            onClick = onForward,
            tint = ScTheme.textPrimary,
            enabled = count > 0,
        )
        SilverIconButton(
            icon = Icons.Filled.Delete,
            contentDescription = "Удалить",
            onClick = onDelete,
            tint = ScTheme.danger,
            enabled = count > 0,
        )
    }
}

@Composable
private fun EmptyThreadHint(isChannel: Boolean) {
    Box(Modifier.fillMaxSize().padding(ScSpacing.xl), contentAlignment = Alignment.Center) {
        Text(
            text = if (isChannel) {
                "В канале пока нет публикаций"
            } else {
                "Сообщений пока нет.\nОтправьте первое — собеседник получит уведомление."
            },
            color = ScTheme.textTertiary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(ScShapes.card)
                .background(ScTheme.surfaceGlass)
                .padding(ScSpacing.lg),
        )
    }
}
