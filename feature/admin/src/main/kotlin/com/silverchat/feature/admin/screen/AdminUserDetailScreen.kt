package com.silverchat.feature.admin.screen

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.LoadingOverlay
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonSize
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverTextField
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.UserBadgesRow
import com.silverchat.core.designsystem.component.admin.AdminAccessLocked
import com.silverchat.core.designsystem.component.admin.AdminConfirmSheet
import com.silverchat.core.designsystem.component.admin.AdminUserCard
import com.silverchat.core.designsystem.component.admin.AdminUserMetrics
import com.silverchat.core.designsystem.component.admin.AuditEntryRow
import com.silverchat.core.designsystem.navigation.AdminNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.AdminAction
import com.silverchat.core.model.AdminRole
import com.silverchat.core.model.labelRu
import com.silverchat.feature.admin.AdminViewModel
import com.silverchat.feature.admin.PendingAction

/**
 * Карточка пользователя в админ-панели.
 *
 * Все привилегированные действия проходят через [AdminConfirmSheet] с
 * обязательным обоснованием: причина попадает в неизменяемый аудит-лог.
 * Кнопки без прав не скрываются, а блокируются — админ должен видеть,
 * какое действие недоступно и почему, а не гадать о его существовании.
 *
 * Кнопки здесь вызывают [AdminViewModel.requestAction], а не репозиторий
 * напрямую: `GrantPrivilegeUseCase` дополнительно запрещает менять
 * собственные права и права мастер-аккаунта `@silver`.
 */
