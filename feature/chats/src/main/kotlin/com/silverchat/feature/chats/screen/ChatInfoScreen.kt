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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.common.format.UsernameFormatter
import com.silverchat.core.designsystem.component.ProfileBanner
import com.silverchat.core.designsystem.component.SettingsRow
import com.silverchat.core.designsystem.component.SettingsSection
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.UserBadgesRow
import com.silverchat.core.designsystem.component.admin.AdminRoleChip
import com.silverchat.core.designsystem.navigation.ChatsNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatInviteLink
import com.silverchat.core.model.ChatMember
import com.silverchat.core.model.MemberRole
import com.silverchat.core.model.labelRu
import com.silverchat.feature.chats.ChatInfoUiState
import com.silverchat.feature.chats.ChatInfoViewModel
import com.silverchat.feature.chats.navigation.ChatInfoTab

/**
 * Информационная панель чата, группы или канала.
 *
 * Один экран с вкладками, а не четыре отдельных: пользователь приходит сюда
 * из шапки диалога и ожидает увидеть всё сразу — описание, участников,
 * ссылки и общие медиа. Вкладки переключаются параметром навигации,
 * поэтому на каждую можно попасть deep link'ом.
 *
 * Состав зависит от прав:
 *  - рядовой участник видит описание, участников и медиа;
 *  - админ/владелец ([Chat.canEditInfo]) дополнительно видит управление
 *    ссылками, медленный режим и модерацию участников.
 *
 * Кастомные блоки «Местоположение» и «Часы работы» — фирменная особенность
 * SilverChat для каналов бизнеса; они показываются только если заполнены.
 */
@Composable
fun ChatInfoScreen(
    chatId: String,
    navigator: ChatsNavigator,
    modifier: Modifier = Modifier,
    initialTab: ChatInfoTab = ChatInfoTab.INFO,
    onOpenMembers: (String) -> Unit = {},
    onOpenInvites: (String) -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    viewModel: ChatInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chat = state.chat

    Column(modifier.fillMaxSize().background(ScTheme.background)) {
        SilverTopBar(
            title = tabTitle(initialTab),
            subtitle = chat?.displayName,
            onBack = navigator::back,
        )

        if (chat == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Загрузка…", color = ScTheme.textTertiary, fontSize = 14.sp)
            }
            return
        }

        when (initialTab) {
            ChatInfoTab.INFO -> InfoTab(chat = chat, state = state, navigator = navigator, viewModel = viewModel)
            ChatInfoTab.MEMBERS -> MembersTab(chat = chat, members = state.members, navigator = navigator)
            ChatInfoTab.INVITES -> InvitesTab(chat = chat, links = state.inviteLinks, viewModel = viewModel)
            ChatInfoTab.MEDIA -> MediaTab(state = state, onBack = navigator::back)
        }
    }
}

private fun tabTitle(tab: ChatInfoTab): String = when (tab) {
    ChatInfoTab.INFO -> "Информация"
    ChatInfoTab.MEMBERS -> "Участники"
    ChatInfoTab.INVITES -> "Пригласительные ссылки"
    ChatInfoTab.MEDIA -> "Общие медиа"
}

/* =========================================================================
   ВКЛАДКА «ИНФОРМАЦИЯ»
   ========================================================================= */

