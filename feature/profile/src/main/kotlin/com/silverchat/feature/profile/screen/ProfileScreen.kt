package com.silverchat.feature.profile.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.LoadingOverlay
import com.silverchat.core.designsystem.component.ProfileBanner
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.UserBadgesRow
import com.silverchat.core.designsystem.navigation.ProfileNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.domain.repository.SharedMediaKind
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.UserPresence
import com.silverchat.feature.profile.ProfileEvent
import com.silverchat.feature.profile.ProfileUiState
import com.silverchat.feature.profile.ProfileViewModel
import com.silverchat.feature.profile.titleRu

/**
 * Карточка пользователя.
 *
 * Один экран для своего и чужого профиля: состав блоков одинаков
 * (баннер, аватар, имя, бейджи, bio, местоположение, часы работы, общие медиа),
 * различается только набор действий. Флаг [ProfileUiState.isMe] приходит
 * из ViewModel, а не вычисляется сравнением ID на экране — иначе при
 * рассинхроне кэша профиль мог бы показать чужие кнопки редактирования.
 *
 * Баннер и аватар накладываются друг на друга: это узнаваемый паттерн
 * Telegram, и он экономит вертикальное место на маленьких экранах.
 */
@Composable
fun ProfileScreen(
    navigator: ProfileNavigator,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(events) {
        if (events != null) viewModel.consumeEvent()
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        if (state.isLoading) {
            LoadingOverlay(message = "Загружаем профиль")
            return
        }

        val user = state.user
        if (user == null) {
            EmptyState(
                icon = Icons.Filled.Flag,
                title = "Профиль недоступен",
                subtitle = "Пользователь удалён, заблокирован\nили скрыл свою страницу.",
                actionLabel = "Назад",
                onAction = navigator::back,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }

        Column(Modifier.fillMaxSize()) {
            SilverTopBar(
                title = if (state.isMe) "Мой профиль" else "Профиль",
                onBack = navigator::back,
                actions = {
                    if (state.isMe) {
                        SilverIconButton(
                            icon = Icons.Filled.Edit,
                            contentDescription = "Редактировать профиль",
                            onClick = navigator::openEditProfile,
                            tint = ScTheme.accent,
                        )
                    } else {
                        SilverIconButton(
                            icon = Icons.Filled.Block,
                            contentDescription = "Заблокировать пользователя",
                            onClick = viewModel::blockUser,
                            tint = ScTheme.danger,
                        )
                        SilverIconButton(
                            icon = Icons.Filled.Flag,
                            contentDescription = "Пожаловаться",
                            onClick = { /* диалог жалобы открывает слой :app */ },
                            tint = ScTheme.textPrimary,
                        )
                    }
                },
            )

            LazyColumn(Modifier.fillMaxSize()) {
                // ── Баннер и аватар ──────────────────────────────────────
                item(key = "hero", contentType = "hero") {
                    HeroBlock(state = state, navigator = navigator)
                }

                // ── Имя, бейджи, присутствие ─────────────────────────────
                item(key = "identity", contentType = "identity") {
                    IdentityBlock(state = state)
                }

                // ── Действия ─────────────────────────────────────────────
                item(key = "actions", contentType = "actions") {
                    ActionsBlock(state = state, navigator = navigator)
                }

                // ── Описание ─────────────────────────────────────────────
                state.bio?.let { bio ->
                    item(key = "bio", contentType = "bio") {
                        InfoBlock(icon = Icons.Filled.Chat, title = "О себе", content = bio)
                    }
                }

                // ── Юзернейм и телефон ───────────────────────────────────
                user.handle?.let { handle ->
                    item(key = "handle", contentType = "handle") {
                        InfoBlock(icon = Icons.Filled.Star, title = "Юзернейм", content = handle)
                    }
                }
                // Номер показывается только на собственном профиле: чужой
                // сервер не отдаёт, если видимость ограничена
                if (state.isMe) {
                    user.phone?.let { phone ->
                        item(key = "phone", contentType = "phone") {
                            InfoBlock(icon = Icons.Filled.Call, title = "Телефон", content = phone)
                        }
                    }
                }

                // ── Местоположение ───────────────────────────────────────
                user.location?.takeIf { it.visible }?.let { location ->
                    item(key = "location", contentType = "location") {
                        InfoBlock(
                            icon = Icons.Filled.LocationOn,
                            title = location.title ?: "Местоположение",
                            content = location.address ?: formatCoordinates(
                                location.latitude,
                                location.longitude,
                            ),
                            onClick = if (state.isMe) navigator::openLocationEditor else null,
                        )
                    }
                }

                // ── Часы работы ──────────────────────────────────────────
                user.workingHours?.takeIf { it.visible }?.let { hours ->
                    item(key = "hours", contentType = "hours") {
                        InfoBlock(
                            icon = Icons.Filled.Schedule,
                            title = "Часы работы",
                            content = if (hours.alwaysOpen) {
                                "Круглосуточно"
                            } else {
                                formatScheduleSummary(hours.schedule.size, hours.timeZone)
                            },
                            onClick = if (state.isMe) navigator::openWorkingHoursEditor else null,
                        )
                    }
                }

                // ── Общие медиа ──────────────────────────────────────────
                if (!state.isMe && state.sharedChatId != null) {
                    item(key = "media_tabs", contentType = "tabs") {
                        MediaTabs(
                            selected = state.mediaTab,
                            onSelect = viewModel::selectMediaTab,
                        )
                    }
                    item(key = "media_grid", contentType = "grid") {
                        SharedMediaGrid(messages = state.sharedMedia)
                    }
                }

                // ── Архив сторис (только свой профиль) ───────────────────
                if (state.isMe) {
                    item(key = "stories", contentType = "stories") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = navigator::openStoriesArchive)
                                .padding(ScSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = ScTheme.storyRingUnseen,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(ScSpacing.md))
                            Text(
                                text = "Архив сторис",
                                color = ScTheme.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        events?.let { event ->
            val message = when (event) {
                ProfileEvent.AvatarUpdated -> "Аватар обновлён"
                ProfileEvent.BannerUpdated -> "Баннер обновлён"
                ProfileEvent.Blocked -> "Пользователь заблокирован"
                ProfileEvent.Unblocked -> "Пользователь разблокирован"
                ProfileEvent.Reported -> "Жалоба отправлена модераторам"
                is ProfileEvent.Error -> event.message
            }
            InlineSnackbar(
                message = message,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(ScSpacing.md),
            )
        }
    }
}

/* =========================================================================
   БАННЕР И АВАТАР
   ========================================================================= */

@Composable
private fun HeroBlock(state: ProfileUiState, navigator: ProfileNavigator) {
    val user = state.user ?: return

    Box(Modifier.fillMaxWidth()) {
        ProfileBanner(
            banner = user.banner,
            allowAnimation = state.hasAnimatedBanner,
            modifier = Modifier
                .fillMaxWidth()
                .height(BANNER_HEIGHT)
                .then(
                    if (state.isMe) {
                        Modifier.clickable(onClick = navigator::openBannerEditor)
                    } else {
                        Modifier
                    },
                ),
        )

        // Аватар накладывается на нижний край баннера
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = ScSpacing.md)
                .offset(y = AVATAR_OVERLAP),
        ) {
            UserAvatar(
                user = user,
                size = ScAvatarSize.profileHero,
                showOnline = user.presence is UserPresence.Online,
                allowAnimation = state.hasAnimatedAvatar,
                borderWidth = 3.dp,
                borderColor = ScTheme.background,
                onClick = if (state.isMe) navigator::openAvatarEditor else null,
            )
            if (state.isMe) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(ScTheme.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Изменить аватар",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** Высота баннера: достаточно для изображения, но не съедает первый экран. */
private val BANNER_HEIGHT = 150.dp

/** Насколько аватар выступает за нижний край баннера. */
private val AVATAR_OVERLAP = 40.dp

/* =========================================================================
   ИДЕНТИЧНОСТЬ
   ========================================================================= */

@Composable
private fun IdentityBlock(state: ProfileUiState) {
    val user = state.user ?: return

    // Отступ сверху компенсирует выступание аватара за баннер
    Column(Modifier.fillMaxWidth().padding(start = ScSpacing.md, end = ScSpacing.md, top = AVATAR_OVERLAP + ScSpacing.sm)) {
        Text(
            text = user.fullName,
            color = ScTheme.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        UserBadgesRow(user = user)

        state.presenceText?.let { presence ->
            val online = user.presence is UserPresence.Online
            Text(
                text = presence,
                color = if (online) ScTheme.online else ScTheme.textTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (state.isPremium) {
            Spacer(Modifier.height(ScSpacing.xs))
            Row(
                Modifier
                    .clip(ScShapes.chip)
                    .background(ScTheme.premium.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = ScTheme.premium,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = "SilverChat Premium",
                    color = ScTheme.premium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/* =========================================================================
   ДЕЙСТВИЯ
   ========================================================================= */

@Composable
private fun ActionsBlock(state: ProfileUiState, navigator: ProfileNavigator) {
    Row(
        Modifier.fillMaxWidth().padding(ScSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
    ) {
        if (state.isMe) {
            SilverButton(
                text = "Изменить профиль",
                onClick = navigator::openEditProfile,
                variant = SilverButtonVariant.SECONDARY,
                leadingIcon = Icons.Filled.Edit,
                modifier = Modifier.weight(1f),
            )
            SilverButton(
                text = "Premium",
                onClick = navigator::openPremium,
                leadingIcon = Icons.Filled.Star,
                modifier = Modifier.weight(1f),
            )
        } else {
            SilverButton(
                text = "Написать",
                onClick = { state.sharedChatId?.let(navigator::openChat) },
                enabled = state.canMessage,
                leadingIcon = Icons.Filled.Chat,
                modifier = Modifier.weight(1f),
            )
            SilverIconButton(
                icon = Icons.Filled.Call,
                contentDescription = "Голосовой звонок",
                onClick = { state.sharedChatId?.let { navigator.startCall(it, video = false) } },
                tint = ScTheme.accent,
            )
            SilverIconButton(
                icon = Icons.Filled.Videocam,
                contentDescription = "Видеозвонок",
                onClick = { state.sharedChatId?.let { navigator.startCall(it, video = true) } },
                tint = ScTheme.accent,
            )
        }
    }
}

/* =========================================================================
   ИНФОРМАЦИОННЫЙ БЛОК
   ========================================================================= */

@Composable
private fun InfoBlock(
    icon: ImageVector,
    title: String,
    content: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ScTheme.textTertiary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(ScSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, color = ScTheme.textTertiary, fontSize = 11.5.sp)
            Text(
                text = content,
                color = ScTheme.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

/* =========================================================================
   ОБЩИЕ МЕДИА
   ========================================================================= */

@Composable
private fun MediaTabs(selected: SharedMediaKind, onSelect: (SharedMediaKind) -> Unit) {
    LazyRow(
        contentPadding = PaddingValuesHorizontal(ScSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
    ) {
        items(SHARED_MEDIA_TABS, key = { it.name }) { kind ->
            val isSelected = kind == selected
            Text(
                text = kind.titleRu,
                color = if (isSelected) Color.White else ScTheme.textSecondary,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(ScShapes.chip)
                    .background(if (isSelected) ScTheme.accent else ScTheme.surfaceGlass)
                    .clickable { onSelect(kind) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            )
        }
    }
}

/** Вкладки общих медиа: показываем не все виды, а только содержательные. */
private val SHARED_MEDIA_TABS = listOf(
    SharedMediaKind.PHOTO,
    SharedMediaKind.VIDEO,
    SharedMediaKind.VOICE,
    SharedMediaKind.FILE,
    SharedMediaKind.LINK,
)

/**
 * Горизонтальный отступ для `LazyRow.contentPadding`.
 *
 * Вынесен в функцию, чтобы не импортировать `PaddingValues` ради одного вызова.
 */
private fun PaddingValuesHorizontal(value: androidx.compose.ui.unit.Dp) =
    androidx.compose.foundation.layout.PaddingValues(horizontal = value)

/**
 * Сетка общих медиа.
 *
 * Рендерится как горизонтальный ряд превью, а не как полноценная сетка:
 * вложенная вертикальная сетка внутри `LazyColumn` требует фиксированной
 * высоты и ломает прокрутку. Полный просмотр открывается тапом по элементу.
 */
@Composable
private fun SharedMediaGrid(messages: List<Message>) {
    if (messages.isEmpty()) {
        Text(
            text = "Общих медиа пока нет",
            color = ScTheme.textTertiary,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(ScSpacing.lg),
        )
        return
    }

    LazyRow(
        contentPadding = PaddingValuesHorizontal(ScSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
    ) {
        // Показываем не более 12 превью: длинный ряд всё равно прокручивается,
        // а полный список доступен в экране медиа чата
        items(messages.take(MAX_MEDIA_PREVIEWS), key = { it.id.raw }) { message ->
            MediaPreview(message = message)
        }
    }
}

private const val MAX_MEDIA_PREVIEWS = 12

@Composable
private fun MediaPreview(message: Message) {
    Box(
        Modifier
            .size(88.dp)
            .clip(ScShapes.cardSmall)
            .background(ScTheme.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message.content.previewLabel(),
            color = ScTheme.textTertiary,
            fontSize = 20.sp,
        )
        Text(
            text = TimeFormatter.daySeparator(message.sentAt),
            color = ScTheme.textTertiary,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(3.dp),
        )
    }
}

/**
 * Короткая метка содержимого для превью без загрузки медиа.
 *
 * Превью общих медиа — навигационный элемент, а не просмотр: грузить
 * 12 изображений ради сетки 88dp неоправданно. Полноценный просмотр
 * открывается в экране медиа чата.
 */
private fun MessageContent.previewLabel(): String = when (this) {
    is MessageContent.Text -> text.take(2).uppercase().ifBlank { "··" }
    is MessageContent.Photo -> "🖼"
    is MessageContent.Video -> "🎬"
    is MessageContent.Voice -> "🎙"
    is MessageContent.VideoCircle -> "⭕"
    is MessageContent.File -> "📄"
    is MessageContent.Sticker -> "🎭"
    is MessageContent.Gif -> "🎞"
    is MessageContent.Location -> "📍"
    is MessageContent.Contact -> "👤"
    is MessageContent.Poll -> "📊"
    /* U+FE0F обязателен: без вариационного селектора ℹ рисуется текстовым
       глифом и выпадает из набора эмодзи выше по стилю. */
    is MessageContent.Service -> "ℹ️"
    is MessageContent.Invite -> "🔗"
    is MessageContent.Gift -> "🎁"
}

/* =========================================================================
   ФОРМАТИРОВАНИЕ
   ========================================================================= */

/** Координаты с точностью до 4 знаков — это примерно 11 метров. */
private fun formatCoordinates(latitude: Double, longitude: Double): String =
    "%.4f, %.4f".format(latitude, longitude)

/** Краткая сводка расписания: число дней и часовой пояс. */
private fun formatScheduleSummary(days: Int, timeZone: String): String =
    if (days == 0) {
        "Расписание не заполнено ($timeZone)"
    } else {
        TimeFormatter.plural(days.toLong(), "день", "дня", "дней") + " в неделю ($timeZone)"
    }
