package com.silverchat.feature.market

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.domain.repository.MarketRepository
import com.silverchat.core.domain.repository.SearchRepository
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.model.Gift
import com.silverchat.core.model.GiftCategory
import com.silverchat.core.model.GiftTransaction
import com.silverchat.core.model.OwnedGift
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Подарки за сильверы.
 *
 * Подарок — это сообщение: после отправки он появляется в чате как
 * анимированный контент, а не остаётся «в инвентаре». Поэтому отправка
 * возвращает `Message`, и экран сразу предлагает перейти в диалог.
 *
 * Экран совмещает витрину и режим отправки ([sendMode]): состав данных
 * одинаков, различается только финальное действие. Две ViewModel дублировали
 * бы загрузку каталога и баланса.
 *
 * Лимитированные подарки показывают остаток (`limitedLeft`): дефицит —
 * часть экономики, и скрывать его означало бы обесценить редкие позиции.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GiftsViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val walletRepository: WalletRepository,
    private val searchRepository: SearchRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val giftIdArg: String? = savedStateHandle[Routes.Args.GIFT_ID]

    /** Показывать только премиум-подарки. */
    private val premiumOnly = MutableStateFlow(false)

    /** Фильтр по категории. */
    private val category = MutableStateFlow<GiftCategory?>(null)

    private val selectedGiftId = MutableStateFlow(giftIdArg)
    private val recipientQuery = MutableStateFlow("")
    private val selectedRecipient = MutableStateFlow<User?>(null)
    private val message = MutableStateFlow("")
    private val anonymous = MutableStateFlow(false)
    private val isProcessing = MutableStateFlow(false)Flow(false)

    /** Вкладка: каталог или мой инвентарь. */
    private val showInventory = MutableStateFlow(false)

    private val _events = MutableStateFlow<GiftEvent?>(null)
    val events: StateFlow<GiftEvent?> = _events

    val uiState: StateFlow<GiftsUiState> = combine(
        premiumOnly.flatMapLatest { marketRepository.observeGifts(it) },
        marketRepository.observeMyGifts(),
        walletRepository.observeWallet(),
        walletRepository.observePremiumStatus(),
        combine(selectedGiftId, selectedRecipient, message, anonymous, isProcessing) {
                gift, recipient, msg, anon, busy ->
            SendForm(gift, recipient, msg, anon, busy)
        },
        recipientQuery,
    ) { gifts, owned, wallet, premium, form, query ->
        build(gifts, owned, wallet?.available ?: 0L, premium.isActive, form, query)
    }.combine(combine(category, showInventory, premiumOnly) { c, inv, po -> Triple(c, inv, po) }) { state, (cat, inv, po) ->
        state.copy(
            gifts = state.allGifts.filterByCategory(cat),
            category = cat,
            showInventory = inv,
            premiumOnly = po,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = GiftsUiState(),
    )

    /** Поиск получателя подарка. */
    val recipientResults: StateFlow<List<User>> = recipientQuery
        .debounce(RECIPIENT_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else searchRepository.searchUsers(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Внутренняя группировка полей формы (лимит `combine` — пять потоков). */
    private data class SendForm(
        val giftId: String?,
        val recipient: User?,
        val message: String,
        val anonymous: Boolean,
        val isProcessing: Boolean,
    )

    private fun build(
        gifts: List<Gift>,
        owned: List<OwnedGift>,
        balance: Long,
        isPremium: Boolean,
        form: SendForm,
        query: String,
    ): GiftsUiState {
        val selected = gifts.firstOrNull { it.id == form.giftId }
        return GiftsUiState(
            allGifts = gifts,
            gifts = gifts,
            ownedGifts = owned.sortedByDescending { it.receivedAt },
            balance = balance,
            isPremium = isPremium,
            selectedGift = selected,
            selectedGiftId = form.giftId,
            recipient = form.recipient,
            message = form.message,
            anonymous = form.anonymous,
            isProcessing = form.isProcessing,
            sendMode = giftIdArg != null || form.giftId != null,
            recipientQuery = query,
        )
    }

    private fun List<Gift>.filterByCategory(cat: GiftCategory?): List<Gift> =
        if (cat == null) this else filter { it.category == cat }

    /* ── Фильтры и вкладки ────────────────────────────────────────────── */

    fun togglePremiumOnly() { premiumOnly.value = !premiumOnly.value }

    fun selectCategory(value: GiftCategory?) {
        category.value = if (category.value == value) null else value
    }

    fun toggleInventory() { showInventory.value = !showInventory.value }

    /* ── Выбор подарка и получателя ───────────────────────────────────── */

    fun selectGift(gift: Gift) {
        selectedGiftId.value = gift.id
    }

    fun clearGift() {
        selectedGiftId.value = null
        selectedRecipient.value = null
        recipientQuery.value = ""
    }

    fun onRecipientQueryChanged(value: String) { recipientQuery.value = value }

    fun selectRecipient(user: User) {
        selectedRecipient.value = user
        recipientQuery.value = ""
    }

    fun clearRecipient() {
        selectedRecipient.value = null
        recipientQuery.value = ""
    }

    fun onMessageChanged(text: String) {
        message.value = text.take(MAX_MESSAGE_LENGTH)
    }

    /** Анонимная отправка: получатель не узнает, от кого подарок. */
    fun toggleAnonymous() { anonymous.value = !anonymous.value }

    /* ── Отправка ─────────────────────────────────────────────────────── */

    /**
     * Отправка подарка.
     *
     * Валидация до запроса: подарок и получатель обязательны, а баланс
     * проверяется локально, чтобы не показывать диалог заведомо провальной
     * покупки. Сервер повторяет все проверки — клиентская экономит трафик.
     */
    fun sendGift() = viewModelScope.launch {
        val gift = uiState.value.selectedGift
        val recipient = selectedRecipient.value

        if (gift == null) {
            _events.value = GiftEvent.Error("Выберите подарок")
            return@launch
        }
        if (recipient == null) {
            _events.value = GiftEvent.Error("Выберите получателя")
            return@launch
        }
        if (gift.premiumOnly && !uiState.value.isPremium) {
            _events.value = GiftEvent.PremiumRequired(gift.title)
            return@launch
        }
        if (gift.soldOut) {
            _events.value = GiftEvent.Error("Подарок закончился")
            return@launch
        }
        if (uiState.value.balance < gift.priceSilver) {
            _events.value = GiftEvent.InsufficientFunds(
                required = gift.priceSilver,
                available = uiState.value.balance,
            )
            return@launch
        }

        isProcessing.value = true
        when (val result = marketRepository.sendGift(
            GiftTransaction(
                giftId = gift.id,
                receiverId = UserId(recipient.id.raw),
                message = message.value.trim().ifBlank { null },
                anonymous = anonymous.value,
            ),
        )) {
            is ScResult.Success -> {
                _events.value = GiftEvent.Sent(gift.title, recipient.fullName, result.data.chatId.raw)
                selectedGiftId.value = null
                selectedRecipient.value = null
                message.value = ""
                anonymous.value = false
            }

            is ScResult.Failure -> _events.value = GiftEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
        isProcessing.value = false
    }

    /* ── Инвентарь ────────────────────────────────────────────────────── */

    /**
     * Конвертация подарка в сильверы.
     *
     * Возвращается меньше цены покупки: иначе подарок стал бы способом
     * бесконечно выводить сильверы, обойдя комиссию маркета.
     */
    fun convertToSilver(owned: OwnedGift) = viewModelScope.launch {
        if (owned.converted) return@launch
        isProcessing.value = true
        when (val result = marketRepository.convertGiftToSilver(owned.id)) {
            is ScResult.Success -> _events.value = GiftEvent.Converted(result.data)
            is ScResult.Failure -> _events.value = GiftEvent.Error("Не удалось конвертировать подарок")
            ScResult.Loading -> Unit
        }
        isProcessing.value = false
    }

    /** Повышение уровня подарка — стоит сильверов, увеличивает ценность. */
    fun upgradeGift(owned: OwnedGift) = viewModelScope.launch {
        isProcessing.value = true
        when (val result = marketRepository.upgradeGift(owned.id)) {
            is ScResult.Success -> _events.value = GiftEvent.Upgraded(result.data.gift.title, result.data.upgradeLevel)
            is ScResult.Failure -> _events.value = GiftEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
        isProcessing.value = false
    }

    fun consumeEvent() { _events.value = null }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RECIPIENT_DEBOUNCE_MS = 350L
        const val MAX_MESSAGE_LENGTH = 200
    }
}

data class GiftsUiState(
    val allGifts: List<Gift> = emptyList(),
    val gifts: List<Gift> = emptyList(),
    val ownedGifts: List<OwnedGift> = emptyList(),
    val balance: Long = 0L,
    val isPremium: Boolean = false,
    val selectedGift: Gift? = null,
    val selectedGiftId: String? = null,
    val recipient: User? = null,
    val message: String = "",
    val anonymous: Boolean = false,
    val isProcessing: Boolean = false,
    val sendMode: Boolean = false,
    val category: GiftCategory? = null,
    val showInventory: Boolean = false,
    val premiumOnly: Boolean = false,
    /** Текст поиска получателя — нужен экрану для отрисовки поля. */
    val recipientQuery: String = "",
) {
    val isEmpty: Boolean get() = gifts.isEmpty() && ownedGifts.isEmpty()

    val canSend: Boolean
        get() = !isProcessing && selectedGift != null && recipient != null &&
            balance >= (selectedGift?.priceSilver ?: 0L)

    val selectedPrice: Long get() = selectedGift?.priceSilver ?: 0L
}

sealed interface GiftEvent {
    data class Sent(val giftTitle: String, val recipientName: String, val chatId: String) : GiftEvent
    data class Converted(val silver: Long) : GiftEvent
    data class Upgraded(val giftTitle: String, val level: Int) : GiftEvent
    data class InsufficientFunds(val required: Long, val available: Long) : GiftEvent
    data class PremiumRequired(val giftTitle: String) : GiftEvent
    data class Error(val message: String) : GiftEvent
}
