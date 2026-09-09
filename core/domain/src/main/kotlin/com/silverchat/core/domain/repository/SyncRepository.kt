package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.ConnectionState
import com.silverchat.core.model.SyncPhase
import kotlinx.coroutines.flow.Flow

/**
 * Реалтайм-канал и синхронизация с веб-версией.
 *
 * Один WebSocket на всё приложение: чаты, сообщения, сторис, звонки,
 * маркет, кошелёк и админ-события идут по одному соединению — так
 * мобильная и веб-версия видят одинаковую последовательность событий.
 */
interface SyncRepository {

    fun observeConnection(): Flow<ConnectionState>

    fun observeSyncPhase(): Flow<SyncPhase>

    /** Сообщения в локальной очереди, ожидающие отправки. */
    fun observePendingOutgoing(): Flow<Int>

    suspend fun connect(): ScResult<Unit>

    suspend fun disconnect(): ScResult<Unit>

    /** Полная пересинхронизация (после долгого офлайна или смены устройства). */
    suspend fun fullResync(): ScResult<Unit>

    /** Очистка локального кэша без выхода из аккаунта. */
    suspend fun clearLocalCache(): ScResult<Unit>
}
