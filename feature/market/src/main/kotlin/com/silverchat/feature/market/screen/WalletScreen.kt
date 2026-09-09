package com.silverchat.feature.market.screen

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.PaginationLoader
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.SilverTextField
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.market.LedgerEntryRow
import com.silverchat.core.designsystem.component.market.MarketStatTile
import com.silverchat.core.designsystem.component.market.SilverBalanceHeader
import com.silverchat.core.designsystem.navigation.MarketNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.User
import com.silverchat.feature.market.LedgerFilter
import com.silverchat.feature.market.WalletViewModel

/**
 * Кошелёк: баланс, журнал операций и переводы.
 *
 * Журнал — не «история для галочки», а инструмент поддержки: когда
 * пользователь спрашивает «куда делись мои сильверы», ответ ищется здесь.
 * Поэтому каждая запись показывает причину на русском ([com.silverchat.feature.market.titleRu])
 * и баланс после операции.
 *
 * Перевод встроен в кошелёк, а не вынесен в отдельный экран: это короткое
 * действие из двух шагов, и отдельный маршрут усложнил бы навигацию.
 */
@Composable
fun WalletScreen(
    navigator: MarketNavigator,
    modifier: Modifier = Modifier,
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recipients by viewModel.recipientResults.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(events) {
        if (events != null) viewModel.consumeEvent()
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SilverTopBar(
                title = "Кошелёк",
                subtitle = if (state.hasFrozen) {
                    "В escrow: ${NumberFormatter.silver(state.frozen)}"
                } else {
                    null
                },
                onBack = navigator::back,
                actions = {
                    if (!state.transfer.mode) {
                        SilverIconButton(
                            icon = Icons.Filled.Send,
                            contentDescription = "Перевести сильверы",
                            onClick = viewModel::openTransfer,
                            tint = ScTheme.accent,
                        )
                    } else {
                        SilverIconButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "Отменить перевод",
                            onClick = viewModel::closeTransfer,
                            tint = ScTheme.textPrimary,
                        )
                    }
                },
            )

            if (state.transfer.mode) {
                TransferForm(
                    state = state,
                    recipients = recipients,
                    viewModel = viewModel,
                )
            } else {
                LedgerContent(state = state, viewModel = viewModel, navigator = navigator)
            }
        }

        EventMessage(event = events, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/* =========================================================================
   ЖУРНАЛ
   ========================================================================= */

@Composable
private fun LedgerContent(
    state: com.silverchat.feature.market.WalletUiState,
    viewModel: WalletViewModel,
    navigator: MarketNavigator,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ScSpacing.xxl),
    ) {
        item(key = "balance", contentType = "balance") {
            SilverBalanceHeader(
                wallet = state.wallet,
                streak = state.streak,
                onTopUp = navigator::openPremium,
                onClaimStreak = viewModel::claimStreak,
                modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
            )
        }

        item(key = "stats", contentType = "stats") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
            ) {
                MarketStatTile(
                    title = "Заработано",
                    value = NumberFormatter.compact(state.earnedTotal),
                    modifier = Modifier.weight(1f),
                )
                MarketStatTile(
                    title = "Потрачено",
                    value = NumberFormatter.compact(state.spentTotal),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Фильтр журнала
        item(key = "filter", contentType = "filter") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
            ) {
                items(LedgerFilter.entries.toList(), key = { it.name }) { filter ->
                    val selected = filter == state.filter
                    Text(
                        text = filter.titleRu,
                        color = if (selected) Color.White else ScTheme.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(ScShapes.chip)
                            .background(if (selected) ScTheme.accent else ScTheme.surfaceGlass)
                            .clickable { viewModel.onFilterChanged(filter) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }

        item(key = "ledger_hdr", contentType = "header") {
            Text(
                text = "Операции · ${state.ledger.size}",
                color = ScTheme.textTertiary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.xs),
            )
        }

        if (state.ledger.isEmpty()) {
            item(key = "ledger_empty", contentType = "empty") {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Операций пока нет",
                    subtitle = "Здесь появятся начисления за стрики,\nпокупки и продажи юзернеймов.",
                )
            }
        }

        items(state.ledger, key = { it.id }, contentType = { "ledger" }) { entry ->
            LedgerEntryRow(
                entry = entry,
                modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.xs),
            )
        }

        /* Футер догрузки истории.
         *
         * Сигналом служит сама композиция элемента: он создаётся, когда
         * пользователь доскроллил до конца списка. Ключ `state.ledger.size`
         * перезапускает эффект после каждой загруженной страницы — без него
         * `LaunchedEffect(Unit)` сработал бы один раз, и непрерывная
         * прокрутка остановилась бы на второй странице.
         *
         * `viewModel.onLoadMoreLedger()` сама отбрасывает повторный вызов во
         * время запроса, поэтому гонки из-за рекомпозиций нет. */
        if (state.ledger.isNotEmpty() && state.ledgerPaging.hasMore) {
            item(key = "ledger_more", contentType = "more") {
                LaunchedEffect(state.ledger.size) { viewModel.onLoadMoreLedger() }
                if (state.ledgerPaging.isLoadingMore) {
                    PaginationLoader()
                }
            }
        }
    }
}

