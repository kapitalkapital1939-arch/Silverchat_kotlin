package com.silverchat.feature.chats.screen

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.SearchField
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.chat.ChatSectionHeader
import com.silverchat.core.designsystem.component.chat.previewText
import com.silverchat.core.designsystem.component.market.ChipSmall
import com.silverchat.core.designsystem.component.market.rarityColor
import com.silverchat.core.designsystem.navigation.ChatsNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.domain.repository.GlobalSearchResult
import com.silverchat.core.model.Chat
import com.silverchat.core.model.User
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.labelRu
import com.silverchat.feature.chats.SearchTarget
import com.silverchat.feature.chats.SearchViewModel

/**
 * Экран поиска.
 *
 * Ищет одновременно по пяти сущностям и группирует результат секциями:
 * пользователи, чаты, каналы, сообщения, лоты маркета. Единый поиск —
 * требование «поиск по @username»: пользователь не должен выбирать,
 * где именно искать человека.
 *
 * Результаты маркета показываются здесь же: если ник занят, пользователь
 * сразу видит, что его можно купить. Это главная точка пересечения
 * мессенджера и экономики.
 */
@Composable
fun SearchScreen(
    navigator: ChatsNavigator,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Точный переход по @handle: если запрос совпал с существующим
    // пользователем или каналом, открываем его сразу, без списка
    LaunchedEffect(state.resolveTarget) {
        state.resolveTarget?.let { target ->
            when (target) {
                is SearchTarget.User -> navigator.openProfile(target.userId)
                is SearchTarget.Chat -> navigator.openChat(target.chatId)
                is SearchTarget.Listing -> navigator.openProfile(target.sellerId)
            }
            viewModel.consumeResolve()
        }
    }

    Column(modifier.fillMaxSize().background(ScTheme.background)) {
        SilverTopBar(
            title = "Поиск",
            onBack = onBack,
            actions = {
                if (state.query.isNotBlank()) {
                    SilverIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Очистить",
                        onClick = { viewModel.onQueryChanged("") },
                        tint = ScTheme.textPrimary,
                    )
                }
            },
        )

        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChanged,
            placeholder = "Имя, @username или текст сообщения",
            onClear = { viewModel.onQueryChanged("") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md),
        )

        if (state.query.isBlank()) {
            RecentSearches(
                recent = state.recentSearches,
                onSelect = viewModel::onQueryChanged,
                onRemove = viewModel::removeRecent,
                onClearAll = viewModel::clearRecent,
            )
        } else if (state.isSearching) {
            SearchSkeleton()
        } else if (state.result.isEmpty) {
            EmptyState(
                icon = Icons.Filled.Search,
                title = "Ничего не найдено",
                subtitle = "Проверьте запрос. Точный поиск по нику: введите @username целиком.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SearchResults(
                result = state.result,
                navigator = navigator,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun SearchResults(
    result: GlobalSearchResult,
    navigator: ChatsNavigator,
    viewModel: SearchViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ScSpacing.xxl),
    ) {
        // ── Пользователи ─────────────────────────────────────────────────
        if (result.users.isNotEmpty()) {
            item(key = "hdr_users", contentType = "header") {
                ChatSectionHeader("Люди", result.users.size)
            }
            items(result.users, key = { "u_${it.id.raw}" }, contentType = { "user" }) { user ->
                UserSearchRow(
                    user = user,
                    onClick = {
                        viewModel.saveRecent(result.query)
                        navigator.openProfile(user.id.raw)
                    },
                )
            }
        }

        // ── Чаты и группы ────────────────────────────────────────────────
        if (result.chats.isNotEmpty()) {
            item(key = "hdr_chats", contentType = "header") {
                ChatSectionHeader("Чаты и группы", result.chats.size)
            }
            items(result.chats, key = { "c_${it.id.raw}" }, contentType = { "chat" }) { chat ->
                ChatSearchRow(
                    chat = chat,
                    onClick = {
                        viewModel.saveRecent(result.query)
                        navigator.openChat(chat.id.raw)
                    },
                )
            }
        }

        // ── Каналы ───────────────────────────────────────────────────────
        if (result.channels.isNotEmpty()) {
            item(key = "hdr_channels", contentType = "header") {
                ChatSectionHeader("Каналы", result.channels.size)
            }
            items(result.channels, key = { "ch_${it.id.raw}" }, contentType = { "chat" }) { chat ->
                ChatSearchRow(
                    chat = chat,
                    isChannel = true,
                    onClick = {
                        viewModel.saveRecent(result.query)
                        navigator.openChat(chat.id.raw)
                    },
                )
            }
        }

        // ── Сообщения ────────────────────────────────────────────────────
        if (result.messages.isNotEmpty()) {
            item(key = "hdr_messages", contentType = "header") {
                ChatSectionHeader("Сообщения", result.messages.size)
            }
            items(result.messages, key = { "m_${it.id.raw}" }, contentType = { "message" }) { message ->
                MessageSearchRow(
                    snippet = previewText(message.content),
                    senderName = message.sender?.fullName ?: "Неизвестный",
                    timestamp = message.sentAt,
                    onClick = {
                        viewModel.saveRecent(result.query)
                        navigator.openChat(message.chatId.raw)
                    },
                )
            }
        }

        // ── Лоты маркета ─────────────────────────────────────────────────
        if (result.listings.isNotEmpty()) {
            item(key = "hdr_listings", contentType = "header") {
                ChatSectionHeader("Юзернеймы в маркете", result.listings.size)
            }
            items(result.listings, key = { "l_${it.id.raw}" }, contentType = { "listing" }) { listing ->
                ListingSearchRow(listing = listing)
            }
        }
    }
}

