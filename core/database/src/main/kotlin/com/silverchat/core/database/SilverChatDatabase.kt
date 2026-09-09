package com.silverchat.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.silverchat.core.database.dao.CallHistoryDao
import com.silverchat.core.database.dao.ChatDao
import com.silverchat.core.database.dao.DraftDao
import com.silverchat.core.database.dao.LedgerDao
import com.silverchat.core.database.dao.MessageDao
import com.silverchat.core.database.dao.PendingOutgoingDao
import com.silverchat.core.database.dao.SearchHistoryDao
import com.silverchat.core.database.dao.StoryDao
import com.silverchat.core.database.entity.CallHistoryEntity
import com.silverchat.core.database.entity.ChatEntity
import com.silverchat.core.database.entity.DraftEntity
import com.silverchat.core.database.entity.LedgerEntity
import com.silverchat.core.database.entity.MemberEntity
import com.silverchat.core.database.entity.MessageEntity
import com.silverchat.core.database.entity.PendingOutgoingEntity
import com.silverchat.core.database.entity.SearchHistoryEntity
import com.silverchat.core.database.entity.StoryEntity

/**
 * Единая БД приложения.
 *
 * Правила ведения схемы:
 *  1. Любое изменение полей — НОВЫЙ [version] + [androidx.room.Migration];
 *     `fallbackToDestructiveMigration` запрещён в release (потеря кэша чатов
 *     выглядит для пользователя как «пропали все сообщения»);
 *  2. JSON-поля (реакции, entities, waveform) хранятся строкой через
 *     [Converters] — иначе пришлось бы плодить таблицы-спутники ради данных,
 *     которые читаются только целиком;
 *  3. Внешние ключи с CASCADE: удаление чата чистит сообщения, черновики
 *     и очередь отправки — иначе кэш растёт навсегда.
 */
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MemberEntity::class,
        DraftEntity::class,
        PendingOutgoingEntity::class,
        StoryEntity::class,
        LedgerEntity::class,
        CallHistoryEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SilverChatDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun draftDao(): DraftDao
    abstract fun pendingOutgoingDao(): PendingOutgoingDao
    abstract fun storyDao(): StoryDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val NAME = "silverchat.db"
    }
}
