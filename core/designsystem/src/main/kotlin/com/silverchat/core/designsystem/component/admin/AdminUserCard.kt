package com.silverchat.core.designsystem.component.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.AdminRoleChip
import com.silverchat.core.designsystem.component.ChipBadge
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.AdminAction
import com.silverchat.core.model.AdminLogEntry
import com.silverchat.core.model.AdminRole
import com.silverchat.core.model.AdminStats
import com.silverchat.core.model.ModerationCase
import com.silverchat.core.model.ModerationStatus
import com.silverchat.core.model.ModerationTargetType
import com.silverchat.core.model.ReportReason
import com.silverchat.core.model.User
import com.silverchat.core.model.labelRu

/**
 * Карточка пользователя в админ-панели @silver.
 *
 * Показывает всё, что нужно админу для решения, БЕЗ перехода на отдельный
 * экран: роль, статусы, метрики и быстрые действия.
 *
 * Быстрые действия намеренно ограничены самыми частыми (Premium, верификация,
 * разработчик). Деструктивные операции (бан, удаление) требуют открытия
 * карточки и ввода причины через [AdminConfirmSheet] — чтобы админ не снёс
 * аккаунт случайным тапом, и чтобы в аудит-логе всегда было обоснование.
 */
@Composable
fun AdminUserCard(
    user: User,
    role: AdminRole,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    metrics: AdminUserMetrics? = null,
    canGrant: Boolean = true,
    onClick: () -> Unit = {},
    onTogglePremium: () -> Unit = {},
    onToggleVerified: () -> Unit = {},
    onToggleDeveloper: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .then(
                if (selected) Modifier.border(1.dp, ScTheme.accent, ScShapes.card) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(ScSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(
                user = user,
                size = ScAvatarSize.listItem,
                showOnline = true,
            )
            Spacer(Modifier.width(ScSpacing.md))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = user.fullName,
                        color = ScTheme.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (user.flags.banned) ChipBadge("БАН", ScTheme.danger)
                    if (user.flags.restricted) ChipBadge("ОГРАНИЧЕН", ScTheme.warning)
                    if (user.flags.isMasterAccount) ChipBadge("OWNER", ScTheme.danger)
                }
                Text(
                    text = user.handle ?: "без юзернейма",
                    color = ScTheme.accent,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AdminRoleChip(role)
                    // Короткий ID нужен, чтобы админ мог сверить карточку с логом
                    Text(
                        text = "ID ${user.id.raw.take(8)}…",
                        color = ScTheme.textTertiary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        if (metrics != null) {
            Spacer(Modifier.height(ScSpacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AdminMetric("Баланс", NumberFormatter.silver(metrics.balance))
                AdminMetric("Заработано", NumberFormatter.compact(metrics.earnedTotal))
                AdminMetric("Потрачено", NumberFormatter.compact(metrics.spentTotal))
                AdminMetric("Жалобы", metrics.reportsCount.toString(), danger = metrics.reportsCount > 0)
            }
        }

        if (canGrant) {
            Spacer(Modifier.height(ScSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
                AdminActionChip(
                    icon = Icons.Filled.Star,
                    label = if (user.premium.isActive) "Снять Premium" else "Выдать Premium",
                    active = user.premium.isActive,
                    tint = ScTheme.premium,
                    modifier = Modifier.weight(1f),
                    onClick = onTogglePremium,
                )
                AdminActionChip(
                    icon = Icons.Filled.Check,
                    label = if (user.badges.verified) "Снять вериф." else "Верифицировать",
                    active = user.badges.verified,
                    tint = ScTheme.accent,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleVerified,
                )
                AdminActionChip(
                    icon = Icons.Filled.Bolt,
                    label = if (user.badges.developer) "Снять dev" else "Разработчик",
                    active = user.badges.developer,
                    tint = DEV_VIOLET,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleDeveloper,
                )
            }
        }
    }
}

/** Метрики пользователя для админ-карточки (приходят из [AdminUserDetails]). */
data class AdminUserMetrics(
    val balance: Long,
    val earnedTotal: Long = 0L,
    val spentTotal: Long = 0L,
    val reportsCount: Int = 0,
)

private val DEV_VIOLET = Color(0xFFA855F7)

@Composable
private fun AdminMetric(label: String, value: String, danger: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = if (danger) ScTheme.danger else ScTheme.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(label, color = ScTheme.textTertiary, fontSize = 10.5.sp)
    }
}

@Composable
private fun AdminActionChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .clip(ScShapes.cardSmall)
            .background(if (active) tint.copy(alpha = 0.16f) else ScTheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) tint else ScTheme.textTertiary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = if (active) tint else ScTheme.textTertiary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Запись журнала аудита.
 *
 * Аудит — обязательное требование к админ-панели: любое действие (выдача
 * прав, бан, изменение цены лота) оставляет неизменяемый след на сервере.
 * Клиент только читает лог — писать в него нельзя даже владельцу.
 */
@Composable
fun AuditEntryRow(entry: AdminLogEntry, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(auditColor(entry.action).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = auditIcon(entry.action),
                contentDescription = null,
                tint = auditColor(entry.action),
                modifier = Modifier.size(17.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.action.labelRu,
                color = ScTheme.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    append(entry.actorName)
                    entry.targetName?.takeIf { it.isNotBlank() }?.let { append(" → ").append(it) }
                },
                color = ScTheme.textSecondary,
                fontSize = 12.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Обоснование обязательно: без него аудит теряет смысл
            entry.reason?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Причина: $it",
                    color = ScTheme.textTertiary,
                    fontSize = 11.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            entry.payload?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = ScTheme.textTertiary,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(TimeFormatter.full(entry.createdAt), color = ScTheme.textTertiary, fontSize = 10.5.sp)
            entry.ip?.let {
                Text(it, color = ScTheme.textTertiary, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun auditColor(action: AdminAction): Color = when (action) {
    AdminAction.GRANT_ADMIN_ROLE, AdminAction.REVOKE_ADMIN_ROLE -> DEV_VIOLET
    AdminAction.BAN_USER, AdminAction.UNBAN_USER, AdminAction.RESTRICT_USER -> ScTheme.danger
    AdminAction.GRANT_PREMIUM, AdminAction.REVOKE_PREMIUM -> ScTheme.premium
    AdminAction.GRANT_VERIFIED, AdminAction.REVOKE_VERIFIED -> ScTheme.accent
    AdminAction.GRANT_DEVELOPER, AdminAction.REVOKE_DEVELOPER -> DEV_VIOLET
    AdminAction.CREDIT_SILVER, AdminAction.DEBIT_SILVER, AdminAction.RESET_WALLET -> ScTheme.success
    AdminAction.BLOCK_USERNAME, AdminAction.ADJUST_LISTING_PRICE, AdminAction.REFUND_TRANSACTION -> ScTheme.warning
    AdminAction.DELETE_MESSAGE, AdminAction.DELETE_CHAT -> ScTheme.danger
    AdminAction.BROADCAST -> ScTheme.info
    AdminAction.RESOLVE_REPORT -> ScTheme.info
}

private fun auditIcon(action: AdminAction): ImageVector = when (action) {
    AdminAction.GRANT_ADMIN_ROLE, AdminAction.REVOKE_ADMIN_ROLE -> Icons.Filled.Shield
    AdminAction.BAN_USER, AdminAction.UNBAN_USER, AdminAction.RESTRICT_USER -> Icons.Filled.Gavel
    AdminAction.GRANT_PREMIUM, AdminAction.REVOKE_PREMIUM -> Icons.Filled.Star
    AdminAction.GRANT_VERIFIED, AdminAction.REVOKE_VERIFIED -> Icons.Filled.Check
    AdminAction.GRANT_DEVELOPER, AdminAction.REVOKE_DEVELOPER -> Icons.Filled.Bolt
    AdminAction.CREDIT_SILVER, AdminAction.DEBIT_SILVER, AdminAction.RESET_WALLET -> Icons.Filled.Bolt
    AdminAction.BLOCK_USERNAME, AdminAction.ADJUST_LISTING_PRICE, AdminAction.REFUND_TRANSACTION -> Icons.Filled.Edit
    AdminAction.DELETE_MESSAGE, AdminAction.DELETE_CHAT -> Icons.Filled.Delete
    AdminAction.BROADCAST -> Icons.Filled.Notifications
    AdminAction.RESOLVE_REPORT -> Icons.Filled.Check
}

/**
 * Панель подтверждения опасного действия.
 *
 * Требует явного ввода причины: она уходит в [AdminGrantRequest.reason]
 * и сохраняется в аудит-логе. Кнопка не активируется, пока причина пуста.
 */
@Composable
fun AdminConfirmSheet(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    reason: String = "",
    onReasonChange: (String) -> Unit = {},
    confirmLabel: String = "Подтвердить",
    destructive: Boolean = true,
    requireReason: Boolean = true,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val canConfirm = !requireReason || reason.isNotBlank()

    Column(
        modifier
            .fillMaxWidth()
            .clip(ScShapes.cardLarge)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.lg),
    ) {
        Text(title, color = ScTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(ScSpacing.sm))
        Text(description, color = ScTheme.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)

        if (requireReason) {
            Spacer(Modifier.height(ScSpacing.md))
            Text(
                text = "Причина (попадёт в журнал аудита)",
                color = ScTheme.textTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(ScSpacing.xs))
            androidx.compose.material3.OutlinedTextField(
                value = reason,
                onValueChange = onReasonChange,
                placeholder = { Text("Например: спам в маркете, жалоба #1234", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().height(104.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ScTheme.accent,
                    unfocusedBorderColor = ScTheme.outline,
                    cursorColor = ScTheme.accent,
                    focusedTextColor = ScTheme.textPrimary,
                    unfocusedTextColor = ScTheme.textPrimary,
                ),
            )
        }

        Spacer(Modifier.height(ScSpacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(ScShapes.chip)
                    .background(ScTheme.surface)
                    .border(1.dp, ScTheme.outline, ScShapes.chip)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Отмена", color = ScTheme.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(ScShapes.chip)
                    .background(if (destructive) ScTheme.danger else ScTheme.accent)
                    .alpha(if (canConfirm) 1f else 0.5f)
                    .clickable(enabled = canConfirm, onClick = onConfirm)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(confirmLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

/** Плашка «доступ закрыт»: раздел виден только при наличии админ-прав. */
@Composable
fun AdminAccessLocked(
    modifier: Modifier = Modifier,
    ownerRequired: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(ScSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = ScTheme.textTertiary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(ScSpacing.md))
        Text(
            text = if (ownerRequired) {
                "Раздел доступен только владельцу @silver"
            } else {
                "Недостаточно прав администратора"
            },
            color = ScTheme.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(ScSpacing.xs))
        Text(
            text = "Права выдаёт владелец продукта в разделе «Администраторы».",
            color = ScTheme.textTertiary,
            fontSize = 13.sp,
        )
    }
}

/** Плитка дашборда админки (статистика из [AdminStats]). */
@Composable
fun AdminStatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = ScTheme.accent,
    icon: ImageVector = Icons.Filled.Shield,
) {
    Column(
        modifier = modifier
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .border(1.dp, ScTheme.outline, ScShapes.card)
            .padding(ScSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(title, color = ScTheme.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.height(ScSpacing.xs))
        Text(value, color = ScTheme.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Строка модерационной жалобы. */
@Composable
fun ModerationCaseRow(
    case: ModerationCase,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onResolve: () -> Unit = {},
) {
    val statusColor = when (case.status) {
        ModerationStatus.OPEN -> ScTheme.danger
        ModerationStatus.IN_REVIEW -> ScTheme.warning
        ModerationStatus.RESOLVED -> ScTheme.success
        ModerationStatus.REJECTED -> ScTheme.textTertiary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(ScSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
            Text(
                text = case.reason.labelRu,
                color = ScTheme.textPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            ChipBadge(case.status.labelRu, statusColor)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${case.targetType.labelRu} · #${case.targetId.take(10)}",
            color = ScTheme.textSecondary,
            fontSize = 12.sp,
        )
        case.comment?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = ScTheme.textTertiary, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(ScSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = TimeFormatter.full(case.createdAt),
                color = ScTheme.textTertiary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            if (case.status == ModerationStatus.OPEN || case.status == ModerationStatus.IN_REVIEW) {
                Text(
                    text = "Обработать",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(ScShapes.chip)
                        .background(ScTheme.accent)
                        .clickable(onClick = onResolve)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Причины жалоб — список для фильтра в админке.
 *
 * Top-level значение, а не экстеншен на `Companion`: у [ReportReason] нет
 * companion object, и добавлять его ради списка значений перечисления не нужно —
 * `entries` доступен статически.
 */
val reportReasonsForFilter: List<ReportReason>
    get() = ReportReason.entries

/** Типы объектов жалоб — для подписи в фильтре. */
fun ModerationTargetType.shortRu(): String = labelRu
