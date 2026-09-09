package com.silverchat.core.common.ext

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Расширения для Flow, используемые во всех ViewModel.
 * Собраны здесь, чтобы каждая фича не изобретала свой debounce/retry.
 */

/** Дёргает Flow при каждом вызове — для «typing» и поиска. */
fun <T> Flow<T>.debounceSearch(timeout: Duration = 300.milliseconds): Flow<T> =
    debounce(timeout).distinctUntilChanged()

/**
 * Экспоненциальный backoff с джиттером.
 *
 * Общий механизм для WebSocket-переподключений и REST-ретраев:
 * 250ms -> 500ms -> 1s -> … -> 30s (потолок), + случайный джиттер,
 * чтобы 10 000 клиентов не ломанулись на сервер одновременно после сбоя.
 */
fun <T> Flow<T>.retryWithBackoff(
    maxAttempts: Int = Int.MAX_VALUE,
    initialDelay: Duration = 250.milliseconds,
    maxDelay: Duration = 30.seconds,
    factor: Double = 2.0,
    shouldRetry: (Throwable) -> Boolean = { true },
): Flow<T> = retryWhen { cause, attempt ->
    if (attempt >= maxAttempts || !shouldRetry(cause)) return@retryWhen false
    val exp = initialDelay.inWholeMilliseconds * factor.pow(attempt.toInt())
    val capped = exp.toLong().coerceAtMost(maxDelay.inWholeMilliseconds)
    val jitter = (0..(capped / 4)).random()
    Timber.w("Retry #$attempt через ${capped + jitter}ms: ${cause.message}")
    delay(capped + jitter)
    true
}

private fun Double.pow(exp: Int): Double {
    var result = 1.0
    repeat(exp) { result *= this }
    return result
}

/** Логирует каждый элемент потока — только для debug-сборок. */
fun <T> Flow<T>.logValues(tag: String, label: String): Flow<T> =
    onEach { Timber.tag(tag).d("$label -> $it") }

/** Переводит Flow в «однократный» запуск с защитой от двойного клика. */
fun <T> Flow<T>.throttleFirst(window: Duration = 600.milliseconds): Flow<T> = flow {
    var lastEmit = 0L
    collect { value ->
        val now = System.currentTimeMillis()
        if (now - lastEmit >= window.inWholeMilliseconds) {
            lastEmit = now
            emit(value)
        }
    }
}

/** Не даёт исключению убить сбор состояния ViewModel. */
fun <T> Flow<T>.safeCatch(tag: String, fallback: T): Flow<T> =
    catch { e ->
        Timber.tag(tag).e(e, "Flow упал, отдаём fallback")
        emit(fallback)
    }

/** Обновление StateFlow с проверкой «изменилось ли что-то» — меньше рекомпозиций. */
fun <T> MutableStateFlow<T>.updateIfChanged(block: (T) -> T) {
    update { current ->
        val next = block(current)
        if (next == current) current else next
    }
}

/** Запуск job'а с отменой предыдущего (для поисковых запросов и отправки). */
fun CoroutineScope.launchCancelPrevious(
    previous: Job?,
    block: suspend CoroutineScope.() -> Unit,
): Job {
    previous?.cancel()
    return launch(block = block)
}

/** Отложенный повтор (например, автопереподключение через N секунд). */
suspend fun delayAtLeast(duration: Duration) = delay(duration.inWholeMilliseconds)
