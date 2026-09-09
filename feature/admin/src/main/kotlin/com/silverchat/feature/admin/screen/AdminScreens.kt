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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
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
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.SearchField
import com.silverchat.core.designsystem.component.SettingsRow
import com.silverchat.core.designsystem.component.SettingsSection
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.admin.AdminAccessLocked
import com.silverchat.core.designsystem.component.admin.AdminStatTile
import com.silverchat.core.designsystem.component.admin.AuditEntryRow
import com.silverchat.core.designsystem.component.admin.ModerationCaseRow
import com.silverchat.core.designsystem.navigation.AdminNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.AdminAction
import com.silverchat.core.model.AdminBroadcastAudience
import com.silverchat.core.model.AdminStats
import com.silverchat.core.model.ModerationStatus
import com.silverchat.core.model.labelRu
import com.silverchat.feature.admin.AdminEvent
import com.silverchat.feature.admin.AdminViewModel

/**
 * Дашборд админ-панели `@silver`.
 *
 * Первое, что проверяет экран, — [com.silverchat.core.domain.repository.AdminAccess].
 * Без прав рисуется [AdminAccessLocked], а не пустой дашборд: админ должен
 * понимать, что раздел существует, но недоступен, а не что данные не загрузились.
 *
 * Клиентская проверка — только UX. Каждый раздел ниже соответствует
 * эндпоинту, закрытому на бэкенде middleware `AdminGuard`.
 */
