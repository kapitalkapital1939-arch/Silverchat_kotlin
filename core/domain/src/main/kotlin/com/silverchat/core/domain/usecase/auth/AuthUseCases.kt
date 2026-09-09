package com.silverchat.core.domain.usecase.auth

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.format.UsernameFormatter
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.base.SuspendUseCase
import com.silverchat.core.domain.repository.AuthRepository
import com.silverchat.core.model.User
import javax.inject.Inject

/* =========================================================================
   UseCase'ы авторизации.
   ------------------------------------------------------------------------
   Тонкая обёртка над AuthRepository, но НЕ бесполезная: здесь живёт
   валидация входа (номер, OTP, юзернейм) и нормализация ошибок в русский
   текст. ViewModel получает готовый к показу [ScResult] и не знает правил.
   ========================================================================= */

/**
 * Запрос OTP-кода на номер телефона.
 *
 * Валидация номера — на клиенте: бессмысленно тратить SMS-квоту сервера
 * на заведомо неверный номер. Формат E.164 ( leading «+», до 15 цифр).
 */
class RequestOtpUseCase @Inject constructor(
    private val auth: AuthRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<String, OtpRequestOutcome>(dispatchers) {

    override suspend fun execute(params: String): ScResult<OtpRequestOutcome> {
        val phone = params.trim()
        val digits = phone.filter { it.isDigit() }

        if (!phone.startsWith("+")) {
            return ScResult.Failure(
                ScError.Validation(
                    message = "Номер должен начинаться с «+»",
                    field = "phone",
                ),
            )
        }
        if (digits.length < MIN_DIGITS || digits.length > MAX_DIGITS) {
            return ScResult.Failure(
                ScError.Validation(
                    message = "Проверьте длину номера: от $MIN_DIGITS до $MAX_DIGITS цифр",
                    field = "phone",
                ),
            )
        }

        return auth.sendOtp(phone).map { ticket ->
            OtpRequestOutcome(
                phone = ticket.phone,
                isNewUser = ticket.isNewUser,
                codeExpiresInSec = ticket.expiresInSec,
                resendAfterSec = ticket.nextRetryInSec,
            )
        }
    }

    private companion object {
        const val MIN_DIGITS = 10
        const val MAX_DIGITS = 15
    }
}

/** Результат запроса кода: сколько ждать и новый ли это пользователь. */
data class OtpRequestOutcome(
    val phone: String,
    val isNewUser: Boolean,
    val codeExpiresInSec: Int,
    val resendAfterSec: Int,
)

/**
 * Проверка OTP-кода.
 *
 * Код — ровно [OTP_LENGTH] цифр. Клиентская проверка формата важна:
 * при 3 попытках ввода бессмысленно сжигать попытку на заведомо коротком коде.
 */
class VerifyOtpUseCase @Inject constructor(
    private val auth: AuthRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<OtpVerificationParams, User>(dispatchers) {

    override suspend fun execute(params: OtpVerificationParams): ScResult<User> {
        if (params.code.length != OTP_LENGTH || params.code.any { !it.isDigit() }) {
            return ScResult.Failure(
                ScError.Validation(
                    message = "Код состоит из $OTP_LENGTH цифр",
                    field = "otp",
                ),
            )
        }
        return auth.verifyOtp(params.phone, params.code)
    }

    private companion object {
        const val OTP_LENGTH = 6
    }
}

data class OtpVerificationParams(
    val phone: String,
    val code: String,
)

/**
 * Регистрация профиля после подтверждения номера.
 *
 * Правила юзернейма едины для всего приложения и живут в
 * [UsernameFormatter.validate]: маркет, поиск и регистрация обязаны
 * проверять ник одинаково, иначе пользователь займёт ник в профиле,
 * который маркет считает невалидным.
 *
 * @silver зарезервирован под мастер-аккаунт — см. [MasterAccount].
 */
class RegisterProfileUseCase @Inject constructor(
    private val auth: AuthRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<RegistrationParams, User>(dispatchers) {

    override suspend fun execute(params: RegistrationParams): ScResult<User> {
        val firstName = params.firstName.trim()
        val lastName = params.lastName?.trim()?.takeIf { it.isNotEmpty() }
        val username = params.username?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        if (firstName.length < MIN_NAME_LENGTH) {
            return ScResult.Failure(
                ScError.Validation(
                    message = "Имя должно содержать минимум $MIN_NAME_LENGTH символа",
                    field = "firstName",
                ),
            )
        }
        if (firstName.length > MAX_NAME_LENGTH) {
            return ScResult.Failure(
                ScError.Validation(
                    message = "Имя не длиннее $MAX_NAME_LENGTH символов",
                    field = "firstName",
                ),
            )
        }

        if (username != null) {
            when (val validation = UsernameFormatter.validate(username)) {
                is UsernameValidationInvalid -> return ScResult.Failure(
                    ScError.Validation(message = validation.reason, field = "username"),
                )

                is UsernameValidationReserved -> return ScResult.Failure(
                    ScError.Conflict("Юзернейм @$username недоступен"),
                )

                else -> Unit
            }
        }

        return auth.register(firstName = firstName, lastName = lastName, username = username)
    }

    private companion object {
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 64
    }
}

data class RegistrationParams(
    val firstName: String,
    val lastName: String? = null,
    val username: String? = null,
)

/* Локальные алиасы, чтобы не тянуть тип из :core:common в when-ветки. */
private typealias UsernameValidationInvalid =
    com.silverchat.core.common.format.UsernameValidation.Invalid

private typealias UsernameValidationReserved =
    com.silverchat.core.common.format.UsernameValidation.Reserved

/**
 * Выход из аккаунта.
 *
 * Отдельный UseCase (а не прямой вызов репозитория) потому, что логаут
 * обязан также разорвать WebSocket и очистить очереди отправки —
 * иначе сообщения «зависнут» в локальной БД и уйдут при следующем входе.
 */
class LogoutUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val sync: com.silverchat.core.domain.repository.SyncRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<Unit, Unit>(dispatchers) {

    override suspend fun execute(params: Unit): ScResult<Unit> {
        // Сначала рвём реалтайм-канал: пока он жив, сервер продолжает
        // присылать события уже разлогиненному клиенту
        sync.disconnect()
        return auth.logout()
    }
}
