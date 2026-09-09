package com.silverchat.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.format.UsernameFormatter
import com.silverchat.core.common.format.UsernameValidation
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.usecase.auth.OtpVerificationParams
import com.silverchat.core.domain.usecase.auth.RegisterProfileUseCase
import com.silverchat.core.domain.usecase.auth.RegistrationParams
import com.silverchat.core.domain.usecase.auth.RequestOtpUseCase
import com.silverchat.core.domain.usecase.auth.VerifyOtpUseCase
import com.silverchat.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel авторизации: номер -> OTP -> регистрация профиля.
 *
 * Ключевые решения:
 *  1. **Один ViewModel на весь граф auth** (`hiltNavGraphViewModel`), а не по
 *     одному на экран: состояние «номер уже подтверждён» должно пережить
 *     переход с экрана OTP на экран регистрации.
 *  2. **Таймер повторной отправки живёт здесь, а не в UI**: при повороте
 *     экрана композабл пересоздаётся, а отсчёт должен продолжаться.
 *  3. **Автоотправка OTP при вводе последней цифры** — стандарт для таких
 *     экранов, экономит пользователю один тап.
 *  4. Все тексты ошибок — на русском, приходят из [ScResult.Failure].
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val requestOtp: RequestOtpUseCase,
    private val verifyOtp: VerifyOtpUseCase,
    private val registerProfile: RegisterProfileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    /* ── Шаг 1: номер телефона ────────────────────────────────────────── */

    fun onCountryChanged(dialCode: String) {
        _uiState.update { it.copy(countryDialCode = dialCode) }
    }

    fun onPhoneChanged(raw: String) {
        // Оставляем только цифры: маска форматируется в UI
        val digits = raw.filter { it.isDigit() }.take(MAX_PHONE_DIGITS)
        _uiState.update {
            it.copy(
                phoneDigits = digits,
                phoneError = null,
                canRequestOtp = digits.length >= MIN_PHONE_DIGITS,
            )
        }
    }

    fun submitPhone() {
        val state = _uiState.value
        if (state.requestOtpLoading) return
        if (!state.canRequestOtp) {
            _uiState.update { it.copy(phoneError = "Введите номер телефона полностью") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(requestOtpLoading = true, phoneError = null) }
            when (val result = requestOtp(state.fullPhone)) {
                is ScResult.Success -> {
                    val outcome = result.data
                    _uiState.update {
                        it.copy(
                            requestOtpLoading = false,
                            step = AuthStep.OTP,
                            verifiedPhone = outcome.phone,
                            isNewUser = outcome.isNewUser,
                            resendSecondsLeft = outcome.resendAfterSec.takeIf { s -> s > 0 } ?: RESEND_COOLDOWN_SEC,
                            otpExpiresInSec = outcome.codeExpiresInSec,
                        )
                    }
                    startCountdown()
                }

                is ScResult.Failure -> _uiState.update {
                    it.copy(requestOtpLoading = false, phoneError = result.error.message)
                }

                ScResult.Loading -> Unit
            }
        }
    }

    /* ── Шаг 2: подтверждение кода ────────────────────────────────────── */

    fun onOtpChanged(code: String) {
        val digits = code.filter { it.isDigit() }.take(OTP_LENGTH)
        _uiState.update { it.copy(otpCode = digits, otpError = null) }
        if (digits.length == OTP_LENGTH) submitOtp()
    }

    fun submitOtp() {
        val state = _uiState.value
        if (state.verifyLoading) return
        if (state.otpCode.length != OTP_LENGTH) {
            _uiState.update { it.copy(otpError = "Введите все $OTP_LENGTH цифры кода") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(verifyLoading = true, otpError = null) }
            val params = OtpVerificationParams(phone = state.verifiedPhone.orEmpty(), code = state.otpCode)
            when (val result = verifyOtp(params)) {
                is ScResult.Success -> {
                    val user = result.data
                    _uiState.update {
                        it.copy(
                            verifyLoading = false,
                            me = user,
                            // Новый пользователь обязан заполнить профиль;
                            // существующий попадает сразу в приложение
                            step = if (it.isNewUser) AuthStep.REGISTER else AuthStep.DONE,
                            // Предзаполняем форму регистрации данными сервера
                            firstName = user.firstName,
                            lastName = user.lastName.orEmpty(),
                            username = user.username.orEmpty(),
                        )
                    }
                    countdownJob?.cancel()
                }

                is ScResult.Failure -> _uiState.update {
                    it.copy(
                        verifyLoading = false,
                        otpCode = "",
                        otpError = result.error.message,
                    )
                }

                ScResult.Loading -> Unit
            }
        }
    }

    fun resendOtp() {
        if (_uiState.value.resendSecondsLeft > 0) return
        _uiState.update { it.copy(otpCode = "", otpError = null) }
        submitPhone()
    }

    fun backToPhone() {
        countdownJob?.cancel()
        _uiState.update { it.copy(step = AuthStep.PHONE, otpCode = "", otpError = null) }
    }

    /* ── Шаг 3: регистрация профиля ───────────────────────────────────── */

    fun onFirstNameChanged(name: String) {
        _uiState.update { it.copy(firstName = name.take(MAX_NAME_LENGTH), registerError = null) }
    }

    fun onLastNameChanged(name: String) {
        _uiState.update { it.copy(lastName = name.take(MAX_NAME_LENGTH), registerError = null) }
    }

    /**
     * Юзернейм валидируется по тем же правилам, что и в маркете, —
     * через [UsernameFormatter.validate]. Иначе пользователь занял бы ник,
     * который система считает невалидным.
     */
    fun onUsernameChanged(raw: String) {
        val sanitized = raw.lowercase()
            .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
            .take(MAX_USERNAME_LENGTH)

        val hint = when (val validation = UsernameFormatter.validate(sanitized)) {
            is UsernameValidation.Invalid -> validation.reason
            is UsernameValidation.Reserved -> validation.reason
            is UsernameValidation.Valid -> null
        }
        // Пустой ник допустим: его можно занять позже или купить в маркете
        val effectiveHint = if (sanitized.isEmpty()) null else hint

        _uiState.update {
            it.copy(username = sanitized, usernameError = effectiveHint, registerError = null)
        }
    }

    fun submitRegistration() {
        val state = _uiState.value
        if (state.registerLoading) return
        if (!state.canRegister) {
            _uiState.update {
                it.copy(registerError = "Укажите имя: минимум $MIN_NAME_LENGTH символа")
            }
            return
        }
        if (state.usernameError != null) {
            _uiState.update { it.copy(registerError = state.usernameError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(registerLoading = true, registerError = null) }
            val params = RegistrationParams(
                firstName = state.firstName.trim(),
                lastName = state.lastName.trim().ifBlank { null },
                username = state.username.ifBlank { null },
            )
            when (val result = registerProfile(params)) {
                is ScResult.Success -> _uiState.update {
                    it.copy(registerLoading = false, me = result.data, step = AuthStep.DONE)
                }

                is ScResult.Failure -> _uiState.update {
                    it.copy(registerLoading = false, registerError = result.error.message)
                }

                ScResult.Loading -> Unit
            }
        }
    }

    /** Пропустить заполнение профиля и войти с данными, которые вернул OTP. */
    fun skipRegistration() {
        if (_uiState.value.me != null) {
            _uiState.update { it.copy(step = AuthStep.DONE) }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_uiState.value.resendSecondsLeft > 0) {
                delay(1_000)
                _uiState.update {
                    it.copy(resendSecondsLeft = (it.resendSecondsLeft - 1).coerceAtLeast(0))
                }
            }
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val OTP_LENGTH = 6
        const val MIN_PHONE_DIGITS = 10
        const val MAX_PHONE_DIGITS = 15
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 64
        const val MAX_USERNAME_LENGTH = 32
        const val RESEND_COOLDOWN_SEC = 60
    }
}

