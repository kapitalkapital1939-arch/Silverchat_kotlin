package com.silverchat.core.network.mapper

import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.common.result.toScError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Обёртка над сетевым вызовом, понимающая HTTP-статусы.
 *
 * ── Зачем отдельная функция, а не улучшение `scResult` ───────────────────
 * `scResult` живёт в `:core:common`, а тот не знает и не должен знать про
 * Retrofit: иначе модуль с `DispatcherProvider` и форматтерами тащил бы
 * HTTP-стек. Поэтому отображение ошибок HTTP добавлено здесь, в
 * `:core:network`, а `:core:data` использует [apiCall] вместо `scResult`.
 *
 * Без этого любой ответ `4xx`/`5xx` превращался в `ScError.Unknown`:
 * `Throwable.toScError()` разбирает только семейство `IOException`.
 * Интерфейс не мог отличить «юзернейм уже занят» от «сервер упал» и показывал
 * в обоих случаях одно и то же сообщение, а `ScError.InsufficientFunds` и
 * `ScError.PremiumRequired` не возникали из сети вовсе.
 *
 * Порядок веток важен: [HttpException] проверяется ДО общего `Exception`,
 * иначе он попадёт в fallback и смысл статуса снова потеряется.
 *
 * Отмена корутины пробрасывается, а не превращается в ошибку: проглотить
 * `CancellationException` — значит сломать structured concurrency.
 */
inline fun <T> apiCall(block: () -> T): ScResult<T> = try {
    ScResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    ScResult.Failure(e.toScErrorFromHttp())
} catch (e: Exception) {
    ScResult.Failure(e.toScError())
}

/**
 * Сетевой вызов с автоматическим повтором при неопределённом исходе.
 *
 * ── Зачем ───────────────────────────────────────────────────────────────
 * Таймаут или обрыв соединения не означают, что операция не выполнена:
 * запрос мог дойти до сервера и быть там обработан, а ответ — потеряться на
 * обратном пути. Если просто показать ошибку, пользователь нажмёт ещё раз —
 * уже с новым `Idempotency-Key`, и сервер честно спишет второй раз.
 *
 * Повтор безопасен именно потому, что ключ создаётся ДО вызова и захватывается
 * замыканием [block]: все попытки одного вызова уходят с одним и тем же
 * ключом, а сервер возвращает первоначальный результат вместо второго
 * списания. Без ключа этот повтор был бы вреден — отсюда и требование
 * применять [apiCallIdempotent] только к операциям, передающим заголовок
 * `Idempotency-Key`.
 *
 * ── Что НЕ повторяется ──────────────────────────────────────────────────
 * Только ошибки с неопределённым исходом — см. [isUnknownOutcome].
 * `Conflict`, `InsufficientFunds` и `Validation` — определённые ответы:
 * действие не выполнено, и повтор с теми же данными даст тот же результат,
 * потратив лишь время пользователя. `RateLimited` тоже не повторяется здесь:
 * у него есть `retryAfterMs`, и паузу показывает интерфейс, а не сетевой слой.
 *
 * Пауза растёт линейно. Экспоненциальный рост при двух попытках выигрыша не
 * даёт, а линейный проще соотносить с логами.
 */
suspend inline fun <T> apiCallIdempotent(
    attempts: Int = IDEMPOTENT_ATTEMPTS,
    block: () -> T,
): ScResult<T> {
    var attempt = 1
    var result = apiCall(block)
    while (result.isUnknownOutcome() && attempt < attempts) {
        delay(RETRY_BACKOFF_MS * attempt)
        attempt += 1
        ScLogger.w(
            LogTag.HTTP,
            "Исход неопределён: повтор $attempt из $attempts с тем же Idempotency-Key",
        )
        result = apiCall(block)
    }
    return result
}

/**
 * Неопределён ли исход операции.
 *
 * `true` для [ScError.Network] и [ScError.Server]: запрос мог быть выполнен,
 * а подтверждение — утеряно. Повтор допустим только с тем же ключом
 * идемпотентности.
 *
 * `false` для всех прочих вариантов, включая [ScResult.Loading] и
 * [ScError.Canceled]: там либо ответ уже получен, либо повторять нечего.
 * Отмена корутины сюда не попадает намеренно — [apiCall] её пробрасывает, и
 * до этой проверки дело не доходит.
 *
 * Функция публичная по той же причине, что и [toScErrorFromHttp]: она
 * вызывается из тела inline-[apiCallIdempotent] и должна быть видна в точке
 * подстановки.
 */
fun ScResult<*>.isUnknownOutcome(): Boolean = when (this) {
    is ScResult.Success -> false
    is ScResult.Loading -> false
    is ScResult.Failure -> error is ScError.Network || error is ScError.Server
}

/**
 * Конверт ошибки сервера.
 *
 * Нормативное описание — в `backend-contract/07-errors.md`. Все поля
 * необязательны: сервер может вернуть только `code`, и клиент обязан это
 * пережить. Лишние поля игнорируются (`ignoreUnknownKeys`), поэтому сервер
 * может расширять конверт без согласования.
 *
 * `required`, `available` и `perk` лежат здесь же, а не в отдельной модели,
 * потому что они приходят только вместе с конкретными кодами и дублировать
 * конверт ради трёх полей смысла нет.
 *
 * Поля `details` намеренно нет: оно потребовало бы `JsonElement` в публичной
 * сигнатуре, а `kotlinx-serialization-json` подключён к `:core:network` как
 * `implementation`, то есть на classpath потребителей его нет. Сервер может
 * передавать `details` — клиент его проигнорирует благодаря
 * `ignoreUnknownKeys`.
 */
