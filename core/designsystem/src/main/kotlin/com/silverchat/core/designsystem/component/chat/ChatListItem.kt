package com.silverchat.core.designsystem.component.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.PremiumBadge
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.VerifiedBadge
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.AvatarMedia
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatType
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.MessageStatus
import com.silverchat.core.model.User
import com.silverchat.core.model.UserPresence

/**
 * Строка списка чатов.
 *
 * Отображает всё, что видно в списке: аватар (с кольцом сторис и значком
 * типа чата), имя с бейджами, превью последнего сообщения с учётом его типа,
 * галочки доставки у своих сообщений, время, счётчик непрочитанных
 * (серый, если чат заглушен), закрепление и «печатает…».
 *
 * Долгий тап открывает контекстное меню. Свайпы намеренно не используются:
 * в мессенджере свайп читается как «удалить», а случайное удаление диалога —
 * самая частая жалоба пользователей.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    chat: Chat,
    modifier: Modifier = Modifier,
    /** Имя пользователя, который сейчас печатает (из typing-события WebSocket). */
    typingUserName: String? = null,
    hasUnseenStories: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val last = chat.lastMessage
    val isTyping = typingUserName != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) ScTheme.accentContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
    ) {
        // ── Аватар ───────────────────────────────────────────────────────
        Box(Modifier.size(ScAvatarSize.listItem)) {
            UserAvatar(
                user = chat.peer,
                avatarOverride = chat.avatar,
                fallbackTitle = chat.title,
                fallbackId = chat.id.raw,
                size = ScAvatarSize.listItem,
                showOnline = chat.isPersonal && chat.peer?.presence is UserPresence.Online,
                showStoryRing = hasUnseenStories,
                storySeen = false,
            )
            if (chat.isChannel || chat.isGroup) {
                ChatKindOverlay(type = chat.type, isSecret = chat.isSecret)
            }
        }

        // ── Имя + превью ─────────────────────────────────────────────────
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.displayName,
                    color = ScTheme.textPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = if (chat.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (chat.verified) {
                    Spacer(Modifier.width(4.dp))
                    VerifiedBadge(size = 15)
                }
                if (chat.peer?.premium?.isActive == true) {
                    Spacer(Modifier.width(2.dp))
                    PremiumBadge(size = 14)
                }
                if (chat.isMuted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.NotificationsOff,
                        contentDescription = "Уведомления отключены",
                        tint = ScTheme.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isTyping) {
                    Text(
                        text = if (chat.isPersonal) "печатает…" else "$typingUserName печатает…",
                        color = ScTheme.accent,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else if (last != null) {
                    // В группах и каналах показываем имя отправителя
                    if (!chat.isPersonal) {
                        Text(
                            text = last.sender?.fullName ?: "Канал",
                            color = ScTheme.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (last.isMine) {
                        DeliveryTick(status = last.status)
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        text = previewText(last.content),
                        color = ScTheme.textSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    Text(
                        text = if (chat.isChannel) "Канал создан" else "Нет сообщений",
                        color = ScTheme.textTertiary,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // ── Время + бейдж непрочитанных ──────────────────────────────────
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (chat.pinned.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Pin,
                        contentDescription = "Есть закреплённые сообщения",
                        tint = ScTheme.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = last?.let { TimeFormatter.chatListTime(it.sentAt) } ?: "",
                    color = if (chat.unreadCount > 0) ScTheme.textSecondary else ScTheme.textTertiary,
                    fontSize = 11.5.sp,
                    fontWeight = if (chat.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                )
            }

            if (chat.unreadCount > 0) {
                // Серый бейдж для заглушённых чатов: непрочитанное есть,
                // но пользователь сознательно отключил уведомления
                val badgeColor = when {
                    chat.isMuted -> ScTheme.navBarUnselected
                    chat.isChannel -> ScTheme.accentLight
                    else -> ScTheme.accent
                }
                UnreadBadge(count = chat.unreadCount, color = badgeColor, hasMentions = chat.mentionsCount > 0)
            } else {
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

/** Значок типа чата в правом нижнем углу аватара. */
@Composable
private fun ChatKindOverlay(type: ChatType, isSecret: Boolean) {
    val icon = when {
        isSecret -> Icons.Filled.Lock
        type == ChatType.CHANNEL || type == ChatType.BROADCAST -> Icons.Filled.Campaign
        type == ChatType.GROUP || type == ChatType.SUPERGROUP -> Icons.Filled.Groups
        else -> null
    } ?: return

    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .size(18.dp)
            .clip(CircleShape)
            .background(ScTheme.surface)
            .padding(1.5.dp)
            .clip(CircleShape)
            .background(if (isSecret) ScTheme.textTertiary else ScTheme.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.labelRuShort(isSecret),
            tint = Color.White,
            modifier = Modifier.size(11.dp),
        )
    }
}

private fun ChatType.labelRuShort(isSecret: Boolean): String = when {
    isSecret -> "Секретный чат"
    this == ChatType.CHANNEL || this == ChatType.BROADCAST -> "Канал"
    this == ChatType.GROUP || this == ChatType.SUPERGROUP -> "Группа"
    else -> "Чат"
}

@Composable
private fun DeliveryTick(status: MessageStatus) {
    val (icon, tint) = when (status) {
        MessageStatus.SENDING, MessageStatus.DRAFT -> Icons.Filled.Schedule to ScTheme.textTertiary
        MessageStatus.SENT -> Icons.Filled.Check to ScTheme.textTertiary
        MessageStatus.DELIVERED -> Icons.Filled.DoneAll to ScTheme.textTertiary
        MessageStatus.READ -> Icons.Filled.DoneAll to ScTheme.accent
        MessageStatus.FAILED -> Icons.Filled.Delete to ScTheme.danger
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(15.dp),
    )
}

/** Бейдж непрочитанных; при упоминании показывает «@» вместо числа. */
@Composable
private fun UnreadBadge(count: Int, color: Color, hasMentions: Boolean) {
    val label = when {
        hasMentions -> "@"
        count > 99 -> "99+"
        else -> count.toString()
    }
    Box(
        Modifier
            .clip(ScShapes.chip)
            .background(color)
            .padding(horizontal = 7.dp, vertical = 1.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Человекочитаемое превью по типу контента.
 *
 * Вместо «[медиа]» показываем описание: пользователь должен понимать,
 * что именно пришло, не открывая диалог.
 */
fun previewText(content: MessageContent): String = when (content) {
    is MessageContent.Text -> content.text
    is MessageContent.Photo -> content.caption ?: "📷 Фото"
    is MessageContent.Video -> content.caption ?: "🎬 Видео"
    is MessageContent.Voice -> "🎙 Голосовое ${TimeFormatter.duration(content.durationMs)}"
    is MessageContent.VideoCircle -> "⭕ Видеосообщение"
    is MessageContent.Sticker -> "Стикер ${content.emoji}"
    is MessageContent.Gif -> "GIF"
    is MessageContent.File -> "📎 ${content.fileName}"
    is MessageContent.Location -> content.title?.let { "📍 $it" } ?: "📍 Геолокация"
    is MessageContent.Contact -> "👤 ${content.firstName} ${content.lastName.orEmpty()}"
    is MessageContent.Poll -> "📊 ${content.question}"
    is MessageContent.Service -> content.title
    is MessageContent.Invite -> "🔗 Приглашение в «${content.chatTitle}»"
    is MessageContent.Gift -> "🎁 Подарок «${content.title}»"
}

/** Принадлежит ли сообщение текущему пользователю. */
val Message.isMine: Boolean
    get() = status == MessageStatus.READ || status == MessageStatus.DELIVERED ||
        status == MessageStatus.SENT || status == MessageStatus.SENDING ||
        status == MessageStatus.FAILED

/**
 * Действия контекстного меню строки чата.
 *
 * Отдельный тип, чтобы меню рисовал вызывающий экран (BottomSheet в :app),
 * а дизайн-система не зависела от конкретного UI-контейнера.
 */
enum class ChatListItemActions(val labelRu: String) {
    PIN("Закрепить"),
    UNPIN("Открепить"),
    MUTE("Отключить уведомления"),
    UNMUTE("Включить уведомления"),
    MARK_READ("Отметить прочитанным"),
    MARK_UNREAD("Отметить непрочитанным"),
    CLEAR_HISTORY("Очистить историю"),
    LEAVE("Покинуть"),
    DELETE("Удалить чат"),
    MANAGE("Управление"),
    ;

    /** Набор действий зависит от типа чата и роли пользователя. */
    fun availableFor(chat: Chat): List<ChatListItemActions> = buildList {
        if (chat.pinned.isNotEmpty()) add(UNPIN) else add(PIN)
        if (chat.isMuted) add(UNMUTE) else add(MUTE)
        if (chat.unreadCount > 0) add(MARK_READ) else add(MARK_UNREAD)

        when {
            chat.isPersonal || chat.isSecret -> {
                add(CLEAR_HISTORY)
                add(DELETE)
            }

            chat.canEditInfo -> {
                add(MANAGE)
                add(CLEAR_HISTORY)
                add(LEAVE)
            }

            else -> {
                add(CLEAR_HISTORY)
                add(LEAVE)
            }
        }
    }
}

/** Пустое состояние списка чатов. */
@Composable
fun EmptyChatsPlaceholder(
    modifier: Modifier = Modifier,
    title: String = "Здесь пока пусто",
    subtitle: String = "Начните переписку, создайте группу или подпишитесь на канал — всё появится в этом списке.",
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(ScSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = ScTheme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(ScSpacing.sm))
        Text(
            text = subtitle,
            color = ScTheme.textTertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(ScSpacing.lg))
            Box(
                Modifier
                    .clip(ScShapes.chip)
                    .background(ScTheme.accent)
                    .combinedClickable(onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(actionLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

/** Заголовок раздела списка («Закреплённые», «Каналы», «Группы»). */
@Composable
fun ChatSectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ScTheme.surfaceGlass)
            .padding(horizontal = ScSpacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = ScTheme.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), color = ScTheme.textTertiary, fontSize = 12.sp)
    }
}

/**
 * Вспомогательная модель для аватара чата.
 *
 * У канала/группы нет [User], но аватар есть — поэтому [UserAvatar]
 * принимает [avatarOverride] и [fallbackId] (для детерминированного градиента).
 */
internal fun Chat.avatarForList(): AvatarMedia? = avatar ?: peer?.avatar
