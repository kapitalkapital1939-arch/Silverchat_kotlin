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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Wallet
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.InlineSnackbar
import com.silverchat.core.designsystem.component.SearchField
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.market.ChipSmall
import com.silverchat.core.designsystem.component.market.MarketStatTile
import com.silverchat.core.designsystem.component.market.SilverBalanceHeader
import com.silverchat.core.designsystem.component.market.UsernameCard
import com.silverchat.core.designsystem.component.market.rarityColor
import com.silverchat.core.designsystem.glass.GlassState
import com.silverchat.core.designsystem.glass.glassSource
import com.silverchat.core.designsystem.navigation.MarketNavigator
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.MarketSort
import com.silverchat.core.model.UsernameCategory
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.UsernameRarity
import com.silverchat.core.model.labelRu
import com.silverchat.feature.market.MarketEvent
import com.silverchat.feature.market.MarketViewModel
import com.silverchat.feature.market.titleRu

/**
 * Витрина маркета юзернеймов.
 *
 * Экран — главная точка монетизации приложения, поэтому он объединяет
 * четыре сущности в одной ленте: баланс кошелька, стрик, лоты и быстрые
 * переходы к подаркам/Premium. Разносить их по вкладкам нельзя: пользователь
 * должен видеть «сколько у меня сильверов» в момент выбора лота.
 *
 * Подтверждение покупки — модальный диалог, а не мгновенное списание:
 * сильверы имеют реальную ценность, и случайный тап не должен их уничтожать.
 */
