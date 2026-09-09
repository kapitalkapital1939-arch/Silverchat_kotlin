package com.silverchat.feature.settings.screen

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.SettingsRow
import com.silverchat.core.designsystem.component.SettingsSection
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonSize
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.navigation.SettingsNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.User
import com.silverchat.core.model.VisibilityRule
import com.silverchat.feature.settings.PrivacyEvent
import com.silverchat.feature.settings.PrivacyField
import com.silverchat.feature.settings.PrivacyViewModel
import com.silverchat.feature.settings.titleRu

/**
 * Конфиденциальность.
 *
 * Каждое правило раскрывается в три значения [VisibilityRule]. Выбранное
 * подсвечивается акцентом, остальные приглушены — это заменяет отдельный
 * экран выбора: правил шесть, и переход на каждый означал бы шесть
 * дополнительных маршрутов в навигации.
 *
 * Чёрный список — сворачиваемая секция. Он показывается по запросу,
 * а не всегда: список заблокированных сам по себе чувствителен,
 * и не должен мелькать при каждом входе в настройки.
 */
@Composable
fun PrivacyScreen(
    navigator: SettingsNavigator,
    modifier: Modifier = Modifier,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(events) {
        if (events != null) viewModel.consumeEvent()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(ScTheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        SilverTopBar(title = "Конфиденциальность", onBack = navigator::back)

        SettingsSection("Кто видит мои данные")
        PrivacyField.entries.forEach { field ->
            RuleBlock(
                field = field,
                selected = state.ruleOf(field),
                onSelect = { rule ->
                    // «Последняя активность» имеет выделенный эндпоинт
                    if (field == PrivacyField.LAST_SEEN) {
                        viewModel.setLastSeenRule(rule)
                    } else {
                        viewModel.setRule(field, rule)
                    }
                },
            )
        }

        SettingsSection("Отчёты о прочтении")
        SettingsRow(
            title = "Галочки прочтения",
            subtitle = "Отключите, чтобы собеседники не видели, что вы прочитали сообщение. " +
                "Взамен вы тоже перестанете видеть их отметки.",
            trailing = {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.privacy.readReceiptsEnabled) ScTheme.success else ScTheme.surface,
                        )
                        .clickable { viewModel.setReadReceipts(!state.privacy.readReceiptsEnabled) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.privacy.readReceiptsEnabled) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            },
        )

        SettingsSection("Чёрный список")
        SettingsRow(
            title = if (state.showBlocked) "Скрыть заблокированных" else "Показать заблокированных",
            subtitle = "${state.blockedUsers.size} в списке",
            icon = Icons.Filled.Block,
            onClick = viewModel::toggleBlockedSection,
        )

        if (state.showBlocked) {
            if (state.blockedUsers.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Block,
                    title = "Список пуст",
                    subtitle = "Заблокированные пользователи появятся здесь.",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                state.blockedUsers.forEach { user ->
                    BlockedRow(user = user, onUnblock = { viewModel.unblock(user) })
                }
            }
        }

        events?.let { event ->
            val message = when (event) {
                is PrivacyEvent.Blocked -> "${event.name} заблокирован"
                is PrivacyEvent.Unblocked -> "${event.name} разблокирован"
                is PrivacyEvent.Error -> event.message
            }
            InlineSnackbar(
                message = message,
                modifier = Modifier.fillMaxWidth().padding(ScSpacing.md),
            )
        }

        Spacer(Modifier.height(ScSpacing.xxl))
    }
}

/* =========================================================================
   ПРАВИЛО ВИДИМОСТИ
   ========================================================================= */

@Composable
private fun RuleBlock(
    field: PrivacyField,
    selected: VisibilityRule,
    onSelect: (VisibilityRule) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
    ) {
        Text(
            text = field.titleRu,
            color = ScTheme.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = field.subtitleRu,
            color = ScTheme.textTertiary,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(ScSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
            VisibilityRule.entries.forEach { rule ->
                val isSelected = rule == selected
                Text(
                    text = rule.titleRu,
                    color = if (isSelected) Color.White else ScTheme.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(ScShapes.chip)
                        .background(if (isSelected) ScTheme.accent else ScTheme.surfaceGlass)
                        .clickable { onSelect(rule) }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/* =========================================================================
   ЧЁРНЫЙ СПИСОК
   ========================================================================= */

@Composable
private fun BlockedRow(user: User, onUnblock: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm)
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.md),
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
                Text(it, color = ScTheme.textTertiary, fontSize = 11.5.sp, maxLines = 1)
            }
        }
        SilverButton(
            text = "Разблокировать",
            onClick = onUnblock,
            variant = SilverButtonVariant.TEXT,
            size = SilverButtonSize.SMALL,
        )
    }
}
