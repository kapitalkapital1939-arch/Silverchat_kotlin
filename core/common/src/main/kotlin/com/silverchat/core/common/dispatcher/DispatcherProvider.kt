package com.silverchat.core.common.dispatcher

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Единственная точка доступа к диспетчерам.
 *
 * Зачем интерфейс: в юнит-тестах подменяется на [UnconfinedTestDispatcher],
 * поэтому UseCase'ы тестируются без `runBlocking`-трюков и без Robolectric.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher

    /** Отдельный диспетчер для WebSocket-callback'ов OkHttp (не IO-пул!). */
    val realtime: CoroutineDispatcher

    /** Тяжёлые медиа-операции: сжатие видео, кодирование «кружков». */
    val media: CoroutineDispatcher
}
