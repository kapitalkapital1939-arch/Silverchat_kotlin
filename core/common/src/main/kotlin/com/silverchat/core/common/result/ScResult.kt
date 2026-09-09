package com.silverchat.core.common.result

/**
 * Результат операции без исключений в сигнатуре.
 *
 * Правило проекта: репозитории и UseCase'ы возвращают [ScResult], а НЕ бросают
 * исключения. Compose-слой не умеет корректно обрабатывать исключения из Flow,
 * а попытка словить их в collect приводит к «тихим» падениям UI.
 */
sealed interface ScResult<out T> {

    data class Success<out T>(val data: T) : ScResult<T>

    data class Failure(val error: ScError) : ScResult<Nothing>

    data object Loading : ScResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw ScException(error)
        Loading -> error("ScResult is still Loading")
    }

    fun <R> map(transform: (T) -> R): ScResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
        Loading -> Loading
    }

    fun onSuccess(block: (T) -> Unit): ScResult<T> = apply {
        if (this is Success) block(data)
    }

    fun onFailure(block: (ScError) -> Unit): ScResult<T> = apply {
        if (this is Failure) block(error)
    }

    companion object {
        fun <T> success(data: T): ScResult<T> = Success(data)
        fun failure(error: ScError): ScResult<Nothing> = Failure(error)
    }
}

/**
 * Классификация ошибок.
 *
 * Клиенту важно отличать «нет сети» (показать оффлайн-баннер и повторить)
 * от «401» (разлогиниться) и от «403» (показать причину). Поэтому все ошибки
 * нормализуются в [ScError] ещё в :core:network, а не ловятся в UI.
 */
sealed class ScError(open val message: String, open val cause: Throwable? = null) {

    /** Нет сети / таймаут / DNS. Retry возможен. */
    data class Network(
        override val message: String = "Нет соединения",
        override val cause: Throwable? = null,
        val retryAfterMs: Long? = null,
    ) : ScError(message, cause)

    /** HTTP 401 / протухший refresh-токен -> требуется повторный вход. */
    data class Unauthorized(override val message: String = "Сессия истекла") : ScError(message)

    /** HTTP 403 / недостаточно прав (например, не админ). */
    data class Forbidden(
        override val message: String = "Недостаточно прав",
        val requiredPermission: String? = null,
    ) : ScError(message)

    /** HTTP 404. */
    data class NotFound(override val message: String = "Не найдено") : ScError(message)

    /** HTTP 409 — конфликт (юзернейм уже занят, сообщение удалено и т.п.). */
    data class Conflict(override val message: String = "Конфликт") : ScError(message)

    /** HTTP 422 — сервер отверг данные; [field] указывает конкретное поле. */
    data class Validation(
        override val message: String,
        val field: String? = null,
    ) : ScError(message)

    /** HTTP 429. */
    data class RateLimited(
        override val message: String = "Слишком много запросов",
        val retryAfterMs: Long? = null,
    ) : ScError(message)

    /** Недостаточно сильверов для покупки. */
    data class InsufficientFunds(
        override val message: String = "Недостаточно сильверов",
        val required: Long = 0L,
        val available: Long = 0L,
    ) : ScError(message)

    /** Premium-функция без подписки -> UI показывает paywall. */
    data class PremiumRequired(
        override val message: String = "Доступно в SilverChat Premium",
        val perk: String? = null,
    ) : ScError(message)

    /** Ошибка сервера 5xx. */
    data class Server(
        override val message: String = "Ошибка сервера",
        val code: String? = null,
    ) : ScError(message)

    /** Локальная ошибка: БД, шифрование, файловая система. */
    data class Local(
        override val message: String,
        override val cause: Throwable? = null,
    ) : ScError(message, cause)

    /** Пользователь отменил операцию — НЕ показывать как ошибку. */
    data object Canceled : ScError("Операция отменена")

    /** Неизвестная ошибка. */
    data class Unknown(
        override val message: String = "Неизвестная ошибка",
        override val cause: Throwable? = null,
    ) : ScError(message, cause)
}

class ScException(val error: ScError) : Exception(error.message, error.cause)

/** Обёртка для suspend-функций: ловит всё и нормализует в [ScResult]. */
inline fun <T> scResult(block: () -> T): ScResult<T> = try {
    ScResult.Success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e // отмену корутины глотать нельзя — сломаем structured concurrency
} catch (e: Exception) {
    ScResult.Failure(e.toScError())
}

/**
 * Классификация локального исключения в [ScError].
 *
 * ── Порядок веток критичен ──────────────────────────────────────────────
 * `SocketTimeoutException`, `UnknownHostException` и `SSLException` —
 * наследники `IOException`, а `when` останавливается на первом совпадении.
 * Поэтому общий `IOException` обязан стоять ПОСЛЕ частных: иначе три
 * уточняющих ветки становятся недостижимыми и пользователь видит «Нет
 * соединения» вместо «Превышено время ожидания», хотя разница для него
 * существенна — в первом случае надо проверить сеть, во втором подождать.
 *
 * Компилятор такое не ловит: формально все ветки синтаксически достижимы,
 * а семантику наследования он не анализирует. Отсюда тест
 * `ScErrorMappingTest` — единственная защита от обратной перестановки.
 */
fun Throwable.toScError(): ScError = when (this) {
    is ScException -> error
    is kotlinx.coroutines.CancellationException -> throw this
    // Частные случаи IOException — ДО общего, см. KDoc.
    is java.net.SocketTimeoutException -> ScError.Network(message = "Превышено время ожидания", cause = this)
    is java.net.UnknownHostException -> ScError.Network(message = "Сервер недоступен", cause = this)
    is javax.net.ssl.SSLException -> ScError.Network(message = "Ошибка защищённого соединения", cause = this)
    is java.io.IOException -> ScError.Network(cause = this)
    else -> ScError.Unknown(cause = this)
}
