package com.silverchat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.silverchat.core.database.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

/**
 * Чаты и папки.
 *
 * Все выборки — Flow: Room сам пересобирает список при изменении таблиц,
 * поэтому UI не опрашивает БД и не знает про «refresh».
 */
@Dao
interface ChatDao {

    @Query(
        """
        SELECT * FROM chats
        WHERE archived = :archived
        ORDER BY (SELECT COUNT(*) FROM chats c2 WHERE c2.archived = :archived AND c2.last_message_at > chats.last_message_at) ASC,
                 last_message_at DESC
        """,
    )
    fun observeChats(archived: Boolean = false): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun observeChat(chatId: String): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChat(chatId: String): ChatEntity?

    @Query("SELECT SUM(unread_count) FROM chats WHERE archived = 0")
    fun observeTotalUnread(): Flow<Int?>

    @Query("SELECT COUNT(*) FROM chats WHERE archived = 1")
    fun observeArchivedCount(): Flow<Int>

    @Upsert
    suspend fun upsert(chat: ChatEntity)

    @Upsert
    suspend fun upsertAll(chats: List<ChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chat: ChatEntity)

    @Query("UPDATE chats SET unread_count = 0, mentions_count = 0 WHERE id = :chatId")
    suspend fun markRead(chatId: String)

    @Query("UPDATE chats SET unread_count = unread_count + :delta WHERE id = :chatId")
    suspend fun incrementUnread(chatId: String, delta: Int = 1)

    @Query("UPDATE chats SET muted = :muted, muted_until = :mutedUntil WHERE id = :chatId")
    suspend fun setMuted(chatId: String, muted: Boolean, mutedUntil: Long?)

    @Query("UPDATE chats SET archived = :archived WHERE id = :chatId")
    suspend fun setArchived(chatId: String, archived: Boolean)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)

    @Query("DELETE FROM chats")
    suspend fun clear()

    /**
     * Транзакция «новое сообщение»: обновляем сообщение, превью чата,
     * счётчик непрочитанных и время сортировки одним атомарным блоком.
     * Иначе список чатов моргает при рассинхроне порядка записи.
     */
    @Transaction
    suspend fun applyIncomingChatUpdate(chatId: String, lastMessageJson: String?, sentAt: Long, incrementUnread: Boolean) {
        upsertLastMessage(chatId, lastMessageJson, sentAt)
        if (incrementUnread) incrementUnread(chatId)
    }

    @Query(
        """
        UPDATE chats
        SET last_message_json = :lastMessageJson,
            last_message_at = :sentAt,
            updated_at = :sentAt
        WHERE id = :chatId
        """,
    )
    suspend fun upsertLastMessage(chatId: String, lastMessageJson: String?, sentAt: Long)
}
