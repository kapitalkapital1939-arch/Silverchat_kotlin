package com.silverchat.feature.market.screen

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverTextField
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.market.ChipSmall
import com.silverchat.core.designsystem.component.market.rarityColor
import com.silverchat.core.designsystem.navigation.MarketNavigator
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.labelRu
import com.silverchat.feature.market.SellEvent
import com.silverchat.feature.market.SellUiState
import com.silverchat.feature.market.SellUsernameViewModel

/**
 * Выставление собственного юзернейма на продажу.
 *
 * Поток: ввод ника -> оценка (редкость + рекомендованная цена) ->
 * настройки лота -> публикация. Оценка обязательна перед публикацией:
 * без неё пользователь не знает рыночной стоимости, а маркет наполнялся бы
 * лотами с нереалистичными ценами.
 *
 * Предупреждение о необратимости показывается всегда, а не только при
 * подтверждении: после продажи все ссылки `silverchat://u/{username}`
 * начинают вести на нового владельца.
 */
@Composable
fun SellUsernameScreen(
    navigator: MarketNavigator,
    modifier: Modifier = Modifier,
    viewModel: SellUsernameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(events) {
        // После публикации переходим к предложениям созданного лота
        val created = events
        if (created is SellEvent.Created) {
            viewModel.consumeEvent()
            navigator.openOffers(created.listing.id.raw)
        }
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = ScSpacing.xxl),
        ) {
            SilverTopBar(
                title = "Продать юзернейм",
                subtitle = "Оценка и публикация лота",
                onBack = navigator::back,
            )

            // ── Юзернейм и оценка ────────────────────────────────────────
            Column(Modifier.padding(horizontal = ScSpacing.md)) {
                SilverTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChanged,
                    label = "Юзернейм",
                    placeholder = "username",
                    leadingIcon = Icons.Filled.Search,
                )
                if (state.suggestion == null) {
                    Spacer(Modifier.height(ScSpacing.xs))
                    Text(
                        text = "Латиница, цифры и «_», от 4 символов",
                        color = ScTheme.textTertiary,
                        fontSize = 11.sp,
                    )
                }

                Spacer(Modifier.height(ScSpacing.sm))

                SilverButton(
                    text = when {
                        state.isEvaluating -> "Оцениваем…"
                        state.isEvaluated -> "Пересчитать"
                        else -> "Оценить юзернейм"
                    },
                    onClick = viewModel::evaluate,
                    enabled = state.canEvaluate,
                    loading = state.isEvaluating,
                    variant = SilverButtonVariant.SECONDARY,
                    leadingIcon = Icons.Filled.Search,
                )

                state.errorText?.let { error ->
                    Spacer(Modifier.height(ScSpacing.sm))
                    InlineSnackbar(message = error, modifier = Modifier.fillMaxWidth())
                }
            }

            // ── Результат оценки ─────────────────────────────────────────
            state.suggestion?.let { suggestion ->
                Column(Modifier.padding(ScSpacing.md)) {
                    EvaluationCard(state = state)

                    if (!suggestion.isOwn) {
                        Spacer(Modifier.height(ScSpacing.sm))
                        WarningBanner(
                            text = "Этот юзернейм вам не принадлежит — выставить его нельзя.",
                            tint = ScTheme.danger,
                            icon = Icons.Filled.Warning,
                        )
                    } else {
                        Spacer(Modifier.height(ScSpacing.sm))
                        WarningBanner(
                            text = "После продажи юзернейм перейдёт покупателю, " +
                                "а все ссылки на него начнут вести на нового владельца. " +
                                "Действие необратимо.",
                            tint = ScTheme.warning,
                            icon = Icons.Filled.Info,
                        )
                    }
                }
            }

            // ── Настройки лота ───────────────────────────────────────────
            if (state.isEvaluated) {
                Column(Modifier.padding(horizontal = ScSpacing.md)) {
                    Spacer(Modifier.height(ScSpacing.md))

                    SilverTextField(
                        value = state.price.takeIf { it > 0 }?.toString().orEmpty(),
                        onValueChange = { raw -> viewModel.onPriceChanged(raw.toLongOrNull() ?: 0L) },
                        label = "Цена в сильверах",
                        placeholder = NumberFormatter.silver(state.suggestion?.suggestedPrice ?: 0L),
                        keyboardType = KeyboardType.Number,
                    )

                    // Быстрые отклонения от рекомендованной цены
                    state.suggestion?.suggestedPrice?.let { suggested ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = ScSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
                        ) {
                            PricePreset("−20%", (suggested * 0.8).toLong(), viewModel::onPriceChanged)
                            PricePreset("Рекоменд.", suggested, viewModel::onPriceChanged)
                            PricePreset("+20%", (suggested * 1.2).toLong(), viewModel::onPriceChanged)
                        }
                    }

                    Spacer(Modifier.height(ScSpacing.md))

                    ToggleRow(
                        title = "Принимать предложения",
                        subtitle = "Покупатели смогут торговаться ниже цены",
                        checked = state.acceptOffers,
                        onToggle = viewModel::toggleAcceptOffers,
                    )

                    if (state.acceptOffers) {
                        Spacer(Modifier.height(ScSpacing.sm))
                        SilverTextField(
                            value = state.minOffer?.takeIf { it > 0 }?.toString().orEmpty(),
                            onValueChange = { raw -> viewModel.onMinOfferChanged(raw.toLongOrNull()) },
                            label = "Минимальная ставка",
                            placeholder = NumberFormatter.silver((state.price * 0.5).toLong()),
                            keyboardType = KeyboardType.Number,
                        )
                        Spacer(Modifier.height(ScSpacing.xs))
                        Text(
                            text = "Ниже этой суммы предложения отклоняются автоматически",
                            color = ScTheme.textTertiary,
                            fontSize = 11.sp,
                        )
                    }

                    Spacer(Modifier.height(ScSpacing.sm))

                    ToggleRow(
                        title = "Аукцион",
                        subtitle = "Ник уходит тому, кто предложит больше к концу срока",
                        checked = state.auction,
                        onToggle = viewModel::toggleAuction,
                    )

                    if (state.auction) {
                        Spacer(Modifier.height(ScSpacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
                            AUCTION_DURATIONS.forEach { hours ->
                                val selected = state.durationHours == hours
                                Text(
                                    text = "${hours} ч",
                                    color = if (selected) Color.White else ScTheme.textSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(ScShapes.chip)
                                        .background(if (selected) ScTheme.accent else ScTheme.surfaceGlass)
                                        .clickable { viewModel.onDurationChanged(hours) }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(ScSpacing.lg))

                    SilverButton(
                        text = if (state.isSubmitting) "Публикуем…" else "Выставить на продажу",
                        onClick = viewModel::submit,
                        enabled = state.canSubmit,
                        loading = state.isSubmitting,
                    )

                    Spacer(Modifier.height(ScSpacing.sm))

                    Text(
                        text = "Комиссия маркета удерживается сервером при завершении сделки.",
                        color = ScTheme.textTertiary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Длительности аукциона в часах. */
private val AUCTION_DURATIONS = listOf(24, 48, 72, 168)

/* =========================================================================
   КАРТОЧКА ОЦЕНКИ
   ========================================================================= */

@Composable
private fun EvaluationCard(state: SellUiState) {
    val suggestion = state.suggestion ?: return
    val tint = rarityColor(suggestion.rarity)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(ScShapes.cardLarge)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("@", color = tint, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(ScSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "@${suggestion.username}",
                    color = ScTheme.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                ChipSmall(suggestion.rarity.labelRu, tint)
            }
        }

        Spacer(Modifier.height(ScSpacing.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Рекомендованная цена",
                color = ScTheme.textTertiary,
                fontSize = 12.5.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = NumberFormatter.silver(suggestion.suggestedPrice),
                color = ScTheme.silver,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/* =========================================================================
   ПРЕСЕТЫ ЦЕНЫ
   ========================================================================= */

@Composable
private fun PricePreset(label: String, price: Long, onSelect: (Long) -> Unit) {
    Text(
        text = label,
        color = ScTheme.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(ScShapes.chip)
            .background(ScTheme.accentContainer)
            .clickable(enabled = price > 0) { onSelect(price) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/* =========================================================================
   ПЕРЕКЛЮЧАТЕЛЬ
   ========================================================================= */

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .clickable(onClick = onToggle)
            .padding(ScSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ScTheme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                color = ScTheme.textTertiary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.width(ScSpacing.sm))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) ScTheme.success else ScTheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/* =========================================================================
   ПРЕДУПРЕЖДЕНИЕ
   ========================================================================= */

@Composable
private fun WarningBanner(
    text: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ScShapes.card)
            .background(tint.copy(alpha = 0.12f))
            .padding(ScSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(ScSpacing.sm))
        Text(
            text = text,
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 17.sp,
        )
    }
}
