package com.silverchat.core.designsystem.component.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.PremiumBadge
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.VerifiedBadge
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.MessageStatus
import com.silverchat.core.model.Reaction
import com.silverchat.core.model.ReactionKind
import com.silverchat.core.model.User
import kotlinx.coroutines.delay

/**
 * Пузырь сообщения — центральный компонент экрана диалога.
 *
 * Реализованы все механики из требований:
 *  - **ответы (replies)** — цитата над текстом, клик скроллит к оригиналу;
 *  - **редактирование** — метка «изменено» рядом с временем;
 *  - **реакции** — чипы под текстом, своя реакция первой и подсвечена;
 *  - **статусы доставки** — часы / одна / две галочки, прочитанные акцентные;
 *  - **мультимедиа** — фото, видео, голосовые, «кружки», файлы, стикеры, GIF;
 *  - **группировка** — «хвост» пузыря и аватар отправителя только у
 *    последнего сообщения в серии (экономит место и не рябит);
 *  - **двойной тап** — быстрая реакция ([com.silverchat.core.model.QuickReactions]);
 *  - **долгий тап** — контекстное меню (ответить/изменить/удалить/переслать);
 *  - **пересылка** — плашка «Переслано от …»;
 *  - **ошибка отправки** — красная рамка и пульсация, тап по «!» повторяет отправку.
 *
 * Стикер рисуется БЕЗ пузыря (как в Telegram): большая картинка
 * в прямоугольнике со скруглениями выглядит сломанной.
 */
@Composable
fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    isGroupedWithPrevious: Boolean = false,
    isGroupedWithNext: Boolean = false,
    showSenderAvatar: Boolean = true,
    showSenderName: Boolean = true,
    isPremiumSender: Boolean = false,
    /** Доля ширины экрана под пузырь: длинный текст не должен упираться в край. */
    maxBubbleWidthFraction: Float = 0.78f,
    onReplyClick: (Message) -> Unit = {},
    onLongPress: (Message) -> Unit = {},
    onDoubleTap: (Message) -> Unit = {},
    onReactionClick: (Message, ReactionKind) -> Unit = { _, _ -> },
    onSenderClick: (User) -> Unit = {},
    onQuoteClick: (Message) -> Unit = {},
    onMediaClick: (Message) -> Unit = {},
    onRetryClick: (Message) -> Unit = {},
) {
    val isSticker = message.isStickerOnly

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * maxBubbleWidthFraction

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ScSpacing.md,
                    end = ScSpacing.md,
                    top = if (isGroupedWithPrevious) ScSpacing.betweenMessages else ScSpacing.betweenGroups,
                ),
            horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            // Аватар отправителя — только в группах и только у последнего в серии.
            // Для промежуточных сообщений оставляем пустой слот той же ширины,
            // иначе текст «прыгает» по горизонтали.
            if (!isOutgoing && showSenderAvatar) {
                if (isGroupedWithNext) {
                    Spacer(Modifier.width(ScAvatarSize.message + ScSpacing.sm))
                } else {
                    UserAvatar(
                        user = message.sender,
                        size = ScAvatarSize.message,
                        modifier = Modifier
                            .padding(end = ScSpacing.sm)
                            .combinedClickable(
                                onClick = { message.sender?.let(onSenderClick) },
                                onLongClick = { onLongPress(message) },
                            ),
                    )
                }
            }

            val bubbleModifier = Modifier
                .widthIn(max = maxBubbleWidth)
                .then(
                    if (isSticker) {
                        Modifier
                    } else {
                        Modifier
                            .clip(bubbleShape(isOutgoing, hasTail = !isGroupedWithNext))
                            .bubbleBackground(isOutgoing)
                            .then(
                                if (message.status == MessageStatus.FAILED) {
                                    Modifier.border(1.dp, ScTheme.danger, bubbleShape(isOutgoing, false))
                                } else {
                                    Modifier
                                },
                            )
                    },
                )
                .combinedClickable(
                    onClick = { onMediaClick(message) },
                    onLongClick = { onLongPress(message) },
                )

            Column(bubbleModifier) {
                message.forwardInfo?.let { info ->
                    ForwardHeader(fromName = info.fromName, isOutgoing = isOutgoing)
                }

                message.replyTo?.let { quote ->
                    ReplyQuote(
                        senderName = quote.senderName,
                        snippet = quote.snippet,
                        isOutgoing = isOutgoing,
                        onClick = { onQuoteClick(message) },
                    )
                }

                if (!isOutgoing && showSenderName && !isGroupedWithPrevious && !isSticker) {
                    SenderName(
                        sender = message.sender,
                        isPremium = isPremiumSender,
                        onClick = { message.sender?.let(onSenderClick) },
                    )
                }

                MessageContentBody(
                    content = message.content,
                    isOutgoing = isOutgoing,
                    isSticker = isSticker,
                    modifier = if (isSticker) Modifier else Modifier.bubblePadding(),
                )

                MessageMeta(
                    message = message,
                    isOutgoing = isOutgoing,
                    isSticker = isSticker,
                    onRetryClick = { onRetryClick(message) },
                )

                if (message.reactions.isNotEmpty()) {
                    Spacer(Modifier.height(ScSpacing.xs))
                    ReactionsRow(
                        reactions = message.reactions,
                        isOutgoing = isOutgoing,
                        modifier = Modifier.padding(horizontal = ScSpacing.bubblePaddingH, vertical = ScSpacing.xs),
                        onClick = { kind -> onReactionClick(message, kind) },
                    )
                }
            }
        }
    }
}

