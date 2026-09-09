package com.silverchat.core.designsystem.component.market

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.Gift
import com.silverchat.core.model.LedgerEntry
import com.silverchat.core.model.ListingStatus
import com.silverchat.core.model.OfferStatus
import com.silverchat.core.model.PremiumPerk
import com.silverchat.core.model.PremiumPerkCode
import com.silverchat.core.model.PremiumTier
import com.silverchat.core.model.Streak
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.UsernameOffer
import com.silverchat.core.model.UsernameRarity
import com.silverchat.core.model.Wallet
import com.silverchat.core.model.labelRu

/* =========================================================================
   МАРКЕТ ЮЗЕРНЕЙМОВ
   ========================================================================= */

/**
 * Карточка лота маркета юзернеймов.
 *
 * Отражает жизненный цикл лота из [ListingStatus]:
 *  AVAILABLE    — кнопки «Купить» / «Предложить»;
 *  AUCTION      — текущая цена, число предложений, таймер до конца;
 *  RESERVED     — плашка «Забронирован» (escrow на время оплаты);
 *  SOLD         — история рынка: цена и продавец;
 *  BLOCKED      — изъят администрацией (@silver);
 *  OWNED_BY_ME  — это собственный юзернейм пользователя.
 *
 * Редкие юзернеймы ([UsernameRarity]) подсвечиваются цветной рамкой:
 * это визуальное обоснование цены — пользователь понимает, за что платит.
 */
