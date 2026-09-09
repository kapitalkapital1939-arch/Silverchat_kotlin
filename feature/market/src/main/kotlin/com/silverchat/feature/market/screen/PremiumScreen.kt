package com.silverchat.feature.market.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.market.PremiumTierCard
import com.silverchat.core.designsystem.component.market.perkIcon
import com.silverchat.core.designsystem.navigation.MarketNavigator
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.PremiumTier
import com.silverchat.core.model.labelRu
import com.silverchat.feature.market.PremiumEvent
import com.silverchat.feature.market.PremiumViewModel
import com.silverchat.feature.market.periodRu

/**
 * Экран SilverChat Premium.
 *
 * Структура: статус подписки -> тарифы -> преимущества -> покупка.
 * Преимущества перечислены отдельным блоком, а не только внутри карточек
 * тарифов: пользователь сначала понимает «зачем», затем выбирает «на сколько».
 *
 * Подтверждение покупки обязательно — Premium стоит сотни сильверов,
 * а случайный тап по карточке тарифа не должен списывать баланс.
 */
@Composable
fun PremiumScreen(
    navigator: MarketNavigator,
    modifier: Modifier = Modifier,
    viewModel: PremiumViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(events) {
        if (events is PremiumEvent.Purchased) navigator.back()
        if (events != null) viewModel.consumeEvent()
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SilverTopBar(
                title = "SilverChat Premium",
                subtitle = if (state.isActive) "Подписка активна" else "Расширенные возможности",
                onBack = navigator::back,
                actions = {
                    if (state.isActive && !state.isLifetime) {
                        SilverIconButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "Отменить автопродление",
                            onClick = viewModel::cancelAutoRenew,
                            tint = ScTheme.textPrimary,
                        )
                    }
                },
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = ScSpacing.xxl),
            ) {
                // ── Текущий статус ───────────────────────────────────────
                item(key = "status", contentType = "status") {
                    StatusBlock(state = state)
                }

                // ── Тарифы ───────────────────────────────────────────────
                if (state.isEmpty) {
                    item(key = "tiers_empty", contentType = "empty") {
                        Text(
                            text = "Тарифы загружаются…",
                            color = ScTheme.textTertiary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(ScSpacing.xl),
                        )
                    }
                }

                items(state.tiers, key = { it.id.raw }, contentType = { "tier" }) { tier ->
                    PremiumTierCard(
                        tier = tier,
                        selected = tier.id == state.selectedTierId,
                        onSelect = { viewModel.selectTier(tier.id) },
                        modifier = Modifier.padding(
                            horizontal = ScSpacing.md,
                            vertical = ScSpacing.sm,
                        ),
                    )
                }

                // ── Преимущества ─────────────────────────────────────────
                item(key = "perks_hdr", contentType = "header") {
                    Text(
                        text = "Что входит в Premium",
                        color = ScTheme.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(ScSpacing.md),
                    )
                }

                // Состав перков берём из выбранного тарифа: сервер управляет
                // наполнением, и зашивать список в клиент нельзя
                val perks = state.selectedTier?.perks.orEmpty()
                items(perks, key = { it.code.name }, contentType = { "perk" }) { perk ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ScTheme.premium.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = perkIcon(perk.code),
                                contentDescription = null,
                                tint = ScTheme.premium,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(ScSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = perk.title.ifBlank { perk.code.labelRu },
                                color = ScTheme.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = perk.description,
                                color = ScTheme.textTertiary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                // ── Баланс и покупка ─────────────────────────────────────
                item(key = "purchase", contentType = "purchase") {
                    PurchaseBlock(state = state, viewModel = viewModel)
                }
            }
        }

        // Диалог подтверждения — поверх контента
        if (state.showConfirm) {
            state.selectedTier?.let { tier ->
                PurchaseConfirm(
                    tier = tier,
                    balance = state.balance,
                    onConfirm = viewModel::confirmPurchase,
                    onDismiss = viewModel::dismissConfirm,
                )
            }
        }

        PremiumEventMessage(event = events, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/* =========================================================================
   СТАТУС
   ========================================================================= */

@Composable
private fun StatusBlock(state: com.silverchat.feature.market.PremiumUiState) {
    val active = state.isActive

    Column(
        Modifier
            .fillMaxWidth()
            .padding(ScSpacing.md)
            .clip(ScShapes.cardLarge)
            .background(
                if (active) {
                    Brush.horizontalGradient(
                        listOf(ScTheme.premium.copy(alpha = 0.28f), ScTheme.premium.copy(alpha = 0.10f)),
                    )
                } else {
                    Brush.horizontalGradient(listOf(ScTheme.surfaceElevated, ScTheme.surfaceGlass))
                },
            )
            .padding(ScSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = if (active) ScTheme.premium else ScTheme.textTertiary,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(ScSpacing.sm))
        Text(
            text = when {
                state.isLifetime -> "Premium навсегда"
                active -> "Premium активен"
                else -> "Premium не оформлен"
            },
            color = ScTheme.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        when {
            state.isLifetime -> Text(
                text = "Выдано администрацией @silver",
                color = ScTheme.textTertiary,
                fontSize = 12.sp,
            )

            active -> Text(
                text = "Действует до ${state.expiresAt?.let { TimeFormatter.full(it) } ?: "—"}",
                color = ScTheme.textSecondary,
                fontSize = 12.5.sp,
            )

            else -> Text(
                text = "Анимированные аватары, HD-звонки, скидка в маркете\nи скрытный просмотр сторис",
                color = ScTheme.textTertiary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/* =========================================================================
   ПОКУПКА
   ========================================================================= */

@Composable
private fun PurchaseBlock(
    state: com.silverchat.feature.market.PremiumUiState,
    viewModel: PremiumViewModel,
) {
    val tier = state.selectedTier

    Column(
        Modifier
            .fillMaxWidth()
            .padding(ScSpacing.md)
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Баланс", color = ScTheme.textTertiary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                text = NumberFormatter.silver(state.balance),
                color = ScTheme.silver,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        tier?.let {
            Spacer(Modifier.height(ScSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(it.title, color = ScTheme.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = it.periodRu(),
                        color = ScTheme.textTertiary,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = NumberFormatter.silver(it.priceSilver),
                    color = ScTheme.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (it.discountPercent > 0) {
                Text(
                    text = "Выгода ${it.discountPercent}%",
                    color = ScTheme.success,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(ScSpacing.md))

        SilverButton(
            text = when {
                state.isProcessing -> "Обрабатываем…"
                state.isLifetime -> "У вас пожизненный Premium"
                tier == null -> "Выберите тариф"
                state.canPurchase -> "Оформить за ${NumberFormatter.silver(tier.priceSilver)}"
                else -> "Недостаточно сильверов"
            },
            onClick = viewModel::requestPurchase,
            enabled = state.canPurchase && !state.isLifetime,
            loading = state.isProcessing,
        )

        if (tier != null && !state.canPurchase && !state.isLifetime) {
            Spacer(Modifier.height(ScSpacing.sm))
            Text(
                text = "Не хватает ${NumberFormatter.silver(tier.priceSilver - state.balance)} сильверов. " +
                    "Заработать можно стриками и продажей юзернеймов.",
                color = ScTheme.textTertiary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/* =========================================================================
   ДИАЛОГ ПОДТВЕРЖДЕНИЯ
   ========================================================================= */

@Composable
private fun PurchaseConfirm(
    tier: PremiumTier,
    balance: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val remaining = balance - tier.priceSilver

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(ScSpacing.xl)
                .clip(ScShapes.dialog)
                .background(ScTheme.surfaceElevated)
                .padding(ScSpacing.lg),
        ) {
            Text(
                text = "Оформить Premium?",
                color = ScTheme.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(ScSpacing.md))

            ConfirmLine("Тариф", tier.title)
            ConfirmLine("Период", tier.periodRu())
            ConfirmLine("Цена", NumberFormatter.silver(tier.priceSilver))
            ConfirmLine("Останется", NumberFormatter.silver(remaining))

            Spacer(Modifier.height(ScSpacing.lg))

            Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
                SilverButton(
                    text = "Отмена",
                    onClick = onDismiss,
                    variant = SilverButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                )
                SilverButton(
                    text = "Купить",
                    onClick = onConfirm,
                    enabled = remaining >= 0,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ConfirmLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = ScTheme.textTertiary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = ScTheme.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PremiumEventMessage(event: PremiumEvent?, modifier: Modifier = Modifier) {
    val message = when (event) {
        is PremiumEvent.Purchased -> "Premium «${event.tierTitle}» оформлен"
        is PremiumEvent.InsufficientFunds ->
            "Не хватает ${NumberFormatter.silver(event.required - event.available)} сильверов"

        PremiumEvent.AutoRenewCancelled -> "Автопродление отключено"
        is PremiumEvent.Error -> event.message
        null -> null
    } ?: return

    InlineSnackbar(message = message, modifier = modifier.fillMaxWidth().padding(ScSpacing.md))
}