@Composable
fun AdminUserDetailScreen(
    userId: String,
    navigator: AdminNavigator,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val confirm by viewModel.confirmState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(userId) {
        viewModel.loadUserDetails(userId)
    }

    LaunchedEffect(events) {
        if (events != null) viewModel.consumeEvent()
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        if (!access.allowed) {
            AdminAccessLocked(modifier = Modifier.fillMaxSize())
            return
        }

        if (state.isLoadingDetails) {
            LoadingOverlay(message = "Загружаем карточку")
            return
        }

        val details = state.details
        if (details == null) {
            EmptyState(
                icon = Icons.Filled.Shield,
                title = "Пользователь не найден",
                subtitle = state.errorText ?: "Карточка недоступна или доступ запрещён.",
                actionLabel = "Назад",
                onAction = navigator::back,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }

        val user = details.user
        val isMaster = user.flags.isMasterAccount

        Column(Modifier.fillMaxSize()) {
            SilverTopBar(
                title = user.fullName,
                subtitle = user.handle ?: user.id.raw,
                onBack = navigator::back,
                actions = {
                    Text(
                        text = "Профиль",
                        color = ScTheme.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(ScShapes.chip)
                            .clickable { navigator.openProfile(user.id.raw) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                },
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = ScSpacing.xxl),
            ) {
                // ── Шапка ────────────────────────────────────────────────
                item(key = "header", contentType = "header") {
                    Column(Modifier.padding(ScSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(user = user, size = ScAvatarSize.profileHero)
                            Spacer(Modifier.width(ScSpacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = user.fullName,
                                    color = ScTheme.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                UserBadgesRow(user = user)
                                if (isMaster) {
                                    MasterWarning()
                                }
                            }
                        }
                        user.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                            Spacer(Modifier.height(ScSpacing.sm))
                            Text(bio, color = ScTheme.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }

                // ── Кошелёк ──────────────────────────────────────────────
                item(key = "wallet", contentType = "wallet") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScSpacing.md)
                            .clip(ScShapes.card)
                            .background(ScTheme.surfaceElevated)
                            .padding(ScSpacing.md),
                    ) {
                        Text(
                            text = "Кошелёк",
                            color = ScTheme.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(ScSpacing.sm))
                        DetailLine("Баланс", NumberFormatter.silver(details.wallet.balance))
                        DetailLine("В escrow", NumberFormatter.silver(details.wallet.frozen))
                        DetailLine("Заработано", NumberFormatter.silver(details.wallet.earnedTotal))
                        DetailLine("Потрачено", NumberFormatter.silver(details.wallet.spentTotal))
                        DetailLine("Стрик", "${details.streak.streakDays} дн. (лучший ${details.streak.bestStreak})")
                        DetailLine(
                            "Premium",
                            details.premium.javaClass.simpleName,
                            valueColor = if (details.premium.isActive) ScTheme.premium else ScTheme.textTertiary,
                        )

                        Spacer(Modifier.height(ScSpacing.md))

                        // Операции с валютой: без лимита суммы по требованию продукта
                        val canFinance = access.can(AdminAction.CREDIT_SILVER) ||
                            access.can(AdminAction.DEBIT_SILVER)
                        Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
                            SilverButton(
                                text = "Начислить",
                                onClick = {
                                    viewModel.requestAction(
                                        PendingAction.CreditSilver(
                                            userId = user.id,
                                            userName = user.fullName,
                                        ),
                                    )
                                },
                                enabled = canFinance && !isMaster,
                                variant = SilverButtonVariant.SECONDARY,
                                size = SilverButtonSize.SMALL,
                                modifier = Modifier.weight(1f),
                            )
                            SilverButton(
                                text = "Сбросить",
                                onClick = {
                                    viewModel.requestAction(
                                        PendingAction.ResetWallet(
                                            userId = user.id,
                                            userName = user.fullName,
                                        ),
                                    )
                                },
                                enabled = access.can(AdminAction.RESET_WALLET) && !isMaster,
                                variant = SilverButtonVariant.TEXT,
                                size = SilverButtonSize.SMALL,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (!canFinance) {
                            Text(
                                text = "Операции с валютой требуют роли FINANCE, ADMIN или OWNER",
                                color = ScTheme.textTertiary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = ScSpacing.xs),
                            )
                        }
                    }
                }

                // ── Статусы ──────────────────────────────────────────────
                item(key = "badges", contentType = "badges") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(ScSpacing.md)
                            .clip(ScShapes.card)
                            .background(ScTheme.surfaceElevated)
                            .padding(ScSpacing.md),
                    ) {
                        Text(
                            text = "Статусы",
                            color = ScTheme.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(ScSpacing.sm))

                        AdminUserCard(
                            user = user,
                            role = AdminRole.NONE,
                            metrics = AdminUserMetrics(
                                balance = details.wallet.balance,
                                earnedTotal = details.wallet.earnedTotal,
                                spentTotal = details.wallet.spentTotal,
                                reportsCount = details.reportsAgainst,
                            ),
                            canGrant = access.can(AdminAction.GRANT_VERIFIED) && !isMaster,
                            onToggleVerified = {
                                viewModel.requestAction(
                                    PendingAction.GrantVerified(
                                        userId = user.id,
                                        userName = user.fullName,
                                        value = !user.badges.verified,
                                    ),
                                )
                            },
                            onTogglePremium = {
                                viewModel.requestAction(
                                    PendingAction.GrantPremium(
                                        userId = user.id,
                                        userName = user.fullName,
                                        value = !details.premium.isActive,
                                    ),
                                )
                            },
                            onToggleDeveloper = {
                                viewModel.requestAction(
                                    PendingAction.GrantDeveloper(
                                        userId = user.id,
                                        userName = user.fullName,
                                        value = !user.badges.developer,
                                    ),
                                )
                            },
                        )

                        // Выдача роли — только OWNER
                        if (access.role == AdminRole.OWNER) {
                            Spacer(Modifier.height(ScSpacing.md))
                            Text(
                                text = "Административная роль",
                                color = ScTheme.textTertiary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Spacer(Modifier.height(ScSpacing.sm))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                // Роль выбирается в диалоге: перечень длинный,
                                // и показывать семь чипов в строке нечитабельно
                                RolePicker(
                                    enabled = !isMaster,
                                    onPick = { role ->
                                        viewModel.requestAction(
                                            PendingAction.GrantRole(
                                                userId = user.id,
                                                userName = user.fullName,
                                                role = role,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // ── Модерация ────────────────────────────────────────────
                item(key = "moderation", contentType = "moderation") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(ScSpacing.md)
                            .clip(ScShapes.card)
                            .background(ScTheme.surfaceElevated)
                            .padding(ScSpacing.md),
                    ) {
                        Text(
                            text = "Модерация",
                            color = ScTheme.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(ScSpacing.xs))
                        Text(
                            text = "Жалоб на пользователя: ${details.reportsAgainst}",
                            color = ScTheme.textTertiary,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(ScSpacing.md))

                        if (user.flags.banned) {
                            SilverButton(
                                text = "Разблокировать",
                                onClick = {
                                    viewModel.requestAction(
                                        PendingAction.Unban(user.id, user.fullName),
                                    )
                                },
                                enabled = access.can(AdminAction.UNBAN_USER) && !isMaster,
                                variant = SilverButtonVariant.SECONDARY,
                                leadingIcon = Icons.Filled.Restore,
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
                                SilverButton(
                                    text = "Ограничить",
                                    onClick = {
                                        viewModel.requestAction(
                                            PendingAction.Restrict(user.id, user.fullName),
                                        )
                                    },
                                    enabled = access.can(AdminAction.RESTRICT_USER) && !isMaster,
                                    variant = SilverButtonVariant.SECONDARY,
                                    size = SilverButtonSize.SMALL,
                                    modifier = Modifier.weight(1f),
                                )
                                SilverButton(
                                    text = "Забанить",
                                    onClick = {
                                        viewModel.requestAction(
                                            PendingAction.Ban(user.id, user.fullName),
                                        )
                                    },
                                    enabled = access.can(AdminAction.BAN_USER) && !isMaster,
                                    size = SilverButtonSize.SMALL,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                // ── Юзернеймы ────────────────────────────────────────────
                if (details.ownedUsernames.isNotEmpty()) {
                    item(key = "usernames", contentType = "usernames") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(ScSpacing.md)
                                .clip(ScShapes.card)
                                .background(ScTheme.surfaceElevated)
                                .padding(ScSpacing.md),
                        ) {
                            Text(
                                text = "Юзернеймы (${details.ownedUsernames.size})",
                                color = ScTheme.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(ScSpacing.sm))
                            details.ownedUsernames.forEach { username ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "@$username",
                                        color = ScTheme.accent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (access.can(AdminAction.BLOCK_USERNAME)) {
                                        Text(
                                            text = "Изъять",
                                            color = ScTheme.danger,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(ScShapes.chip)
                                                .clickable {
                                                    viewModel.requestAction(
                                                        PendingAction.BlockUsername(username),
                                                    )
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Сессии ───────────────────────────────────────────────
                if (details.sessions.isNotEmpty()) {
                    item(key = "sessions_hdr", contentType = "header") {
                        Text(
                            text = "Устройства · ${details.sessions.size}",
                            color = ScTheme.textTertiary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                        )
                    }
                    items(details.sessions, key = { "s_${it.id}" }, contentType = { "session" }) { session ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ScSpacing.md, vertical = ScSpacing.xs)
                                .clip(ScShapes.cardSmall)
                                .background(ScTheme.surfaceElevated)
                                .padding(ScSpacing.md),
                        ) {
                            Text(
                                text = session.deviceName,
                                color = ScTheme.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${session.platform} · ${session.appVersion}",
                                color = ScTheme.textTertiary,
                                fontSize = 11.sp,
                            )
                            Text(
                                text = TimeFormatter.lastSeen(session.lastActiveAt),
                                color = ScTheme.textTertiary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // ── Последние действия ───────────────────────────────────
                if (details.recentAudit.isNotEmpty()) {
                    item(key = "audit_hdr", contentType = "header") {
                        Text(
                            text = "Последние действия администрации",
                            color = ScTheme.textTertiary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                        )
                    }
                    items(details.recentAudit, key = { "a_${it.id}" }, contentType = { "audit" }) { entry ->
                        AuditEntryRow(
                            entry = entry,
                            modifier = Modifier.padding(
                                horizontal = ScSpacing.md,
                                vertical = ScSpacing.xs,
                            ),
                        )
                    }
                }
            }
        }

        // ── Диалог подтверждения ─────────────────────────────────────────
        confirm?.let { pending ->
            ConfirmSheet(
                action = pending,
                onReasonChange = viewModel::onConfirmReasonChanged,
                onAmountChange = viewModel::onConfirmAmountChanged,
                onConfirm = viewModel::confirmAction,
                onDismiss = viewModel::dismissConfirm,
            )
        }

        events?.let { event ->
            AdminEventSnackbar(
                event = event,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

/* =========================================================================
   ЭЛЕМЕНТЫ КАРТОЧКИ
   ========================================================================= */

@Composable
private fun MasterWarning() {
    Row(
        Modifier
            .padding(top = ScSpacing.xs)
            .clip(ScShapes.chip)
            .background(ScTheme.warning.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            tint = ScTheme.warning,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = "Мастер-аккаунт: права неизменяемы",
            color = ScTheme.warning,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String, valueColor: Color = ScTheme.textPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = ScTheme.textTertiary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Выбор административной роли.
 *
 * Раскрывающийся список, а не ряд чипов: ролей семь, и горизонтальный ряд
 * не поместился бы на узком экране. Выбор открывает диалог с обоснованием.
 */
@Composable
private fun RolePicker(enabled: Boolean, onPick: (AdminRole) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        SilverButton(
            text = if (expanded) "Скрыть роли" else "Изменить роль",
            onClick = { expanded = !expanded },
            enabled = enabled,
            variant = SilverButtonVariant.SECONDARY,
            size = SilverButtonSize.SMALL,
            leadingIcon = Icons.Filled.Shield,
        )
        if (expanded) {
            Spacer(Modifier.height(ScSpacing.sm))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(ScShapes.card)
                    .background(ScTheme.surface),
            ) {
                // NONE первым: понижение прав — самое частое действие после выдачи
                ROLES_FOR_PICK.forEach { role ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                expanded = false
                                onPick(role)
                            }
                            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = role.labelRu,
                            color = ScTheme.textPrimary,
                            fontSize = 13.5.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = role.scopeRu,
                            color = ScTheme.textTertiary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Роли, доступные для выдачи. OWNER исключён: он принадлежит только `@silver`. */
private val ROLES_FOR_PICK = listOf(
    AdminRole.NONE,
    AdminRole.MODERATOR,
    AdminRole.SUPPORT,
    AdminRole.MARKET_MANAGER,
    AdminRole.FINANCE,
    AdminRole.ADMIN,
)

/** Краткое описание полномочий роли — помогает не выдать лишнее. */
val AdminRole.scopeRu: String
    get() = when (this) {
        AdminRole.NONE -> "без прав"
        AdminRole.MODERATOR -> "жалобы, бан, удаление"
        AdminRole.SUPPORT -> "только ответы на жалобы"
        AdminRole.MARKET_MANAGER -> "маркет: лоты и цены"
        AdminRole.FINANCE -> "операции с сильверами"
        AdminRole.ADMIN -> "статусы и модерация"
        AdminRole.OWNER -> "полный доступ"
    }

/* =========================================================================
   ДИАЛОГ ПОДТВЕРЖДЕНИЯ
   ========================================================================= */

/**
 * Диалог подтверждения привилегированного действия.
 *
 * Заголовок и описание формируются из [PendingAction]: одно место знает,
 * что именно подтверждается, и экран не содержит ветвлений по каждому
 * действию дважды.
 */
@Composable
private fun ConfirmSheet(
    action: PendingAction,
    onReasonChange: (String) -> Unit,
    onAmountChange: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, description, destructive, extra) = action.confirmText(onAmountChange)

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(ScSpacing.lg)
                .clip(ScShapes.dialog)
                .background(ScTheme.surfaceElevated)
                .padding(ScSpacing.lg),
        ) {
            AdminConfirmSheet(
                title = title,
                description = description,
                reason = action.reason,
                onReasonChange = onReasonChange,
                confirmLabel = action.confirmLabel(),
                destructive = destructive,
                requireReason = true,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )

            // Дополнительные параметры, специфичные для действия
            extra?.let { content -> content() }
        }
    }
}

/**
 * Текст диалога для действия.
 *
 * @return заголовок, описание, признак разрушительности и необязательный
 *   дополнительный блок ввода (сумма, срок, цена).
 */
private fun PendingAction.confirmText(
    onAmountChange: (Long) -> Unit,
): ConfirmText = when (this) {
    is PendingAction.GrantVerified -> ConfirmText(
        title = if (value) "Выдать верификацию" else "Снять верификацию",
        description = "$userName ${if (value) "получит" else "лишится"} галочку подтверждённого аккаунта.",
        destructive = !value,
    )

    is PendingAction.GrantPremium -> ConfirmText(
        title = if (value) "Выдать Premium" else "Снять Premium",
        description = if (value) {
            "$userName получит подписку на ${days ?: 0} дн. (null — пожизненно)."
        } else {
            "$userName лишится подписки Premium."
        },
        destructive = !value,
    )

    is PendingAction.GrantDeveloper -> ConfirmText(
        title = if (value) "Выдать статус разработчика" else "Снять статус разработчика",
        description = "$userName ${if (value) "получит" else "лишится"} бейдж разработчика.",
        destructive = !value,
    )

    is PendingAction.GrantRole -> ConfirmText(
        title = if (role == AdminRole.NONE) "Снять админ-роль" else "Выдать роль: ${role.labelRu}",
        description = "$userName: ${role.scopeRu}.",
        destructive = role == AdminRole.NONE,
    )

    is PendingAction.Ban -> ConfirmText(
        title = if (permanent) "Перманентный бан" else "Бан пользователя",
        description = "$userName потеряет доступ к аккаунту.",
        destructive = true,
    )

    is PendingAction.Unban -> ConfirmText(
        title = "Разблокировать",
        description = "$userName снова сможет войти.",
        destructive = false,
    )

    is PendingAction.Restrict -> ConfirmText(
        title = "Ограничить пользователя",
        description = "$userName не сможет писать и звонить $hours ч.",
        destructive = true,
    )

    is PendingAction.CreditSilver -> ConfirmText(
        title = if (amount >= 0) "Начислить сильверы" else "Списать сильверы",
        description = "$userName: ${if (amount >= 0) "+" else "−"}" +
            NumberFormatter.silver(kotlin.math.abs(amount)) +
            ". Лимита суммы нет — действие пишется в аудит-лог.",
        destructive = amount < 0,
        extra = { AmountField(amount = amount, onAmountChange = onAmountChange) },
    )

    is PendingAction.ResetWallet -> ConfirmText(
        title = "Сбросить кошелёк",
        description = "$userName: баланс, стрик и история будут обнулены. Действие необратимо.",
        destructive = true,
    )

    is PendingAction.BlockUsername -> ConfirmText(
        title = "Изъять юзернейм",
        description = "@$username будет заблокирован и исчезнет из маркета.",
        destructive = true,
    )

    is PendingAction.AdjustPrice -> ConfirmText(
        title = "Изменить цену лота",
        description = "@$username: новая цена ${NumberFormatter.silver(priceSilver)}.",
        destructive = false,
    )

    is PendingAction.Refund -> ConfirmText(
        title = "Возврат по операции",
        description = "Операция $transactionId будет отменена, сильверы вернутся плательщику.",
        destructive = false,
    )
}

private data class ConfirmText(
    val title: String,
    val description: String,
    val destructive: Boolean,
    val extra: (@Composable () -> Unit)? = null,
)

/** Метка кнопки подтверждения. */
private fun PendingAction.confirmLabel(): String = when (this) {
    is PendingAction.Ban -> if (permanent) "Забанить навсегда" else "Забанить"
    is PendingAction.ResetWallet -> "Сбросить кошелёк"
    is PendingAction.BlockUsername -> "Изъять юзернейм"
    is PendingAction.Refund -> "Вернуть средства"
    else -> "Подтвердить"
}

/**
 * Поле суммы для начисления сильверов.
 *
 * Сумма редактируется прямо в диалоге и уходит в [PendingAction.CreditSilver]
 * через [onAmountChange]. Отрицательное значение означает списание:
 * `CreditSilverUseCase` выбирает действие по знаку и проверяет
 * соответствующее право (CREDIT_SILVER или DEBIT_SILVER).
 */
@Composable
private fun AmountField(amount: Long, onAmountChange: (Long) -> Unit) {
    Column(Modifier.padding(top = ScSpacing.sm)) {
        SilverTextField(
            value = if (amount == 0L) "" else amount.toString(),
            onValueChange = { raw -> onAmountChange(raw.toLongOrNull() ?: 0L) },
            label = "Сумма (отрицательная — списание)",
            placeholder = "0",
            keyboardType = KeyboardType.Number,
        )
        Text(
            text = "Лимита суммы нет: это требование продукта. " +
                "Каждое начисление записывается в аудит-лог с причиной.",
            color = ScTheme.textTertiary,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = ScSpacing.xs),
        )
    }
}
