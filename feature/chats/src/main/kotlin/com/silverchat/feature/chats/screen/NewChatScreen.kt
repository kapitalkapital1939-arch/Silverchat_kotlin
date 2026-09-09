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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.designsystem.component.SearchField
import com.silverchat.core.designsystem.component.SilverTextField
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.navigation.ChatsNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.User
import com.silverchat.feature.chats.NewChatViewModel
import com.silverchat.feature.chats.navigation.NewChatMode

/**
 * Создание группы/канала ИЛИ выбор получателей для пересылки.
 *
 * Один композабл на два сценария — осознанное решение: обе задачи состоят
 * из «поиска контактов -> мультивыбор -> подтверждение», различается только
 * последний шаг. Дублировать список контактов в двух экранах — значит
 * дважды поддерживать одинаковую логику поиска и выделения.
 *
 * Лимит выбора ([MAX_SELECTION]) проверяется здесь, а не только на сервере:
 * мгновенная обратная связь лучше, чем ошибка после отправки.
 */
@Composable
fun NewChatScreen(
    isChannel: Boolean,
    navigator: ChatsNavigator,
    modifier: Modifier = Modifier,
    mode: NewChatMode = NewChatMode.CREATE,
    onPickAvatar: () -> Unit = {},
    viewModel: NewChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().background(ScTheme.background)) {
        SilverTopBar(
            title = when {
                mode == NewChatMode.FORWARD_PICKER -> "Переслать"
                isChannel -> "Новый канал"
                else -> "Новая группа"
            },
            subtitle = if (state.selected.isNotEmpty()) {
                "Выбрано: ${state.selected.size}"
            } else {
                null
            },
            onBack = navigator::back,
            actions = {
                if (state.selected.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ScTheme.accent)
                            .clickable { viewModel.confirm(navigator) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Готово", tint = Color.White)
                    }
                }
            },
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = ScSpacing.xxl),
        ) {
            // ── Форма создания (не нужна при пересылке) ──────────────────
            if (mode == NewChatMode.CREATE) {
                item(key = "form", contentType = "form") {
                    Column(Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
                        ) {
                            Box(
                                Modifier
                                    .size(ScAvatarSize.chatHeader)
                                    .clip(CircleShape)
                                    .background(ScTheme.surfaceGlass)
                                    .clickable { onPickAvatar() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = "Выбрать аватар",
                                    tint = ScTheme.textTertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (isChannel) "Аватар канала" else "Аватар группы",
                                    color = ScTheme.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Можно добавить позже",
                                    color = ScTheme.textTertiary,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(ScSpacing.md))
                        SilverTextField(
                            value = state.title,
                            onValueChange = viewModel::onTitleChanged,
                            label = if (isChannel) "Название канала" else "Название группы",
                            placeholder = "Например: Дизайн-команда",
                            maxLength = MAX_TITLE_LENGTH,
                        )
                        Spacer(Modifier.height(ScSpacing.sm))
                        if (isChannel) {
                            SilverTextField(
                                value = state.about,
                                onValueChange = viewModel::onAboutChanged,
                                label = "Описание",
                                placeholder = "О чём этот канал",
                                maxLength = MAX_ABOUT_LENGTH,
                                singleLine = false,
                            )
                        } else {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(ScShapes.card)
                                    .background(ScTheme.surfaceElevated)
                                    .clickable { viewModel.togglePrivate() }
                                    .padding(ScSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (state.isPrivate) "Приватная группа" else "Публичная группа",
                                        color = ScTheme.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = if (state.isPrivate) {
                                            "Доступ только по пригласительной ссылке"
                                        } else {
                                            "Находится через поиск по названию"
                                        },
                                        color = ScTheme.textTertiary,
                                        fontSize = 11.5.sp,
                                    )
                                }
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (state.isPrivate) ScTheme.accent else ScTheme.surface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.isPrivate) {
                                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Поиск контактов ──────────────────────────────────────────
            item(key = "search", contentType = "search") {
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChanged,
                    placeholder = if (mode == NewChatMode.FORWARD_PICKER) {
                        "Кому переслать"
                    } else {
                        "Добавить участников"
                    },
                    onClear = { viewModel.onQueryChanged("") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                )
            }

            // ── Уже выбранные (горизонтальная лента, как в Telegram) ─────
            if (state.selected.isNotEmpty()) {
                item(key = "selected_header", contentType = "header") {
                    Text(
                        text = "Выбранные · ${state.selected.size}",
                        color = ScTheme.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.xs),
                    )
                }
                item(key = "selected_row", contentType = "selected") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
                    ) {
                        state.selected.take(MAX_VISIBLE_SELECTED).forEach { user ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(ScShapes.cardSmall)
                                    .clickable { viewModel.toggleUser(user) }
                                    .padding(4.dp),
                            ) {
                                Box {
                                    UserAvatar(user = user, size = ScAvatarSize.tray)
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(ScTheme.accent),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                                Text(
                                    text = user.firstName,
                                    color = ScTheme.textSecondary,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (state.selected.size > MAX_VISIBLE_SELECTED) {
                            Box(
                                Modifier.size(ScAvatarSize.storyTray).clip(CircleShape).background(ScTheme.surfaceGlass),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+${state.selected.size - MAX_VISIBLE_SELECTED}",
                                    color = ScTheme.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // ── Результаты поиска ────────────────────────────────────────
            item(key = "contacts_header", contentType = "header") {
                Text(
                    text = if (state.query.isBlank()) "Контакты" else "Результаты · ${state.results.size}",
                    color = ScTheme.textTertiary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                )
            }

            if (state.results.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Text(
                        text = if (state.query.isBlank()) {
                            "Контактов пока нет.\nПригласите друзей по @username — они появятся здесь."
                        } else {
                            "Никого не нашли по запросу «${state.query}»"
                        },
                        color = ScTheme.textTertiary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(ScSpacing.xl),
                    )
                }
            }

            items(state.results, key = { it.id.raw }, contentType = { "contact" }) { user ->
                ContactRow(
                    user = user,
                    selected = user.id in state.selectedIds,
                    enabled = state.selected.size < MAX_SELECTION || user.id in state.selectedIds,
                    onClick = { viewModel.toggleUser(user) },
                )
            }
        }
    }
}

@Composable
private fun ContactRow(
    user: User,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Чекбокс до аватара: зона тапа крупнее и выбор очевиден
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when {
                        selected -> ScTheme.accent
                        enabled -> ScTheme.surface
                        else -> ScTheme.surface.copy(alpha = 0.5f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(ScSpacing.md))
        UserAvatar(user = user, size = ScAvatarSize.member, showOnline = true)
        Spacer(Modifier.width(ScSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = user.fullName,
                color = if (enabled) ScTheme.textPrimary else ScTheme.textTertiary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = user.handle ?: "без юзернейма",
                color = ScTheme.textTertiary,
                fontSize = 12.5.sp,
                maxLines = 1,
            )
        }
        if (user.badges.verified) {
            com.silverchat.core.designsystem.component.VerifiedBadge(size = 14)
        }
    }
}

/** Порог выбора и прочие ограничения экрана. */
private const val MAX_SELECTION = 200
private const val MAX_VISIBLE_SELECTED = 8
private const val MAX_TITLE_LENGTH = 64
private const val MAX_ABOUT_LENGTH = 255