@Composable
private fun InfoTab(
    chat: Chat,
    state: ChatInfoUiState,
    navigator: ChatsNavigator,
    viewModel: ChatInfoViewModel,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ScSpacing.xxl),
    ) {
        // ── Hero: баннер + аватар + имя ──────────────────────────────────
        item(key = "hero", contentType = "hero") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProfileBanner(
                    banner = chat.peer?.banner,
                    height = 150.dp,
                    allowAnimation = state.animationsEnabled,
                )
                Box(
                    Modifier
                        .padding(top = ScSpacing.lg)
                        .size(ScAvatarSize.profileHero),
                ) {
                    UserAvatar(
                        user = chat.peer,
                        avatarOverride = chat.avatar,
                        fallbackTitle = chat.title,
                        fallbackId = chat.id.raw,
                        size = ScAvatarSize.profileHero,
                        allowAnimation = state.animationsEnabled,
                        borderWidth = 3.dp,
                        borderColor = ScTheme.surface,
                    )
                }
                Spacer(Modifier.height(ScSpacing.md))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = ScSpacing.md),
                ) {
                    Text(
                        text = chat.displayName,
                        color = ScTheme.textPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    if (chat.verified) {
                        com.silverchat.core.designsystem.component.VerifiedBadge(size = 18)
                    }
                }
                Text(
                    text = chat.type.labelRu,
                    color = ScTheme.textTertiary,
                    fontSize = 13.sp,
                )
                chat.username?.let { handle ->
                    Spacer(Modifier.height(ScSpacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(ScShapes.chip)
                            .background(ScTheme.surfaceGlass)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("@$handle", color = ScTheme.accent, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(ScSpacing.sm))
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Скопировать ссылку",
                            tint = ScTheme.textTertiary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Spacer(Modifier.height(ScSpacing.lg))

                // Быстрые действия
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = ScSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (!chat.isChannel) {
                        QuickAction(Icons.Filled.Notifications, "Без звука") { viewModel.toggleMute() }
                        QuickAction(Icons.Filled.PersonAdd, "Пригласить") { navigator.openChatInvites(chat.id.raw) }
                    }
                    QuickAction(Icons.Filled.PhotoLibrary, "Медиа") { navigator.openChatMedia(chat.id.raw) }
                    QuickAction(Icons.Filled.Shield, "Участники") { navigator.openChatMembers(chat.id.raw) }
                }
                Spacer(Modifier.height(ScSpacing.lg))
            }
        }

        // ── Описание ─────────────────────────────────────────────────────
        chat.about?.takeIf { it.isNotBlank() }?.let { about ->
            item(key = "about", contentType = "text") {
                Column(Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm)) {
                    Text("Описание", color = ScTheme.textTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(about, color = ScTheme.textPrimary, fontSize = 14.5.sp, lineHeight = 20.sp)
                }
            }
        }

        // ── Кастомные блоки канала ───────────────────────────────────────
        chat.location?.takeIf { it.visible }?.let { location ->
            item(key = "location", contentType = "row") {
                SettingsRow(
                    title = location.title ?: location.address ?: "Местоположение",
                    subtitle = "${location.latitude}, ${location.longitude}",
                    icon = Icons.Filled.LocationOn,
                    iconTint = ScTheme.accent,
                )
            }
        }
        chat.workingHours?.let { hours ->
            item(key = "hours", contentType = "row") {
                SettingsRow(
                    title = "Часы работы",
                    subtitle = hours.summaryRu(),
                    icon = Icons.Filled.Schedule,
                    iconTint = ScTheme.success,
                )
            }
        }

        // ── Статистика ───────────────────────────────────────────────────
        item(key = "stats_header", contentType = "header") {
            SettingsSection("Статистика")
        }
        item(key = "stats", contentType = "row") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCell(
                    value = NumberFormatter.compact(chat.membersCount.toLong()),
                    label = if (chat.isChannel) "подписчиков" else "участников",
                )
                if (chat.onlineCount > 0) {
                    StatCell(NumberFormatter.compact(chat.onlineCount.toLong()), "в сети")
                }
                StatCell(state.mediaCount.toString(), "медиа")
            }
        }

        // ── Настройки уведомлений ────────────────────────────────────────
        item(key = "notify_header", contentType = "header") { SettingsSection("Уведомления") }
        item(key = "notify", contentType = "row") {
            SettingsRow(
                title = if (chat.isMuted) "Уведомления отключены" else "Уведомления включены",
                subtitle = chat.mutedUntil?.let { "До ${TimeFormatter.full(it)}" } ?: "Нажмите, чтобы изменить",
                icon = if (chat.isMuted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                iconTint = if (chat.isMuted) ScTheme.textTertiary else ScTheme.accent,
                onClick = viewModel::toggleMute,
            )
        }

        // ── Управление (только для админов) ──────────────────────────────
        if (chat.canEditInfo) {
            item(key = "manage_header", contentType = "header") { SettingsSection("Управление") }
            item(key = "manage_edit", contentType = "row") {
                SettingsRow(
                    title = "Изменить информацию",
                    subtitle = "Название, описание, аватар",
                    icon = Icons.Filled.Edit,
                    iconTint = ScTheme.accent,
                    onClick = viewModel::startEditInfo,
                )
            }
            item(key = "manage_links", contentType = "row") {
                SettingsRow(
                    title = "Пригласительные ссылки",
                    subtitle = "${chat.inviteLinks.size} активных · ${chat.joinRequestsCount} заявок",
                    icon = Icons.Filled.Link,
                    iconTint = ScTheme.accent,
                    onClick = { navigator.openChatInvites(chat.id.raw) },
                )
            }
            if (chat.slowModeSeconds > 0) {
                item(key = "manage_slow", contentType = "row") {
                    SettingsRow(
                        title = "Медленный режим",
                        subtitle = "${chat.slowModeSeconds} с между сообщениями",
                        icon = Icons.Filled.Schedule,
                        iconTint = ScTheme.warning,
                        onClick = viewModel::disableSlowMode,
                    )
                }
            }
            item(key = "manage_delete", contentType = "row") {
                SettingsRow(
                    title = if (chat.isPersonal) "Удалить чат" else "Покинуть и удалить",
                    icon = Icons.Filled.Delete,
                    iconTint = ScTheme.danger,
                    danger = true,
                    onClick = viewModel::requestDelete,
                )
            }
        } else {
            item(key = "leave", contentType = "row") {
                SettingsRow(
                    title = if (chat.isChannel) "Отписаться от канала" else "Покинуть группу",
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    iconTint = ScTheme.danger,
                    danger = true,
                    onClick = viewModel::requestLeave,
                )
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(ScShapes.cardSmall)
            .clickable(onClick = onClick)
            .padding(ScSpacing.sm),
    ) {
        Box(
            Modifier.size(44.dp).clip(ScShapes.chip).background(ScTheme.surfaceGlass),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ScTheme.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = ScTheme.textSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = ScTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ScTheme.textTertiary, fontSize = 11.sp)
    }
}

/** Краткая сводка часов работы на русском. */
private fun com.silverchat.core.model.WorkingHours.summaryRu(): String {
    if (alwaysOpen) return "Круглосуточно"
    val openDays = schedule.filter { !it.closed }
    if (openDays.isEmpty()) return "Закрыто"
    val first = openDays.first()
    val range = "${first.openMinute.toClock()}–${first.closeMinute.toClock()}"
    return if (openDays.size == DAYS_IN_WEEK) {
        "Ежедневно, $range"
    } else {
        "${openDays.size} ${pluralDays(openDays.size)} в неделю · $range"
    }
}

private const val DAYS_IN_WEEK = 7

/** Минуты от полуночи -> «9:30». */
private fun Int.toClock(): String = "%d:%02d".format(this / 60, this % 60)

private fun pluralDays(n: Int): String = when {
    n == 1 -> "день"
    n in 2..4 -> "дня"
    else -> "дней"
}

/* =========================================================================
   ВКЛАДКА «УЧАСТНИКИ»
   ========================================================================= */

@Composable
private fun MembersTab(
    chat: Chat,
    members: List<ChatMember>,
    navigator: ChatsNavigator,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ScSpacing.xxl)) {
        item(key = "hdr", contentType = "header") {
            ChatSectionHeaderLocal("Участники", members.size)
        }
        // Админы первыми: в большой группе их иначе не найти
        items(
            items = members.sortedByDescending { it.role != MemberRole.MEMBER },
            key = { it.user.id.raw },
            contentType = { "member" },
        ) { member ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { navigator.openProfile(member.user.id.raw) }
                    .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(user = member.user, size = ScAvatarSize.member, showOnline = true)
                Spacer(Modifier.width(ScSpacing.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = member.customTitle ?: member.user.fullName,
                            color = ScTheme.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        UserBadgesRow(member.user.badges, iconSize = 14)
                    }
                    Text(
                        text = member.user.handle ?: member.role.labelRu(),
                        color = ScTheme.textTertiary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (member.role != MemberRole.MEMBER) AdminRoleChip(member.role.toAdminRole())
            }
        }
    }
}

