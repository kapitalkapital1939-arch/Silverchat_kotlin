package com.silverchat.feature.admin.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Replay
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.common.format.UsernameFormatter
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.SettingsSection
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverTextField
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.admin.AdminAccessLocked
import com.silverchat.core.designsystem.component.admin.AdminConfirmSheet
import com.silverchat.core.designsystem.navigation.AdminNavigator
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.AdminAction
import com.silverchat.core.model.ListingId
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.UsernameRarity
import com.silverchat.core.model.labelRu
import com.silverchat.feature.admin.AdminEvent
import com.silverchat.feature.admin.AdminViewModel
import com.silverchat.feature.admin.PendingAction

/**
 * Инструменты маркета в админ-панели.
 *
 * Три независимых действия: изъятие юзернейма из оборота, корректировка цены
 * лота и возврат по операции. Все требуют роль MARKET_MANAGER и выше
 * ([AdminAction.BLOCK_USERNAME], [AdminAction.ADJUST_LISTING_PRICE],
 * [AdminAction.REFUND_TRANSACTION]).
 *
 * Список активных лотов показывается здесь же: искать лот по ID вручную
 * неудобно, а маркет-менеджер почти всегда приходит из конкретного лота.
 */
@Composable
fun AdminMarketToolsScreen(
    navigator: AdminNavigator,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val confirm by viewModel.confirmState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    // Локальная форма: поля ввода не связаны с наблюдаемым состоянием,
    // поэтому держать их во ViewModel смысла нет
    var blockUsername by remember { mutableStateOf("") }
    var refundTxId by remember { mutableStateOf("") }

    LaunchedEffect(events) {
        if (events != null) viewModel.consumeEvent()
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        if (!access.allowed) {
            AdminAccessLocked(modifier = Modifier.fillMaxSize())
            return
        }

        val canBlock = access.can(AdminAction.BLOCK_USERNAME)
        val canAdjust = access.can(AdminAction.ADJUST_LISTING_PRICE)
        val canRefund = access.can(AdminAction.REFUND_TRANSACTION)

        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SilverTopBar(title = "Инструменты маркета", onBack = navigator::back)

            if (!canBlock && !canAdjust && !canRefund) {
                EmptyState(
                    icon = Icons.Filled.LocalOffer,
                    title = "Нет прав на операции маркета",
                    subtitle = "Требуется роль MARKET_MANAGER, ADMIN или OWNER.",
                    actionLabel = "Назад",
                    onAction = navigator::back,
                    modifier = Modifier.fillMaxWidth(),
                )
                return
            }

            // ── Изъятие юзернейма ────────────────────────────────────────
            if (canBlock) {
                SettingsSection("Изъять юзернейм из оборота")
                Column(Modifier.padding(horizontal = ScSpacing.md)) {
                    SilverTextField(
                        value = blockUsername,
                        onValueChange = { raw ->
                            blockUsername = UsernameFormatter.strip(raw).orEmpty()
                        },
                        label = "@username",
                        placeholder = "username",
                    )
                    Spacer(Modifier.height(ScSpacing.xs))
                    Text(
                        text = "Юзернейм будет заблокирован навсегда: он исчезнет " +
                            "из маркета и не сможет быть куплен или зарегистрирован.",
                        color = ScTheme.textTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(ScSpacing.sm))
                    SilverButton(
                        text = "Изъять юзернейм",
                        onClick = {
                            viewModel.requestAction(
                                PendingAction.BlockUsername(blockUsername.trim()),
                            )
                        },
                        enabled = blockUsername.trim().length >= UsernameFormatter.MIN_LENGTH,
                        variant = SilverButtonVariant.SECONDARY,
                        leadingIcon = Icons.Filled.Block,
                    )
                }
            }

            // ── Возврат по операции ──────────────────────────────────────
            if (canRefund) {
                SettingsSection("Возврат по операции")
                Column(Modifier.padding(horizontal = ScSpacing.md)) {
                    SilverTextField(
                        value = refundTxId,
                        onValueChange = { refundTxId = it },
                        label = "ID транзакции",
                        placeholder = "tx_…",
                    )
                    Spacer(Modifier.height(ScSpacing.xs))
                    Text(
                        text = "ID берётся из журнала кошелька пользователя. " +
                            "Сильверы вернутся плательщику, юзернейм — продавцу.",
                        color = ScTheme.textTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(ScSpacing.sm))
                    SilverButton(
                        text = "Оформить возврат",
                        onClick = {
                            viewModel.requestAction(PendingAction.Refund(refundTxId.trim()))
                        },
                        enabled = refundTxId.isNotBlank(),
                        variant = SilverButtonVariant.SECONDARY,
                        leadingIcon = Icons.Filled.Replay,
                    )
                }
            }

            // ── Корректировка цены лота ─────────────────────────────────
            if (canAdjust) {
                SettingsSection("Изменить цену лота")
                ManualAdjustForm(
                    onAdjust = { listing, price ->
                        viewModel.requestAction(
                            PendingAction.AdjustPrice(
                                listingId = ListingId(listing.id.raw),
                                username = listing.username,
                                priceSilver = price,
                            ),
                        )
                    },
                )
            }

            Spacer(Modifier.height(ScSpacing.xxl))
        }

        // ── Диалог подтверждения ─────────────────────────────────────────
        confirm?.let { pending ->
            Box(
                Modifier.fillMaxSize().background(ScTheme.background.copy(alpha = 0.7f)),
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
                        title = pending.marketTitle(),
                        description = pending.marketDescription(),
                        reason = pending.reason,
                        onReasonChange = viewModel::onConfirmReasonChanged,
                        confirmLabel = "Подтвердить",
                        destructive = pending is PendingAction.BlockUsername,
                        requireReason = true,
                        onConfirm = viewModel::confirmAction,
                        onDismiss = viewModel::dismissConfirm,
                    )
                }
            }
        }

        events?.let { event ->
            InlineSnackbar(
                message = when (event) {
                    is AdminEvent.UsernameBlocked -> "@${event.username} изъят из оборота"
                    is AdminEvent.PriceAdjusted ->
                        "@${event.username}: цена ${NumberFormatter.silver(event.price)}"

                    is AdminEvent.Refunded -> "Возврат по операции ${event.transactionId}"
                    is AdminEvent.Forbidden -> "Нет прав: ${event.action.labelRu}"
                    is AdminEvent.Error -> event.message
                    else -> return@let
                },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(ScSpacing.md),
            )
        }
    }
}

