package com.silverchat.core.data.repository

import com.silverchat.core.common.log.ScLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Дедупликация фоновых подкачек.
 *
 * ── Проблема ────────────────────────────────────────────────────────────
 * Репозитории отдают данные как «кэш сразу + сеть фоном»:
 * `state.onStart { refresh() }`. Но `onStart` срабатывает на КАЖДУЮ новую
 * подписку, а в Compose экраны переподписываются постоянно — при повороте
 * устройства, при возврате по стеку навигации, при пересоздании ViewModel.
 * Без защиты один вход на экран профиля способен отправить десяток
 * одинаковых запросов.
 *
 * ── Решение ─────────────────────────────────────────────────────────────
 * Ключ операции регистрируется в наборе до запуска и снимается после.
 * Пока ключ занят, повторные вызовы отбрасываются: данные всё равно
 * придут от первого запроса и попадут в тот же `StateFlow`.
 *
 * `ConcurrentHashMap.newKeySet()` выбран вместо `MutableSet` потому, что
 * подписки приходят с разных диспетчеров, а `onStart` не синхронизирован.
 *
 * ── Почему ошибки не пробрасываются ──────────────────────────────────────
 * К этому моменту подписчик уже получил кэш, поэтому сбой подкачки — не
 * ошибка операции, а деградация свежести. Он логируется и гаснет: общий
 * оффлайн-баннер рисуется по состоянию сети, а не по каждой неудаче.
 */
internal class InFlightGuard(
    private val scope: CoroutineScope,
    private val logTag: String,
) {

    private val keys = ConcurrentHashMap.newKeySet<String>()

    /** Запускает [block], если операция с таким [key] ещё не выполняется. */
    fun launch(key: String, block: suspend () -> Unit) {
        if (!keys.add(key)) return
        scope.launch {
            try {
                runCatching { block() }.onFailure { error ->
                    ScLogger.w(logTag, "Фоновое обновление «$key» не удалось", error)
                }
            } finally {
                keys.remove(key)
            }
        }
    }

    /** Сбрасывает занятые ключи — используется при смене аккаунта. */
    fun reset() = keys.clear()
}
