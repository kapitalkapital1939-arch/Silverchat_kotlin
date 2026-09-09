package com.silverchat.core.network.mapper

import com.google.common.truth.Truth.assertThat
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Отображение HTTP-ответов в [ScError].
 *
 * Проверяются два правила, которые легко сломать правкой одного `when`:
 *
 *  1. **бизнес-код приоритетнее статуса** — сервер может ответить `403` с кодом
 *     `insufficient_funds`, и интерфейс обязан показать суммы, а не
 *     «недостаточно прав»;
 *  2. **`Retry-After` измеряется в секундах** (RFC 9110), а миллисекундная
 *     точность берётся из поля `retry_after_ms` в теле. Раньше заголовок
 *     читался как миллисекунды, и обратный отсчёт после `429` заканчивался
 *     мгновенно.
 */
class ApiCallTest {

    /* ── Статусы ───────────────────────────────────────────────────────── */

    @Test
    fun status400BecomesValidation() {
        val error = errorOf(400, """{"code":"bad_request","message":"Пустое имя"}""")

        assertThat(error).isInstanceOf(ScError.Validation::class.java)
        assertThat(error.message).isEqualTo("Пустое имя")
    }

    @Test
    fun status413And415BecomeValidationNotServer() {
        /* Отказ принять полезную нагрузку — это валидация запроса, а не сбой
         * сервера: повтор без изменения данных бессмыслен. */
        assertThat(errorOf(413)).isInstanceOf(ScError.Validation::class.java)
        assertThat(errorOf(415)).isInstanceOf(ScError.Validation::class.java)
    }

    @Test
    fun status422CarriesFieldName() {
        val error = errorOf(422, """{"code":"invalid","field":"username"}""") as ScError.Validation

        assertThat(error.field).isEqualTo("username")
    }

    @Test
    fun status401BecomesUnauthorized() {
        assertThat(errorOf(401)).isInstanceOf(ScError.Unauthorized::class.java)
    }

    @Test
    fun status402CarriesRequiredAndAvailableAmounts() {
        val body = """{"code":"insufficient_funds","required":2500,"available":1250}"""

        val error = errorOf(402, body) as ScError.InsufficientFunds

        assertThat(error.required).isEqualTo(2500L)
        assertThat(error.available).isEqualTo(1250L)
    }

    @Test
    fun status403CarriesRequiredPermission() {
        val error = errorOf(403, """{"code":"admin_required"}""") as ScError.Forbidden

        assertThat(error.requiredPermission).isEqualTo("admin_required")
    }

    @Test
    fun status404And410BecomeNotFound() {
        assertThat(errorOf(404)).isInstanceOf(ScError.NotFound::class.java)
        assertThat(errorOf(410)).isInstanceOf(ScError.NotFound::class.java)
    }

    @Test
    fun status409BecomesConflict() {
        assertThat(errorOf(409)).isInstanceOf(ScError.Conflict::class.java)
    }

    @Test
    fun status5xxBecomesServerAndKeepsCode() {
        val error = errorOf(503, """{"code":"db_unavailable","message":"База недоступна"}""")

        assertThat(error).isInstanceOf(ScError.Server::class.java)
        assertThat((error as ScError.Server).code).isEqualTo("db_unavailable")
    }

    @Test
    fun unmappedStatusBecomesUnknown() {
        /* 418 — статуса нет в таблице. Классифицировать его как Validation
         * или Server было бы выдумкой: клиент показывает общий случай. */
        assertThat(errorOf(418)).isInstanceOf(ScError.Unknown::class.java)
    }

    /* ── Бизнес-коды важнее статуса ────────────────────────────────────── */

    @Test
    fun insufficientFundsCodeOverridesForbiddenStatus() {
        val body = """{"code":"insufficient_funds","required":100,"available":10}"""

        val error = errorOf(403, body)

        assertThat(error).isInstanceOf(ScError.InsufficientFunds::class.java)
    }

    @Test
    fun premiumRequiredCodeOverridesForbiddenStatusAndKeepsPerk() {
        val body = """{"code":"premium_required","perk":"animated_avatar"}"""

        val error = errorOf(403, body) as ScError.PremiumRequired

        assertThat(error.perk).isEqualTo("animated_avatar")
    }

    @Test
    fun rateLimitedCodeOverridesBadRequestStatus() {
        val error = errorOf(400, """{"code":"rate_limited","retry_after_ms":4200}""")

        assertThat(error).isInstanceOf(ScError.RateLimited::class.java)
    }

    /* ── Retry-After ───────────────────────────────────────────────────── */

    @Test
    fun retryAfterHeaderIsMeasuredInSeconds() {
        val error = errorOf(429, headers = arrayOf("Retry-After", "3")) as ScError.RateLimited

        assertThat(error.retryAfterMs).isEqualTo(3_000L)
    }

    @Test
    fun bodyRetryAfterMsWinsOverHeader() {
        val error = errorOf(
            status = 429,
            body = """{"code":"rate_limited","retry_after_ms":4200}""",
            headers = arrayOf("Retry-After", "3"),
        ) as ScError.RateLimited

        assertThat(error.retryAfterMs).isEqualTo(4200L)
    }

