package com.silverchat.feature.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.format.UsernameFormatter
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.MarketRepository
import com.silverchat.core.domain.usecase.market.PrepareSaleUseCase
import com.silverchat.core.model.SaleRequest
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.UsernameRarity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Выставление собственного юзернейма на продажу.
 *
 * Ключевая особенность — **оценка до публикации**. Пользователь редко знает,
 * сколько стоит его ник, поэтому [PrepareSaleUseCase] считает редкость и
 * предлагает цену. Расчёт дублирует серверную логику намеренно: подсказка
 * должна появляться мгновенно, а не после round-trip. Сервер всё равно
 * пересчитывает цену и имеет последнее слово — иначе её подделали бы в запросе.
 *
 * Продажа юзернейма необратима для чатов: ник переходит покупателю, а все
 * ссылки `silverchat://u/{username}` начинают вести на нового владельца.
 * Поэтому форма требует явного подтверждения и показывает предупреждение.
 */
@HiltViewModel
class SellUsernameViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
    private val prepareSale: PrepareSaleUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SellUiState())
    val uiState: StateFlow<SellUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<SellEvent?>(null)
    val events: StateFlow<SellEvent?> = _events

    /* ── Ввод ─────────────────────────────────────────────────────────── */

    fun onUsernameChanged(value: String) {
        val normalized = UsernameFormatter.strip(value)?.lowercase().orEmpty()
        _uiState.update {
            it.copy(username = normalized.take(MAX_USERNAME_LENGTH), errorText = null)
        }
        // Подсказку о цене сбрасываем: она относится к прежнему нику
        if (_uiState.value.suggestion != null) {
            _uiState.update { it.copy(suggestion = null) }
        }
    }

    fun onPriceChanged(price: Long) {
        _uiState.update { it.copy(price = price.coerceAtLeast(0), errorText = null) }
    }

    fun onMinOfferChanged(minOffer: Long?) {
        _uiState.update { it.copy(minOffer = minOffer?.coerceAtLeast(0)) }
    }

    fun toggleAcceptOffers() {
        _uiState.update {
            it.copy(
                acceptOffers = !it.acceptOffers,
                // Минимальная ставка бессмысленна без приёма предложений
                minOffer = if (it.acceptOffers) null else it.minOffer,
            )
        }
    }

    fun toggleAuction() {
        _uiState.update { it.copy(auction = !it.auction) }
    }

    fun onDurationChanged(hours: Int?) {
        _uiState.update { it.copy(durationHours = hours) }
    }

    /* ── Оценка ───────────────────────────────────────────────────────── */

    /**
     * Проверка доступности и расчёт рекомендованной цены.
     *
     * Вызывается по кнопке, а не на каждый символ: проверка идёт на сервер,
     * и автоматический режим породил бы запрос на каждую клавишу.
     */
    fun evaluate() = viewModelScope.launch {
        val username = _uiState.value.username.trim()
        val validation = UsernameFormatter.validate(username)
        if (!validation.isValid) {
            _uiState.update { it.copy(errorText = validation.reasonOrNull()) }
            return@launch
        }

        _uiState.update { it.copy(isEvaluating = true, errorText = null) }
        when (val result = prepareSale(username)) {
            is ScResult.Success -> {
                val suggestion = result.data
                _uiState.update {
                    it.copy(
                        isEvaluating = false,
                        suggestion = suggestion,
                        // Подставляем рекомендованную цену, только если поле пустое:
                        // перезаписывать введённое вручную значение нельзя
                        price = if (it.price <= 0) suggestion.suggestedPrice else it.price,
                        rarity = suggestion.rarity,
                    )
                }
            }

            is ScResult.Failure -> _uiState.update {
                it.copy(isEvaluating = false, errorText = result.error.message)
            }

            ScResult.Loading -> Unit
        }
    }

    /* ── Публикация лота ──────────────────────────────────────────────── */

    /**
     * Выставление на продажу.
     *
     * Валидация на клиенте дублирует серверную: сообщения об очевидных
     * ошибках («введите цену») не должны ждать ответа сети.
     */
    fun submit() = viewModelScope.launch {
        val state = _uiState.value
        if (state.isSubmitting) return@launch

        val username = state.username.trim()
        val validation = UsernameFormatter.validate(username)
        if (!validation.isValid) {
            _uiState.update { it.copy(errorText = validation.reasonOrNull()) }
            return@launch
        }
        if (state.price < MIN_PRICE) {
            _uiState.update { it.copy(errorText = "Минимальная цена: $MIN_PRICE сильверов") }
            return@launch
        }
        if (state.acceptOffers && state.minOffer != null && state.minOffer > state.price) {
            _uiState.update {
                it.copy(errorText = "Минимальная ставка не может превышать цену")
            }
            return@launch
        }
        if (state.auction && state.durationHours == null) {
            _uiState.update { it.copy(errorText = "Для аукциона укажите длительность") }
            return@launch
        }

        _uiState.update { it.copy(isSubmitting = true, errorText = null) }
        when (val result = marketRepository.sell(
            SaleRequest(
                username = username,
                priceSilver = state.price,
                acceptOffers = state.acceptOffers,
                minOfferSilver = state.minOffer,
                auction = state.auction,
                durationHours = state.durationHours,
            ),
        )) {
            is ScResult.Success -> {
                _uiState.update { SellUiState() }
                _events.value = SellEvent.Created(result.data)
            }

            is ScResult.Failure -> _uiState.update {
                it.copy(isSubmitting = false, errorText = result.error.message)
            }

            ScResult.Loading -> Unit
        }
    }

    fun consumeEvent() { _events.value = null }

    fun dismissError() { _uiState.update { it.copy(errorText = null) } }

    private companion object {
        const val MAX_USERNAME_LENGTH = 32

        /**
         * Порог цены.
         *
         * Нулевая цена означала бы мгновенную передачу ника первому
         * нажавшему — это не продажа, а раздача, и сервер её отвергает.
         */
        const val MIN_PRICE = 100L
    }
}

