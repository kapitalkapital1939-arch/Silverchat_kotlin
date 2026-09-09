package com.silverchat.feature.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.AuthRepository
import com.silverchat.core.domain.repository.MarketRepository
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.domain.usecase.market.BuyUsernameParams
import com.silverchat.core.domain.usecase.market.BuyUsernameUseCase
import com.silverchat.core.domain.usecase.market.PrepareSaleUseCase
import com.silverchat.core.domain.usecase.wallet.ClaimStreakUseCase
import com.silverchat.core.model.Gift
import com.silverchat.core.model.ListingStatus
import com.silverchat.core.model.MarketFeed
import com.silverchat.core.model.MarketQuery
import com.silverchat.core.model.MarketSort
import com.silverchat.core.model.PremiumStatus
import com.silverchat.core.model.PurchaseResult
import com.silverchat.core.model.SaleRequest
import com.silverchat.core.model.Streak
import com.silverchat.core.model.UsernameCategory
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.UsernameRarity
import com.silverchat.core.model.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Витрина маркета.
 *
 * Экономика SilverChat — это не отдельный магазин, а часть мессенджера:
 * юзернейм покупается, чтобы им пользоваться в чатах. Поэтому состояние
 * экрана собирается из четырёх независимых источников (витрина, кошелёк,
 * статус Premium, стрик) и живёт в одном `StateFlow`.
 *
 * Решения:
 *  1. **Запрос витрины переключается через `flatMapLatest` по фильтрам.**
 *     Каждый фильтр — это новый `MarketQuery`, и старые результаты должны
 *     отменяться, а не складываться: иначе список «прыгал» бы между
 *     двумя сортировками.
 *  2. **Debounce на поиске** обязателен: витрина — серверный запрос,
 *     а набирать «alice» без задержки означало бы пять запросов.
 *  3. **Баланс в том же состоянии, что и лоты.** Кнопка «Купить» должна
 *     быть неактивна, если сильверов не хватает, — считать это в компоузле
 *     означало бы две независимые подписки и мерцание доступности.
 *  4. **Покупка — оптимистичная блокировка кнопки, а не оптимистичный
 *     баланс.** Списание денег нельзя показывать до подтверждения сервера:
 *     при отказе пользователь увидел бы «исчезнувшие» сильверы.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MarketViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val walletRepository: WalletRepository,
    private val authRepository: AuthRepository,
    private val buyUsername: BuyUsernameUseCase,
    private val prepareSale: PrepareSaleUseCase,
    private val claimStreak: ClaimStreakUseCase,
) : ViewModel() {

    private val search = MutableStateFlow("")
    private val sort = MutableStateFlow(MarketSort.POPULAR)
    private val categories = MutableStateFlow<Set<UsernameCategory>>(emptySet())
    private val rarities = MutableStateFlow<Set<UsernameRarity>>(emptySet())
    private val maxPrice = MutableStateFlow<Long?>(null)

    /**
     * ID текущего пользователя как состояние.
     *
     * `AuthRepository.currentUserId` — холодный `Flow`, у него нет `.value`,
     * а покупатель нужен синхронно в момент подтверждения покупки.
     * Материализуем поток один раз при создании ViewModel.
     */
    private val currentUserId: StateFlow<String?> = authRepository.currentUserId
        .map { it?.raw }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Лот, по которому идёт покупка: блокируем кнопку до ответа сервера. */
    private val purchasingListingId = MutableStateFlow<String?>(null)

    /** Диалог подтверждения покупки — деньги списываются только после «да». */
    private val confirmPurchase = MutableStateFlow<UsernameListing?>(null)

    /** Форма выставления своего юзернейма на продажу. */
    private val saleForm = MutableStateFlow(SaleForm())

    private val _events = MutableStateFlow<MarketEvent?>(null)
    val events: StateFlow<MarketEvent?> = _events

    /** Фильтры, собранные в один запрос. */
    private val query = combine(search, sort, categories, rarities, maxPrice) { q, s, c, r, mp ->
        MarketQuery(
            search = q.trim(),
            sort = s,
            categories = c.toList(),
            rarity = r.toList(),
            maxPriceSilver = mp,
        )
    }

    val uiState: StateFlow<MarketUiState> = combine(
        query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged()
            .flatMapLatest { marketRepository.observeFeed(it) },
        walletRepository.observeWallet(),
        walletRepository.observePremiumStatus(),
        walletRepository.observeStreak(),
        // Мгновенные значения фильтров — без debounce: чипы должны
        // подсвечиваться в тот же кадр, когда пользователь их нажал
        combine(query, confirmPurchase, purchasingListingId) { q, confirm, purchasing ->
            Selection(q, confirm, purchasing)
        },
    ) { feed, wallet, premium, streak, selection ->
        MarketUiState(
            feed = feed,
            wallet = wallet,
            premiumStatus = premium,
            streak = streak,
            availableBalance = wallet?.available ?: 0L,
            isPremium = premium.isActive,
            query = selection.query,
            purchasingListingId = selection.purchasingListingId,
            confirmPurchase = selection.confirmPurchase,
            saleForm = saleForm.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = MarketUiState(),
    )

    /**
     * Мгновенная выборка фильтров и диалога.
     *
     * Отдельный класс нужен из-за лимита `combine` в пять потоков:
     * лента, кошелёк, Premium, стрик — уже четыре, а фильтры, диалог
     * покупки и флаг «идёт покупка» добавляются пятым слотом.
     */
    private data class Selection(
        val query: MarketQuery,
        val confirmPurchase: UsernameListing?,
        val purchasingListingId: String?,
    )

    /* ── Фильтры ──────────────────────────────────────────────────────── */

    fun onSearchChanged(value: String) { search.value = value }

    fun onSortChanged(value: MarketSort) { sort.value = value }

    fun toggleCategory(category: UsernameCategory) {
        categories.value = categories.value.toggle(category)
    }

    fun toggleRarity(rarity: UsernameRarity) {
        rarities.value = rarities.value.toggle(rarity)
    }

    fun setMaxPrice(value: Long?) { maxPrice.value = value }

    fun resetFilters() {
        search.value = ""
        sort.value = MarketSort.POPULAR
        categories.value = emptySet()
        rarities.value = emptySet()
        maxPrice.value = null
    }

    /* ── Покупка ──────────────────────────────────────────────────────── */

    /**
     * Запрос подтверждения покупки.
     *
     * Список сильверов — реальная ценность, поэтому покупка в один тап
     * недопустима: случайное нажатие списало бы деньги безвозвратно.
     */
    fun requestPurchase(listing: UsernameListing) {
        if (listing.isReserved) {
            _events.value = MarketEvent.Error("Лот уже забронирован или продан")
            return
        }
        val balance = uiState.value.availableBalance
        if (balance < listing.priceSilver) {
            _events.value = MarketEvent.InsufficientFunds(
                required = listing.priceSilver,
                available = balance,
            )
            return
        }
        confirmPurchase.value = listing
    }

    fun dismissPurchaseDialog() { confirmPurchase.value = null }

    /** Подтверждённая покупка: списание выполняет сервер в одной транзакции. */
    fun confirmPurchase() = viewModelScope.launch {
        val listing = confirmPurchase.value ?: return@launch
        val buyerId = currentUserId.value?.let { com.silverchat.core.model.UserId(it) }
            ?: run {
                _events.value = MarketEvent.Error("Сессия не активна — войдите заново")
                return@launch
            }
        confirmPurchase.value = null
        purchasingListingId.value = listing.id.raw

        when (val result = buyUsername(
            BuyUsernameParams(
                listingId = listing.id,
                buyerId = buyerId,
                offerSilver = null,
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

                is PurchaseResult.Reserved -> _events.value = MarketEvent.Error(
                    "Лот забронирован до ${purchase.reservedUntil}",
                )

                PurchaseResult.Forbidden -> _events.value =
                    MarketEvent.Error("Покупка запрещена: юзернейм заблокирован администрацией")
            }

            is ScResult.Failure -> _events.value = MarketEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }

        purchasingListingId.value = null
    }

    /** Встречное предложение по цене — для лотов с `minOfferSilver`. */
    fun makeOffer(listing: UsernameListing, amountSilver: Long) = viewModelScope.launch {
        if (listing.minOfferSilver != null && amountSilver < listing.minOfferSilver) {
            _events.value = MarketEvent.Error(
                "Минимальная ставка: ${listing.minOfferSilver} сильверов",
            )
            return@launch
        }
        when (marketRepository.makeOffer(listing.id, amountSilver)) {
            is ScResult.Success -> _events.value = MarketEvent.OfferSent(amountSilver)
            is ScResult.Failure -> _events.value = MarketEvent.Error("Не удалось отправить предложение")
            ScResult.Loading -> Unit
        }
    }

    /* ── Продажа своего юзернейма ─────────────────────────────────────── */

    /**
     * Оценка юзернейма перед выставлением.
     *
     * Редкость и цена считаются на клиенте ([PrepareSaleUseCase]) для
     * мгновенной подсказки, но сервер пересчитывает их сам и имеет
     * последнее слово: иначе цену можно было бы подделать в запросе.
     */
    fun evaluateUsername(username: String) = viewModelScope.launch {
        if (username.isBlank()) return@launch
        saleForm.value = saleForm.value.copy(isEvaluating = true)
        when (val result = prepareSale(username)) {
            is ScResult.Success -> saleForm.value = saleForm.value.copy(
                username = result.data.username,
                suggestedPrice = result.data.suggestedPrice,
                rarity = result.data.rarity,
                isOwn = result.data.isOwn,
                isEvaluating = false,
            )

            is ScResult.Failure -> saleForm.value = saleForm.value.copy(
                isEvaluating = false,
                error = result.error.message,
            )

            ScResult.Loading -> Unit
        }
    }

    fun onSalePriceChanged(price: Long) {
        saleForm.value = saleForm.value.copy(price = price, error = null)
    }

    fun onSaleAcceptOffersChanged(accept: Boolean) {
        saleForm.value = saleForm.value.copy(acceptOffers = accept)
    }

    fun onSaleMinOfferChanged(minOffer: Long?) {
        saleForm.value = saleForm.value.copy(minOffer = minOffer)
    }

    /** Выставление юзернейма на продажу. */
    fun submitSale() = viewModelScope.launch {
        val form = saleForm.value
        if (form.username.isBlank()) {
            saleForm.value = form.copy(error = "Введите юзернейм")
            return@launch
        }
        if (form.price <= 0) {
            saleForm.value = form.copy(error = "Укажите цену больше нуля")
            return@launch
        }
        // Минимальная ставка ниже цены — бессмыслица, которую сервер
        // всё равно отвергнет; ловим раньше, чтобы не ждать ответа
        if (form.acceptOffers && form.minOffer != null && form.minOffer > form.price) {
            saleForm.value = form.copy(error = "Минимальная ставка не может превышать цену")
            return@launch
        }

        saleForm.value = form.copy(isSubmitting = true, error = null)
        when (val result = marketRepository.sell(
            SaleRequest(
                username = form.username,
                priceSilver = form.price,
                acceptOffers = form.acceptOffers,
                minOfferSilver = form.minOffer,
            ),
        )) {
            is ScResult.Success -> {
                saleForm.value = SaleForm()
                _events.value = MarketEvent.ListingCreated(result.data.username)
            }

            is ScResult.Failure -> saleForm.value = form.copy(
                isSubmitting = false,
                error = result.error.message,
            )

            ScResult.Loading -> Unit
        }
    }

    fun cancelListing(listing: UsernameListing) = viewModelScope.launch {
        when (marketRepository.cancelListing(listing.id)) {
            is ScResult.Success -> _events.value = MarketEvent.ListingCancelled(listing.username)
            is ScResult.Failure -> _events.value = MarketEvent.Error("Не удалось снять лот с продажи")
            ScResult.Loading -> Unit
        }
    }

    /* ── Стрик ────────────────────────────────────────────────────────── */

    /**
     * Дневное начисление.
     *
     * Клиент только отправляет запрос: сервер сверяет `lastClaimDate` по
     * часовому поясу пользователя. Локальная проверка «можно ли забрать»
     * невозможна — устройство может иметь сбитое время.
     */
    fun claimDailyStreak() = viewModelScope.launch {
        when (val result = claimStreak(Unit)) {
            is ScResult.Success -> _events.value = MarketEvent.StreakClaimed(
                days = result.data.streakDays,
                reward = result.data.nextRewardSilver,
            )

            is ScResult.Failure -> _events.value = MarketEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
    }

    fun consumeEvent() { _events.value = null }

    /* ── Вспомогательное ──────────────────────────────────────────────── */

    private fun <T> Set<T>.toggle(item: T): Set<T> =
        if (item in this) this - item else this + item

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class MarketUiState(
    val feed: MarketFeed = EMPTY_FEED,
    val wallet: Wallet? = null,
    val premiumStatus: PremiumStatus = PremiumStatus.None,
    val streak: Streak? = null,
    val availableBalance: Long = 0L,
    val isPremium: Boolean = false,
    /** Активные фильтры: экран подсвечивает чипы по ним, а не по выдаче. */
    val query: MarketQuery = MarketQuery(),
    val purchasingListingId: String? = null,
    val confirmPurchase: UsernameListing? = null,
    val saleForm: SaleForm = SaleForm(),
) {
    val listings: List<UsernameListing> get() = feed.listings
    val gifts: List<Gift> get() = feed.gifts
    val trending: List<String> get() = feed.trending
    val volume24h: Long get() = feed.volume24h
    val isEmpty: Boolean get() = feed.listings.isEmpty()

    /** Любой фильтр отличается от значения по умолчанию. */
    val hasActiveFilters: Boolean
        get() = query.search.isNotBlank() ||
            query.categories.isNotEmpty() ||
            query.rarity.isNotEmpty() ||
            query.maxPriceSilver != null ||
            query.sort != MarketSort.POPULAR

    private companion object {
        val EMPTY_FEED = MarketFeed(
            listings = emptyList(),
            gifts = emptyList(),
            premiumTiers = emptyList(),
        )
    }
}

/** Форма выставления юзернейма на продажу. */
data class SaleForm(
    val username: String = "",
    val price: Long = 0L,
    val suggestedPrice: Long = 0L,
    val minOffer: Long? = null,
    val acceptOffers: Boolean = true,
    val rarity: UsernameRarity = UsernameRarity.COMMON,
    val isOwn: Boolean = false,
    val isEvaluating: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

/** Одноразовые события экрана: результат покупки, ошибки, тосты. */
sealed interface MarketEvent {
    data class Purchased(val username: String, val newBalance: Long) : MarketEvent
    data class InsufficientFunds(val required: Long, val available: Long) : MarketEvent
    data class OfferSent(val amount: Long) : MarketEvent
    data class ListingCreated(val username: String) : MarketEvent
    data class ListingCancelled(val username: String) : MarketEvent
    data class StreakClaimed(val days: Int, val reward: Long) : MarketEvent
    data class Error(val message: String) : MarketEvent
}

/** Русская подпись сортировки витрины. */
val MarketSort.titleRu: String
    get() = when (this) {
        MarketSort.POPULAR -> "Популярные"
        MarketSort.NEWEST -> "Новые"
        MarketSort.PRICE_ASC -> "Дешевле"
        MarketSort.PRICE_DESC -> "Дороже"
        MarketSort.RARITY -> "По редкости"
        MarketSort.LENGTH -> "По длине"
    }

/** Русская подпись категории юзернейма для фильтра. */
val UsernameCategory.titleRu: String
    get() = when (this) {
        UsernameCategory.SHORT -> "Короткие"
        UsernameCategory.WORD -> "Слова"
        UsernameCategory.NAME -> "Имена"
        UsernameCategory.CRYPTO -> "Крипто"
        UsernameCategory.GAMING -> "Игровые"
        UsernameCategory.BUSINESS -> "Бизнес"
        UsernameCategory.NUMERIC -> "Числа"
        UsernameCategory.PALINDROME -> "Палиндромы"
        UsernameCategory.REPEATED -> "Повторы"
        UsernameCategory.PREMIUM_ONLY -> "Премиум"
    }

/** Удобный алиас: лот недоступен к покупке (продан, забронирован, заблокирован). */
val UsernameListing.isReserved: Boolean
    get() = status == ListingStatus.SOLD ||
        status == ListingStatus.RESERVED ||
        status == ListingStatus.BLOCKED
