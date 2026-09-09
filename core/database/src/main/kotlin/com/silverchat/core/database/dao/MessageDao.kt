package com.silverchat.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.silverchat.core.database.entity.MemberEntity
import com.silverchat.core.database.entity.MessageEntity
import com.silverchat.core.model.MessageStatus
import kotlinx.coroutines.flow.Flow

/** Сообщения, участники, статусы доставки. */
@Dao
interface MessageDao {

    /**
     * Лента сообщений: пагинация курсором по sent_at (а не OFFSET).
     * OFFSET при 100k сообщений деградирует линейно, курсор — нет.
     */
    @Query(
        """
        SELECT * FROM messages
        WHERE chat_id = :chatId
          AND deleted = 0
          AND (:beforeSentAt IS NULL OR sent_at < :beforeSentAt)
        ORDER BY sent_at DESC
        LIMIT :limit
        """,
    )
    fun observeMessages(chatId: String, beforeSentAt: Long? = null, limit: Int = 40): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    fun observeMessage(messageId: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessage(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE client_message_id = :clientMessageId LIMIT 1")
    suspend fun findByClientMessageId(clientMessageId: String): MessageEntity?

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: MessageStatus)

    /**
     * Замена локального ID на серверный после ack.
     *
     * Отдельный запрос, а не upsert: сообщение уже могло быть отрисовано
     * и процитировано в reply — нужно сохранить связи и не плодить дубликат.
     */
    @Query(
        """
        UPDATE messages
        SET id = :serverId,
            acknowledged = 1,
            sent_at = :sentAt,
            status = :status
        WHERE client_message_id = :clientMessageId
        """,
    )
    suspend fun acknowledge(clientMessageId: String, serverId: String, sentAt: Long, status: MessageStatus)

    @Query("UPDATE messages SET reactions = :reactionsJson WHERE id = :messageId")
    suspend fun updateReactions(messageId: String, reactionsJson: List<com.silverchat.core.model.Reaction>)

    @Query("UPDATE messages SET edited_at = :editedAt, content_json = :contentJson WHERE id = :messageId")
    suspend fun updateContent(messageId: String, contentJson: String, editedAt: Long)

    @Query("UPDATE messages SET deleted = 1 WHERE id IN (:messageIds)")
    suspend fun markDeleted(messageIds: List<String>)

    @Query("UPDATE messages SET pinned = :pinned WHERE id = :messageId")
    suspend fun setPinned(messageId: String, pinned: Boolean)

    @Query("UPDATE messages SET views_count = :views WHERE id = :messageId")
    suspend fun updateViews(messageId: String, views: Int)

    @Query("SELECT id FROM messages WHERE chat_id = :chatId AND status IN ('SENDING','FAILED')")
    suspend fun pendingIds(chatId: String): List<String>

    @Query("DELETE FROM messages WHERE chat_id = :chatId")
    suspend fun deleteChatMessages(chatId: String)

    /* ── Участники ──────────────────────────────────────────────────────── */

    @Query("SELECT * FROM members WHERE chat_id = :chatId ORDER BY role ASC, joined_at ASC")
    fun observeMembers(chatId: String): Flow<List<MemberEntity>>

    @Upsert
    suspend fun upsertMember(member: MemberEntity)

    @Upsert
    suspend fun upsertMembers(members: List<MemberEntity>)

    @Query("DELETE FROM members WHERE chat_id = :chatId AND user_id = :userId")
    suspend fun deleteMember(chatId: String, userId: String)

    @Query("SELECT COUNT(*) FROM members WHERE chat_id = :chatId")
    suspend fun memberCount(chatId: String): Int
}
