package com.silverchat.feature.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.SearchRepository
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.domain.usecase.wallet.ClaimStreakUseCase
import com.silverchat.core.model.LedgerEntry
import com.silverchat.core.model.LedgerReason
import com.silverchat.core.model.Streak
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import com.silverchat.core.model.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Кошелёк: баланс, история операций, стрик и переводы.
 *
 * История операций (`ledger`) — единственный способ объяснить пользователю,
 * куда делись сильверы. Без неё списание за юзернейм выглядит как ошибка,
 * поэтому журнал показываем с фильтром по типу операции и догружаем
 * страницами по мере прокрутки: выгружать всю историю разом нельзя — у
 * активного пользователя там тысячи записей.
 *
 * Перевод требует получателя: поиск пользователей с debounce встроен прямо
 * в экран кошелька, чтобы не отправлять пользователя в отдельный поиск.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val searchRepository: SearchRepository,
    private val claimStreakUseCase: ClaimStreakUseCase,
) : ViewModel() {

    /** Фильтр журнала: все операции, только начисления, только списания. */
    private val ledgerFilter = MutableStateFlow(LedgerFilter.ALL)

    /** Догрузка истории: идёт ли запрос сейчас и остались ли ещё страницы. */
    private val ledgerPaging = MutableStateFlow(LedgerPaging())

    /** Поисковый запрос получателя перевода. */
    private val recipientQuery = MutableStateFlow("")

    private val transferAmount = MutableStateFlow<Long?>(null)
    private val transferComment = MutableStateFlow("")
    private val selectedRecipient = MutableStateFlow<User?>(null)

    /** Режим перевода включён — скрываем журнал, показываем форму. */
    private val transferMode = MutableStateFlow(false)

    private val isProcessing = MutableStateFlow(false)

    private val _events = MutableStateFlow<WalletEvent?>(null)
    val events: StateFlow<WalletEvent?> = _events

    val uiState: StateFlow<WalletUiState> = combine(
        walletRepository.observeWallet(),
        walletRepository.observeLedger(),
        walletRepository.observeStreak(),
        ledgerFilter,
        combine(
            transferMode,
            transferAmount,
            transferComment,
            selectedRecipient,
            isProcessing,
        ) { mode, amount, comment, recipient, busy ->
            TransferFormPublic(mode, amount, comment, recipient, busy)
        },
    ) { wallet, ledger, streak, filter, form ->
        WalletUiState(
            wallet = wallet,
            balance = wallet?.available ?: 0L,
            frozen = wallet?.frozen ?: 0L,
            earnedTotal = wallet?.earnedTotal ?: 0L,
            spentTotal = wallet?.spentTotal ?: 0L,
            ledger = ledger.filterBy(filter),
            filter = filter,
            streak = streak,
            transfer = form,
            recipientQuery = recipientQuery.value,
            recipientResults = emptyList(),
        )
    }.combine(recipientQuery) { state, query -> state.copy(recipientQuery = query) }
        /* Отдельный `combine`, а не шестой поток в основном: лимит в пять
         * аргументов у `combine` уже выбран, а вложенный `combine` для формы
         * перевода занимает пятый слот. */
        .combine(ledgerPaging) { state, paging -> state.copy(ledgerPaging = paging) }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = WalletUiState(),
    )

    /** Поиск получателя перевода — отдельный поток с debounce. */
    val recipientResults: StateFlow<List<User>> = recipientQuery
        .debounce(RECIPIENT_DEBOUNCE_MS)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else searchRepository.searchUsers(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Группировка полей формы перевода.
     *
     * Отдельный `combine` нужен из-за лимита в пять потоков: кошелёк, журнал,
     * стрик и фильтр уже занимают четыре слота.
     */

    private fun List<LedgerEntry>.filterBy(filter: LedgerFilter): List<LedgerEntry> =
        when (filter) {
            LedgerFilter.ALL -> this
            LedgerFilter.INCOME -> filter { it.amount > 0 }
            LedgerFilter.EXPENSE -> filter { it.amount < 0 }
        }

    /* ── Фильтры журнала ──────────────────────────────────────────────── */

    fun onFilterChanged(filter: LedgerFilter) { ledgerFilter.value = filter }

    /**
     * Догрузка страницы истории.
     *
     * Вызывается экраном, когда пользователь доскроллил до конца списка.
     * Повторный вызов во время запроса игнорируется: без этой проверки
     * `LaunchedEffect` отправлял бы новый запрос на каждую рекомпозицию
     * футера.
     *
     * Сбой догрузки не превращается в `WalletEvent.Error` намеренно: это
     * фоновая подкачка, а не действие пользователя, иSnackBar поверх журнала
     * из-за потерянной страницы — худшая из реакций. Состояние возвращается к
     * `hasMore = true`, поэтому следующий скролл к концу списка повторит
     * попытку.
     */
    fun onLoadMoreLedger() {
        val current = ledgerPaging.value
        if (current.isLoadingMore || !current.hasMore) return
        viewModelScope.launch {
            ledgerPaging.value = current.copy(isLoadingMore = true)
            ledgerPaging.value = when (val result = walletRepository.loadMoreLedger()) {
                is ScResult.Success -> LedgerPaging(hasMore = result.data)
                is ScResult.Failure -> LedgerPaging(hasMore = true)
                ScResult.Loading -> current
            }
        }
    }

    /* ── Стрик ────────────────────────────────────────────────────────── */

    /**
     * Дневное начисление.
     *
     * Клиент не проверяет «можно ли забрать»: сервер сверяет `lastClaimDate`
     * по часовому поясу пользователя, а время на устройстве может быть сбито.
     */
    fun claimStreak() = viewModelScope.launch {
        isProcessing.value = true
        when (val result = claimStreakUseCase(Unit)) {
            is ScResult.Success -> _events.value = WalletEvent.StreakClaimed(
                days = result.data.streakDays,
                reward = result.data.nextRewardSilver,
            )

            is ScResult.Failure -> _events.value = WalletEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
        isProcessing.value = false
    }

    /* ── Перевод ──────────────────────────────────────────────────────── */

    fun openTransfer() {
        transferMode.value = true
        selectedRecipient.value = null
        transferAmount.value = null
        transferComment.value = ""
    }

    fun closeTransfer() {
        transferMode.value = false
        recipientQuery.value = ""
    }

    fun onRecipientQueryChanged(value: String) { recipientQuery.value = value }

    /** Сброс получателя: возвращаем форму к поиску. */
    fun clearRecipient() {
        selectedRecipient.value = null
        recipientQuery.value = ""
    }

    fun selectRecipient(user: User) {
        selectedRecipient.value = user
        recipientQuery.value = ""
    }

    fun onAmountChanged(amount: Long?) { transferAmount.value = amount }

    fun onCommentChanged(text: String) {
        transferComment.value = text.take(MAX_COMMENT_LENGTH)
    }

    /**
     * Перевод сильверов другому пользователю.
     *
     * Валидация на клиенте дублирует серверную намеренно: сумма и получатель
     * очевидны пользователю до отправки, и ждать ответа сети ради сообщения
     * «введите сумму» — плохой UX. Сервер остаётся источником истины.
     */
    fun sendTransfer() = viewModelScope.launch {
        val recipient = selectedRecipient.value
        val amount = transferAmount.value

        if (recipient == null) {
            _events.value = WalletEvent.Error("Выберите получателя")
            return@launch
        }
        if (amount == null || amount <= 0) {
            _events.value = WalletEvent.Error("Введите сумму больше нуля")
            return@launch
        }
        if (amount > uiState.value.balance) {
            _events.value = WalletEvent.Error("Недостаточно сильверов: доступно ${uiState.value.balance}")
            return@launch
        }
        if (amount < MIN_TRANSFER) {
            _events.value = WalletEvent.Error("Минимальная сумма перевода: $MIN_TRANSFER")
            return@launch
        }

        isProcessing.value = true
        when (val result = walletRepository.transfer(
            toUserId = UserId(recipient.id.raw),
            amount = amount,
            comment = transferComment.value.trim().ifBlank { null },
        )) {
            is ScResult.Success -> {
                _events.value = WalletEvent.TransferSent(recipient.fullName, amount)
                closeTransfer()
            }

            is ScResult.Failure -> _events.value = WalletEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
        isProcessing.value = false
    }

    fun consumeEvent() { _events.value = null }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RECIPIENT_DEBOUNCE_MS = 350L
        const val MAX_COMMENT_LENGTH = 140

        /** Порог защищает от переводов по 1 сильверу: комиссия съела бы смысл. */
        const val MIN_TRANSFER = 10L
    }
}

/**
 * Состояние догрузки истории операций.
 *
 * [hasMore] по умолчанию `true`: до первого ответа сервера экран обязан
 * показывать футер догрузки, иначе журнал из одной страницы выглядел бы
 * законченным, а доскроллить до «ещё» пользователь бы не смог.
 */
data class LedgerPaging(
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
)

/** Фильтр журнала операций. */
enum class LedgerFilter(val titleRu: String) {
    ALL("Все"),
    INCOME("Начисления"),
    EXPENSE("Списания"),
}

data class WalletUiState(
    val wallet: Wallet? = null,
    val balance: Long = 0L,
    val frozen: Long = 0L,
    val earnedTotal: Long = 0L,
    val spentTotal: Long = 0L,
    val ledger: List<LedgerEntry> = emptyList(),
    val filter: LedgerFilter = LedgerFilter.ALL,
    val ledgerPaging: LedgerPaging = LedgerPaging(),
    val streak: Streak? = null,
    val transfer: TransferFormPublic = TransferFormPublic(),
    /** Текст поиска получателя — нужен экрану для отрисовки поля. */
    val recipientQuery: String = "",
    val recipientResults: List<User> = emptyList(),
) {
    /** Сильверы в escrow на время сделки — их нельзя тратить. */
    val hasFrozen: Boolean get() = frozen > 0

    val canClaimStreak: Boolean get() = streak?.claimable == true
}

/** Публичная форма перевода (без внутренних полей ViewModel). */
data class TransferFormPublic(
    val mode: Boolean = false,
    val amount: Long? = null,
    val comment: String = "",
    val recipient: User? = null,
    val isProcessing: Boolean = false,
)

sealed interface WalletEvent {
    data class TransferSent(val recipientName: String, val amount: Long) : WalletEvent
    data class StreakClaimed(val days: Int, val reward: Long) : WalletEvent
    data class Error(val message: String) : WalletEvent
}

/** Русская подпись причины операции в журнале. */
val LedgerReason.titleRu: String
    get() = when (this) {
        LedgerReason.SIGNUP_BONUS -> "Приветственный бонус"
        LedgerReason.DAILY_BONUS -> "Дневной бонус"
        LedgerReason.STREAK_REWARD -> "Награда за стрик"
        LedgerReason.GIFT_RECEIVED -> "Подарок получен"
        LedgerReason.GIFT_CONVERTED -> "Подарок конвертирован"
        LedgerReason.USERNAME_SALE -> "Продажа юзернейма"
        LedgerReason.USERNAME_PURCHASE -> "Покупка юзернейма"
        LedgerReason.USERNAME_OFFER -> "Встречное предложение"
        LedgerReason.GIFT_PURCHASE -> "Покупка подарка"
        LedgerReason.PREMIUM_PURCHASE -> "Покупка Premium"
        LedgerReason.ADMIN_GRANT -> "Начисление администрацией"
        LedgerReason.ADMIN_REVOKE -> "Списание администрацией"
        LedgerReason.REFUND -> "Возврат средств"
        LedgerReason.TRANSFER -> "Перевод"
        LedgerReason.REFERRAL -> "Реферальная награда"
    }
