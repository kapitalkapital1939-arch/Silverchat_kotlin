package com.silverchat.feature.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.domain.usecase.wallet.PurchasePremiumUseCase
import com.silverchat.core.model.PremiumStatus
import com.silverchat.core.model.PremiumTier
import com.silverchat.core.model.PremiumTierId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Экран SilverChat Premium.
 *
 * Premium покупается за сильверы, а не за реальные деньги: это осознанное
 * решение экономики приложения — вся ценность замкнута на внутреннюю валюту,
 * которую можно заработать (стрики, продажа юзернеймов, подарки) или получить
 * от администрации `@silver`.
 *
 * Тарифы приходят с сервера ([WalletRepository.observePremiumTiers]), а не
 * зашиты в клиент: цены и состав перков меняются без релиза приложения.
 */
@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val purchasePremium: PurchasePremiumUseCase,
) : ViewModel() {

    /** Выбранный тариф; по умолчанию подсвечиваем «выгодный» от сервера. */
    private val selectedTierId = MutableStateFlow<PremiumTierId?>(null)

    private val isProcessing = MutableStateFlow(false)
    private val showConfirm = MutableStateFlow(false)

    private val _events = MutableStateFlow<PremiumEvent?>(null)
    val events: StateFlow<PremiumEvent?> = _events

    val uiState: StateFlow<PremiumUiState> = combine(
        walletRepository.observePremiumTiers(),
        walletRepository.observePremiumStatus(),
        walletRepository.observeWallet(),
        combine(selectedTierId, isProcessing, showConfirm) { tier, busy, confirm ->
            Interaction(tier, busy, confirm)
        },
    ) { tiers, status, wallet, interaction ->
        // Если пользователь ещё не выбрал тариф, подсвечиваем bestValue:
        // сервер помечает самый выгодный, и пустой выбор выглядел бы багом
        val selected = interaction.selectedTierId
            ?: tiers.firstOrNull { it.bestValue }?.id
            ?: tiers.firstOrNull()?.id

        PremiumUiState(
            tiers = tiers.sortedBy { it.periodDays },
            premiumStatus = status,
            isActive = status.isActive,
            selectedTierId = selected,
            selectedTier = tiers.firstOrNull { it.id == selected },
            balance = wallet?.available ?: 0L,
            isProcessing = interaction.isProcessing,
            showConfirm = interaction.showConfirm,
            expiresAt = status.expiresAtOrNull,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PremiumUiState(),
    )

    /** Внутренняя группировка полей (лимит `combine` — пять потоков). */
    private data class Interaction(
        val selectedTierId: PremiumTierId?,
        val isProcessing: Boolean,
        val showConfirm: Boolean,
    )

    fun selectTier(tierId: PremiumTierId) {
        selectedTierId.value = tierId
    }

    /**
     * Запрос подтверждения покупки.
     *
     * Проверка баланса здесь, а не в диалоге: показывать подтверждение,
     * которое заведомо закончится ошибкой, — лишний шаг для пользователя.
     */
    fun requestPurchase() {
        val tier = uiState.value.selectedTier ?: return
        if (uiState.value.balance < tier.priceSilver) {
            _events.value = PremiumEvent.InsufficientFunds(
                required = tier.priceSilver,
                available = uiState.value.balance,
            )
            return
        }
        showConfirm.value = true
    }

    fun dismissConfirm() { showConfirm.value = false }

    fun confirmPurchase() = viewModelScope.launch {
        val tier = uiState.value.selectedTier ?: return@launch
        showConfirm.value = false
        isProcessing.value = true

        when (val result = purchasePremium(tier.id)) {
            is ScResult.Success -> _events.value = PremiumEvent.Purchased(tier.title)
            is ScResult.Failure -> _events.value = PremiumEvent.Error(result.error.message)
            ScResult.Loading -> Unit
        }
        isProcessing.value = false
    }

    /**
     * Отмена автопродления.
     *
     * Подписка остаётся активной до конца оплаченного периода — это
     * требование добросовестной монетизации: деньги уже внесены.
     */
    fun cancelAutoRenew() = viewModelScope.launch {
        isProcessing.value = true
        when (walletRepository.cancelAutoRenew()) {
            is ScResult.Success -> _events.value = PremiumEvent.AutoRenewCancelled
            is ScResult.Failure -> _events.value = PremiumEvent.Error("Не удалось отменить продление")
            ScResult.Loading -> Unit
        }
        isProcessing.value = false
    }

    fun consumeEvent() { _events.value = null }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class PremiumUiState(
    val tiers: List<PremiumTier> = emptyList(),
    val premiumStatus: PremiumStatus = PremiumStatus.None,
    val isActive: Boolean = false,
    val selectedTierId: PremiumTierId? = null,
    val selectedTier: PremiumTier? = null,
    val balance: Long = 0L,
    val isProcessing: Boolean = false,
    val showConfirm: Boolean = false,
    val expiresAt: Long? = null,
) {
    val isEmpty: Boolean get() = tiers.isEmpty()

    val isLifetime: Boolean get() = premiumStatus == PremiumStatus.Lifetime

    val canPurchase: Boolean
        get() = !isProcessing && selectedTier != null && balance >= (selectedTier?.priceSilver ?: 0L)
}

sealed interface PremiumEvent {
    data class Purchased(val tierTitle: String) : PremiumEvent
    data class InsufficientFunds(val required: Long, val available: Long) : PremiumEvent
    data object AutoRenewCancelled : PremiumEvent
    data class Error(val message: String) : PremiumEvent
}

/** Короткое описание периода тарифа на русском. */
fun PremiumTier.periodRu(): String = when {
    periodDays <= 0 -> "Бессрочно"
    periodDays % 365 == 0 -> {
        val years = periodDays / 365
        "$years ${pluralYears(years)}"
    }

    periodDays % 30 == 0 -> {
        val months = periodDays / 30
        "$months ${pluralMonths(months)}"
    }

    else -> "$periodDays ${pluralDays(periodDays)}"
}

private fun pluralYears(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "год"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "года"
    else -> "лет"
}

private fun pluralMonths(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "месяц"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "месяца"
    else -> "месяцев"
}

private fun pluralDays(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "день"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "дня"
    else -> "дней"
}