/** Шаг авторизации — по нему UI решает, какой экран показать. */
enum class AuthStep { PHONE, OTP, REGISTER, DONE }

data class AuthUiState(
    val step: AuthStep = AuthStep.PHONE,

    /* номер */
    val countryDialCode: String = "+373",
    val phoneDigits: String = "",
    val phoneError: String? = null,
    val requestOtpLoading: Boolean = false,
    val canRequestOtp: Boolean = false,

    /* OTP */
    val otpCode: String = "",
    val otpError: String? = null,
    val otpExpiresInSec: Int = 0,
    val resendSecondsLeft: Int = 0,
    val verifyLoading: Boolean = false,

    /* регистрация */
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val usernameError: String? = null,
    val registerError: String? = null,
    val registerLoading: Boolean = false,

    /* результат */
    val verifiedPhone: String? = null,
    val isNewUser: Boolean = false,
    val me: User? = null,
) {
    /** Номер в формате E.164 для отправки на сервер. */
    val fullPhone: String
        get() {
            val dial = countryDialCode.filter { it.isDigit() }
            val digits = phoneDigits
            // Если пользователь ввёл код страны вместе с номером — не дублируем его
            return if (dial.isNotEmpty() && digits.startsWith(dial)) "+$digits" else "+$dial$digits"
        }

    val canRegister: Boolean
        get() = firstName.trim().length >= 2 && registerError == null && !registerLoading

    val resendAvailable: Boolean
        get() = resendSecondsLeft == 0 && !requestOtpLoading
}
