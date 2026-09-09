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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
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
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.EmptyState
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.SilverIconButton
import com.silverchat.core.designsystem.component.SilverTextField
import com.silverchat.core.designsystem.component.SilverTopBar
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.component.market.GiftCard
import com.silverchat.core.designsystem.component.market.categoryColor
import com.silverchat.core.designsystem.navigation.MarketNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.GiftCategory
import com.silverchat.core.model.OwnedGift
import com.silverchat.core.model.User
import com.silverchat.core.model.labelRu
import com.silverchat.feature.market.GiftEvent
import com.silverchat.feature.market.GiftsViewModel

/**
 * Подарки за сильверы.
 *
 * Две вкладки в одном экране: **каталог** (что можно купить и отправить)
 * и **инвентарь** (что подарили мне, с возможностью конвертировать в сильверы).
 * Разделение на два маршрута не нужно: данные пересекаются — баланс и статус
 * Premium влияют на обе половины.
 *
 * Режим отправки ([sendMode]) включается, когда экран открыт с `giftId`
 * в аргументах: подарок уже выбран, и экран сразу показывает форму получателя.
 */
@Composable
fun GiftsScreen(
    navigator: MarketNavigator,
    modifier: Modifier = Modifier,
    sendMode: Boolean = false,
    viewModel: GiftsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recipients by viewModel.recipientResults.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(events) {
        // После успешной отправки предлагаем перейти в чат с получателем
        if (events is GiftEvent.Sent) navigator.back()
        if (events != null) viewModel.consumeEvent()
    }

    Box(modifier.fillMaxSize().background(ScTheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SilverTopBar(
                title = if (state.sendMode) "Отправить подарок" else "Подарки",
                subtitle = "Баланс: ${NumberFormatter.silver(state.balance)}",
                onBack = navigator::back,
                actions = {
                    if (!state.sendMode) {
                        SilverIconButton(
                            icon = Icons.Filled.Inventory,
                            contentDescription = if (state.showInventory) {
                                "Вернуться к каталогу"
                            } else {
                                "Мои подарки"
                            },
                            onClick = viewModel::toggleInventory,
                            tint = if (state.showInventory) ScTheme.accent else ScTheme.textPrimary,
                        )
                    } else if (state.selectedGift != null) {
                        SilverIconButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "Убрать выбранный подарок",
                            onClick = viewModel::clearGift,
                            tint = ScTheme.textPrimary,
                        )
                    }
                },
            )

            when {
                state.sendMode -> SendGiftForm(state = state, recipients = recipients, viewModel = viewModel)
                state.showInventory -> InventoryList(state = state, viewModel = viewModel)
                else -> GiftCatalog(state = state, viewModel = viewModel, navigator = navigator)
            }
        }

        GiftEventMessage(event = events, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/* =========================================================================
   КАТАЛОГ
   ========================================================================= */

@Composable
private fun GiftCatalog(
    state: com.silverchat.feature.market.GiftsUiState,
    viewModel: GiftsViewModel,
    navigator: MarketNavigator,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ScSpacing.xxl),
    ) {
        // ── Фильтры ──────────────────────────────────────────────────────
        item(key = "filters", contentType = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
            ) {
                items(GiftCategory.entries.toList(), key = { it.name }) { category ->
                    val selected = category == state.category
                    CategoryChip(
                        label = category.labelRu,
                        selected = selected,
                        color = categoryColor(category),
                        onClick = { viewModel.selectCategory(category) },
                    )
                }
            }
        }

        // ── Премиум-переключатель ────────────────────────────────────────
        item(key = "premium_filter", contentType = "premium") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScSpacing.md)
                    .clip(ScShapes.card)
                    .background(
                        if (state.premiumOnly) {
                            ScTheme.premium.copy(alpha = 0.14f)
                        } else {
                            ScTheme.surfaceElevated
                        },
                    )
                    .clickable(onClick = viewModel::togglePremiumOnly)
                    .padding(ScSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (state.premiumOnly) ScTheme.premium else ScTheme.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(ScSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Только эксклюзивные",
                        color = ScTheme.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (state.isPremium) {
                            "Вам доступны все подарки Premium"
                        } else {
                            "Доступны только с подпиской Premium"
                        },
                        color = ScTheme.textTertiary,
                        fontSize = 11.5.sp,
                    )
                }
                if (!state.isPremium && state.premiumOnly) {
                    Text(
                        text = "Оформить",
                        color = ScTheme.premium,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(ScShapes.chip)
                            .clickable { navigator.openPremium() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (state.gifts.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    icon = Icons.Filled.CardGiftcard,
                    title = "Подарков не найдено",
                    subtitle = "Измените категорию или отключите фильтр.",
                )
            }
        }

        // ── Сетка подарков ───────────────────────────────────────────────
        item(key = "grid", contentType = "grid") {
            // Вложенная сетка внутри LazyColumn: высота фиксирована по числу
            // рядов, иначе бесконечная прокрутка вложенного списка сломалась бы
            val columns = GRID_COLUMNS
            val rows = (state.gifts.size + columns - 1) / columns
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((rows * GIFT_CELL_HEIGHT_DP).dp),
                contentPadding = PaddingValues(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(ScSpacing.sm),
                userScrollEnabled = false,
            ) {
                items(state.gifts, key = { it.id }) { gift ->
                    GiftCard(
                        gift = gift,
                        availableBalance = state.balance,
                        isPremium = state.isPremium,
                        onClick = { viewModel.selectGift(gift) },
                        onSend = { viewModel.selectGift(gift) },
                    )
                }
            }
        }

        // ── Панель выбранного подарка ────────────────────────────────────
        state.selectedGift?.let { gift ->
            item(key = "selected", contentType = "selected") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(ScSpacing.md)
                        .clip(ScShapes.card)
                        .background(ScTheme.surfaceElevated)
                        .padding(ScSpacing.md),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${gift.emoji} ${gift.title}",
                            color = ScTheme.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = NumberFormatter.silver(gift.priceSilver),
                            color = ScTheme.silver,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Лимитированные подарки: остаток виден сразу
                    if (gift.isLimited) {
                        Text(
                            text = if (gift.soldOut) {
                                "Тираж закончился"
                            } else {
                                "Осталось ${gift.limitedLeft} из ${gift.limitedTotal}"
                            },
                            color = if (gift.soldOut) ScTheme.danger else ScTheme.warning,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    gift.convertibleSilver?.let { convertible ->
                        Text(
                            text = "Конвертация в сильверы: ${NumberFormatter.silver(convertible)}",
                            color = ScTheme.textTertiary,
                            fontSize = 11.5.sp,
                        )
                    }

                    Spacer(Modifier.height(ScSpacing.md))
                    SilverButton(
                        text = when {
                            gift.soldOut -> "Тираж закончился"
                            state.balance < gift.priceSilver ->
                                "Не хватает ${NumberFormatter.silver(gift.priceSilver - state.balance)}"

                            else -> "Отправить за ${NumberFormatter.silver(gift.priceSilver)}"
                        },
                        // selectGift переводит экран в режим отправки (sendMode)
                        onClick = { viewModel.selectGift(gift) },
                        enabled = !gift.soldOut && state.balance >= gift.priceSilver,
                    )
                }
            }
        }
    }
}