/**
     * Русская подпись роли участника.
     *
     * В `Labels.kt` есть `labelRu` для ChatType и AdminRole, но не для
     * MemberRole: роли участника отображаются только здесь, поэтому
     * маппинг живёт рядом с использованием, а не в общем словаре.
     */
    private fun MemberRole.labelRu(): String = when (this) {
        MemberRole.OWNER -> "владелец"
        MemberRole.ADMIN -> "админ"
        MemberRole.MEMBER -> "участник"
        MemberRole.RESTRICTED -> "ограничен"
        MemberRole.KICKED -> "исключён"
    }

/** Роль участника чата -> роль для отображения чипа. */
private fun MemberRole.toAdminRole() = when (this) {
    MemberRole.OWNER -> com.silverchat.core.model.AdminRole.OWNER
    MemberRole.ADMIN -> com.silverchat.core.model.AdminRole.ADMIN
    else -> com.silverchat.core.model.AdminRole.NONE
}

@Composable
private fun ChatSectionHeaderLocal(title: String, count: Int) {
    Text(
        text = "$title · $count",
        color = ScTheme.accent,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
    )
}

/* =========================================================================
   ВКЛАДКА «ПРИГЛАСИТЕЛЬНЫЕ ССЫЛКИ»
   ========================================================================= */

/**
 * Защищённые пригласительные ссылки.
 *
 * Вместо открытого доступа — ссылки с ограничением по числу использований,
 * сроку действия и с ручным подтверждением заявок. Это требование
 * «защищённые пригласительные ссылки вместо открытого доступа».
 */
