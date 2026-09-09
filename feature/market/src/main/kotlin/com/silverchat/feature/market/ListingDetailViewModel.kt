package com.silverchat.feature.market

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.domain.repository.AuthRepository
import com.silverchat.core.domain.repository.MarketRepository
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.model.ListingId
import com.silverchat.core.model.OfferStatus
import com.silverchat.core.model.PremiumStatus
import com.silverchat.core.model.PurchaseRequest
import com.silverchat.core.model.PurchaseResult
import com.silverchat.core.model.UserId
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.UsernameOffer
import com.silverchat.core.model.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Карточка лота и встречные предложения.
 *
 * Экран совмещает два режима (детали и список офферов), потому что данные
 * общие: лот, его цена, история цены и предложения покупателей. Держать
 * их в двух ViewModel означало бы два независимых источника для одной
 * сущности и рассинхрон при обновлении цены.
 *
 * **Скидка Premium** (`USERNAME_DISCOUNT`) применяется на клиенте только для
 * отображения итоговой суммы: сервер пересчитывает её сам в момент списания.
 * Показывать цену без учёта скидки нельзя — пользователь с Premium увидел бы
 * «свою» цену только в платёжном подтверждении.
 */
@HiltViewModel
class ListingDetailViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val walletRepository: WalletRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val listingId: String = savedStateHandle[Routes.Args.LISTING_ID] ?: ""
    private val id = ListingId(listingId)

    private val currentUserId: StateFlow<String?> = authRepository.currentUserId
        .map { it?.raw }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Сумма встречного предложения, введённая пользователем. */
    private val offerAmount = MutableStateFlow<Long?>(null)

    private val isProcessing = MutableStateFlow(false)
    private val showBuyDialog = MutableStateFlow(false)

    private val _events = MutableStateFlow<MarketEvent?>(null)
    val events: StateFlow<MarketEvent?> = _events

    val uiState: StateFlow<ListingDetailUiState> = combine(
        marketRepository.observeListing(id),
        marketRepository.observeOffers(id),
        walletRepository.observeWallet(),
        walletRepository.observePremiumStatus(),
        combine(offerAmount, isProcessing, showBuyDialog) { amount, busy, dialog ->
            Interaction(offerAmount = amount, isProcessing = busy, showBuyDialog = dialog)
        },
    ) { listing, offers, wallet, premium, interaction ->
        build(listing, offers, wallet, premium, interaction)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ListingDetailUiState(),
    )

    /** Внутренняя группировка полей ввода (лимит `combine` — пять потоков). */
    private data class Interaction(
        val offerAmount: Long?,
        val isProcessing: Boolean,
        val showBuyDialog: Boolean,
    )

    private fun build(
        listing: UsernameListing?,
        offers: List<UsernameOffer>,
        wallet: Wallet?,
        premium: PremiumStatus,
        interaction: Interaction,
    ): ListingDetailUiState {
        val balance = wallet?.available ?: 0L
        val basePrice = listing?.priceSilver ?: 0L
        // Скидка Premium — только для отображения; сервер считает сам
        val discount = if (premium.isActive) basePrice * PREMIUM_DISCOUNT_PERCENT / 100 else 0L

        return ListingDetailUiState(
            listing = listing,
            offers = offers.sortedByDescending { it.createdAt },
            wallet = wallet,
            premiumStatus = premium,
            availableBalance = balance,
            basePrice = basePrice,
            discountSilver = discount,
            finalPrice = basePrice - discount,
            isAffordable = balance >= basePrice - discount,
            myOffer = offers.firstOrNull {
                it.status == OfferStatus.PENDING && it.buyerId.raw == currentUserId.value
            },
            isSeller = listing?.sellerId?.raw == currentUserId.value,
            offerAmount = interaction.offerAmount,
            isProcessing = interaction.isProcessing,
            showBuyDialog = interaction.showBuyDialog,
            minOfferSilver = listing?.minOfferSilver,
        )
    }

    /* ── Покупка ──────────────────────────────────────────────────────── */

    fun requestPurchase() { showBuyDialog.value = true }

    fun dismissBuyDialog() { showBuyDialog.value = false }

    /**
     * Покупка по текущей цене.
     *
     * `useBalance = true` — списываем с кошелька; при нехватке сервер
     * вернёт `InsufficientFunds`, и мы покажем точную недостающую сумму.
     */
    fun confirmPurchase() = viewModelScope.launch {
        val buyerId = userIdOrNull() ?: return@launch
        showBuyDialog.value = false
        isProcessing.value = true

        when (val result = marketRepository.buy(
            PurchaseRequest(
                listingId = id,
                buyerId = buyerId,
                offerSilver = null,
                useBalance = true,
            ),
        )) {
            is ScResult.Success -> when (val purchase = result.data) {
                is PurchaseResult.Success -> _events.value = MarketEvent.Purchased(
                    username = purchase.listing.username,
                    newBalance = purchase.newBalance,
                )

                is PurchaseResult.InsufficientFunds -> _events.value = MarketEvent.InsufficientFunds(
                    required = purchase.required,
                    available = purchase.available,
                )

                PurchaseResult.AlreadyTaken -> _events.value =
                    MarketEvent.Error("Юзернейм уже куплен другим пользователем")

                is PurchaseResult.Reserved -> _events.value =
                    MarketEvent.Error("Лот забронирован: оплатите до истечения брони")

                PurchaseResult.Forbidden -> _events.value =
                    MarketEvent.Error("Покупка запрещена администрацией")
            }

            is ScResult.Failure -> _events.value = MarketEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
        isProcessing.value = false
    }

    /* ── Встречные предложения ────────────────────────────────────────── */

    fun onOfferAmountChanged(amount: Long?) { offerAmount.value = amount }

    /**
     * Отправка оффера.
     *
     * Проверяем минимальную ставку на клиенте: сервер всё равно отвергнет
     * заниженное предложение, но ждать ответа ради очевидной ошибки —
     * лишние секунды для пользователя.
     */
    fun sendOffer() = viewModelScope.launch {
        val amount = offerAmount.value
        if (amount == null || amount <= 0) {
            _events.value = MarketEvent.Error("Введите сумму предложения")
            return@launch
        }
        val min = uiState.value.minOfferSilver
        if (min != null && amount < min) {
            _events.value = MarketEvent.Error("Минимальная ставка: $min сильверов")
            return@launch
        }
        if (amount > uiState.value.availableBalance) {
            _events.value = MarketEvent.InsufficientFunds(
                required = amount,
                available = uiState.value.availableBalance,
            )
            return@launch
        }

        isProcessing.value = true
        when (marketRepository.makeOffer(id, amount)) {
            is ScResult.Success -> {
                offerAmount.value = null
                _events.value = MarketEvent.OfferSent(amount)
            }

            is ScResult.Failure -> _events.value = MarketEvent.Error("Не удалось отправить предложение")
            ScResult.Loading -> Unit
        }
        isProcessing.value = false
    }

    /** Продавец принимает оффер — юзернейм переходит покупателю. */
    fun acceptOffer(offer: UsernameOffer) = viewModelScope.launch {
        isProcessing.value = true
        when (marketRepository.acceptOffer(offer.id)) {
            is ScResult.Success -> _events.value = MarketEvent.ListingCancelled(offer.buyerName)
            is ScResult.Failure -> _events.value = MarketEvent.Error("Не удалось принять предложение")
            ScResult.Loading -> Unit
        }
        isProcessing.value = false
    }

    fun declineOffer(offer: UsernameOffer) = viewModelScope.launch {
        when (marketRepository.declineOffer(offer.id)) {
            is ScResult.Success -> Unit
            is ScResult.Failure -> _events.value = MarketEvent.Error("Не удалось отклонить предложение")
            ScResult.Loading -> Unit
        }
    }

    /** Снять собственный лот с продажи. */
    fun cancelListing() = viewModelScope.launch {
        val username = uiState.value.listing?.username ?: return@launch
        isProcessing.value = true
        when (marketRepository.cancelListing(id)) {
            is ScResult.Success -> _events.value = MarketEvent.ListingCancelled(username)
            is ScResult.Failure -> _events.value = MarketEvent.Error("Не удалось снять лот с продажи")
            ScResult.Loading -> Unit
        }
        isProcessing.value = false
    }

    fun consumeEvent() { _events.value = null }

    /**
     * ID текущего пользователя.
     *
     * Берём первое значение потока, а не кэш: на экране лота покупка —
     * разовое действие, и ждать состояния ради него не нужно.
     */
    private suspend fun userIdOrNull(): UserId? {
        val raw = currentUserId.value ?: authRepository.currentUserId.first()?.raw
        if (raw == null) {
            _events.value = MarketEvent.Error("Сессия не активна — войдите заново")
            return null
        }
        return UserId(raw)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Скидка Premium в маркете; сервер применяет её же при списании. */
        const val PREMIUM_DISCOUNT_PERCENT = 10L
    }
}

data class ListingDetailUiState(
    val listing: UsernameListing? = null,
    val offers: List<UsernameOffer> = emptyList(),
    val wallet: Wallet? = null,
    val premiumStatus: PremiumStatus = PremiumStatus.None,
    val availableBalance: Long = 0L,
    val basePrice: Long = 0L,
    val discountSilver: Long = 0L,
    val finalPrice: Long = 0L,
    val isAffordable: Boolean = false,
    val myOffer: UsernameOffer? = null,
    val isSeller: Boolean = false,
    val offerAmount: Long? = null,
    val isProcessing: Boolean = false,
    val showBuyDialog: Boolean = false,
    val minOfferSilver: Long? = null,
) {
    val isPremium: Boolean get() = premiumStatus.isActive
    val pendingOffers: List<UsernameOffer>
        get() = offers.filter { it.status == OfferStatus.PENDING }

    val canBuy: Boolean
        get() = listing != null && !listing.isReserved && !isSeller && !isProcessing

    val canOffer: Boolean
        get() = listing != null && !listing.isReserved && !isSeller && minOfferSilver != null
}
