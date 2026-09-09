package com.silverchat.core.network.ws

import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Очередь исходящих кадров на время офлайна (offline-first).
 *
 * Поведение:
 *  - сообщения, поставленные в очередь, сохраняются с client_message_id —
 *    при повторной отправке сервер дедуплицирует их, «двойников» не будет;
 *  - очередь ограничена по размеру: бесконечный рост на «вечном офлайне»
 *    приводит к OOM;
 *  - при входе в приложение очередь переживает процесс, потому что
 *    :core:database дополнительно persist'ит неотправленные сообщения.
 *    Здесь — только оперативный буфер между переподключениями.
 */
@Singleton
class OutgoingQueue @Inject constructor() {

    private val queue = ConcurrentLinkedDeque<QueuedFrame>()

    private val _size = MutableStateFlow(0)
    val size: StateFlow<Int> = _size.asStateFlow()

    fun enqueue(op: String, id: String, payload: JsonElement?) {
        if (queue.size >= MAX_SIZE) {
            val dropped = queue.pollFirst()
            ScLogger.w(LogTag.WS, "Очередь переполнена, сброшен кадр ${dropped?.op}")
        }
        queue.addLast(QueuedFrame(op, id, payload, System.currentTimeMillis()))
        _size.value = queue.size
    }

    /** Забирает всё содержимое очереди (вызывается после успешной авторизации). */
    fun drain(): List<QueuedFrame> {
        val result = mutableListOf<QueuedFrame>()
        while (true) {
            val item = queue.pollFirst() ?: break
            // Протухшие кадры не отправляем: «печатает…» через 10 минут бессмысленно
            if (System.currentTimeMillis() - item.enqueuedAt > TTL_MS && item.op in EPHEMERAL_OPS) {
                continue
            }
            result += item
        }
        _size.value = queue.size
        return result
    }

    fun clear() {
        queue.clear()
        _size.value = 0
    }

    private companion object {
        const val MAX_SIZE = 500
        const val TTL_MS = 5 * 60 * 1000L

        /** Кадры, теряющие смысл при задержке. */
        val EPHEMERAL_OPS = setOf(
            com.silverchat.core.model.SocketOps.MESSAGE_TYPING,
            com.silverchat.core.model.SocketOps.PRESENCE_UPDATE,
            com.silverchat.core.model.SocketOps.SESSION_PING,
            com.silverchat.core.model.SocketOps.STORY_VIEW,
        )
    }
}

data class QueuedFrame(
    val op: String,
    val id: String,
    val payload: JsonElement?,
    val enqueuedAt: Long,
)
