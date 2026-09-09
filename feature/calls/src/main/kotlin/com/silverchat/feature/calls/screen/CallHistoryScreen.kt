package com.silverchat.feature.calls.screen

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.navigation.CallsNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.CallHistoryEntry
import com.silverchat.feature.calls.CallDayGroup
import com.silverchat.feature.calls.CallHistoryViewModel
import com.silverchat.feature.calls.isVideo
import com.silverchat.feature.calls.typeRu

/**
 * История звонков.
 *
 * Пропущенные подсвечены красным и вынесены в отдельный фильтр: это единственная
 * категория, требующая действия пользователя (перезвонить). Остальные звонки —
 * справочная информация.
 *
 * Список сгруппирован по дням с «липкими» заголовками через обычные `item`:
 * `stickyHeader` в Compose требует `ExperimentalFoundationApi`, а история
 * звонков короткая — выигрыш от липкости не оправдывает экспериментальный API.
 */
@Composable
fun CallHistoryScreen(
    navigator: CallsNavigator,
    modifier: Modifier = Modifier,
    viewModel: CallHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().background(ScTheme.background)) {
        SilverTopBar(
            title = "Звонки",
            subtitle = when {
                state.totalMissed > 0 -> "Пропущенных: ${state.totalMissed}"
                state.totalCount > 0 -> "Всего звонков: ${state.totalCount}"
                else -> null
            },
            onBack = navigator::back,
        )

        // Фильтр «только пропущенные»
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
        ) {
            FilterChip(
                label = "Все",
                selected = !state.missedOnly,
                onClick = { if (state.missedOnly) viewModel.toggleMissedOnly() },
            )
            FilterChip(
                label = "Пропущенные" + if (state.totalMissed > 0) " · ${state.totalMissed}" else "",
                selected = state.missedOnly,
                onClick = { if (!state.missedOnly) viewModel.toggleMissedOnly() },
                accent = state.totalMissed > 0,
            )
        }

        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.CallEnd,
                title = if (state.missedOnly) "Пропущенных звонков нет" else "История пуста",
                subtitle = if (state.missedOnly) {
                    "Все входящие звонки были приняты."
                } else {
                    "Здесь появятся ваши голосовые и видеозвонки.\nПозвонить можно из шапки любого чата."
                },
                modifier = Modifier.fillMaxSize(),
            )
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = ScSpacing.xxl),
        ) {
            state.groups.forEach { group ->
                item(key = "day_${group.day}", contentType = "day_header") {
                    DayHeader(group)
                }
                group.entries.forEach { entry ->
                    item(key = "call_${entry.callId.raw}", contentType = "call_row") {
                        CallHistoryRow(
                            entry = entry,
                            onOpenProfile = { navigator.openProfile(entry.peer.id.raw) },
                            onRecall = { navigator.openChat(entry.chatId.raw) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    Text(
        text = label,
        color = when {
            selected -> Color.White
            accent -> ScTheme.danger
            else -> ScTheme.textSecondary
        },
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(ScShapes.chip)
            .background(
                when {
                    selected && accent -> ScTheme.danger
                    selected -> ScTheme.accent
                    else -> ScTheme.surfaceGlass
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun DayHeader(group: CallDayGroup) {
    Text(
        text = TimeFormatter.daySeparator(group.firstStartedAt),
        color = ScTheme.accent,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
    )
}

@Composable
private fun CallHistoryRow(
    entry: CallHistoryEntry,
    onOpenProfile: () -> Unit,
    onRecall: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onRecall)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(user = entry.peer, size = ScAvatarSize.member, onClick = onOpenProfile)

        Spacer(Modifier.width(ScSpacing.md))

        // Иконка направления звонка: пропущенный — красная, входящий/исходящий — свои
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (entry.missed) {
                        ScTheme.danger.copy(alpha = 0.14f)
                    } else {
                        ScTheme.surfaceGlass
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    entry.missed -> Icons.Filled.PhoneMissed
                    entry.isVideo -> Icons.Filled.Videocam
                    else -> Icons.AutoMirrored.Filled.CallReceived
                },
                contentDescription = null,
                tint = if (entry.missed) ScTheme.danger else ScTheme.textSecondary,
                modifier = Modifier.size(15.dp),
            )
        }

        Spacer(Modifier.width(ScSpacing.md))

        Column(Modifier.weight(1f)) {
            Text(
                text = entry.peer.fullName,
                color = if (entry.missed) ScTheme.danger else ScTheme.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.typeRu,
                    color = ScTheme.textTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                // Длительность показываем только для состоявшихся звонков:
                // «0 сек» у пропущенного выглядит как ошибка данных
                if (!entry.missed && entry.durationMs > 0) {
                    Text("·", color = ScTheme.textTertiary, fontSize = 12.sp)
                    Text(
                        text = TimeFormatter.duration(entry.durationMs),
                        color = ScTheme.textTertiary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = TimeFormatter.messageTime(entry.startedAt),
                color = ScTheme.textTertiary,
                fontSize = 11.5.sp,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallMade,
                contentDescription = "Перезвонить",
                tint = ScTheme.accent,
                modifier = Modifier.size(16.dp).padding(top = 2.dp),
            )
        }
    }
}