@Composable
fun UsernameCard(
    listing: UsernameListing,
    modifier: Modifier = Modifier,
    availableBalance: Long = 0,
    myOffer: UsernameOffer? = null,
    onClick: () -> Unit = {},
    onBuyClick: () -> Unit = {},
    onOfferClick: () -> Unit = {},
) {
    val isSold = listing.status == ListingStatus.SOLD
    val isAuction = listing.status == ListingStatus.AUCTION
    val isMine = listing.status == ListingStatus.OWNED_BY_ME
    val isBlocked = listing.status == ListingStatus.BLOCKED
    val affordable = availableBalance >= listing.priceSilver
    val acceptsOffers = listing.minOfferSilver != null

    val borderColor = when {
        isSold || isBlocked -> ScTheme.outline
        isMine -> ScTheme.success.copy(alpha = 0.5f)
        listing.rarity == UsernameRarity.GRAIL || listing.rarity == UsernameRarity.LEGENDARY ->
            ScTheme.premium.copy(alpha = 0.6f)
        isAuction -> ScTheme.warning.copy(alpha = 0.5f)
        else -> ScTheme.outline
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .border(1.dp, borderColor, ScShapes.card)
            .clickable(enabled = !isBlocked, onClick = onClick)
            .padding(ScSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "@${listing.username}",
                        color = if (isSold || isBlocked) ScTheme.textTertiary else ScTheme.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (listing.rarity.ordinal >= UsernameRarity.EPIC.ordinal) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Редкий юзернейм: ${listing.rarity.labelRu}",
                            tint = rarityColor(listing.rarity),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChipSmall("${listing.length} симв.", ScTheme.textTertiary)
                    ChipSmall(listing.status.labelRu, statusColor(listing.status))
                    listing.categories.firstOrNull()?.let { ChipSmall(it.labelRu, ScTheme.accent) }
                }
            }

            // ── Цена ─────────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.End) {
                // Зачёркнутая стартовая цена показывает выгоду аукциона
                if (isAuction && listing.priceHistory.isNotEmpty()) {
                    val startPrice = listing.priceHistory.first().priceSilver
                    if (startPrice != listing.priceSilver) {
                        Text(
                            text = NumberFormatter.silver(startPrice),
                            color = ScTheme.textTertiary,
                            fontSize = 11.sp,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = if (isSold || isBlocked) ScTheme.textTertiary else ScTheme.silver,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = NumberFormatter.silver(listing.priceSilver),
                        color = if (isSold || isBlocked) ScTheme.textTertiary else ScTheme.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                if (isAuction) {
                    Text(
                        text = "${listing.offersCount} ${pluralOffers(listing.offersCount)}",
                        color = ScTheme.warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // ── Таймер аукциона / бронь ──────────────────────────────────────
        if (isAuction && listing.expiresAt != null) {
            Spacer(Modifier.height(ScSpacing.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.HourglassEmpty, null, tint = ScTheme.warning, modifier = Modifier.size(13.dp))
                Text(
                    text = "До конца ${TimeFormatter.ago(listing.expiresAt - System.currentTimeMillis())}",
                    color = ScTheme.warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (listing.status == ListingStatus.RESERVED) {
            Spacer(Modifier.height(ScSpacing.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.LocalOffer, null, tint = ScTheme.info, modifier = Modifier.size(13.dp))
                Text(
                    text = "Забронирован на время оплаты",
                    color = ScTheme.info,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (isSold) {
            Spacer(Modifier.height(ScSpacing.sm))
            Text(
                text = buildString {
                    append("Продан ${TimeFormatter.daySeparator(listing.createdAt)}")
                    listing.sellerName?.let { append(" · продавец $it") }
                },
                color = ScTheme.textTertiary,
                fontSize = 11.5.sp,
            )
        }

        // ── Моя ставка ───────────────────────────────────────────────────
        myOffer?.let { offer ->
            Spacer(Modifier.height(ScSpacing.sm))
            val tint = offerColor(offer.status)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ScShapes.cardSmall)
                    .background(tint.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Моя ставка", color = ScTheme.textSecondary, fontSize = 12.sp)
                Text(
                    text = NumberFormatter.silver(offer.amountSilver),
                    color = tint,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(offer.status.labelRu, color = tint, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Действия ─────────────────────────────────────────────────────
        if (!isSold && !isBlocked && !isMine) {
            Spacer(Modifier.height(ScSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
                if (acceptsOffers || isAuction) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(ScShapes.chip)
                            .background(ScTheme.surface)
                            .border(1.dp, ScTheme.outline, ScShapes.chip)
                            .clickable(onClick = onOfferClick)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isAuction) "Ставка" else "Предложить",
                            color = ScTheme.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(ScShapes.chip)
                        .background(if (affordable) ScTheme.accent else ScTheme.surfaceVariant)
                        .alpha(if (affordable) 1f else 0.65f)
                        .clickable(enabled = affordable, onClick = onBuyClick)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (affordable) {
                            "Купить"
                        } else {
                            "Не хватает ${NumberFormatter.silver(listing.priceSilver - availableBalance)}"
                        },
                        color = if (affordable) Color.White else ScTheme.textTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

private fun pluralOffers(count: Int): String =
    if (count % 10 == 1 && count % 100 != 11) "предложение" else "предложений"

fun rarityColor(rarity: UsernameRarity): Color = when (rarity) {
    UsernameRarity.COMMON -> ScTheme.textTertiary
    UsernameRarity.RARE -> ScTheme.accent
    UsernameRarity.EPIC -> Color(0xFFA855F7)
    UsernameRarity.LEGENDARY -> ScTheme.premium
    UsernameRarity.GRAIL -> ScTheme.premiumLight
}

private fun statusColor(status: ListingStatus): Color = when (status) {
    ListingStatus.AVAILABLE -> ScTheme.success
    ListingStatus.RESERVED -> ScTheme.info
    ListingStatus.SOLD -> ScTheme.textTertiary
    ListingStatus.AUCTION -> ScTheme.warning
    ListingStatus.BLOCKED -> ScTheme.danger
    ListingStatus.OWNED_BY_ME -> ScTheme.success
}

private fun offerColor(status: OfferStatus): Color = when (status) {
    OfferStatus.PENDING -> ScTheme.warning
    OfferStatus.ACCEPTED -> ScTheme.success
    OfferStatus.DECLINED -> ScTheme.danger
    OfferStatus.EXPIRED -> ScTheme.textTertiary
}

@Composable
fun ChipSmall(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .clip(ScShapes.chip)
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * Строка встречного предложения — для продавца в списке офферов.
 */
@Composable
fun OfferRow(
    offer: UsernameOffer,
    modifier: Modifier = Modifier,
    listingPrice: Long,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
) {
    val tint = offerColor(offer.status)
    val pending = offer.status == OfferStatus.PENDING

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(offer.buyerName, color = ScTheme.textPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Bolt, null, tint = tint, modifier = Modifier.size(13.dp))
                Text(NumberFormatter.silver(offer.amountSilver), color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                // Сравнение с ценой лота: покупатель сразу видит, насколько оффер ниже
                if (offer.amountSilver < listingPrice) {
                    Text(
                        text = "−${NumberFormatter.percent(listingPrice.toInt() - offer.amountSilver.toInt(), listingPrice.toInt())}%",
                        color = ScTheme.textTertiary,
                        fontSize = 11.sp,
                    )
                }
            }
            Text(
                text = "${offer.status.labelRu} · ${TimeFormatter.ago(System.currentTimeMillis() - offer.createdAt)}",
                color = ScTheme.textTertiary,
                fontSize = 11.sp,
            )
        }

        if (pending) {
            Row(horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
                Box(
                    Modifier
                        .clip(ScShapes.chip)
                        .background(ScTheme.surface)
                        .border(1.dp, ScTheme.outline, ScShapes.chip)
                        .clickable(onClick = onDecline)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("Отклонить", color = ScTheme.textSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
                Box(
                    Modifier
                        .clip(ScShapes.chip)
                        .background(ScTheme.success)
                        .clickable(onClick = onAccept)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("Принять", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/* =========================================================================
   ПОДАРКИ
   ========================================================================= */

/**
 * Карточка подарка.
 *
 * Подарок — социальный жест и способ потратить серебро, поэтому цена
 * показывается всегда. Категория ([GiftCategory]) влияет на оформление
 * рамки: LUXURY и COLLECTIBLE подсвечиваются золотом.
 */
@Composable
fun GiftCard(
    gift: Gift,
    modifier: Modifier = Modifier,
    availableBalance: Long = 0,
    isPremium: Boolean = false,
    onSend: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    val affordable = availableBalance >= gift.priceSilver
    val unlocked = !gift.premiumOnly || isPremium
    val frame = categoryColor(gift.category)

    Column(
        modifier = modifier
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .then(
                if (gift.category == GiftCategoryLuxury || gift.category == UsernameCategoryToGift) {
                    Modifier.border(1.5.dp, frame.copy(alpha = 0.7f), ScShapes.card)
                } else {
                    Modifier.border(1.dp, ScTheme.outline, ScShapes.card)
                },
            )
            .clickable(onClick = onClick)
            .padding(ScSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(frame.copy(alpha = 0.28f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Text(gift.emoji, fontSize = 34.sp)
        }

        Spacer(Modifier.height(ScSpacing.sm))
        Text(
            text = gift.title,
            color = ScTheme.textPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(gift.category.labelRu, color = frame, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)

        // Лимитированные подарки: остаток тиража — триггер срочности
        if (gift.isLimited) {
            Text(
                text = if (gift.soldOut) "Распродан" else "Осталось ${gift.limitedLeft ?: 0}",
                color = if (gift.soldOut) ScTheme.danger else ScTheme.warning,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(ScSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(Icons.Filled.Bolt, null, tint = ScTheme.silver, modifier = Modifier.size(13.dp))
            Text(
                text = NumberFormatter.silver(gift.priceSilver),
                color = ScTheme.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(ScSpacing.sm))
        val enabled = affordable && unlocked && !gift.soldOut
        val label = when {
            gift.soldOut -> "Распродан"
            !unlocked -> "Только Premium"
            !affordable -> "Мало серебра"
            else -> "Подарить"
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(ScShapes.chip)
                .background(if (enabled) frame else ScTheme.surfaceVariant)
                .alpha(if (enabled) 1f else 0.7f)
                .clickable(enabled = enabled, onClick = onSend)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (enabled) Color.White else ScTheme.textTertiary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

// Локальные алиасы, чтобы не загромождать условие импортами
private val GiftCategoryLuxury = com.silverchat.core.model.GiftCategory.LUXURY
private val UsernameCategoryToGift = com.silverchat.core.model.GiftCategory.COLLECTIBLE

fun categoryColor(category: com.silverchat.core.model.GiftCategory): Color = when (category) {
    com.silverchat.core.model.GiftCategory.CLASSIC -> ScTheme.textTertiary
    com.silverchat.core.model.GiftCategory.ANIMATED -> ScTheme.accent
    com.silverchat.core.model.GiftCategory.LUXURY -> ScTheme.premium
    com.silverchat.core.model.GiftCategory.SEASONAL -> ScTheme.success
    com.silverchat.core.model.GiftCategory.COLLECTIBLE -> Color(0xFFA855F7)
}

/* =========================================================================
   PREMIUM
   ========================================================================= */

/**
 * Карточка тарифа Premium.
 *
 * [selected] — выбранный тариф; тариф с `bestValue = true` получает
 * градиентный фон и метку «Выгодно». Пользователи сравнивают цены
 * в месяц, а не за период, поэтому добавлена строка [monthlyEquivalent].
 */
@Composable
fun PremiumTierCard(
    tier: PremiumTier,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit = {},
) {
    val highlighted = tier.bestValue
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, tween(180), label = "tierScale")
    val onGradient = Color.White
    val contentColor = if (highlighted) onGradient else ScTheme.textPrimary
    val subColor = if (highlighted) onGradient.copy(alpha = 0.85f) else ScTheme.textTertiary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScShapes.cardLarge)
            .then(
                if (highlighted) {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(ScTheme.premiumGradientStart, ScTheme.premiumGradientEnd),
                        ),
                    )
                } else {
                    Modifier.background(ScTheme.surfaceElevated)
                },
            )
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = if (highlighted) Color.White else ScTheme.accent,
                        shape = ScShapes.cardLarge,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onSelect)
            .padding(ScSpacing.lg)
            .alpha(if (scale > 0f) 1f else 1f),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tier.title, color = contentColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${tier.periodDays} ${pluralDays(tier.periodDays)}",
                        color = subColor,
                        fontSize = 12.5.sp,
                    )
                }
                if (highlighted) {
                    Text(
                        text = "Выгодно −${tier.discountPercent}%",
                        color = ScTheme.premium,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(ScShapes.chip)
                            .background(Color.White)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(ScSpacing.md))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = NumberFormatter.silver(tier.priceSilver),
                    color = contentColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text("сильверов", color = subColor, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "≈ ${NumberFormatter.silver(tier.monthlyEquivalent)} в месяц",
                    color = if (highlighted) onGradient.copy(alpha = 0.9f) else ScTheme.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                tier.priceFiat?.let {
                    Spacer(Modifier.width(ScSpacing.sm))
                    Text("· $it", color = subColor, fontSize = 12.sp)
                }
            }

            if (tier.perks.isNotEmpty()) {
                Spacer(Modifier.height(ScSpacing.md))
                tier.perks.forEach { perk -> PremiumPerkRow(perk, highlighted) }
            }
        }
    }
}

/** Цена тарифа в пересчёте на месяц — так тарифы сравниваются честно. */
val PremiumTier.monthlyEquivalent: Long
    get() = if (periodDays <= 0) priceSilver else priceSilver * 30 / periodDays

private fun pluralDays(days: Int): String = when {
    days % 10 == 1 && days % 100 != 11 -> "день"
    days % 10 in 2..4 && days % 100 !in 12..14 -> "дня"
    else -> "дней"
}

@Composable
private fun PremiumPerkRow(perk: PremiumPerk, highlighted: Boolean) {
    Row(
        Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
    ) {
        Icon(
            imageVector = perkIcon(perk.code),
            contentDescription = null,
            tint = if (highlighted) Color.White else ScTheme.premium,
            modifier = Modifier.size(15.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = perk.title.ifBlank { perk.code.labelRu },
                color = if (highlighted) Color.White else ScTheme.textSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (perk.description.isNotBlank()) {
                Text(
                    text = perk.description,
                    color = if (highlighted) Color.White.copy(alpha = 0.8f) else ScTheme.textTertiary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun perkIcon(code: PremiumPerkCode): ImageVector = when (code) {
    PremiumPerkCode.NO_ADS -> Icons.Filled.Check
    PremiumPerkCode.ANIMATED_AVATAR -> Icons.Filled.Star
    PremiumPerkCode.ANIMATED_BANNER -> Icons.Filled.Star
    PremiumPerkCode.CUSTOM_THEMES -> Icons.Filled.Bolt
    PremiumPerkCode.EXCLUSIVE_STICKERS -> Icons.Filled.CardGiftcard
    PremiumPerkCode.EXCLUSIVE_GIFS -> Icons.Filled.CardGiftcard
    PremiumPerkCode.FREE_REACTIONS -> Icons.Filled.Star
    PremiumPerkCode.LARGER_UPLOADS -> Icons.Filled.Check
    PremiumPerkCode.VOICE_TO_TEXT -> Icons.Filled.Check
    PremiumPerkCode.STORY_STEALTH -> Icons.Filled.Check
    PremiumPerkCode.USERNAME_DISCOUNT -> Icons.Filled.LocalOffer
    PremiumPerkCode.MORE_FOLDERS -> Icons.Filled.Check
    PremiumPerkCode.HD_CALLS -> Icons.Filled.Star
}

/* =========================================================================
   КОШЕЛЁК
   ========================================================================= */

/** Шапка баланса: кошелёк + стрик. */
@Composable
fun SilverBalanceHeader(
    wallet: Wallet?,
    modifier: Modifier = Modifier,
    streak: Streak? = null,
    onTopUp: () -> Unit = {},
    onClaimStreak: () -> Unit = {},
) {
    val balance = wallet?.available ?: 0L

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScShapes.cardLarge)
            .background(
                Brush.horizontalGradient(
                    listOf(ScTheme.silver.copy(alpha = 0.18f), ScTheme.accent.copy(alpha = 0.12f)),
                ),
            )
            .padding(ScSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, null, tint = ScTheme.silver, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(ScSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Доступно", color = ScTheme.textSecondary, fontSize = 12.sp)
                Text(
                    text = NumberFormatter.silver(balance),
                    color = ScTheme.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Box(
                Modifier
                    .clip(ScShapes.chip)
                    .background(ScTheme.accent)
                    .clickable(onClick = onTopUp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("Пополнить", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }

        // Замороженная часть = escrow по активной сделке; скрывать её нельзя,
        // иначе пользователь решит, что серебро пропало
        if ((wallet?.frozen ?: 0L) > 0) {
            Spacer(Modifier.height(ScSpacing.xs))
            Text(
                text = "В сделке (заморожено): ${NumberFormatter.silver(wallet?.frozen ?: 0)}",
                color = ScTheme.warning,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        streak?.let { current ->
            Spacer(Modifier.height(ScSpacing.sm))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ScShapes.cardSmall)
                    .background(ScTheme.surface.copy(alpha = 0.5f))
                    .clickable(enabled = current.claimable, onClick = onClaimStreak)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔥", fontSize = 16.sp)
                Spacer(Modifier.width(ScSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Серия ${current.streakDays} ${pluralDays(current.streakDays)}",
                        color = ScTheme.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Следующая награда: ${NumberFormatter.silver(current.nextRewardSilver)}",
                        color = ScTheme.textTertiary,
                        fontSize = 11.sp,
                    )
                }
                if (current.claimable) {
                    Text(
                        text = "Забрать",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(ScShapes.chip)
                            .background(ScTheme.success)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

/** Движение средств в журнале кошелька (двойная запись). */
@Composable
fun LedgerEntryRow(entry: LedgerEntry, modifier: Modifier = Modifier) {
    val positive = entry.amount >= 0
    val tint = if (positive) ScTheme.success else ScTheme.danger

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (positive) "+" else "−", color = tint, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.reason.labelRu,
                color = ScTheme.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            // Кто вторая сторона сделки — без этого журнал нечитаем
            val subtitle = entry.counterpartyName ?: entry.reference
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = ScTheme.textTertiary, fontSize = 12.sp, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (positive) "+" else "") + NumberFormatter.silver(entry.amount),
                color = tint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "остаток ${NumberFormatter.silver(entry.balanceAfter)}",
                color = ScTheme.textTertiary,
                fontSize = 10.5.sp,
            )
            Text(TimeFormatter.full(entry.createdAt), color = ScTheme.textTertiary, fontSize = 10.sp)
        }
    }
}

/** Плитка статистики маркета (объём торгов, продано лотов). */
@Composable
fun MarketStatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.LocalOffer,
) {
    Column(
        modifier = modifier
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .border(1.dp, ScTheme.outline, ScShapes.card)
            .padding(ScSpacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = ScTheme.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(ScSpacing.sm))
        Text(value, color = ScTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text(title, color = ScTheme.textTertiary, fontSize = 11.5.sp, maxLines = 2)
    }
}

/**
 * Модель пакета серебра для покупки за реальные деньги.
 *
 * В :core:model её нет намеренно: каталог IAP-товаров приходит из
 * Google Play Billing, а не с нашего бэкенда, поэтому это UI-модель.
 */
data class SilverPackUi(
    val productId: String,
    val silverAmount: Long,
    val priceFormatted: String,
    val bonusPercent: Int = 0,
)

@Composable
fun SilverPackCard(
    pack: SilverPackUi,
    modifier: Modifier = Modifier,
    onBuy: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .border(1.dp, ScTheme.outline, ScShapes.card)
            .clickable(onClick = onBuy)
            .padding(ScSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(ScTheme.silver.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Bolt, null, tint = ScTheme.silver, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = NumberFormatter.silver(pack.silverAmount),
                color = ScTheme.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            if (pack.bonusPercent > 0) {
                Text(
                    text = "+${pack.bonusPercent}% бонус",
                    color = ScTheme.success,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(pack.priceFormatted, color = ScTheme.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