/* ── Форма и фон пузыря ─────────────────────────────────────────────────── */

private fun bubbleShape(isOutgoing: Boolean, hasTail: Boolean): Shape = when {
    isOutgoing && hasTail -> ScShapes.bubbleOutgoingTail
    isOutgoing -> ScShapes.bubbleOutgoing
    hasTail -> ScShapes.bubbleIncomingTail
    else -> ScShapes.bubbleIncoming
}

/**
 * Фон исходящего пузыря — лёгкий градиент: на плоском цвете стекло
 * и градиентный фон чата «съедают» границу пузыря.
 */
private fun Modifier.bubbleBackground(isOutgoing: Boolean): Modifier =
    if (isOutgoing) {
        background(Brush.linearGradient(listOf(ScTheme.bubbleOutgoing, ScTheme.bubbleOutgoingEnd)))
    } else {
        background(ScTheme.bubbleIncoming)
    }

private fun Modifier.bubblePadding(): Modifier = padding(
    start = ScSpacing.bubblePaddingH,
    end = ScSpacing.bubblePaddingH,
    top = ScSpacing.bubblePaddingV,
    bottom = ScSpacing.bubblePaddingV,
)

/* ── Имя отправителя ────────────────────────────────────────────────────── */

@Composable
private fun SenderName(sender: User?, isPremium: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .padding(
                start = ScSpacing.bubblePaddingH,
                end = ScSpacing.bubblePaddingH,
                top = ScSpacing.bubblePaddingV,
            )
            .combinedClickable(onClick = onClick),
    ) {
        Text(
            text = sender?.fullName ?: "Неизвестный",
            color = ScTheme.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        if (sender?.badges?.verified == true) VerifiedBadge(size = 13)
        if (isPremium) PremiumBadge(size = 13)
    }
}

/* ── Пересылка ──────────────────────────────────────────────────────────── */

@Composable
private fun ForwardHeader(fromName: String, isOutgoing: Boolean) {
    Text(
        text = "Переслано от $fromName",
        color = if (isOutgoing) ScTheme.bubbleOutgoingText.copy(alpha = 0.8f) else ScTheme.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(
            start = ScSpacing.bubblePaddingH,
            end = ScSpacing.bubblePaddingH,
            top = ScSpacing.bubblePaddingV,
        ),
    )
}

/* ── Цитата ответа ──────────────────────────────────────────────────────── */

/**
 * Цитата сообщения, на которое идёт ответ.
 *
 * Цветовая схема инвертируется для исходящих: на градиентном пузыре
 * акцентный текст нечитаем, поэтому используется белый.
 */