@Composable
fun AdminDashboardScreen(
    navigator: AdminNavigator,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(events) {
        if (events != null) viewModel.consumeEvent()
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        if (!access.allowed) {
            AdminAccessLocked(
                ownerRequired = true,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SilverTopBar(
                title = "Панель @silver",
                subtitle = "Роль: ${access.role.labelRu}",
                onBack = navigator::back,
            )

            // ── Сводка ───────────────────────────────────────────────────
            state.stats?.let { stats ->
                StatsGrid(stats = stats)
            } ?: run {
                Text(
                    text = if (state.isLoadingStats) "Загружаем сводку…" else "Сводка недоступна",
                    color = ScTheme.textTertiary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(ScSpacing.lg),
                )
            }

            SilverButton(
                text = "Обновить сводку",
                onClick = viewModel::loadStats,
                enabled = !state.isLoadingStats,
                loading = state.isLoadingStats,
                variant = SilverButtonVariant.SECONDARY,
                modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
            )

            // ── Разделы ──────────────────────────────────────────────────
            SettingsSection("Разделы")
            SectionRow(
                title = "Пользователи",
                subtitle = "Поиск, выдача статусов, операции с балансом",
                icon = Icons.Filled.People,
                enabled = access.can(AdminAction.GRANT_VERIFIED) ||
                    access.can(AdminAction.CREDIT_SILVER),
                onClick = navigator::openUsers,
            )
            SectionRow(
                title = "Жалобы",
                subtitle = "Открытые кейсы модерации",
                icon = Icons.Filled.Report,
                iconTint = if ((state.stats?.openReports ?: 0) > 0) {
                    ScTheme.danger
                } else {
                    ScTheme.textSecondary
                },
                enabled = access.can(AdminAction.RESOLVE_REPORT),
                onClick = navigator::openReports,
            )
            SectionRow(
                title = "Инструменты маркета",
                subtitle = "Блокировка юзернеймов, корректировка цен, возвраты",
                icon = Icons.Filled.LocalOffer,
                enabled = access.can(AdminAction.BLOCK_USERNAME) ||
                    access.can(AdminAction.ADJUST_LISTING_PRICE),
                onClick = navigator::openMarketTools,
            )
            SectionRow(
                title = "Аудит-лог",
                subtitle = "Все действия администрации, неизменяемо",
                icon = Icons.Filled.History,
                // Лог доступен любой роли с доступом: это инструмент самопроверки
                enabled = access.allowed,
                onClick = navigator::openAuditLog,
            )
            SectionRow(
                title = "Рассылка",
                subtitle = "Сообщение всем пользователям или сегменту",
                icon = Icons.Filled.Campaign,
                iconTint = ScTheme.warning,
                enabled = access.can(AdminAction.BROADCAST),
                onClick = navigator::openBroadcast,
            )

            // ── Экономика ────────────────────────────────────────────────
            state.stats?.let { stats ->
                SettingsSection("Экономика сильверов")
                Column(Modifier.padding(horizontal = ScSpacing.md)) {
                    EconomyLine(
                        label = "Эмитировано всего",
                        value = NumberFormatter.silver(stats.silverEmittedTotal),
                        color = ScTheme.success,
                    )
                    EconomyLine(
                        label = "Сожжено всего",
                        value = NumberFormatter.silver(stats.silverBurnedTotal),
                        color = ScTheme.danger,
                    )
                    val net = stats.silverEmittedTotal - stats.silverBurnedTotal
                    EconomyLine(
                        label = "В обращении",
                        value = NumberFormatter.silver(net),
                        color = ScTheme.textPrimary,
                    )
                    EconomyLine(
                        label = "Оборот маркета за 24 ч",
                        value = NumberFormatter.silver(stats.marketVolume24h),
                        color = ScTheme.silver,
                    )
                }
            }

            Spacer(Modifier.height(ScSpacing.xxl))
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
   СВОДКА
   ========================================================================= */

@Composable
private fun StatsGrid(stats: AdminStats) {
    Column(Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
            AdminStatTile(
                title = "Пользователей",
                value = NumberFormatter.compact(stats.totalUsers),
                modifier = Modifier.weight(1f),
            )
            AdminStatTile(
                title = "Активны за 24 ч",
                value = NumberFormatter.compact(stats.activeUsers24h),
                accent = ScTheme.success,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(ScSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
            AdminStatTile(
                title = "Premium",
                value = NumberFormatter.compact(stats.premiumUsers),
                accent = ScTheme.premium,
                modifier = Modifier.weight(1f),
            )
            AdminStatTile(
                title = "Сообщений за 24 ч",
                value = NumberFormatter.compact(stats.totalMessages24h),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(ScSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
            AdminStatTile(
                title = "Открытых жалоб",
                value = stats.openReports.toString(),
                accent = if (stats.openReports > 0) ScTheme.danger else ScTheme.textTertiary,
                modifier = Modifier.weight(1f),
            )
            AdminStatTile(
                title = "Активных звонков",
                value = stats.activeCalls.toString(),
                accent = ScTheme.info,
                modifier = Modifier.weight(1f),
            )
        }

        // Спарклайны: тренд важнее абсолютного числа
        if (stats.sparklineUsers.isNotEmpty() || stats.sparklineMessages.isNotEmpty()) {
            Spacer(Modifier.height(ScSpacing.md))
            SparklineRow(
                title = "Динамика",
                users = stats.sparklineUsers,
                messages = stats.sparklineMessages,
            )
        }
    }
}

/**
 * Спарклайн активности.
 *
 * Рисуется линиями из текста, а не графиком: `sparkline*` — это массив
 * из 12–30 чисел, и полноценный Canvas-график ради них избыточен.
 * Числовые значения важнее формы, поэтому показываем и пик, и последнее.
 */
@Composable
private fun SparklineRow(title: String, users: List<Int>, messages: List<Int>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.md),
    ) {
        Text(title, color = ScTheme.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(ScSpacing.sm))
        if (users.isNotEmpty()) {
            SparklineLine(
                label = "Пользователи",
                values = users,
                color = ScTheme.accent,
            )
        }
        if (messages.isNotEmpty()) {
            SparklineLine(
                label = "Сообщения",
                values = messages,
                color = ScTheme.success,
            )
        }
    }
}

@Composable
private fun SparklineLine(label: String, values: List<Int>, color: Color) {
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Column(Modifier.padding(vertical = ScSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = ScTheme.textTertiary, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
            Text(
                text = "пик ${NumberFormatter.compact(max.toLong())} · сейчас ${NumberFormatter.compact(values.last().toLong())}",
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().height(SPARKLINE_HEIGHT),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            values.forEach { value ->
                // Высота столбца пропорциональна значению; минимум 1.dp,
                // чтобы нулевой период оставался видимым
                val fraction = value.toFloat() / max
                Box(
                    Modifier
                        .weight(1f)
                        .height((SPARKLINE_HEIGHT * fraction).coerceAtLeast(1.dp))
                        .clip(ScShapes.chip)
                        .background(color.copy(alpha = 0.35f + 0.5f * fraction)),
                )
            }
        }
    }
}

private val SPARKLINE_HEIGHT = 28.dp

@Composable
private fun EconomyLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = ScTheme.textTertiary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    iconTint: Color = ScTheme.textSecondary,
) {
    SettingsRow(
        title = title,
        subtitle = if (enabled) subtitle else "$subtitle · нет прав",
        icon = icon,
        iconTint = if (enabled) iconTint else ScTheme.textTertiary,
        enabled = enabled,
        onClick = if (enabled) onClick else null,
    )
}

/* =========================================================================
   ПОЛЬЗОВАТЕЛИ
   ========================================================================= */

/**
 * Поиск пользователей для админ-действий.
 *
 * Результаты приходят suspend-запросом с debounce: поиск по всей базе на
 * каждую клавишу недопустим. Карточка ведёт на [AdminUserDetailScreen],
 * где доступны привилегированные действия.
 */
@Composable
fun AdminUsersScreen(
    navigator: AdminNavigator,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val results by viewModel.userResults.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(events) {
        if (events != null) viewModel.consumeEvent()
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SilverTopBar(title = "Пользователи", onBack = navigator::back)

            if (!access.allowed) {
                AdminAccessLocked(modifier = Modifier.fillMaxSize())
                return
            }

            SearchField(
                query = searchState.query,
                onQueryChange = viewModel::onUserQueryChanged,
                placeholder = "Имя, @username или телефон",
                onClear = { viewModel.onUserQueryChanged("") },
                modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
            )

            SettingsRow(
                title = "Только с жалобами",
                subtitle = "Пользователи, на которых есть открытые кейсы",
                icon = Icons.Filled.Report,
                trailing = {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                if (searchState.onlyFlagged) ScTheme.danger else ScTheme.surface,
                            )
                            .clickable { viewModel.toggleOnlyFlagged() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (searchState.onlyFlagged) {
                            Box(
                                Modifier.size(9.dp).clip(CircleShape).background(Color.White),
                            )
                        }
                    }
                },
            )

            if (results.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Никого не найдено",
                    subtitle = "Введите имя, @username или номер телефона.\nМинимальная длина запроса — 2 символа.",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = ScSpacing.xxl),
                ) {
                    items(results, key = { it.id.raw }, contentType = { "user" }) { user ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { navigator.openUserDetail(user.id.raw) }
                                .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(user = user, size = ScAvatarSize.listItem)
                            Spacer(Modifier.width(ScSpacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = user.fullName,
                                    color = ScTheme.textPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = buildString {
                                        user.handle?.let { append(it) }
                                        user.phone?.let {
                                            if (isNotEmpty()) append(" · ")
                                            append(it)
                                        }
                                    }.ifBlank { user.id.raw },
                                    color = ScTheme.textTertiary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                )
                            }
                            if (user.flags.banned) {
                                Text("Бан", color = ScTheme.danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
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
   ЖАЛОБЫ
   ========================================================================= */

@Composable
fun AdminReportsScreen(
    navigator: AdminNavigator,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val reportFilter by viewModel.reportFilter.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(events) {
        if (events != null) viewModel.consumeEvent()
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SilverTopBar(
                title = "Жалобы",
                subtitle = "${reports.size} в выборке",
                onBack = navigator::back,
            )

            if (!access.can(AdminAction.RESOLVE_REPORT)) {
                AdminAccessLocked(modifier = Modifier.fillMaxSize())
                return
            }

            // Фильтр по статусу
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
            ) {
                items(STATUS_FILTERS, key = { it?.name ?: "ALL" }) { status ->
                    StatusChip(
                        label = status?.labelRu ?: "Все",
                        selected = status == reportFilter,
                        onClick = { viewModel.setReportStatus(status) },
                    )
                }
            }

            if (reports.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Report,
                    title = "Жалоб нет",
                    subtitle = "В этой выборке нет модерационных кейсов.",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = ScSpacing.xxl),
                ) {
                    items(reports, key = { it.id }, contentType = { "case" }) { case ->
                        ModerationCaseRow(
                            case = case,
                            onClick = { navigator.openProfile(case.reporterId.raw) },
                            onResolve = {
                                viewModel.resolveReport(
                                    case = case,
                                    status = ModerationStatus.RESOLVED,
                                    comment = "Решено из очереди жалоб",
                                )
                            },
                            modifier = Modifier.padding(
                                horizontal = ScSpacing.md,
                                vertical = ScSpacing.sm,
                            ),
                        )
                    }
                }
            }
        }

        events?.let { event ->
            AdminEventSnackbar(
                event = event,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

/** Значения фильтра жалоб: null означает «все статусы». */
private val STATUS_FILTERS = listOf(
    ModerationStatus.OPEN,
    ModerationStatus.IN_REVIEW,
    ModerationStatus.RESOLVED,
    ModerationStatus.REJECTED,
    null,
)

/* =========================================================================
   АУДИТ-ЛОГ
   ========================================================================= */

/**
 * Аудит-лог.
 *
 * Лог неизменяем и хранится на бэкенде: клиент только читает. Это главный
 * инструмент самопроверки администрации — любое действие (включая выдачу
 * себе прав, если бы она была возможна) оставило бы след.
 */
@Composable
fun AdminAuditScreen(
    navigator: AdminNavigator,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val entries by viewModel.auditLog.collectAsStateWithLifecycle()
    val auditFilter by viewModel.auditFilter.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SilverTopBar(
                title = "Аудит-лог",
                subtitle = "${entries.size} записей",
                onBack = navigator::back,
            )

            if (!access.allowed) {
                AdminAccessLocked(modifier = Modifier.fillMaxSize())
                return
            }

            // Фильтр по типу действия
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
            ) {
                item(key = "all") {
                    StatusChip(
                        label = "Все",
                        selected = auditFilter == null,
                        onClick = { viewModel.setAuditAction(null) },
                    )
                }
                items(AUDIT_ACTION_FILTERS, key = { it.name }) { action ->
                    StatusChip(
                        label = action.labelRu,
                        selected = auditFilter == action,
                        onClick = { viewModel.setAuditAction(action) },
                    )
                }
            }

            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.History,
                    title = "Записей нет",
                    subtitle = "Действий администрации в этой выборке не найдено.",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = ScSpacing.xxl),
                ) {
                    items(entries, key = { it.id }, contentType = { "audit" }) { entry ->
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
    }
}

/** Действия, по которым чаще всего фильтруют лог. */
private val AUDIT_ACTION_FILTERS = listOf(
    AdminAction.GRANT_VERIFIED,
    AdminAction.GRANT_PREMIUM,
    AdminAction.CREDIT_SILVER,
    AdminAction.DEBIT_SILVER,
    AdminAction.BAN_USER,
    AdminAction.BLOCK_USERNAME,
    AdminAction.BROADCAST,
)

/* =========================================================================
   РАССЫЛКА
   ========================================================================= */

/**
 * Рассылка сообщения.
 *
 * Аудитория выбирается явно, значение по умолчанию — [AdminBroadcastAudience.ACTIVE],
 * а не `ALL`: отправка всем — необратимое действие, и оно не должно
 * происходить из-за случайно не тронутого селектора.
 */
@Composable
fun AdminBroadcastScreen(
    navigator: AdminNavigator,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var audience by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(AdminBroadcastAudience.ACTIVE)
    }

    LaunchedEffect(events) {
        if (events != null) viewModel.consumeEvent()
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SilverTopBar(title = "Рассылка", onBack = navigator::back)

            if (!access.can(AdminAction.BROADCAST)) {
                AdminAccessLocked(modifier = Modifier.fillMaxWidth())
                return
            }

            Column(Modifier.padding(ScSpacing.md)) {
                com.silverchat.core.designsystem.component.SilverTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_BROADCAST_LENGTH) },
                    label = "Текст сообщения",
                    placeholder = "Что сообщить пользователям",
                    singleLine = false,
                    maxLength = MAX_BROADCAST_LENGTH,
                )
                Spacer(Modifier.height(ScSpacing.xs))
                Text(
                    text = "Осталось символов: ${MAX_BROADCAST_LENGTH - text.length}",
                    color = ScTheme.textTertiary,
                    fontSize = 11.sp,
                )

                Spacer(Modifier.height(ScSpacing.md))

                SettingsSection("Аудитория")
                AdminBroadcastAudience.entries.forEach { value ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(ScShapes.cardSmall)
                            .background(
                                if (value == audience) ScTheme.accentContainer else ScTheme.surfaceElevated,
                            )
                            .clickable { audience = value }
                            .padding(ScSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = value.titleRu,
                                color = if (value == audience) ScTheme.accent else ScTheme.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = value.subtitleRu,
                                color = ScTheme.textTertiary,
                                fontSize = 11.5.sp,
                            )
                        }
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (value == audience) ScTheme.accent else ScTheme.surface),
                        )
                    }
                    Spacer(Modifier.height(ScSpacing.xs))
                }

                Spacer(Modifier.height(ScSpacing.md))

                Text(
                    text = "Рассылка отправляется от имени @silver и приходит " +
                        "каждому получателю как служебное сообщение. Действие " +
                        "необратимо и записывается в аудит-лог.",
                    color = ScTheme.warning,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )

                Spacer(Modifier.height(ScSpacing.lg))

                SilverButton(
                    text = "Отправить рассылку",
                    onClick = { viewModel.broadcast(text, audience) },
                    enabled = text.isNotBlank(),
                    leadingIcon = Icons.Filled.Campaign,
                )
            }

            Spacer(Modifier.height(ScSpacing.xxl))
        }

        events?.let { event ->
            AdminEventSnackbar(
                event = event,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

private const val MAX_BROADCAST_LENGTH = 1000

val AdminBroadcastAudience.titleRu: String
    get() = when (this) {
        AdminBroadcastAudience.ALL -> "Все пользователи"
        AdminBroadcastAudience.ACTIVE -> "Активные за 30 дней"
        AdminBroadcastAudience.PREMIUM -> "Подписчики Premium"
        AdminBroadcastAudience.VERIFIED -> "Верифицированные"
        AdminBroadcastAudience.NON_PREMIUM -> "Без Premium"
    }

val AdminBroadcastAudience.subtitleRu: String
    get() = when (this) {
        AdminBroadcastAudience.ALL -> "Максимальный охват, включая неактивных"
        AdminBroadcastAudience.ACTIVE -> "Рекомендуется: доходят до тех, кто читает"
        AdminBroadcastAudience.PREMIUM -> "Для сообщений о подписке"
        AdminBroadcastAudience.VERIFIED -> "Только аккаунты с галочкой"
        AdminBroadcastAudience.NON_PREMIUM -> "Для промо-рассылок"
    }

/* =========================================================================
   ОБЩИЕ ЭЛЕМЕНТЫ
   ========================================================================= */

@Composable
private fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else ScTheme.textSecondary,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(ScShapes.chip)
            .background(if (selected) ScTheme.accent else ScTheme.surfaceGlass)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

/**
 * Снэкбар события админки.
 *
 * Формулировки действий важнее технических деталей: админ должен сразу
 * понимать, что именно произошло и с кем.
 */
@Composable
private fun AdminEventSnackbar(event: AdminEvent, modifier: Modifier = Modifier) {
    val message = when (event) {
        is AdminEvent.PrivilegeGranted -> "${event.userName}: ${event.action.labelRu}"
        is AdminEvent.UserBanned -> "${event.userName} заблокирован"
        is AdminEvent.UserUnbanned -> "${event.userName} разблокирован"
        is AdminEvent.UserRestricted -> "${event.userName} ограничен"
        is AdminEvent.SilverAdjusted -> buildString {
            append(event.userName)
            append(": ")
            append(if (event.amount >= 0) "+" else "−")
            append(NumberFormatter.silver(kotlin.math.abs(event.amount)))
            append(" · баланс ")
            append(NumberFormatter.silver(event.newBalance))
        }

        is AdminEvent.WalletReset -> "Кошелёк ${event.userName} сброшен"
        is AdminEvent.UsernameBlocked -> "@${event.username} изъят из оборота"
        is AdminEvent.PriceAdjusted -> "@${event.username}: цена ${NumberFormatter.silver(event.price)}"
        is AdminEvent.Refunded -> "Возврат по операции ${event.transactionId}"
        is AdminEvent.ReportResolved -> "Жалоба ${event.caseId} закрыта"
        AdminEvent.MessageDeleted -> "Сообщение удалено для всех"
        is AdminEvent.BroadcastSent -> "Рассылка отправлена: ${event.audience.titleRu}"
        is AdminEvent.Forbidden -> "Нет прав: ${event.action.labelRu}"
        is AdminEvent.Error -> event.message
    }

    InlineSnackbar(
        message = message,
        modifier = modifier.fillMaxWidth().padding(ScSpacing.md),
    )
}
