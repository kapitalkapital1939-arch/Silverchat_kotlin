package com.silverchat.core.data.repository

import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.data.mapper.toDomain
import com.silverchat.core.data.mapper.toEntity
import com.silverchat.core.database.dao.MessageDao
import com.silverchat.core.database.dao.PendingOutgoingDao
import com.silverchat.core.database.entity.PendingOutgoingEntity
import com.silverchat.core.domain.repository.MessageRepository
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.MessageId
import com.silverchat.core.model.MessageStatus
import com.silverchat.core.model.ReactionKind
import com.silverchat.core.model.SocketOps
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.dto.DeliveryRequest
import com.silverchat.core.network.dto.EditMessageRequest
import com.silverchat.core.network.dto.ForwardRequest
import com.silverchat.core.network.dto.PinRequest
import com.silverchat.core.network.dto.ReactionRequest
import com.silverchat.core.network.dto.SendMessageRequest
import com.silverchat.core.network.dto.UnpinAllRequest
import com.silverchat.core.network.dto.VoteRequest
import com.silverchat.core.network.mapper.apiCall
import com.silverchat.core.network.mapper.toDomain as dtoToDomain
import com.silverchat.core.network.mapper.toDto
import com.silverchat.core.network.ws.RealtimeSocket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Сообщения: оптимистичная отправка, правки, удаления, реакции, опросы.
 *
 * ── Как устроена оптимистичная отправка ─────────────────────────────────
 *  1. Генерируем `client_message_id` (UUID) и сразу пишем строку в Room со
 *     статусом [MessageStatus.SENDING] — пузырь появляется в UI за один кадр,
 *     не дожидаясь сети.
 *  2. Тот же UUID кладём в `pending_outgoing` вместе с сериализованным
 *     запросом. Если процесс умрёт или сеть пропадёт — после перезапуска
 *     [SyncRepositoryImpl] поднимет очередь и отправит её как есть.
 *  3. Сервер отвечает `MessageDto` с настоящим id: вызываем
 *     `MessageDao.acknowledge(clientMessageId, serverId, …)`, который
 *     переносит строку на новый primary key. UI при этом не мигает, потому
 *     что Compose-ключ списка — `clientMessageId`, а не серверный id.
 *  4. Дубли исключены: сервер идемпотентен по `client_message_id`, а
 *     `acknowledge` — единственный путь, которым локальная строка получает
 *     серверный id.
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val api: SilverChatApi,
    private val messageDao: MessageDao,
    private val pendingOutgoingDao: PendingOutgoingDao,
    private val socket: RealtimeSocket,
    private val json: Json,
) : MessageRepository {

    override fun observeMessages(chatId: ChatId, before: MessageId?): Flow<List<Message>> = flow {
        // `before` — это id сообщения, а DAO пагинирует по `sent_at`.
        // Резолвим один раз на подписку: дальше поток живёт сам.
        val beforeSentAt = before?.let { messageDao.getMessage(it)?.sentAt }
        emitAll(messageDao.observeMessages(chatId, beforeSentAt).map { list ->
            list.map { it.toDomain() }
        })
    }

    override fun observeMessage(messageId: MessageId): Flow<Message?> =
        messageDao.observeMessage(messageId).map { it?.toDomain() }

    override suspend fun loadOlder(
        chatId: ChatId,
        before: MessageId,
        limit: Int,
    ): ScResult<List<Message>> = apiCall {
        val response = api.messages(chatId, before = before, limit = limit)
        val messages = response.messages.map { it.dtoToDomain() }
        messageDao.upsertAll(messages.map { it.toEntity() })
        messages
    }

    /* ── Отправка ──────────────────────────────────────────────────────── */

    override suspend fun send(
        chatId: ChatId,
        content: MessageContent,
        replyTo: MessageId?,
        silent: Boolean,
        scheduledAt: Long?,
    ): ScResult<Message> {
        val clientMessageId = UUID.randomUUID().toString()
        val optimistic = Message(
            id = clientMessageId,
            chatId = chatId,
            senderId = "",
            content = content,
            status = MessageStatus.SENDING,
            replyTo = null,
            sentAt = System.currentTimeMillis(),
            scheduledAt = scheduledAt,
            silent = silent,
        )

        // 1. Пузырь в UI — немедленно.
        messageDao.upsert(optimistic.toEntity(clientMessageId = clientMessageId))

        val request = SendMessageRequest(
            clientMessageId = clientMessageId,
            content = content.toDto(),
            replyTo = replyTo,
            silent = silent,
            scheduledAt = scheduledAt,
        )

        // 2. В очередь — чтобы пережить смерть процесса.
        persistPending(clientMessageId, chatId, request)

        return apiCall {
            val sent = api.sendMessage(chatId, request).dtoToDomain()
            messageDao.acknowledge(
                clientMessageId = clientMessageId,
                serverId = sent.id,
                sentAt = sent.sentAt,
                status = MessageStatus.SENT,
            )
            pendingOutgoingDao.delete(clientMessageId)
            sent
        }.onFailure { error ->
            messageDao.updateStatus(clientMessageId, MessageStatus.FAILED)
            pendingOutgoingDao.markAttempt(
                clientMessageId,
                System.currentTimeMillis(),
                error.message,
            )
        }
    }

    /**
     * Повтор неудачной отправки.
     *
     * Берём сохранённый в очереди payload и шлём его как есть — с тем же
     * `client_message_id`, поэтому сервер не создаст дубль.
     */
    override suspend fun retryFailed(messageId: MessageId): ScResult<Message> {
        val local = messageDao.getMessage(messageId)
            ?: return ScResult.Failure(ScError.NotFound("Сообщение не найдено"))
        val pending = pendingOutgoingDao.all().firstOrNull { it.clientMessageId == messageId }

        return apiCall {
            val sent = if (pending != null) {
                // Идемпотентный повтор исходного запроса.
                api.sendMessage(
                    pending.chatId,
                    json.decodeFromString(SendMessageRequest.serializer(), pending.payloadJson),
                ).dtoToDomain()
            } else {
                // Очередь уже очищена (например, после переустановки) — шлём заново
                // с новым client_message_id, иначе сервер отвергнет неизвестный id.
                val freshId = UUID.randomUUID().toString()
                api.sendMessage(
                    local.chatId,
                    SendMessageRequest(
                        clientMessageId = freshId,
                        content = json.decodeFromString(
                            MessageContent.serializer(),
                            local.contentJson,
                        ),
                    ),
                ).dtoToDomain()
            }
            messageDao.acknowledge(messageId, sent.id, sent.sentAt, MessageStatus.SENT)
            pendingOutgoingDao.delete(messageId)
            sent
        }.onFailure { messageDao.updateStatus(messageId, MessageStatus.FAILED) }
    }

    /* ── Правка и удаление ─────────────────────────────────────────────── */

    override suspend fun edit(
        chatId: ChatId,
        messageId: MessageId,
        newText: String,
    ): ScResult<Message> = apiCall {
        val edited = api.editMessage(
            messageId,
            EditMessageRequest(MessageContent.Text(newText).toDto()),
        ).dtoToDomain()
        messageDao.updateContent(
            messageId = messageId,
            contentJson = json.encodeToString(MessageContent.serializer(), edited.content),
            editedAt = edited.editedAt ?: System.currentTimeMillis(),
        )
        edited
    }

    override suspend fun delete(
        chatId: ChatId,
        messageIds: List<MessageId>,
        forEveryone: Boolean,
    ): ScResult<Unit> = apiCall {
        messageIds.forEach { id ->
            runCatching { api.deleteMessage(id, forEveryone) }
                .onSuccess { messageDao.markDeleted(listOf(id)) }
        }
        Unit
    }

    /* ── Реакции ───────────────────────────────────────────────────────── */

    override suspend fun toggleReaction(
        chatId: ChatId,
        messageId: MessageId,
        kind: ReactionKind,
    ): ScResult<Unit> = apiCall {
        val updated = api.react(messageId, ReactionRequest(kind.name.lowercase())).dtoToDomain()
        messageDao.updateReactions(messageId, updated.reactions)
    }

    override suspend fun clearReactions(chatId: ChatId, messageId: MessageId): ScResult<Unit> =
        apiCall {
            val updated = api.clearReactions(messageId).dtoToDomain()
            messageDao.updateReactions(messageId, updated.reactions)
        }

    override suspend fun availableReactions(chatId: ChatId): ScResult<List<ReactionKind>> =
        apiCall {
            api.availableReactions(chatId).mapNotNull { raw ->
                ReactionKind.entries.firstOrNull { it.name.equals(raw, true) || it.emoji == raw }
            }
        }

    /* ── Закрепление ───────────────────────────────────────────────────── */

    override suspend fun pin(
        chatId: ChatId,
        messageId: MessageId,
        notifyMembers: Boolean,
    ): ScResult<Unit> = apiCall {
        api.pinMessage(messageId, PinRequest(notifyMembers))
        messageDao.setPinned(messageId, pinned = true)
    }

    override suspend fun unpin(chatId: ChatId, messageId: MessageId): ScResult<Unit> = apiCall {
        api.unpinMessage(messageId)
        messageDao.setPinned(messageId, pinned = false)
    }

    override suspend fun unpinAll(chatId: ChatId): ScResult<Unit> = apiCall {
        api.unpinAll(UnpinAllRequest(chatId))
        // Bulk-unpin в DAO нет намеренно: закреплённых сообщений единицы,
        // и следующее chat.updated перезапишет список закреплённых целиком.
        Unit
    }

    /* ── Пересылка, доставка, прочтение ────────────────────────────────── */

    override suspend fun forward(
        messageIds: List<MessageId>,
        toChatIds: List<ChatId>,
    ): ScResult<Unit> = apiCall {
        val created = api.forward(ForwardRequest(messageIds, toChatIds))
        messageDao.upsertAll(created.map { it.dtoToDomain().toEntity() })
    }

    override suspend fun markDelivered(
        chatId: ChatId,
        messageIds: List<MessageId>,
    ): ScResult<Unit> = apiCall {
        if (messageIds.isNotEmpty()) {
            api.markDelivered(DeliveryRequest(chatId, messageIds))
        }
    }

    override suspend fun markRead(
        chatId: ChatId,
        messageIds: List<MessageId>,
    ): ScResult<Unit> = apiCall {
        if (messageIds.isNotEmpty()) {
            api.markMessagesRead(DeliveryRequest(chatId, messageIds))
            messageIds.forEach { messageDao.updateStatus(it, MessageStatus.READ) }
        }
    }

    /* ── Опросы ────────────────────────────────────────────────────────── */

    override suspend fun voteInPoll(
        chatId: ChatId,
        messageId: MessageId,
        optionIndexes: List<Int>,
    ): ScResult<Message> = apiCall {
        val voted = api.vote(messageId, VoteRequest(optionIndexes)).dtoToDomain()
        messageDao.upsert(voted.toEntity())
        voted
    }

    override suspend fun closePoll(chatId: ChatId, messageId: MessageId): ScResult<Message> =
        apiCall {
            val closed = api.closePoll(messageId).dtoToDomain()
            messageDao.upsert(closed.toEntity())
            closed
        }

    /* ── Внутреннее ────────────────────────────────────────────────────── */

    /** Применил `message.new` / `message.edited` из WebSocket. */
    suspend fun applyIncoming(message: Message) {
        messageDao.upsert(message.toEntity())
    }

    /** Применил `message.sent.ack` из WebSocket (дубль REST-ответа допустим). */
    suspend fun applyAck(clientMessageId: String, server: Message) {
        messageDao.acknowledge(clientMessageId, server.id, server.sentAt, MessageStatus.SENT)
        pendingOutgoingDao.delete(clientMessageId)
    }

    private suspend fun persistPending(
        clientMessageId: String,
        chatId: ChatId,
        request: SendMessageRequest,
    ) {
        pendingOutgoingDao.upsert(
            PendingOutgoingEntity(
                clientMessageId = clientMessageId,
                chatId = chatId,
                op = SocketOps.MESSAGE_SEND,
                payloadJson = json.encodeToString(SendMessageRequest.serializer(), request),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Отправка сохранённого запроса через WebSocket.
     *
     * Используется [SyncRepositoryImpl] при разборе `pending_outgoing`:
     * payload уходит как есть, с исходным `client_message_id`, поэтому
     * сервер идемпотентно схлопнет повтор в ту же строку.
     */
    suspend fun flushPendingViaSocket(chatId: ChatId, payloadJson: String): String =
        socket.send(
            SocketOps.MESSAGE_SEND,
            buildJsonObject {
                put("chat_id", chatId)
                put("payload", json.parseToJsonElement(payloadJson))
            },
        )
}
