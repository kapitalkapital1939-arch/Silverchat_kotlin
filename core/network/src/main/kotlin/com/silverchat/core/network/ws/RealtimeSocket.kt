package com.silverchat.core.network.ws

import com.silverchat.core.model.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Контракт реалтайм-канала.
 *
 * Один WebSocket на всё приложение: чаты, сообщения, реакции, сторис, звонки,
 * маркет, кошелёк и админ-события. Благодаря этому мобильная и веб-версия
 * получают идентичную последовательность событий и синхронизируются бесшовно.
 *
 * Реализация — [OkHttpRealtimeSocket].
 */
interface RealtimeSocket {

    /** Состояние соединения для UI-баннера и очередей отправки. */
    val state: Flow<ConnectionState>

    /**
     * Входящие кадры. SharedFlow с replay=0:
     * подписчики получают только новые события, а история восстанавливается
     * отдельным REST-запросом (см. SyncRepository.fullResync).
     */
    val frames: SharedFlow<IncomingFrame>

    /** Подключиться и авторизоваться. Идемпотентно. */
    suspend fun connect()

    /** Разорвать соединение штатно (выход из аккаунта, переход в фон надолго). */
    suspend fun disconnect()

    /**
     * Отправить команду.
     *
     * Если соединения нет — кадр кладётся в [OutgoingQueue] и уходит
     * автоматически после переподключения (offline-first отправка сообщений).
     *
     * @return id кадра для сопоставления с ack
     */
    suspend fun send(op: String, payload: kotlinx.serialization.json.JsonElement? = null): String

    /**
     * Отправить команду и дождаться ответа с тем же correlation id.
     * Используется для действий, где UI нужен результат (покупка, удаление).
     */
    suspend fun <T> sendAndAwait(
        op: String,
        payload: kotlinx.serialization.json.JsonElement? = null,
        timeoutMs: Long = 15_000L,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T?

    /** Обновить токен без разрыва соединения (ротация refresh-токена). */
    suspend fun updateToken(accessToken: String)

    /** Подписаться на конкретные чаты (экономит трафик на больших списках). */
    suspend fun subscribe(chatIds: List<String>)

    val isConnected: Boolean
}

/** Входящий кадр с уже распознанной операцией. */
data class IncomingFrame(
    val id: String,
    val op: String,
    val timestamp: Long,
    val payload: kotlinx.serialization.json.JsonElement?,
)
