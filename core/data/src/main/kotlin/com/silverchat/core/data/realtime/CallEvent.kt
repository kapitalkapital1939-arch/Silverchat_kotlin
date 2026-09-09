package com.silverchat.core.data.realtime

import com.silverchat.core.model.CallId
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.UserId

/**
 * Сырое событие звонка из WebSocket.
 *
 * Почему не `CallSession`: кадр `call.incoming` содержит только идентификаторы
 * и состояние, но не список участников и не ICE-параметры. Собирать из него
 * полноценную сессию означало бы выдумывать поля. Поэтому шина передаёт ровно
 * то, что прислал сервер, а [com.silverchat.core.data.repository.CallRepositoryImpl]
 * достраивает сессию ответом `GET /calls/{id}`.
 */
data class CallEvent(
    val callId: CallId,
    val chatId: ChatId?,
    val type: String?,
    val state: String?,
    val fromUserId: UserId?,
    val reason: String?,
    val receivedAt: Long,
)