@Composable
fun MarketScreen(
    navigator: MarketNavigator,
    glassState: GlassState,
    modifier: Modifier = Modifier,
    viewModel: MarketViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    // События покупки — одноразовые: гасим сразу после обработки
    LaunchedEffect(events) {
        when (val event = events) {
            is MarketEvent.Purchased -> Unit // успех показываем снекбаром ниже
            is MarketEvent.InsufficientFunds -> Unit
            is MarketEvent.Error -> Unit
            else -> Unit
        }
        if (events != null) viewModel.consumeEvent()
    }

    Column(modifier.fillMaxSize().background(ScTheme.background)) {
        SilverTopBar(
            title = "Маркет",
            subtitle = "Юзернеймы, подарки и Premium",
            actions = {
                SilverIconButton(
                    icon = Icons.Filled.Wallet,
                    contentDescription = "Кошелёк и история операций",
                    onClick = navigator::openWallet,
                    tint = ScTheme.textPrimary,
                )
                SilverIconButton(
                    icon = Icons.Filled.Star,
                    contentDescription = "SilverChat Premium",
                    onClick = navigator::openPremium,
                    tint = ScTheme.premium,
                )
            },
        )

        Box(Modifier.weight(1f).glassSource(glassState)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = ScSpacing.xxl),
            ) {
                // ── Баланс и стрик ───────────────────────────────────────
                item(key = "wallet", contentType = "wallet") {
                    SilverBalanceHeader(
                        wallet = state.wallet,
                        streak = state.streak,
                        onTopUp = navigator::openWallet,
                        onClaimStreak = viewModel::claimDailyStreak,
                        modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                    )
                }

                // ── Статистика рынка ─────────────────────────────────────
                item(key = "stats", contentType = "stats") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
                    ) {
                        MarketStatTile(
                            title = "Лотов",
                            value = NumberFormatter.compact(state.listings.size.toLong()),
                            icon = Icons.Filled.Search,
                            modifier = Modifier.weight(1f),
                        )
                        MarketStatTile(
                            title = "Объём за 24 ч",
                            value = NumberFormatter.silver(state.volume24h),
                            icon = Icons.Filled.TrendingUp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── Быстрые разделы ──────────────────────────────────────
                item(key = "shortcuts", contentType = "shortcuts") {
                    Row(
                        Modifier.fillMaxWidth().padding(ScSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
                    ) {
                        ShortcutTile(
                            icon = Icons.Filled.CardGiftcard,
                            label = "Подарки",
                            tint = ScTheme.accent,
                            modifier = Modifier.weight(1f),
                            onClick = navigator::openGifts,
                        )
                        ShortcutTile(
                            icon = Icons.Filled.Star,
                            label = "Premium",
                            tint = ScTheme.premium,
                            modifier = Modifier.weight(1f),
                            onClick = navigator::openPremium,
                        )
                        ShortcutTile(
                            icon = Icons.Filled.TrendingUp,
                            label = "Продать ник",
                            tint = ScTheme.success,
                            modifier = Modifier.weight(1f),
                            onClick = navigator::openSell,
                        )
                    }
                }

                // ── Поиск и сортировка ───────────────────────────────────
                item(key = "search", contentType = "search") {
                    SearchField(
                        query = state.query.search,
                        onQueryChange = viewModel::onSearchChanged,
                        placeholder = "Поиск юзернейма: @alice, btc, short…",
                        onClear = { viewModel.onSearchChanged("") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md),
                    )
                }

                item(key = "sort", contentType = "sort") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
                    ) {
                        items(MarketSort.entries.toList(), key = { it.name }) { option ->
                            val selected = option == state.query.sort
                            Text(
                                text = option.titleRu,
                                color = if (selected) Color.White else ScTheme.textSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier
                                    .clip(ScShapes.chip)
                                    .background(if (selected) ScTheme.accent else ScTheme.surfaceGlass)
                                    .clickable { viewModel.onSortChanged(option) }
                                    .padding(horizontal = 13.dp, vertical = 7.dp),
                            )
                        }
                    }
                }

                // ── Фильтры по категориям и редкости ─────────────────────
                item(key = "filters", contentType = "filters") {
                    FilterRow(state = state, viewModel = viewModel)
                }

                // ── Тренды ───────────────────────────────────────────────
                if (state.trending.isNotEmpty()) {
                    item(key = "trending_hdr", contentType = "header") {
                        SectionHeader("Сейчас в тренде", state.trending.size)
                    }
                    item(key = "trending", contentType = "trending") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
                        ) {
                            items(state.trending, key = { it }) { tag ->
                                Text(
                                    text = "#$tag",
                                    color = ScTheme.accent,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(ScShapes.chip)
                                        .background(ScTheme.accentContainer)
                                        .clickable { viewModel.onSearchChanged(tag) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                // ── Лоты ─────────────────────────────────────────────────
                item(key = "listings_hdr", contentType = "header") {
                    SectionHeader("Юзернеймы", state.listings.size)
                }

                if (state.isEmpty) {
                    item(key = "empty", contentType = "empty") {
                        EmptyState(
                            icon = Icons.Filled.Search,
                            title = "Лотов не найдено",
                            subtitle = "Измените фильтры или поисковый запрос.\n" +
                                "Свой юзернейм можно выставить на продажу.",
                            actionLabel = "Продать юзернейм",
                            onAction = navigator::openSell,
                        )
                    }
                } else {
                    items(
                        items = state.listings,
                        key = { it.id.raw },
                        contentType = { "listing" },
                    ) { listing ->
                        ListingRow(
                            listing = listing,
                            balance = state.availableBalance,
                            isPurchasing = state.purchasingListingId == listing.id.raw,
                            onClick = { navigator.openListing(listing.id.raw) },
                            onBuy = { viewModel.requestPurchase(listing) },
                            onOffer = { navigator.openOffers(listing.id.raw) },
                            modifier = Modifier.padding(
                                horizontal = ScSpacing.md,
                                vertical = ScSpacing.sm,
                            ),
                        )
                    }
                }
            }

            // Диалог подтверждения покупки — поверх ленты
            state.confirmPurchase?.let { listing ->
                PurchaseConfirmDialog(
                    listing = listing,
                    balance = state.availableBalance,
                    onConfirm = viewModel::confirmPurchase,
                    onDismiss = viewModel::dismissPurchaseDialog,
                )
            }
        }

        // Результат операции — снекбар внизу экрана
        EventSnackbar(event = events, modifier = Modifier.fillMaxWidth())
    }
}

/* =========================================================================
   СТРОКА ЛОТА
   ========================================================================= */

@Composable
private fun ListingRow(
    listing: UsernameListing,
    balance: Long,
    isPurchasing: Boolean,
    onClick: () -> Unit,
    onBuy: () -> Unit,
    onOffer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        if (isPurchasing) {
            // Индикация обработки: кнопка блокируется, но карточка остаётся видимой
            Box(
                Modifier
                    .matchParentSize()
                    .clip(ScShapes.card)
                    .background(ScTheme.surfaceGlass.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Обрабатываем покупку…", color = ScTheme.textPrimary, fontSize = 13.sp)
            }
        }
        UsernameCard(
            listing = listing,
            availableBalance = balance,
            onClick = onClick,
            onBuyClick = onBuy,
            onOfferClick = onOffer,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* =========================================================================
   ФИЛЬТРЫ
   ========================================================================= */

@Composable
private fun FilterRow(
    state: com.silverchat.feature.market.MarketUiState,
    viewModel: MarketViewModel,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = ScSpacing.sm)) {
        // Редкость — главный фильтр маркета: от неё зависит цена
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
        ) {
            items(UsernameRarity.entries.toList(), key = { "r_${it.name}" }) { rarity ->
                FilterChip(
                    label = rarity.labelRu,
                    selected = rarity in state.query.rarity,
                    color = rarityColor(rarity),
                    onClick = { viewModel.toggleRarity(rarity) },
                )
            }
        }
        Spacer(Modifier.height(ScSpacing.sm))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
        ) {
            items(UsernameCategory.entries.toList(), key = { "c_${it.name}" }) { category ->
                FilterChip(
                    label = category.titleRu,
                    selected = category in state.query.categories,
                    color = ScTheme.textSecondary,
                    onClick = { viewModel.toggleCategory(category) },
                )
            }
        }

        if (state.hasActiveFilters) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md, vertical = ScSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    tint = ScTheme.accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Фильтры активны", color = ScTheme.accent, fontSize = 11.5.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Сбросить",
                    color = ScTheme.textTertiary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(ScShapes.chip)
                        .clickable(onClick = viewModel::resetFilters)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(ScShapes.chip)
            .background(if (selected) color.copy(alpha = 0.18f) else ScTheme.surfaceGlass)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (selected) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        }
        Text(
            text = label,
            color = if (selected) color else ScTheme.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/* =========================================================================
   ЗАГОЛОВКИ И ПЛИТКИ
   ========================================================================= */

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = ScTheme.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            ChipSmall(text = count.toString(), color = ScTheme.textTertiary)
        }
    }
}