@Composable
private fun UserSearchRow(user: User, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(user = user, size = ScAvatarSize.member, showOnline = true)
        Spacer(Modifier.width(ScSpacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = user.fullName,
                    color = ScTheme.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.badges.verified) {
                    com.silverchat.core.designsystem.component.VerifiedBadge(size = 14)
                }
            }
            Text(
                text = user.handle ?: "без юзернейма",
                color = ScTheme.accent,
                fontSize = 13.sp,
                maxLines = 1,
            )
            user.bio?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = ScTheme.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ChatSearchRow(chat: Chat, isChannel: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            avatarOverride = chat.avatar,
            fallbackTitle = chat.title,
            fallbackId = chat.id.raw,
            size = ScAvatarSize.member,
        )
        Spacer(Modifier.width(ScSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = chat.displayName,
                color = ScTheme.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = if (isChannel) {
                    "${NumberFormatter.compact(chat.membersCount.toLong())} подписчиков"
                } else {
                    "${NumberFormatter.compact(chat.membersCount.toLong())} участников"
                },
                color = ScTheme.textTertiary,
                fontSize = 13.sp,
            )
        }
        Icon(
            imageVector = if (isChannel) Icons.Filled.Campaign else Icons.Filled.Groups,
            contentDescription = null,
            tint = ScTheme.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MessageSearchRow(
    snippet: String,
    senderName: String,
    timestamp: Long,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(ScAvatarSize.member).clip(CircleShape).background(ScTheme.surfaceGlass),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Search, null, tint = ScTheme.textTertiary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(ScSpacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = senderName,
                    color = ScTheme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(TimeFormatter.chatListTime(timestamp), color = ScTheme.textTertiary, fontSize = 11.sp)
            }
            Text(snippet, color = ScTheme.textSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ListingSearchRow(listing: UsernameListing) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(ScAvatarSize.member).clip(CircleShape).background(ScTheme.silver.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("@", color = ScTheme.silver, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(ScSpacing.md))
        Column(Modifier.weight(1f)) {
            Text("@${listing.username}", color = ScTheme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipSmall(listing.rarity.labelRu, rarityColor(listing.rarity))
                ChipSmall(listing.status.labelRu, ScTheme.textTertiary)
            }
        }
        Text(
            text = NumberFormatter.silver(listing.priceSilver),
            color = ScTheme.silver,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** История поисковых запросов — показывается до ввода. */
@Composable
private fun RecentSearches(
    recent: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (recent.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(ScSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Найдите собеседника по имени или @username,\nканал по названию или сообщение по тексту.",
                color = ScTheme.textTertiary,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(top = ScSpacing.md)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Недавние",
                color = ScTheme.textSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Очистить",
                color = ScTheme.accent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(ScShapes.chip)
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
        ) {
            items(recent, key = { it }) { query ->
                Row(
                    Modifier
                        .clip(ScShapes.chip)
                        .background(ScTheme.surfaceGlass)
                        .clickable { onSelect(query) }
                        .padding(start = 12.dp, top = 7.dp, bottom = 7.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.History, null, tint = ScTheme.textTertiary, modifier = Modifier.size(14.dp))
                    Text(query, color = ScTheme.textPrimary, fontSize = 13.sp, maxLines = 1)
                    SilverIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Убрать из недавних: $query",
                        onClick = { onRemove(query) },
                        size = 22,
                        tint = ScTheme.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSkeleton() {
    Column(Modifier.fillMaxWidth().padding(ScSpacing.md)) {
        repeat(6) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = ScSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(ScAvatarSize.member).clip(CircleShape).background(ScTheme.shimmerBase),
                )
                Spacer(Modifier.width(ScSpacing.md))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth(0.4f).height(12.dp).clip(ScShapes.chip).background(ScTheme.shimmerBase),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth(0.7f).height(10.dp).clip(ScShapes.chip).background(ScTheme.shimmerHighlight),
                    )
                }
            }
        }
    }
}
