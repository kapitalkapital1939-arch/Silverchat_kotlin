package com.silverchat.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.silverchat.core.database.entity.CallHistoryEntity
import com.silverchat.core.database.entity.DraftEntity
import com.silverchat.core.database.entity.LedgerEntity
import com.silverchat.core.database.entity.PendingOutgoingEntity
import com.silverchat.core.database.entity.SearchHistoryEntity
import com.silverchat.core.database.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

/** Черновики — синхронизируются между устройствами, но живут и офлайн. */
@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE chat_id = :chatId")
    fun observeDraft(chatId: String): Flow<DraftEntity?>

    @Query("SELECT * FROM drafts")
    fun observeAll(): Flow<List<DraftEntity>>

    @Upsert
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE chat_id = :chatId")
    suspend fun delete(chatId: String)
}

/** Очередь отправки: переживает смерть процесса. */
@Dao
interface PendingOutgoingDao {
    @Query("SELECT * FROM pending_outgoing ORDER BY created_at ASC")
    fun observeAll(): Flow<List<PendingOutgoingEntity>>

    @Query("SELECT COUNT(*) FROM pending_outgoing")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM pending_outgoing ORDER BY created_at ASC")
    suspend fun all(): List<PendingOutgoingEntity>

    @Upsert
    suspend fun upsert(entity: PendingOutgoingEntity)

    @Query("UPDATE pending_outgoing SET attempts = attempts + 1, last_attempt_at = :at, last_error = :error WHERE client_message_id = :id")
    suspend fun markAttempt(id: String, at: Long, error: String?)

    @Query("DELETE FROM pending_outgoing WHERE client_message_id = :id")
    suspend fun delete(id: String)

    /** Кадры, которые безуспешно ретраились слишком много раз, — в «мёртвую» зону. */
    @Query("DELETE FROM pending_outgoing WHERE attempts >= :maxAttempts")
    suspend fun purgeFailed(maxAttempts: Int = 10)

    @Query("DELETE FROM pending_outgoing")
    suspend fun clear()
}

/** Сторис-кэш: лента рисуется из БД, WebSocket лишь обновляет её. */
@Dao
interface StoryDao {
    @Query("SELECT * FROM stories WHERE expires_at > :now ORDER BY created_at DESC")
    fun observeFeed(now: Long = System.currentTimeMillis()): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories WHERE author_id = :authorId ORDER BY created_at DESC")
    fun observeByAuthor(authorId: String): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories WHERE id = :storyId")
    fun observeStory(storyId: String): Flow<StoryEntity?>

    @Upsert
    suspend fun upsert(story: StoryEntity)

    @Upsert
    suspend fun upsertAll(stories: List<StoryEntity>)

    @Query("UPDATE stories SET seen_by_me = 1 WHERE id = :storyId")
    suspend fun markSeen(storyId: String)

    @Query("DELETE FROM stories WHERE id = :storyId")
    suspend fun delete(storyId: String)

    /** Истёкшие сторис удаляем локально — сервер их тоже уже не отдаёт. */
    @Query("DELETE FROM stories WHERE expires_at <= :now")
    suspend fun purgeExpired(now: Long = System.currentTimeMillis())
}

/**
 * Локальный кэш истории кошелька. Баланс — всегда серверный.
 *
 * Кэш накапливает ровно то, что пользователь реально запросил: первая
 * страница при входе на экран и по одной странице на каждое «загрузить ещё».
 *
 * Обрезки по размеру здесь нет намеренно. Курсор следующей страницы — это
 * идентификатор самой СТАРОЙ кэшированной записи, а обрезка удаляет именно
 * самые старые: догрузка тут же вернула бы удалённое, и экран вошёл бы в
 * бесконечный круг «запросил — обрезали — запросил то же самое». Таблица
 * очищается целиком при выходе из аккаунта и через
 * `SyncRepository.clearLocalCache()`.
 */
@Dao
interface LedgerDao {

    /** Всё, что накоплено в кэше, свежие записи первыми. */
    @Query("SELECT * FROM ledger ORDER BY created_at DESC")
    fun observeAll(): Flow<List<LedgerEntity>>

    /**
     * Идентификатор самой старой кэшированной записи — курсор следующей страницы.
     *
     * `null`, когда кэш пуст: догружать не от чего, нужна первая страница.
     */
    @Query("SELECT id FROM ledger ORDER BY created_at ASC LIMIT 1")
    suspend fun oldestId(): String?

    @Upsert
    suspend fun upsert(entry: LedgerEntity)

    @Upsert
    suspend fun upsertAll(entries: List<LedgerEntity>)

    @Query("DELETE FROM ledger")
    suspend fun clear()
}

/** История звонков. */
@Dao
interface CallHistoryDao {
    @Query("SELECT * FROM call_history ORDER BY started_at DESC LIMIT :limit")
    fun observe(limit: Int = 50): Flow<List<CallHistoryEntity>>

    @Upsert
    suspend fun upsert(entry: CallHistoryEntity)

    @Query("DELETE FROM call_history WHERE started_at < :before")
    suspend fun purge(before: Long)
}

/** Недавние поисковые запросы. */
@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY last_used_at DESC LIMIT 20")
    fun observe(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE query = :query LIMIT 1")
    suspend fun find(query: String): SearchHistoryEntity?

    @Upsert
    suspend fun upsert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
