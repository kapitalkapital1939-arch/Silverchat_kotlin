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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.SilverTextField
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.market.ChipSmall
import com.silverchat.core.designsystem.component.market.OfferRow
import com.silverchat.core.designsystem.component.market.rarityColor
import com.silverchat.core.designsystem.navigation.MarketNavigator
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.PricePoint
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.labelRu
import com.silverchat.feature.market.ListingDetailViewModel
import com.silverchat.feature.market.MarketEvent

/**
 * Карточка лота юзернейма.
 *
 * Один экран с двумя режимами ([showOffers]): детали и список встречных
 * предложений. Данные общие — лот, цена, история цены и офферы, поэтому
 * разделение на два экрана означало бы две независимые подписки на одну
 * сущность и рассинхрон при изменении цены.
 *
 * История цены показывается списком, а не графиком: у коротких лотов
 * две-три точки, и линейный график на них нечитаем. Список же работает
 * при любом числе записей.
 */
@Composable
fun ListingDetailScreen(
    navigator: MarketNavigator,
    modifier: Modifier = Modifier,
    showOffers: Boolean = false,
    onCopyLink: (String) -> Unit = {},
    viewModel: ListingDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    // Закрытие экрана после успешной покупки или снятия лота
    LaunchedEffect(events) {
        when (val event = events) {
            is MarketEvent.Purchased -> navigator.back()
            is MarketEvent.ListingCancelled -> navigator.back()
            else -> Unit
        }
        if (events != null) viewModel.consumeEvent()
    }

    val listing = state.listing

    Column(modifier.fillMaxSize().background(ScTheme.background)) {
        SilverTopBar(
            title = if (showOffers) "Предложения" else "Юзернейм",
            subtitle = listing?.let { "@${it.username}" },
            onBack = navigator::back,
            actions = {
                if (listing != null) {
                    SilverIconButton(
                        icon = Icons.Filled.ContentCopy,
                        contentDescription = "Скопировать ссылку на юзернейм",
                        onClick = { onCopyLink(listing.username) },
                        tint = ScTheme.textPrimary,
                    )
                }
            },
        )

        if (listing == null) {
            EmptyState(
                icon = Icons.Filled.Close,
                title = "Лот недоступен",
                subtitle = "Юзернейм уже продан, снят с продажи\nили заблокирован администрацией.",
                actionLabel = "Вернуться в маркет",
                onAction = navigator::back,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = ScSpacing.xxl),
            ) {
                item(key = "hero", contentType = "hero") {
                    ListingHero(listing = listing, state = state)
                }

                item(key = "price", contentType = "price") {
                    PriceBlock(state = state)
                }

                if (listing.description?.isNotBlank() == true) {
                    item(key = "description", contentType = "text") {
                        Column(Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm)) {
                            Text(
                                "Описание",
                                color = ScTheme.textTertiary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = listing.description,
                                color = ScTheme.textPrimary,
                                fontSize = 14.5.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }

                // ── История цены ─────────────────────────────────────────
                if (listing.priceHistory.isNotEmpty()) {
                    item(key = "history_hdr", contentType = "header") {
                        SectionTitle("История цены", listing.priceHistory.size)
                    }
                    items(
                        items = listing.priceHistory.sortedByDescending { it.at },
                        key = { "ph_${it.at}" },
                        contentType = { "history" },
                    ) { point ->
                        PriceHistoryRow(point)
                    }
                }

                // ── Встречные предложения ────────────────────────────────
                if (state.isSeller || showOffers) {
                    item(key = "offers_hdr", contentType = "header") {
                        SectionTitle("Предложения", state.pendingOffers.size)
                    }
                    if (state.offers.isEmpty()) {
                        item(key = "offers_empty", contentType = "empty") {
                            Text(
                                text = "Предложений пока нет",
                                color = ScTheme.textTertiary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(ScSpacing.lg),
                            )
                        }
                    }
                    items(state.offers, key = { it.id }, contentType = { "offer" }) { offer ->
                        OfferRow(
                            offer = offer,
                            listingPrice = listing.priceSilver,
                            onAccept = { viewModel.acceptOffer(offer) },
                            onDecline = { viewModel.declineOffer(offer) },
                            modifier = Modifier.padding(
                                horizontal = ScSpacing.md,
                                vertical = ScSpacing.sm,
                            ),
                        )
                    }
                }

                // ── Форма своего предложения ─────────────────────────────
                if (state.canOffer && !state.isSeller) {
                    item(key = "offer_form", contentType = "form") {
                        OfferForm(state = state, viewModel = viewModel)
                    }
                }

                // ── Информация о продавце ────────────────────────────────
                item(key = "seller", contentType = "seller") {
                    SellerBlock(listing = listing, onOpenProfile = navigator::openProfile)
                }
            }

            // Панель действий закреплена внизу: цена и кнопка всегда видны
            ActionPanel(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        EventMessage(event = events, modifier = Modifier.fillMaxWidth())
    }
}

/* =========================================================================
   ГЕРОЙ-БЛОК
   ========================================================================= */

@Composable
private fun ListingHero(
    listing: UsernameListing,
    state: com.silverchat.feature.market.ListingDetailUiState,
) {
    val rarityTint = rarityColor(listing.rarity)

    Column(
        Modifier.fillMaxWidth().padding(ScSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(rarityTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "@",
                color = rarityTint,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
            )
        }

        Spacer(Modifier.height(ScSpacing.md))

        Text(
            text = "@${listing.username}",
            color = ScTheme.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(ScSpacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
            ChipSmall(listing.rarity.labelRu, rarityTint)
            ChipSmall(listing.status.labelRu, ScTheme.textTertiary)
            ChipSmall("${listing.length} симв.", ScTheme.textSecondary)
        }

        if (listing.categories.isNotEmpty()) {
            Spacer(Modifier.height(ScSpacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Категорий может быть несколько — показываем первые три,
                // иначе строка чипов уехала бы за край экрана
                listing.categories.take(MAX_VISIBLE_CATEGORIES).forEach { category ->
                    ChipSmall(category.labelRu, ScTheme.accent)
                }
                if (listing.categories.size > MAX_VISIBLE_CATEGORIES) {
                    ChipSmall("+${listing.categories.size - MAX_VISIBLE_CATEGORIES}", ScTheme.textTertiary)
                }
            }
        }

        Spacer(Modifier.height(ScSpacing.md))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HeroStat("Просмотров", NumberFormatter.compact(listing.views.toLong()))
            HeroStat("Предложений", listing.offersCount.toString())
            HeroStat("Мин. ставка", listing.minOfferSilver?.let { NumberFormatter.silver(it) } ?: "—")
        }

        if (state.isPremium) {
            Spacer(Modifier.height(ScSpacing.sm))
            Row(
                Modifier
                    .clip(ScShapes.chip)
                    .background(ScTheme.premium.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = ScTheme.premium,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "Скидка Premium применена",
                    color = ScTheme.premium,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private const val MAX_VISIBLE_CATEGORIES = 3

@Composable
private fun HeroStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = ScTheme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ScTheme.textTertiary, fontSize = 11.sp)
    }
}

/* =========================================================================
   ЦЕНА
   ========================================================================= */

@Composable
private fun PriceBlock(state: com.silverchat.feature.market.ListingDetailUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md)
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Цена",
                color = ScTheme.textTertiary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            if (state.discountSilver > 0) {
                Text(
                    text = NumberFormatter.silver(state.basePrice),
                    color = ScTheme.textTertiary,
                    fontSize = 13.sp,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                )
                Spacer(Modifier.width(ScSpacing.sm))
            }
            Text(
                text = NumberFormatter.silver(state.finalPrice),
                color = ScTheme.silver,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (state.discountSilver > 0) {
            Text(
                text = "Скидка Premium: −${NumberFormatter.silver(state.discountSilver)}",
                color = ScTheme.premium,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(ScSpacing.sm))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ваш баланс",
                color = ScTheme.textTertiary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = NumberFormatter.silver(state.availableBalance),
                color = if (state.isAffordable) ScTheme.success else ScTheme.danger,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (!state.isAffordable) {
            Spacer(Modifier.height(ScSpacing.sm))
            Text(
                text = "Не хватает ${NumberFormatter.silver(state.finalPrice - state.availableBalance)} сильверов",
                color = ScTheme.danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PriceHistoryRow(point: PricePoint) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.TrendingUp,
            contentDescription = null,
            tint = ScTheme.textTertiary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(ScSpacing.sm))
        Text(
            text = NumberFormatter.silver(point.priceSilver),
            color = ScTheme.textPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = TimeFormatter.daySeparator(point.at),
            color = ScTheme.textTertiary,
            fontSize = 11.5.sp,
        )
    }
}

/* =========================================================================
   ПРОДАВЕЦ
   ========================================================================= */

@Composable
private fun SellerBlock(
    listing: UsernameListing,
    onOpenProfile: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(ScSpacing.md)
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.md),
    ) {
        Text(
            text = "Продавец",
            color = ScTheme.textTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(ScSpacing.sm))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(ScShapes.cardSmall)
                .then(
                    if (listing.sellerId != null) {
                        Modifier.clickable { onOpenProfile(listing.sellerId.raw) }
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = ScSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(ScTheme.surfaceGlass),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = listing.sellerName?.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                    color = ScTheme.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(ScSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = listing.sellerName ?: "SilverChat (официальная продажа)",
                    color = ScTheme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = if (listing.sellerId == null) {
                        "Юзернейм из пула приложения"
                    } else {
                        "Открыть профиль"
                    },
                    color = ScTheme.textTertiary,
                    fontSize = 11.5.sp,
                )
            }
        }

        Spacer(Modifier.height(ScSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = ScTheme.success,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Сделка проходит через escrow: сильверы списываются только после передачи юзернейма",
                color = ScTheme.textTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

/* =========================================================================
   ФОРМА ПРЕДЛОЖЕНИЯ
   ========================================================================= */

@Composable
private fun OfferForm(
    state: com.silverchat.feature.market.ListingDetailUiState,
    viewModel: ListingDetailViewModel,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(ScSpacing.md)
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.md),
    ) {
        Text(
            text = "Предложить свою цену",
            color = ScTheme.textPrimary,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
        )
        state.minOfferSilver?.let { min ->
            Text(
                text = "Минимальная ставка: ${NumberFormatter.silver(min)} сильверов",
                color = ScTheme.textTertiary,
                fontSize = 11.5.sp,
            )
        }
        Spacer(Modifier.height(ScSpacing.sm))

        SilverTextField(
            value = state.offerAmount?.toString().orEmpty(),
            onValueChange = { raw -> viewModel.onOfferAmountChanged(raw.toLongOrNull()) },
            label = "Сумма в сильверах",
            placeholder = NumberFormatter.silver(state.basePrice),
            keyboardType = KeyboardType.Number,
        )

        Spacer(Modifier.height(ScSpacing.sm))

        if (state.myOffer != null) {
            Text(
                text = "Ваше предложение на ${NumberFormatter.silver(state.myOffer.amountSilver)} уже отправлено",
                color = ScTheme.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(ScSpacing.sm))
        }

        SilverButton(
            text = "Отправить предложение",
            onClick = viewModel::sendOffer,
            enabled = state.offerAmount != null && state.offerAmount > 0 && !state.isProcessing,
            variant = SilverButtonVariant.SECONDARY,
        )
    }
}

/* =========================================================================
   ПАНЕЛЬ ДЕЙСТВИЙ
   ========================================================================= */

@Composable
private fun ActionPanel(
    state: com.silverchat.feature.market.ListingDetailUiState,
    viewModel: ListingDetailViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(ScTheme.surfaceGlass)
            .padding(ScSpacing.md),
    ) {
        when {
            state.isSeller -> SilverButton(
                text = "Снять с продажи",
                onClick = viewModel::cancelListing,
                variant = SilverButtonVariant.SECONDARY,
                enabled = !state.isProcessing,
            )

            state.listing?.isReserved == true -> SilverButton(
                text = "Лот недоступен",
                onClick = {},
                enabled = false,
            )

            else -> SilverButton(
                text = when {
                    state.isProcessing -> "Обрабатываем…"
                    state.isAffordable -> "Купить за ${NumberFormatter.silver(state.finalPrice)}"
                    else -> "Недостаточно сильверов"
                },
                onClick = viewModel::requestPurchase,
                enabled = state.canBuy && state.isAffordable,
                loading = state.isProcessing,
            )
        }
    }
}

/* =========================================================================
   СООБЩЕНИЕ О СОБЫТИИ
   ========================================================================= */

@Composable
private fun EventMessage(event: MarketEvent?, modifier: Modifier = Modifier) {
    val message = when (event) {
        is MarketEvent.Error -> event.message
        is MarketEvent.InsufficientFunds ->
            "Не хватает ${NumberFormatter.silver(event.required - event.available)} сильверов"

        is MarketEvent.OfferSent -> "Предложение на ${NumberFormatter.silver(event.amount)} отправлено"
        is MarketEvent.Purchased -> "Юзернейм @${event.username} ваш!"
        is MarketEvent.ListingCancelled -> "Лот снят с продажи"
        else -> null
    } ?: return

    InlineSnackbar(message = message, modifier = modifier.padding(ScSpacing.md))
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = ScTheme.textPrimary,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) ChipSmall(count.toString(), ScTheme.textTertiary)
    }
}