@Serializable
data class ApiErrorBody(
    @SerialName("code") val code: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("field") val field: String? = null,
    @SerialName("retry_after_ms") val retryAfterMs: Long? = null,
    @SerialName("required") val required: Long? = null,
    @SerialName("available") val available: Long? = null,
    @SerialName("perk") val perk: String? = null,
)

/**
 * Разбор `HttpException` в доменную ошибку.
 *
 * Функция публичная, потому что вызывается из inline-[apiCall]: приватный
 * символ был бы недоступен в точке подстановки без `@PublishedApi`.
 */
fun HttpException.toScErrorFromHttp(): ScError {
    val status = code()
    val body = parseErrorBody()
    val code = body?.code
    val message = body?.message ?: message()

    /* Бизнес-код приоритетнее статуса.
     *
     * Сервер может ответить 200 с телом ошибки (покупка отклонена — это не
     * сбой протокола) или 403 с кодом `premium_required`, который точнее
     * любого статуса. Проверка кода первой гарантирует, что уточнение не
     * потеряется за общей классификацией. */
    when (code) {
        CODE_INSUFFICIENT_FUNDS -> return ScError.InsufficientFunds(
            message = message,
            required = body?.required ?: 0L,
            available = body?.available ?: 0L,
        )

        CODE_PREMIUM_REQUIRED -> return ScError.PremiumRequired(
            message = message,
            perk = body?.perk,
        )

        CODE_RATE_LIMITED -> return ScError.RateLimited(
            message = message,
            retryAfterMs = body?.retryAfterMs ?: retryAfterHeader(),
        )
    }

    return when (status) {
        // 413 и 415 — отказ принять полезную нагрузку (слишком большой файл,
        // неподдерживаемый MIME). Это валидация запроса, а не сбой сервера:
        // повтор без изменения данных бессмыслен.
        400, 413, 415, 422 -> ScError.Validation(message = message, field = body?.field)
        401 -> ScError.Unauthorized(message = message)
        403 -> ScError.Forbidden(message = message, requiredPermission = code)
        404, 410 -> ScError.NotFound(message = message)
        409 -> ScError.Conflict(message = message)
        402 -> ScError.InsufficientFunds(
            message = message,
            required = body?.required ?: 0L,
            available = body?.available ?: 0L,
        )
        429 -> ScError.RateLimited(
            message = message,
            retryAfterMs = body?.retryAfterMs ?: retryAfterHeader(),
        )
        in 500..599 -> ScError.Server(message = message, code = code)
        else -> ScError.Unknown(message = "HTTP $status: $message")
    }
}

/**
 * Разбор тела ошибки.
 *
 * Ошибка разбора не пробрасывается: тело может быть пустым, HTML-страницей
 * балансировщика или обрезанным. В любом из этих случаев статус сам по себе
 * уже несёт достаточно смысла, а падение внутри обработчика ошибок — худший
 * из возможных исходов.
 */
private fun HttpException.parseErrorBody(): ApiErrorBody? = runCatching {
    val raw = response()?.errorBody()?.string()
    if (raw.isNullOrBlank()) return@runCatching null
    ERROR_JSON.decodeFromString(ApiErrorBody.serializer(), raw)
}.onFailure { e ->
    ScLogger.w(LogTag.HTTP, "Тело ошибки не разобрано (HTTP ${code()}): ${e.message}")
}.getOrNull()

/**
 * `Retry-After` из заголовка, приведённый к миллисекундам.
 *
 * Заголовок по RFC 9110 измеряется в СЕКУНДАХ, и отступать от этого нельзя:
 * его выставляют не только наше приложение, но и балансировщики, CDN и
 * промежуточные прокси. Миллисекундная точность берётся из тела ответа
 * (`retry_after_ms`), а заголовок — запасной вариант, когда тело отсутствует
 * или не разобрано.
 *
 * Значение-дата (`Retry-After: Wed, 21 Oct 2026 07:28:00 GMT`) намеренно не
 * поддерживается: `toLongOrNull` вернёт null, и клиент просто не покажет
 * обратный отсчёт. Точность до секунды в мессенджере не нужна, а разбор HTTP-даты
 * добавил бы зависимость от локали.
 */
private fun HttpException.retryAfterHeader(): Long? = runCatching {
    response()?.headers()?.get(HEADER_RETRY_AFTER)?.toLongOrNull()?.times(MS_IN_SECOND)
}.getOrNull()

private val ERROR_JSON = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
}

private const val HEADER_RETRY_AFTER = "Retry-After"
private const val MS_IN_SECOND = 1000L

/* Параметры повтора при неопределённом исходе. `@PublishedApi`, а не `private`:
 * оба значения попадают в тело inline-функции и в её аргумент по умолчанию,
 * а значит подставляются в код потребителя и должны быть ему доступны. */
@PublishedApi
internal const val IDEMPOTENT_ATTEMPTS = 2

@PublishedApi
internal const val RETRY_BACKOFF_MS = 400L

/* Бизнес-коды, которые клиент распознаёт явно. Полный словарь — в
 * backend-contract/07-errors.md; всё нераспознанное обрабатывается по статусу. */
private const val CODE_INSUFFICIENT_FUNDS = "insufficient_funds"
private const val CODE_PREMIUM_REQUIRED = "premium_required"
private const val CODE_RATE_LIMITED = "rate_limited"
