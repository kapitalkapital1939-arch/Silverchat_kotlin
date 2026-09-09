package com.silverchat.feature.chats.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.designsystem.component.ChatListItem
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.NewChatFab
import com.silverchat.core.designsystem.component.ScreenHeader
import com.silverchat.core.designsystem.component.SearchField
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.StoryTrayAvatar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.chat.ChatListItemActions
import com.silverchat.core.designsystem.component.chat.EmptyChatsPlaceholder
import com.silverchat.core.designsystem.glass.GlassState
import com.silverchat.core.designsystem.glass.glassSource
import com.silverchat.core.designsystem.navigation.ChatsNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.domain.usecase.chat.ChatListFilter
import com.silverchat.core.model.Chat
import com.silverchat.core.model.StoryCluster
import com.silverchat.core.model.User
import com.silverchat.feature.chats.ChatListUiState
import com.silverchat.feature.chats.ChatListViewModel

/**
 * Экран «Чаты» — первая вкладка нижней навигации.
 *
 * Структура сверху вниз:
 *  1. шапка с заголовком и поиском;
 *  2. трей сторис (авторы с непросмотренными сторис);
 *  3. фильтр-чипы (Все / Непрочитанные / Личные / Группы / Каналы);
 *  4. список чатов;
 *  5. FAB «Новый чат» поверх списка.
 *
 * Производительность:
 *  - список и шапка помечены [glassSource], поэтому нижняя панель
 *    размывает именно их (а не пустой фон);
 *  - [items] с ключом `chat.id` — Compose переиспользует узлы строк
 *    вместо полной пересборки списка при каждом новом сообщении;
 *  - трей сторис вынесен в отдельный LazyRow: он скроллится горизонтально
 *    и не влияет на вертикальную ленту чатов.
 */
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    navigator: ChatsNavigator,
    glassState: GlassState,
    modifier: Modifier = Modifier,
    me: User? = null,
    storyClusters: List<StoryCluster> = emptyList(),
    typingByChat: Map<String, String> = emptyMap(),
    unseenStoryChats: Set<String> = emptySet(),
    onLongPressChat: (Chat, List<ChatListItemActions>) -> Unit = { _, _ -> },
    onOpenSearch: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── Шапка ────────────────────────────────────────────────────
            ScreenHeader(
                title = "Чаты",
                subtitle = when {
                    state.totalUnread > 0 -> "${state.totalUnread} непрочитанных"
                    state.archivedCount > 0 -> "В архиве: ${state.archivedCount}"
                    else -> null
                },
                trailing = {
                    SilverIconButton(
                        icon = Icons.Filled.Search,
                        contentDescription = "Поиск по чатам и пользователям",
                        onClick = onOpenSearch,
                        tint = ScTheme.textPrimary,
                    )
                    Spacer(Modifier.width(ScSpacing.xs))
                    SilverIconButton(
                        icon = Icons.Filled.DoneAll,
                        contentDescription = "Отметить все чаты прочитанными",
                        onClick = viewModel::markAllRead,
                        tint = ScTheme.textPrimary,
                        enabled = state.totalUnread > 0,
                    )
                },
            )

            // ── Полоса состояния соединения ──────────────────────────────
            ConnectionBar(state)

            // ── Поиск по открытому списку ────────────────────────────────
            SearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChanged,
                placeholder = "Поиск чатов, каналов и @username",
                onClear = { viewModel.onQueryChanged("") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScSpacing.md),
            )

            // ── Скроллящийся контент: источник для blur нижней панели ───
            Column(
                Modifier
                    .weight(1f)
                    .glassSource(glassState),
            ) {
                // Трейд сторис — только если есть что показывать
                if (storyClusters.isNotEmpty()) {
                    StoriesTray(
                        clusters = storyClusters,
                        me = me,
                        onCreateStory = navigator::openStoryCreate,
                        onOpenStories = { cluster -> navigator.openStoryViewer(cluster.authorId.raw, 0) },
                    )
                }

                FilterChips(
                    selected = state.filter,
                    unreadCount = state.totalUnread,
                    onSelect = viewModel::onFilterSelected,
                )

                if (state.isLoading) {
                    ChatListSkeleton()
                } else if (state.isEmpty) {
                    EmptyChatsPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        title = if (state.query.isNotBlank()) {
                            "Ничего не найдено"
                        } else {
                            when (state.filter) {
                                ChatListFilter.UNREAD -> "Непрочитанных нет"
                                ChatListFilter.CHANNELS -> "Нет каналов"
                                ChatListFilter.GROUPS -> "Нет групп"
                                ChatListFilter.BOTS -> "Нет ботов"
                                ChatListFilter.MUTED -> "Нет заглушённых чатов"
                                ChatListFilter.PERSONAL -> "Нет личных чатов"
                                ChatListFilter.ALL -> "Здесь пока пусто"
                            }
                        },
                        subtitle = if (state.query.isNotBlank()) {
                            "Попробуйте изменить запрос или найдите собеседника по @username."
                        } else {
                            "Начните переписку, создайте группу или подпишитесь на канал — " +
                                "всё появится в этом списке."
                        },
                        actionLabel = if (state.query.isBlank()) "Найти собеседника" else null,
                        onAction = onOpenSearch,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        items(
                            items = state.chats,
                            key = { it.id.raw },
                            // contentType позволяет Compose переиспользовать узлы
                            // только между строками одного типа
                            contentType = { it.type },
                        ) { chat ->
                            ChatListItem(
                                chat = chat,
                                typingUserName = typingByChat[chat.id.raw],
                                hasUnseenStories = chat.id.raw in unseenStoryChats,
                                onClick = { navigator.openChat(chat.id.raw) },
                                onLongClick = {
                                    onLongPressChat(chat, ChatListItemActions.availableFor(chat))
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── FAB ──────────────────────────────────────────────────────────
        // Скрывается при прокрутке вниз: не перекрывает последние чаты.
        // Направление определяется по дельте первого видимого индекса.
        val fabVisible = rememberFabVisibility(listState)

        AnimatedVisibility(
            visible = fabVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ScSpacing.lg, bottom = 84.dp),
        ) {
            NewChatFab(onClick = navigator::openSearch)
        }
    }
}

/**
 * Видимость FAB по направлению прокрутки.
 *
 * `snapshotFlow` вместо производного state: подписка живёт в LaunchedEffect
 * и отменяется вместе с экраном, а сам FAB не провоцирует рекомпозицию списка.
 */
@Composable
private fun rememberFabVisibility(
    listState: androidx.compose.foundation.lazy.LazyListState,
): Boolean {
    var visible by remember { mutableStateOf(true) }
    var lastIndex by remember { mutableStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                // Прокрутка вниз скрывает FAB, вверх — возвращает.
                // В самом верху FAB виден всегда.
                visible = index <= lastIndex || index < 2
                lastIndex = index
            }
    }
    return visible
}

/** Полоса состояния соединения. */
@Composable
private fun ConnectionBar(state: ChatListUiState) {
    val text = state.connection.textRu
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    when (state.connection) {
                        is com.silverchat.feature.chats.ConnectionBanner.Offline ->
                            ScTheme.warning.copy(alpha = 0.14f)

                        else -> ScTheme.surfaceGlass
                    },
                )
                .padding(horizontal = ScSpacing.md, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text.orEmpty(),
                color = ScTheme.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Трейд сторис: «Моя сторис» + авторы с непросмотренными сторис. */
@Composable
private fun StoriesTray(
    clusters: List<StoryCluster>,
    me: User?,
    onCreateStory: () -> Unit,
    onOpenStories: (StoryCluster) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
    ) {
        // Первая ячейка — собственные сторис: самый частый сценарий
        item(key = "__my_story__") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(ScAvatarSize.tray + 8.dp),
            ) {
                Box {
                    UserAvatar(
                        user = me,
                        size = ScAvatarSize.tray,
                        fallbackTitle = "Вы",
                        fallbackId = me?.id?.raw ?: "me",
                        showOnline = false,
                        onClick = onCreateStory,
                    )
                    // Плюсик поверх аватара — явный сигнал «добавить»
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(ScShapes.chip)
                            .background(ScTheme.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ваша история",
                    color = ScTheme.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }

        // Непросмотренные первыми — иначе их не найти в длинном трейе
        items(
            items = clusters.sortedByDescending { it.hasUnseen },
            key = { it.authorId.raw },
        ) { cluster ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(ScAvatarSize.tray + 8.dp),
            ) {
                Box(Modifier.clickable { onOpenStories(cluster) }) {
                    StoryTrayAvatar(
                        user = cluster.author,
                        stories = cluster.stories,
                        size = ScAvatarSize.tray,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = cluster.author.firstName,
                    color = if (cluster.hasUnseen) ScTheme.textPrimary else ScTheme.textTertiary,
                    fontSize = 11.sp,
                    fontWeight = if (cluster.hasUnseen) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Фильтр-чипы списка чатов. */
@Composable
private fun FilterChips(
    selected: ChatListFilter,
    unreadCount: Int,
    onSelect: (ChatListFilter) -> Unit,
) {
    val chips = listOf(
        ChatListFilter.ALL to "Все",
        ChatListFilter.UNREAD to if (unreadCount > 0) "Непрочитанные $unreadCount" else "Непрочитанные",
        ChatListFilter.PERSONAL to "Личные",
        ChatListFilter.GROUPS to "Группы",
        ChatListFilter.CHANNELS to "Каналы",
        ChatListFilter.MUTED to "Без звука",
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
    ) {
        items(chips, key = { it.first }) { (filter, label) ->
            val isSelected = filter == selected
            // Порядок clip -> background -> clickable -> padding фиксирован:
            // фон закрашивает всю область тапа, а текст не прижат к краю
            Text(
                text = label,
                color = if (isSelected) Color.White else ScTheme.textSecondary,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(ScShapes.chip)
                    .background(if (isSelected) ScTheme.accent else ScTheme.surfaceGlass)
                    .clickable(onClick = { onSelect(filter) })
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** Скелетон списка чатов на время первой загрузки. */
@Composable
private fun ChatListSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(vertical = ScSpacing.sm)) {
        repeat(8) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
            ) {
                Box(
                    Modifier
                        .size(ScAvatarSize.listItem)
                        .clip(ScShapes.chip)
                        .background(ScTheme.shimmerBase),
                )
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(13.dp)
                            .clip(ScShapes.chip)
                            .background(ScTheme.shimmerBase),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(11.dp)
                            .clip(ScShapes.chip)
                            .background(ScTheme.shimmerHighlight),
                    )
                }
            }
        }
    }
}

/**
 * Пустое состояние при первом входе: чатов ещё нет.
 *
 * Два действия, а не одно: «найти людей» и «создать канал» — два разных
 * сценария первого запуска, и выбирать между ними должен пользователь.
 */
@Composable
fun ChatsEmptyScreen(onFindPeople: () -> Unit, onCreateChannel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(ScSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            icon = Icons.Filled.Groups,
            title = "Добро пожаловать в SilverChat",
            subtitle = "Найдите собеседников по @username, создайте группу или запустите свой канал.",
            actionLabel = "Найти людей",
            onAction = onFindPeople,
        )
        Spacer(Modifier.height(ScSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
            Icon(Icons.Filled.Campaign, contentDescription = null, tint = ScTheme.textTertiary, modifier = Modifier.size(18.dp))
            Text(
                text = "Создать канал",
                color = ScTheme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(ScShapes.chip)
                    .clickable(onClick = onCreateChannel)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
