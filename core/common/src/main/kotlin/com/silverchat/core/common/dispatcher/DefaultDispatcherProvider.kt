package com.silverchat.core.common.dispatcher

import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher

@Singleton
internal class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {

    override val main: CoroutineDispatcher = Dispatchers.Main.immediate
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined

    /**
     * Realtime-поток вынесен отдельно: OkHttp зовёт WebSocketListener на своём
     * диспетчере, и если обрабатывать события в общем IO-пуле, то при массовых
     * загрузках медиа heartbeat начинает опаздывать и сервер рвёт соединение.
     */
    override val realtime: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "silverchat-realtime").apply {
                priority = Thread.MAX_PRIORITY
                isDaemon = true
            }
        }.asCoroutineDispatcher()

    /**
     * Медиа-пул ограничен числом ядер: параллельное транскодирование 8 видео
     * на 4-ядерном устройстве убивает и CPU, и батарею.
     */
    override val media: CoroutineDispatcher =
        Executors.newFixedThreadPool(
            maxOf(2, Runtime.getRuntime().availableProcessors() - 1),
        ) { runnable ->
            Thread(runnable, "silverchat-media").apply { isDaemon = true }
        }.asCoroutineDispatcher()
}
