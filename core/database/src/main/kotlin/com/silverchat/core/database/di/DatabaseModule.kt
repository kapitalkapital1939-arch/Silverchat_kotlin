package com.silverchat.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.database.BuildConfig
import com.silverchat.core.database.SilverChatDatabase
import com.silverchat.core.database.dao.CallHistoryDao
import com.silverchat.core.database.dao.ChatDao
import com.silverchat.core.database.dao.DraftDao
import com.silverchat.core.database.dao.LedgerDao
import com.silverchat.core.database.dao.MessageDao
import com.silverchat.core.database.dao.PendingOutgoingDao
import com.silverchat.core.database.dao.SearchHistoryDao
import com.silverchat.core.database.dao.StoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Создание БД.
 *
 * Миграции добавляются ЯВНО в [MIGRATIONS]. `fallbackToDestructiveMigration`
 * не используем: для мессенджера потеря кэша = «пропали все сообщения»,
 * а это воспринимается как критический баг, даже если данные есть на сервере.
 *
 * В debug добавлена колбэк-диагностика медленных запросов — на релизе
 * её нет, чтобы не платить за логирование в горячем пути.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SilverChatDatabase =
        Room.databaseBuilder(context, SilverChatDatabase::class.java, SilverChatDatabase.NAME)
            .addMigrations(*MIGRATIONS)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Начальные значения не нужны: кэш пустой, всё придёт с сервера
                }
            })
            .apply {
                if (BuildConfig.DEBUG) {
                    // Диагностический колбэк только в debug: на релизе
                    // логирование каждого запроса в горячем пути не нужно.
                    setQueryCallback(
                        { sql, args -> ScLogger.d(LogTag.DB, "SQL: $sql ARGS: $args") },
                        null,
                    )
                }
            }
            .build()

    /** Сюда добавляются все миграции: `MIGRATION_1_2`, `MIGRATION_2_3`, … */
    private val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

    @Provides fun provideChatDao(db: SilverChatDatabase): ChatDao = db.chatDao()
    @Provides fun provideMessageDao(db: SilverChatDatabase): MessageDao = db.messageDao()
    @Provides fun provideDraftDao(db: SilverChatDatabase): DraftDao = db.draftDao()
    @Provides fun providePendingOutgoingDao(db: SilverChatDatabase): PendingOutgoingDao = db.pendingOutgoingDao()
    @Provides fun provideStoryDao(db: SilverChatDatabase): StoryDao = db.storyDao()
    @Provides fun provideLedgerDao(db: SilverChatDatabase): LedgerDao = db.ledgerDao()
    @Provides fun provideCallHistoryDao(db: SilverChatDatabase): CallHistoryDao = db.callHistoryDao()
    @Provides fun provideSearchHistoryDao(db: SilverChatDatabase): SearchHistoryDao = db.searchHistoryDao()

    /**
     * Транзакции Room вынесены в отдельный провайдер, чтобы репозитории
     * не зависели от самой БД (и не могли дёргать DAO в обход контрактов).
     */
    @Provides
    @Singleton
    fun provideTransactionRunner(db: SilverChatDatabase): TransactionRunner = TransactionRunner(db)
}

/** Обёртка над [withTransaction] — единственная точка атомарных операций. */
class TransactionRunner(private val db: SilverChatDatabase) {
    suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