/* =========================================================================
   ПЕРЕВОД
   ========================================================================= */

/**
 * Форма перевода сильверов.
 *
 * Получатель выбирается поиском по @username: переводить «в никуда»
 * по ID невозможно, а номер телефона в SilverChat не является публичным
 * идентификатором.
 */
@Composable
private fun TransferForm(
    state: com.silverchat.feature.market.WalletUiState,
    recipients: List<User>,
    viewModel: WalletViewModel,
) {
    val form = state.transfer

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = ScSpacing.md),
    ) {
        Text(
            text = "Перевод сильверов",
            color = ScTheme.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = ScSpacing.sm),
        )
        Text(
            text = "Доступно: ${NumberFormatter.silver(state.balance)}",
            color = ScTheme.textTertiary,
            fontSize = 12.5.sp,
        )

        Spacer(Modifier.height(ScSpacing.md))

        // Получатель: выбранная карточка или поиск
        if (form.recipient != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ScShapes.card)
                    .background(ScTheme.surfaceElevated)
                    .padding(ScSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(user = form.recipient, size = ScAvatarSize.member)
                Spacer(Modifier.width(ScSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = form.recipient.fullName,
                        color = ScTheme.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    form.recipient.handle?.let {
                        Text(it, color = ScTheme.accent, fontSize = 12.sp)
                    }
                }
                SilverIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Убрать получателя",
                    onClick = viewModel::clearRecipient,
                    size = 32,
                )
            }
        } else {
            SilverTextField(
                value = state.recipientQuery,
                onValueChange = viewModel::onRecipientQueryChanged,
                label = "Получатель",
                placeholder = "Имя или @username",
                leadingIcon = Icons.Filled.Search,
            )

            if (recipients.isNotEmpty()) {
                Spacer(Modifier.height(ScSpacing.sm))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(ScShapes.card)
                        .background(ScTheme.surfaceElevated),
                ) {
                    recipients.take(MAX_RECIPIENT_SUGGESTIONS).forEach { user ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectRecipient(user) }
                                .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(user = user, size = ScAvatarSize.small)
                            Spacer(Modifier.width(ScSpacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = user.fullName,
                                    color = ScTheme.textPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                )
                                user.handle?.let {
                                    Text(it, color = ScTheme.textTertiary, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(ScSpacing.md))

        SilverTextField(
            value = form.amount?.toString().orEmpty(),
            onValueChange = { raw -> viewModel.onAmountChanged(raw.toLongOrNull()) },
            label = "Сумма",
            placeholder = "0",
            keyboardType = KeyboardType.Number,
        )

        // Быстрые суммы: перевод «всего баланса» — частый сценарий
        Row(
            Modifier.fillMaxWidth().padding(top = ScSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
        ) {
            QuickAmount("25%", state.balance / 4) { viewModel.onAmountChanged(it) }
            QuickAmount("50%", state.balance / 2) { viewModel.onAmountChanged(it) }
            QuickAmount("Всё", state.balance) { viewModel.onAmountChanged(it) }
        }

        Spacer(Modifier.height(ScSpacing.md))

        SilverTextField(
            value = form.comment,
            onValueChange = viewModel::onCommentChanged,
            label = "Комментарий (необязательно)",
            placeholder = "За что перевод",
            maxLength = MAX_COMMENT_LENGTH,
            singleLine = false,
        )

        Spacer(Modifier.weight(1f))

        SilverButton(
            text = if (form.isProcessing) "Отправляем…" else "Перевести",
            onClick = viewModel::sendTransfer,
            enabled = form.recipient != null && (form.amount ?: 0L) > 0 && !form.isProcessing,
            loading = form.isProcessing,
            modifier = Modifier.padding(bottom = ScSpacing.md),
        )
        SilverButton(
            text = "Отмена",
            onClick = viewModel::closeTransfer,
            variant = SilverButtonVariant.TEXT,
            modifier = Modifier.padding(bottom = ScSpacing.md),
        )
    }
}

private const val MAX_RECIPIENT_SUGGESTIONS = 6
private const val MAX_COMMENT_LENGTH = 140

@Composable
private fun QuickAmount(label: String, amount: Long, onSelect: (Long) -> Unit) {
    Text(
        text = label,
        color = ScTheme.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(ScShapes.chip)
            .background(ScTheme.accentContainer)
            .clickable(enabled = amount > 0) { onSelect(amount) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