private const val GRID_COLUMNS = 2
private const val GIFT_CELL_HEIGHT_DP = 168

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) Color.White else color,
        fontSize = 12.5.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(ScShapes.chip)
            .background(if (selected) color else ScTheme.surfaceGlass)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

/* =========================================================================
   ОТПРАВКА
   ========================================================================= */

@Composable
private fun SendGiftForm(
    state: com.silverchat.feature.market.GiftsUiState,
    recipients: List<User>,
    viewModel: GiftsViewModel,
) {
    val gift = state.selectedGift

    Column(
        Modifier.fillMaxSize().padding(horizontal = ScSpacing.md),
        verticalArrangement = Arrangement.Top,
    ) {
        if (gift == null) {
            EmptyState(
                icon = Icons.Filled.CardGiftcard,
                title = "Подарок не выбран",
                subtitle = "Вернитесь в каталог и выберите подарок.",
                modifier = Modifier.fillMaxWidth().padding(top = ScSpacing.xxl),
            )
            return
        }

        // ── Выбранный подарок ────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .clip(ScShapes.card)
                .background(ScTheme.surfaceElevated)
                .padding(ScSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(categoryColor(gift.category).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(gift.emoji, fontSize = 24.sp)
            }
            Spacer(Modifier.width(ScSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(gift.title, color = ScTheme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = gift.category.labelRu,
                    color = ScTheme.textTertiary,
                    fontSize = 11.5.sp,
                )
            }
            Text(
                text = NumberFormatter.silver(gift.priceSilver),
                color = ScTheme.silver,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(ScSpacing.md))

        // ── Получатель ───────────────────────────────────────────────────
        if (state.recipient != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(ScShapes.card)
                    .background(ScTheme.surfaceElevated)
                    .padding(ScSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(user = state.recipient, size = ScAvatarSize.member)
                Spacer(Modifier.width(ScSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.recipient.fullName,
                        color = ScTheme.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    state.recipient.handle?.let {
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
            Column {
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
                        recipients.take(MAX_SUGGESTIONS).forEach { user ->
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
                                        overflow = TextOverflow.Ellipsis,
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
        }

        Spacer(Modifier.height(ScSpacing.md))

        SilverTextField(
            value = state.message,
            onValueChange = viewModel::onMessageChanged,
            label = "Сообщение (необязательно)",
            placeholder = "С днём рождения!",
            maxLength = MAX_MESSAGE_LENGTH,
            singleLine = false,
        )

        Spacer(Modifier.height(ScSpacing.sm))

        // Анонимность — важная опция: получатель не узнает отправителя
        Row(
            Modifier
                .fillMaxWidth()
                .clip(ScShapes.card)
                .background(ScTheme.surfaceElevated)
                .clickable(onClick = viewModel::toggleAnonymous)
                .padding(ScSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Отправить анонимно",
                    color = ScTheme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Получатель увидит подарок, но не узнает отправителя",
                    color = ScTheme.textTertiary,
                    fontSize = 11.5.sp,
                )
            }
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (state.anonymous) ScTheme.accent else ScTheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (state.anonymous) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        SilverButton(
            text = when {
                state.isProcessing -> "Отправляем…"
                state.recipient == null -> "Выберите получателя"
                else -> "Отправить за ${NumberFormatter.silver(state.selectedPrice)}"
            },
            onClick = viewModel::sendGift,
            enabled = state.canSend,
            loading = state.isProcessing,
            leadingIcon = Icons.Filled.Send,
            modifier = Modifier.padding(bottom = ScSpacing.md),
        )
        SilverButton(
            text = "Отмена",
            onClick = viewModel::clearGift,
            variant = SilverButtonVariant.TEXT,
            modifier = Modifier.padding(bottom = ScSpacing.md),
        )
    }
}

private const val MAX_SUGGESTIONS = 6
private const val MAX_MESSAGE_LENGTH = 200

/* =========================================================================
   ИНВЕНТАРЬ
   ========================================================================= */

@Composable
private fun InventoryList(
    state: com.silverchat.feature.market.GiftsUiState,
    viewModel: GiftsViewModel,
) {
    if (state.ownedGifts.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Inventory,
            title = "Подарков пока нет",
            subtitle = "Подаренные вам подарки появятся здесь.\nИх можно конвертировать в сильверы.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ScSpacing.xxl),
    ) {
        item(key = "hdr", contentType = "header") {
            Text(
                text = "Мои подарки · ${state.ownedGifts.size}",
                color = ScTheme.textTertiary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
            )
        }

        items(state.ownedGifts, key = { it.id }, contentType = { "owned" }) { owned ->
            OwnedGiftRow(
                owned = owned,
                isProcessing = state.isProcessing,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun OwnedGiftRow(
    owned: OwnedGift,
    isProcessing: Boolean,
    viewModel: GiftsViewModel,
) {
    val gift = owned.gift

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm)
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .padding(ScSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(categoryColor(gift.category).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(gift.emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(ScSpacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = gift.title,
                    color = ScTheme.textPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (owned.upgradeLevel > 1) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(ScShapes.chip)
                            .background(ScTheme.premium.copy(alpha = 0.16f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "ур. ${owned.upgradeLevel}",
                            color = ScTheme.premium,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                text = buildString {
                    append("Получен ")
                    append(TimeFormatter.daySeparator(owned.receivedAt))
                    if (owned.converted) append(" · конвертирован")
                },
                color = ScTheme.textTertiary,
                fontSize = 11.5.sp,
                maxLines = 1,
            )
        }

        // Конвертация доступна только для неподаренных назад и неконвертированных
        if (!owned.converted && gift.convertibleSilver != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = NumberFormatter.silver(gift.convertibleSilver),
                    color = ScTheme.silver,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Конвертировать",
                    color = ScTheme.accent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(ScShapes.chip)
                        .clickable(enabled = !isProcessing) {
                            viewModel.convertToSilver(owned)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        SilverIconButton(
            icon = Icons.Filled.Star,
            contentDescription = "Повысить уровень подарка",
            onClick = { viewModel.upgradeGift(owned) },
            tint = ScTheme.premium,
            enabled = !isProcessing && !owned.converted,
            size = 34,
        )
    }
}