data class SellUiState(
    val username: String = "",
    val price: Long = 0L,
    val minOffer: Long? = null,
    val acceptOffers: Boolean = true,
    val auction: Boolean = false,
    val durationHours: Int? = null,
    val suggestion: com.silverchat.core.domain.usecase.market.SaleSuggestion? = null,
    val rarity: UsernameRarity = UsernameRarity.COMMON,
    val isEvaluating: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorText: String? = null,
) {
    val canEvaluate: Boolean
        get() = !isEvaluating && username.length >= MIN_EVALUATE_LENGTH

    val canSubmit: Boolean
        get() = !isSubmitting && !isEvaluating &&
            username.length >= MIN_EVALUATE_LENGTH && price > 0

    /** Оценка есть и ник принадлежит пользователю — можно публиковать. */
    val isEvaluated: Boolean get() = suggestion != null && suggestion.isOwn

    private companion object {
        const val MIN_EVALUATE_LENGTH = 4
    }
}

sealed interface SellEvent {
    data class Created(val listing: UsernameListing) : SellEvent
}

/**
 * Текст причины отказа валидации.
 *
 * `UsernameValidation` — sealed-интерфейс, и поле `reason` есть только у
 * `Invalid` и `Reserved`; у `Valid` его нет. Сводим к одному геттеру,
 * чтобы не писать `when` в каждом месте использования.
 */
private fun com.silverchat.core.common.format.UsernameValidation.reasonOrNull(): String? =
    when (this) {
        is com.silverchat.core.common.format.UsernameValidation.Invalid -> reason
        is com.silverchat.core.common.format.UsernameValidation.Reserved -> reason
        com.silverchat.core.common.format.UsernameValidation.Valid -> null
    }
