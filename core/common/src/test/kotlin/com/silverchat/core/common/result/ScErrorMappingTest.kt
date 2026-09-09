package com.silverchat.core.common.result

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Классификация локальных исключений в [ScError].
 *
 * ── Зачем этот тест существует ──────────────────────────────────────────
 * `SocketTimeoutException`, `UnknownHostException` и `SSLException` —
 * наследники `IOException`. В `when` побеждает первая подходящая ветка,
 * поэтому общий `IOException`, стоящий выше частных, делает их
 * недостижимыми. Компилятор это не ловит: синтаксически все ветки на месте.
 *
 * Именно так код и был написан изначально — три уточняющих сообщения
 * («Превышено время ожидания», «Сервер недоступен», «Ошибка защищённого
 * соединения») не показывались никогда, и пользователь всегда видел «Нет
 * соединения». Первые три теста ниже фиксируют порядок веток и не дадут
 * переставить их обратно.
 */
class ScErrorMappingTest {

    /* ── Порядок веток IOException ─────────────────────────────────────── */

    @Test
    fun socketTimeoutReportsTimeoutNotGenericNetwork() {
        val error = SocketTimeoutException("timeout").toScError()

        assertThat(error).isInstanceOf(ScError.Network::class.java)
        assertThat(error.message).isEqualTo("Превышено время ожидания")
    }

    @Test
    fun unknownHostReportsUnreachableServer() {
        val error = UnknownHostException("api.silver.chat").toScError()

        assertThat(error).isInstanceOf(ScError.Network::class.java)
        assertThat(error.message).isEqualTo("Сервер недоступен")
    }

    @Test
    fun sslFailureReportsSecureConnectionError() {
        val error = SSLException("Handshake failed").toScError()

        assertThat(error).isInstanceOf(ScError.Network::class.java)
        assertThat(error.message).isEqualTo("Ошибка защищённого соединения")
    }

    @Test
    fun genericIoFailureKeepsDefaultNetworkMessage() {
        val error = IOException("socket closed").toScError()

        assertThat(error).isInstanceOf(ScError.Network::class.java)
        assertThat(error.message).isEqualTo("Нет соединения")
    }

    @Test
    fun networkErrorPreservesOriginalCause() {
        val cause = SocketTimeoutException("timeout")

        val error = cause.toScError() as ScError.Network

        assertThat(error.cause).isSameInstanceAs(cause)
    }

    /* ── Прочие исключения ─────────────────────────────────────────────── */

    @Test
    fun unknownExceptionBecomesUnknownError() {
        val error = IllegalStateException("что-то пошло не так").toScError()

        assertThat(error).isInstanceOf(ScError.Unknown::class.java)
        assertThat(error.message).contains("Неизвестная ошибка")
    }

    @Test
    fun scExceptionUnwrapsIntoItsOwnError() {
        val original = ScError.Conflict("Юзернейм уже занят")

        val error = ScException(original).toScError()

        assertThat(error).isSameInstanceAs(original)
    }

    @Test
    fun coroutineCancellationIsRethrownNotWrapped() {
        /* Проглотить отмену — значит сломать structured concurrency: корутина
         * продолжит жить, хотя её отменили, а `ScError.Canceled` попал бы в
         * интерфейс как обычная ошибка. */
        assertThrows(CancellationException::class.java) {
            CancellationException("scope cancelled").toScError()
        }
    }

    /* ── Обёртка scResult ──────────────────────────────────────────────── */

    @Test
    fun scResultWrapsValueIntoSuccess() {
        val result = scResult { 42 }

        assertThat(result.getOrNull()).isEqualTo(42)
    }

    @Test
    fun scResultWrapsExceptionIntoFailure() {
        val result = scResult { throw IOException("нет сети") }

        assertThat(result).isInstanceOf(ScResult.Failure::class.java)
        assertThat((result as ScResult.Failure).error).isInstanceOf(ScError.Network::class.java)
    }

    @Test
    fun scResultRethrowsCancellation() {
        assertThrows(CancellationException::class.java) {
            scResult { throw CancellationException("отмена") }
        }
    }

    /* ── Комбинаторы ScResult ──────────────────────────────────────────── */

    @Test
    fun mapTransformsSuccessAndPassesFailureThrough() {
        val failure: ScResult<Int> = ScResult.failure(ScError.NotFound("нет"))

        assertThat(ScResult.success(2).map { it * 10 }.getOrNull()).isEqualTo(20)
        assertThat(failure.map { it * 10 }).isSameInstanceAs(failure)
    }

    @Test
    fun onSuccessRunsOnlyForSuccess() {
        var seen = 0

        ScResult.success(5).onSuccess { seen = it }
        ScResult.failure(ScError.NotFound("нет")).onSuccess { seen = -1 }

        assertThat(seen).isEqualTo(5)
    }

    @Test
    fun onFailureRunsOnlyForFailure() {
        var seen: ScError? = null

        ScResult.success(5).onFailure { seen = it }
        ScResult.failure(ScError.NotFound("нет")).onFailure { seen = it }

        assertThat(seen).isInstanceOf(ScError.NotFound::class.java)
    }

    @Test
    fun getOrNullReturnsNullForNonSuccess() {
        /* Явный `Any?` обязателен: у `Failure` и `Loading` параметр типа —
         * `Nothing`, а `assertThat(Nothing?)` попадает сразу во все перегрузки
         * Truth (String, Iterable, Map, Throwable, …) и не компилируется. */
        val fromFailure: Any? = ScResult.failure(ScError.NotFound("нет")).getOrNull()
        val fromLoading: Any? = ScResult.Loading.getOrNull()

        assertThat(fromFailure).isNull()
        assertThat(fromLoading).isNull()
    }

    @Test
    fun getOrThrowUnwrapsFailureIntoScException() {
        val exception = assertThrows(ScException::class.java) {
            ScResult.failure(ScError.Forbidden("нельзя")).getOrThrow()
        }

        assertThat(exception.error).isInstanceOf(ScError.Forbidden::class.java)
    }
}