@Composable
private fun InvitesTab(chat: Chat, links: List<ChatInviteLink>, viewModel: ChatInfoViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ScSpacing.xxl)) {
        item(key = "create", contentType = "row") {
            SettingsRow(
                title = "Создать ссылку",
                subtitle = "С лимитом использований, сроком и подтверждением заявок",
                icon = Icons.Filled.Link,
                iconTint = ScTheme.accent,
                onClick = viewModel::createInviteLink,
            )
        }

        item(key = "hdr", contentType = "header") {
            ChatSectionHeaderLocal("Активные ссылки", links.size)
        }

        if (links.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                Text(
                    text = "Ссылок пока нет. Создайте первую — её можно ограничить\nпо числу входов и сроку действия.",
                    color = ScTheme.textTertiary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(ScSpacing.xl),
                )
            }
        }

        items(links, key = { it.token }, contentType = { "link" }) { link ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm)
                    .clip(ScShapes.card)
                    .background(ScTheme.surfaceElevated)
                    .padding(ScSpacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = link.name ?: "Без названия",
                            color = ScTheme.textPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = UsernameFormatter.inviteUrl(link.token),
                            color = ScTheme.accent,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (link.requiresApproval) {
                        Text(
                            text = "Заявки",
                            color = ScTheme.warning,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(ScShapes.chip)
                                .background(ScTheme.warning.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(ScSpacing.sm))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
                ) {
                    LinkStat(
                        label = "Входов",
                        value = link.maxUses?.let { "${link.usedCount}/$it" } ?: "${link.usedCount}/∞",
                    )
                    LinkStat(
                        label = "Действует до",
                        value = link.expiresAt?.let { TimeFormatter.daySeparator(it) } ?: "бессрочно",
                    )
                    LinkStat(
                        label = "Статус",
                        value = if (link.revoked) "отозвана" else "активна",
                        danger = link.revoked,
                    )
                }
                Spacer(Modifier.height(ScSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(ScShapes.chip)
                            .background(ScTheme.surface)
                            .clickable { viewModel.copyLink(link) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Скопировать", color = ScTheme.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(ScShapes.chip)
                            .background(ScTheme.danger.copy(alpha = 0.12f))
                            .clickable(enabled = !link.revoked) { viewModel.revokeLink(link) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Отозвать", color = ScTheme.danger, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun LinkStat(label: String, value: String, danger: Boolean = false) {
    Column {
        Text(label, color = ScTheme.textTertiary, fontSize = 10.5.sp)
        Text(
            text = value,
            color = if (danger) ScTheme.danger else ScTheme.textPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/* =========================================================================
   ВКЛАДКА «ОБЩИЕ МЕДИА»
   ========================================================================= */

@Composable
private fun MediaTab(state: ChatInfoUiState, onBack: () -> Unit) {
    val kinds = com.silverchat.core.domain.repository.SharedMediaKind.entries

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
        ) {
            items(kinds, key = { it }) { kind ->
                val selected = kind == state.selectedMediaKind
                Text(
                    text = kind.labelRu(),
                    color = if (selected) androidx.compose.ui.graphics.Color.White else ScTheme.textSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(ScShapes.chip)
                        .background(if (selected) ScTheme.accent else ScTheme.surfaceGlass)
                        .clickable { state.onMediaKindSelected?.invoke(kind) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        if (state.sharedMedia.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "В этой категории пока ничего нет",
                    color = ScTheme.textTertiary,
                    fontSize = 13.5.sp,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ScSpacing.xxl)) {
                items(state.sharedMedia, key = { it.id.raw }, contentType = { "media" }) { message ->
                    Text(
                        text = com.silverchat.core.designsystem.component.chat.previewText(message.content),
                        color = ScTheme.textPrimary,
                        fontSize = 13.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                    )
                }
            }
        }
    }
}

private fun com.silverchat.core.domain.repository.SharedMediaKind.labelRu(): String = when (this) {
    com.silverchat.core.domain.repository.SharedMediaKind.PHOTO -> "Фото"
    com.silverchat.core.domain.repository.SharedMediaKind.VIDEO -> "Видео"
    com.silverchat.core.domain.repository.SharedMediaKind.VOICE -> "Голосовые"
    com.silverchat.core.domain.repository.SharedMediaKind.FILE -> "Файлы"
    com.silverchat.core.domain.repository.SharedMediaKind.LINK -> "Ссылки"
    com.silverchat.core.domain.repository.SharedMediaKind.MUSIC -> "Музыка"
    com.silverchat.core.domain.repository.SharedMediaKind.CIRCLE -> "Кружки"
}