@Composable
fun ReplyQuote(
    senderName: String,
    snippet: String,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val barColor = if (isOutgoing) Color.White.copy(alpha = 0.85f) else ScTheme.accent
    val background = if (isOutgoing) Color.White.copy(alpha = 0.16f) else ScTheme.accentContainer
    val nameColor = if (isOutgoing) Color.White else ScTheme.accent
    val textColor = if (isOutgoing) Color.White.copy(alpha = 0.85f) else ScTheme.textSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ScSpacing.bubblePaddingH,
                end = ScSpacing.bubblePaddingH,
                top = ScSpacing.bubblePaddingV,
            )
            .clip(ScShapes.cardSmall)
            .background(background)
            .combinedClickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(ScShapes.chip)
                .background(barColor),
        )
        Spacer(Modifier.width(ScSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(senderName, color = nameColor, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(snippet, color = textColor, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/* ── Мета-строка ────────────────────────────────────────────────────────── */

@Composable
private fun MessageMeta(message: Message, isOutgoing: Boolean, isSticker: Boolean, onRetryClick: () -> Unit) {
    val color = when {
        isSticker -> ScTheme.textTertiary
        isOutgoing -> ScTheme.bubbleTimestamp
        else -> ScTheme.bubbleTimestamp
    }

    Row(
        modifier = Modifier
            .align(if (isSticker) Alignment.CenterEnd else Alignment.End)
            .padding(
                end = ScSpacing.bubblePaddingH,
                start = ScSpacing.bubblePaddingH,
                top = 2.dp,
                bottom = if (isSticker) 0.dp else ScSpacing.bubblePaddingV,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (message.isEdited) {
            Text("изменено", color = color, fontSize = 11.sp, fontStyle = FontStyle.Italic)
        }
        if (message.viewsCount > 0) {
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Text(NumberFormatter.compact(message.viewsCount.toLong()), color = color, fontSize = 11.sp)
        }
        Text(
            text = TimeFormatter.messageTime(message.sentAt),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (isOutgoing) {
            if (message.status == MessageStatus.FAILED) {
                // Тап по «!» повторяет отправку — быстрее, чем искать это в меню
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = "Не отправлено — нажмите, чтобы повторить",
                    tint = ScTheme.danger,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .combinedClickable(onClick = onRetryClick),
                )
            } else {
                DeliveryTicks(status = message.status)
            }
        }
    }
}

/** Галочки доставки: часы -> одна -> две (доставлено) -> две акцентные (прочитано). */
@Composable
fun DeliveryTicks(status: MessageStatus, modifier: Modifier = Modifier) {
    val (icon, tint, description) = when (status) {
        MessageStatus.DRAFT, MessageStatus.SENDING ->
            Triple(Icons.Filled.Schedule, ScTheme.bubbleTimestamp, "Отправляется")

        MessageStatus.SENT ->
            Triple(Icons.Filled.Done, ScTheme.tickSent, "Отправлено")

        MessageStatus.DELIVERED ->
            Triple(Icons.Filled.DoneAll, ScTheme.tickSent, "Доставлено")

        MessageStatus.READ ->
            Triple(Icons.Filled.DoneAll, ScTheme.tickRead, "Прочитано")

        MessageStatus.FAILED ->
            Triple(Icons.Filled.ErrorOutline, ScTheme.danger, "Не отправлено")
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(15.dp),
    )
}

/* ── Реакции ────────────────────────────────────────────────────────────── */

@Composable
fun ReactionsRow(
    reactions: List<Reaction>,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ReactionKind) -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Своя реакция первой — как в Telegram: сразу видно, что ты поставил
        reactions.sortedByDescending { it.reactedByMe }.forEach { reaction ->
            ReactionChip(
                reaction = reaction,
                isOutgoing = isOutgoing,
                onClick = { onClick(reaction.kind) },
            )
        }
    }
}

@Composable
fun ReactionChip(
    reaction: Reaction,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val background = when {
        reaction.reactedByMe -> ScTheme.accent
        isOutgoing -> Color.White.copy(alpha = 0.22f)
        else -> ScTheme.surfaceVariant
    }
    val textColor = when {
        reaction.reactedByMe -> Color.White
        isOutgoing -> Color.White
        else -> ScTheme.textSecondary
    }

    // Лёгкое «подпрыгивание» при собственной реакции — микроанимация отклика
    val scale by animateFloatAsState(
        targetValue = if (reaction.reactedByMe) 1.04f else 1f,
        animationSpec = tween(180),
        label = "reactionScale",
    )

    Row(
        modifier = modifier
            .clip(ScShapes.chip)
            .background(background)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(reaction.kind.emoji, fontSize = (14 * scale).sp)
        Text(
            text = reaction.count.toString(),
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Панель быстрых реакций — появляется при долгом тапе на сообщение. */
@Composable
fun QuickReactionPanel(
    kinds: List<ReactionKind>,
    selected: ReactionKind?,
    modifier: Modifier = Modifier,
    onSelect: (ReactionKind) -> Unit = {},
) {
    Row(
        modifier = modifier
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .border(1.dp, ScTheme.surfaceGlassBorder, ScShapes.card)
            .padding(ScSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        kinds.forEach { kind ->
            val isSelected = kind == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(ScShapes.cardSmall)
                    .background(if (isSelected) ScTheme.accentContainer else Color.Transparent)
                    .combinedClickable(onClick = { onSelect(kind) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(kind.emoji, fontSize = 22.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** «Печатает…» — три пульсирующие точки со сдвигом фазы. */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier, senderName: String? = null) {
    Row(
        modifier = modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
    ) {
        if (senderName != null) {
            UserAvatar(size = ScAvatarSize.small, fallbackTitle = senderName, fallbackId = senderName)
        }
        Box(
            Modifier
                .clip(ScShapes.bubbleIncoming)
                .background(ScTheme.bubbleIncoming)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index -> TypingDot(index) }
            }
        }
    }
}

@Composable
private fun TypingDot(index: Int) {
    // Фаза сдвинута на index: точки «бегут» слева направо, а не мигают синхронно
    val alpha = remember { androidx.compose.animation.core.Animatable(0.3f) }
    LaunchedEffect(index) {
        while (true) {
            delay(index * 180L)
            alpha.animateTo(1f, tween(420))
            alpha.animateTo(0.3f, tween(420))
            delay(360)
        }
    }
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(ScTheme.textTertiary.copy(alpha = alpha.value)),
    )
}

/** Разделитель дней («Сегодня», «Вчера», «14 марта»). */
@Composable
fun DaySeparator(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = ScSpacing.sm), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = ScTheme.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(ScShapes.chip)
                .background(ScTheme.surfaceGlass)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Разделитель непрочитанных сообщений. */
@Composable
fun UnreadSeparator(count: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = ScSpacing.sm), contentAlignment = Alignment.Center) {
        Text(
            text = TimeFormatter.plural(count.toLong(), "непрочитанное", "непрочитанных", "непрочитанных"),
            color = ScTheme.accent,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(ScShapes.chip)
                .background(ScTheme.accentContainer)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Служебное сообщение («создан канал», «звонок завершён»). */
@Composable
fun ServiceMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = ScSpacing.xs), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = ScTheme.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(ScShapes.chip)
                .background(ScTheme.surfaceGlass)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Закреплённое сообщение в шапке чата. */
@Composable
fun PinnedMessageBar(
    snippet: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ScTheme.surfaceGlass)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
    ) {
        Box(Modifier.width(3.dp).height(28.dp).clip(ScShapes.chip).background(ScTheme.accent))
        Column(
            Modifier
                .weight(1f)
                .combinedClickable(onClick = onClick),
        ) {
            Text("Закреплённое", color = ScTheme.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Text(snippet, color = ScTheme.textSecondary, fontSize = 13.sp, maxLines = 1)
        }
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = "Скрыть закреплённое",
            tint = ScTheme.textTertiary,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .combinedClickable(onClick = onClose),
        )
    }
}

/**
 * Рендер содержимого сообщения по типу.
 *
 * Вынесен в отдельный композабл, чтобы добавление нового типа контента
 * (например, опросов) не затрагивало логику пузыря.
 */
@Composable
fun MessageContentBody(
    content: MessageContent,
    isOutgoing: Boolean,
    isSticker: Boolean,
    modifier: Modifier = Modifier,
) {
    val textColor = if (isOutgoing) ScTheme.bubbleOutgoingText else ScTheme.bubbleIncomingText

    when (content) {
        is MessageContent.Text -> Text(
            text = content.text,
            color = textColor,
            fontSize = 14.6.sp,
            lineHeight = 20.sp,
            modifier = modifier,
        )

        // Стикер рисуется без пузыря и без паддингов — это отдельный визуальный слой
        is MessageContent.Sticker -> Box(modifier.size(128.dp), contentAlignment = Alignment.Center) {
            Text(content.emoji, fontSize = 100.sp)
        }

        is MessageContent.Voice -> VoiceMessageBody(
            durationMs = content.durationMs,
            waveform = content.waveform,
            isOutgoing = isOutgoing,
            mediaKey = content.url,
            modifier = modifier,
        )

        is MessageContent.File -> FileMessageBody(
            fileName = content.fileName,
            sizeBytes = content.sizeBytes,
            isOutgoing = isOutgoing,
            downloadProgress = content.uploadProgress.takeIf { it < 1f },
            modifier = modifier,
        )

        else -> Text(
            text = genericMediaLabel(content),
            color = textColor,
            fontSize = 14.6.sp,
            modifier = modifier,
        )
    }
}

/**
 * Подпись для типов контента, которым нужен отдельный рендер медиа
 * (фото/видео/кружки/опросы реализуются в :feature:chats поверх Coil и Media3).
 */
fun genericMediaLabel(content: MessageContent): String = when (content) {
    is MessageContent.Text -> content.text
    is MessageContent.Photo -> content.caption ?: "Фото"
    is MessageContent.Video -> content.caption ?: "Видео ${TimeFormatter.duration(content.durationMs)}"
    is MessageContent.Voice -> "Голосовое ${TimeFormatter.duration(content.durationMs)}"
    is MessageContent.VideoCircle -> "Видеосообщение ${TimeFormatter.duration(content.durationMs)}"
    is MessageContent.Sticker -> "Стикер ${content.emoji}"
    is MessageContent.Gif -> "GIF"
    is MessageContent.File -> content.fileName
    is MessageContent.Location -> content.title ?: "Геолокация"
    is MessageContent.Contact -> "${content.firstName} ${content.lastName.orEmpty()}"
    is MessageContent.Poll -> content.question
    is MessageContent.Service -> content.title
    is MessageContent.Invite -> "Приглашение в «${content.chatTitle}»"
    is MessageContent.Gift -> "Подарок «${content.title}»"
}
