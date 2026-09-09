package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.MessageId
import com.silverchat.core.model.ReactionKind
import kotlinx.coroutines.flow.Flow

/**
 * Сообщения: весь набор механик чата из требований —
 * реакции, ответы (replies), редактирование, удаление, пересылка,
 * закрепление, опросы, статусы доставки, повтор отправки.
 */
interface MessageRepository {

    /* ── Лента ──────────────────────────────────────────────────────────── */

    /**
     * Локальный кэш (Room) как источник истины + live-обновления из WebSocket.
     * Offline-first: экран открывается мгновенно даже без сети.
     */
    fun observeMessages(chatId: ChatId, before: MessageId? = null): Flow<List<Message>>

    fun observeMessage(messageId: MessageId): Flow<Message?>

    suspend fun loadOlder(chatId: ChatId, before: MessageId, limit: Int = 30): ScResult<List<Message>>

    /* ── Отправка ───────────────────────────────────────────────────────── */

    suspend fun send(
        chatId: ChatId,
        content: MessageContent,
        replyTo: MessageId? = null,
        silent: Boolean = false,
        scheduledAt: Long? = null,
    ): ScResult<Message>

    /** Повторная отправка сообщения в статусе FAILED (кнопка «!» в пузыре). */
    suspend fun retryFailed(messageId: MessageId): ScResult<Message>

    /* ── Редактирование и удаление ──────────────────────────────────────── */

    suspend fun edit(
        chatId: ChatId,
        messageId: MessageId,
        newText: String,
    ): ScResult<Message>

    suspend fun delete(
        chatId: ChatId,
        messageIds: List<MessageId>,
        forEveryone: Boolean,
    ): ScResult<Unit>

    /* ── Реакции ────────────────────────────────────────────────────────── */

    /** Ставит/снимает реакцию (toggle-семантика, как в Telegram). */
    suspend fun toggleReaction(
        chatId: ChatId,
        messageId: MessageId,
        kind: ReactionKind,
    ): ScResult<Unit>

    suspend fun clearReactions(chatId: ChatId, messageId: MessageId): ScResult<Unit>

    suspend fun availableReactions(chatId: ChatId): ScResult<List<ReactionKind>>

    /* ── Закрепление / пересылка ────────────────────────────────────────── */

    suspend fun pin(chatId: ChatId, messageId: MessageId, notifyMembers: Boolean): ScResult<Unit>

    suspend fun unpin(chatId: ChatId, messageId: MessageId): ScResult<Unit>

    suspend fun unpinAll(chatId: ChatId): ScResult<Unit>

    suspend fun forward(messageIds: List<MessageId>, toChatIds: List<ChatId>): ScResult<Unit>

    /* ── Статусы доставки/прочтения ─────────────────────────────────────── */

    suspend fun markDelivered(chatId: ChatId, messageIds: List<MessageId>): ScResult<Unit>

    suspend fun markRead(chatId: ChatId, messageIds: List<MessageId>): ScResult<Unit>

    /* ── Опросы ─────────────────────────────────────────────────────────── */

    suspend fun voteInPoll(
        chatId: ChatId,
        messageId: MessageId,
        optionIndexes: List<Int>,
    ): ScResult<Message>

    suspend fun closePoll(chatId: ChatId, messageId: MessageId): ScResult<Message>
}