@Composable
private fun ShortcutTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(vertical = ScSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = ScTheme.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* =========================================================================
   ДИАЛОГ ПОДТВЕРЖДЕНИЯ ПОКУПКИ
   ========================================================================= */

/**
 * Подтверждение списания сильверов.
 *
 * Показываем не только цену, но и остаток после покупки: пользователь должен
 * понимать последствия, а не просто нажимать «ОК». При нехватке средств
 * кнопка подтверждения заменяется переходом к пополнению.
 */
@Composable
private fun PurchaseConfirmDialog(
    listing: UsernameListing,
    balance: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val remaining = balance - listing.priceSilver
    val affordable = remaining >= 0

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = false, onClick = {}),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Покупка юзернейма",
                    color = ScTheme.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                SilverIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Отменить покупку",
                    onClick = onDismiss,
                    tint = ScTheme.textTertiary,
                    size = 32,
                )
            }

            Spacer(Modifier.height(ScSpacing.md))

            Text(
                text = "@${listing.username}",
                color = rarityColor(listing.rarity),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = listing.rarity.labelRu,
                color = ScTheme.textTertiary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(ScSpacing.lg))

            PriceLine("Цена", listing.priceSilver, ScTheme.textPrimary)
            PriceLine("Ваш баланс", balance, ScTheme.textSecondary)
            PriceLine(
                label = "Останется",
                amount = remaining,
                color = if (affordable) ScTheme.success else ScTheme.danger,
            )

            if (!affordable) {
                Spacer(Modifier.height(ScSpacing.md))
                Text(
                    text = "Не хватает ${NumberFormatter.silver(-remaining)} сильверов",
                    color = ScTheme.danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(ScSpacing.lg))

            SilverButton(
                text = if (affordable) "Купить за ${NumberFormatter.silver(listing.priceSilver)}" else "Недостаточно средств",
                onClick = onConfirm,
                enabled = affordable,
            )
            Spacer(Modifier.height(ScSpacing.sm))
            SilverButton(
                text = "Отмена",
                onClick = onDismiss,
                variant = com.silverchat.core.designsystem.component.SilverButtonVariant.TEXT,
            )
        }
    }
}

@Composable
private fun PriceLine(label: String, amount: Long, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ScTheme.textTertiary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            text = NumberFormatter.silver(amount),
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/* =========================================================================
   СНЕКБАР СОБЫТИЙ
   ========================================================================= */

@Composable
private fun EventSnackbar(event: MarketEvent?, modifier: Modifier = Modifier) {
    val message = when (event) {
        is MarketEvent.Purchased -> "Юзернейм @${event.username} ваш! Баланс: ${NumberFormatter.silver(event.newBalance)}"
        is MarketEvent.InsufficientFunds ->
            "Не хватает ${NumberFormatter.silver(event.required - event.available)} сильверов"

        is MarketEvent.OfferSent -> "Предложение на ${NumberFormatter.silver(event.amount)} отправлено"
        is MarketEvent.ListingCreated -> "@${event.username} выставлен на продажу"
        is MarketEvent.ListingCancelled -> "@${event.username} снят с продажи"
        is MarketEvent.StreakClaimed -> "Стрик ${event.days} дн. · награда ${NumberFormatter.silver(event.reward)}"
        is MarketEvent.Error -> event.message
        null -> null
    } ?: return

    InlineSnackbar(message = message, modifier = modifier.padding(ScSpacing.md))
}