    @Test
    fun headerIsIgnoredWhenItIsNotANumber() {
        /* Значение-дата (`Wed, 21 Oct 2026 07:28:00 GMT`) намеренно не
         * поддерживается: клиент просто не покажет обратный отсчёт. */
        val error = errorOf(
            status = 429,
            headers = arrayOf("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT"),
        ) as ScError.RateLimited

        assertThat(error.retryAfterMs).isNull()
    }

    /* ── Устойчивость к мусору в теле ──────────────────────────────────── */

    @Test
    fun malformedBodyFallsBackToStatusWithoutThrowing() {
        /* Тело ошибки может прийти HTML-страницей балансировщика. Падение
         * внутри обработчика ошибок — худший из возможных исходов. */
        val error = errorOf(502, "<html><body>Bad Gateway</body></html>")

        assertThat(error).isInstanceOf(ScError.Server::class.java)
    }

    @Test
    fun emptyBodyUsesHttpReasonPhrase() {
        val error = errorOf(404, body = null)

        assertThat(error.message).isNotEmpty()
    }

    @Test
    fun partialBodyToleratesMissingFields() {
        val error = errorOf(400, """{"code":"bad_request"}""")

        assertThat(error).isInstanceOf(ScError.Validation::class.java)
    }

    /* ── Обёртка apiCall ───────────────────────────────────────────────── */

    @Test
    fun apiCallWrapsValueIntoSuccess() {
        val result = apiCall { "ok" }

        assertThat(result.getOrNull()).isEqualTo("ok")
    }

    @Test
    fun apiCallMapsHttpException() {
        val result = apiCall<String> { throw httpException(409, """{"code":"username_taken"}""") }

        assertThat((result as ScResult.Failure).error).isInstanceOf(ScError.Conflict::class.java)
    }

    @Test
    fun apiCallMapsIoExceptionToNetwork() {
        val result = apiCall<String> { throw IOException("нет сети") }

        assertThat((result as ScResult.Failure).error).isInstanceOf(ScError.Network::class.java)
    }

    @Test
    fun apiCallRethrowsCancellation() {
        assertThrows(CancellationException::class.java) {
            apiCall<String> { throw CancellationException("отмена") }
        }
    }

    /* ── Неопределённость исхода ───────────────────────────────────────── */

    @Test
    fun networkAndServerErrorsHaveUnknownOutcome() {
        /* Запрос мог дойти до сервера и быть выполненным, а ответ —
         * потеряться. Повтор допустим только с тем же Idempotency-Key. */
        assertThat(ScResult.failure(ScError.Network()).isUnknownOutcome()).isTrue()
        assertThat(ScResult.failure(ScError.Server()).isUnknownOutcome()).isTrue()
    }

    @Test
    fun definiteAnswersHaveKnownOutcome() {
        /* Действие точно не выполнено — повтор с теми же данными бессмыслен. */
        assertThat(ScResult.failure(ScError.Conflict()).isUnknownOutcome()).isFalse()
        assertThat(ScResult.failure(ScError.InsufficientFunds()).isUnknownOutcome()).isFalse()
        assertThat(ScResult.failure(ScError.Validation("пусто")).isUnknownOutcome()).isFalse()
        assertThat(ScResult.failure(ScError.NotFound()).isUnknownOutcome()).isFalse()
    }

    @Test
    fun rateLimitIsNotRetriedAutomatically() {
        /* У `429` есть `retryAfterMs`, и паузу показывает интерфейс.
         * Автоматический повтор здесь превратился бы в лавину. */
        assertThat(ScResult.failure(ScError.RateLimited()).isUnknownOutcome()).isFalse()
    }

    @Test
    fun cancellationIsNotAnUnknownOutcome() {
        assertThat(ScResult.failure(ScError.Canceled).isUnknownOutcome()).isFalse()
    }

    @Test
    fun successAndLoadingHaveKnownOutcome() {
        assertThat(ScResult.success(1).isUnknownOutcome()).isFalse()
        assertThat(ScResult.Loading.isUnknownOutcome()).isFalse()
    }

    /* ── Сборка HttpException ──────────────────────────────────────────── */

    private fun errorOf(
        status: Int,
        body: String? = EMPTY_BODY,
        headers: Array<String> = emptyArray(),
    ): ScError {
        val exception = httpException(status, body, headers)
        val result = apiCall<String> { throw exception }
        return (result as ScResult.Failure).error
    }

    private fun httpException(
        status: Int,
        body: String? = EMPTY_BODY,
        headers: Array<String> = emptyArray(),
    ): HttpException {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url(URL).build())
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message(REASON_PHRASE)
            .headers(Headers.headersOf(*headers))
            .build()
        // `null` означает «тела нет вовсе» — так приходит ответ от прокси.
        val errorBody = body?.toResponseBody(JSON) ?: "".toResponseBody(null)
        return HttpException(Response.error<Any>(errorBody, raw))
    }

    private companion object {
        const val URL = "https://api.silver.chat/v1/test"
        const val REASON_PHRASE = "Сообщение статуса"
        const val EMPTY_BODY = """{"code":"error","message":"Сообщение сервера"}"""
        val JSON = "application/json".toMediaType()
    }
}