/* =========================================================================
   СПИСОК ЛОТОВ
   ========================================================================= */

/**
 * Ручная форма корректировки цены.
 *
 * Используется, когда список лотов не загружен: админ вводит ID лота
 * и новую цену. Действие то же — `ADJUST_LISTING_PRICE`.
 */
@Composable
private fun ManualAdjustForm(onAdjust: (UsernameListing, Long) -> Unit) {
    var listingId by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    Column(Modifier.padding(horizontal = ScSpacing.md)) {
        SilverTextField(
            value = listingId,
            onValueChange = { listingId = it },
            label = "ID лота",
            placeholder = "listing_…",
        )
        Spacer(Modifier.height(ScSpacing.sm))
        SilverTextField(
            value = price,
            onValueChange = { raw -> price = raw.filter { it.isDigit() } },
            label = "Новая цена в сильверах",
            placeholder = "0",
            keyboardType = KeyboardType.Number,
        )
        Spacer(Modifier.height(ScSpacing.sm))

        // Синтетический листинг нужен только чтобы передать ID и имя в диалог;
        // сервер принимает listingId и цену, остальные поля не используются
        SilverButton(
            text = "Изменить цену лота",
            onClick = {
                val newPrice = price.toLongOrNull() ?: 0L
                val synthetic = UsernameListing(
                    id = ListingId(listingId.trim()),
                    // Имя в синтетическом лоте неизвестно: показываем ID,
                    // чтобы в диалоге подтверждения было видно, что правим
                    username = listingId.trim(),
                    priceSilver = newPrice,
                    rarity = UsernameRarity.COMMON,
                )
                onAdjust(synthetic, newPrice)
            },
            enabled = listingId.isNotBlank() && (price.toLongOrNull() ?: 0L) > 0,
            variant = SilverButtonVariant.SECONDARY,
            leadingIcon = Icons.Filled.LocalOffer,
        )
        Spacer(Modifier.height(ScSpacing.xs))
        Text(
            text = "ID лота можно взять в карточке юзернейма в маркете.",
            color = ScTheme.textTertiary,
            fontSize = 11.sp,
        )
    }
}

/* =========================================================================
   ТЕКСТЫ ДИАЛОГА
   ========================================================================= */

private fun PendingAction.marketTitle(): String = when (this) {
    is PendingAction.BlockUsername -> "Изъять @${username} из оборота"
    is PendingAction.AdjustPrice -> "Изменить цену @${username}"
    is PendingAction.Refund -> "Возврат по операции"
    else -> "Действие администрации"
}

private fun PendingAction.marketDescription(): String = when (this) {
    is PendingAction.BlockUsername ->
        "Юзернейм @$username будет заблокирован навсегда и исчезнет из маркета."

    is PendingAction.AdjustPrice ->
        "Новая цена лота: ${NumberFormatter.silver(priceSilver)} сильверов."

    is PendingAction.Refund ->
        "Операция $transactionId будет отменена: сильверы вернутся плательщику, " +
            "юзернейм — продавцу."

    else -> "Действие будет записано в аудит-лог."
}
