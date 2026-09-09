package com.silverchat.core.data.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.database.dao.SearchHistoryDao
import com.silverchat.core.database.entity.SearchHistoryEntity
import com.silverchat.core.domain.repository.GlobalSearchResult
import com.silverchat.core.domain.repository.SearchRepository
import com.silverchat.core.domain.repository.SearchResolveResult
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.Message
import com.silverchat.core.model.User
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.mapper.apiCall
import com.silverchat.core.network.mapper.toDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Поиск: люди, чаты, сообщения, юзернеймы и глобальный поиск.
 *
 * В отличие от списка чатов поиск НЕ кэшируется в Room: его результаты
 * устаревают мгновенно, а хранить чужие профили локально — лишний след
 * на устройстве. Поэтому каждый `observe*` здесь — холодный `flow {}` с
 * одним сетевым запросом.
 *
 * Debounce намеренно не здесь, а во ViewModel: репозиторий не должен знать,
 * как быстро пользователь печатает. `SearchViewModel` применяет
 * `debounceSearch()` + `flatMapLatest` к потоку запроса.
 */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val api: SilverChatApi,
    private val searchHistoryDao: SearchHistoryDao,
) : SearchRepository {

    override fun searchUsers(query: String): Flow<List<User>> = guardedFlow(query) {
        api.searchUsers(query).map { it.toDomain() }
    }

    override fun searchChats(query: String): Flow<List<Chat>> = guardedFlow(query) {
        api.searchChats(query).map { it.toDomain() }
    }

    override fun searchMessages(query: String, chatId: ChatId?): Flow<List<Message>> =
        guardedFlow(query) {
            api.searchMessages(query, chatId).map { it.toDomain() }
        }

    override fun searchUsernames(query: String): Flow<List<UsernameListing>> =
        guardedFlow(query) {
            api.marketListings(query = query).listings.map { it.toDomain() }
        }

    /**
     * Глобальный поиск — один запрос вместо пяти.
     *
     * Секции приходят уже сгруппированными, поэтому клиенту не нужно
     * демультиплексировать ответ: это экономит и трафик, и время до первой
     * отрисовки.
     */
    override fun searchGlobal(query: String): Flow<GlobalSearchResult> = guardedFlow(query) {
        val users = runCatching { api.searchUsers(query, limit = 10) }.getOrDefault(emptyList())
        val chats = runCatching { api.searchChats(query) }.getOrDefault(emptyList())
        val listings =
            runCatching { api.marketListings(query = query) }.getOrNull()?.listings.orEmpty()

        val mappedChats = chats.map { it.toDomain() }
        GlobalSearchResult(
            users = users.map { it.toDomain() },
            chats = mappedChats.filter { it.type == com.silverchat.core.model.ChatType.PERSONAL },
            channels = mappedChats.filter {
                it.type == com.silverchat.core.model.ChatType.CHANNEL ||
                    it.type == com.silverchat.core.model.ChatType.BROADCAST
            },
            messages = emptyList(),
            listings = listings.map { it.toDomain() },
            query = query,
        )
    }

    /**
     * Резолв `@handle` — то, что стоит за вводом «@silver» в строке поиска.
     *
     * Сервер различает юзернейм пользователя, публичный чат и лот маркета,
     * поэтому ответ приходит с явным `kind`, а не угадывается на клиенте.
     */
    override suspend fun resolveHandle(handle: String): ScResult<SearchResolveResult> = apiCall {
        val normalized = handle.trim().removePrefix("@")
        val response = api.resolveHandle(normalized)
        when (response.kind) {
            "user" -> response.user?.let { SearchResolveResult.FoundUser(it.toDomain()) }
                ?: SearchResolveResult.NotFound
            "chat" -> response.chat?.let { SearchResolveResult.FoundChat(it.toDomain()) }
                ?: SearchResolveResult.NotFound
            "listing" -> response.listing?.let { SearchResolveResult.FoundListing(it.toDomain()) }
                ?: SearchResolveResult.NotFound
            else -> SearchResolveResult.NotFound
        }
    }

    /* ── История поиска: локальная, приватная ──────────────────────────── */

    override fun recentSearches(): Flow<List<String>> =
        searchHistoryDao.observe().map { entities -> entities.map { it.query } }

    override suspend fun saveRecentSearch(query: String): ScResult<Unit> = apiCall {
        val normalized = query.trim()
        if (normalized.isEmpty()) return@apiCall
        // Один запрос на строку: повторный поиск того же термина поднимает его
        // наверх и увеличивает счётчик, а не плодит дубли.
        val existing = searchHistoryDao.find(normalized)
        searchHistoryDao.upsert(
            SearchHistoryEntity(
                query = normalized,
                lastUsedAt = System.currentTimeMillis(),
                useCount = (existing?.useCount ?: 0) + 1,
            ),
        )
    }

    override suspend fun removeRecentSearch(query: String): ScResult<Unit> = apiCall {
        searchHistoryDao.delete(query.trim())
    }

    override suspend fun clearRecentSearches(): ScResult<Unit> = apiCall {
        searchHistoryDao.clear()
    }

    /** Пустой запрос не должен дёргать сеть: UI и так показывает пустой экран. */
    private inline fun <T> guardedFlow(
        query: String,
        crossinline block: suspend () -> T,
    ): Flow<T> = flow {
        if (query.isBlank()) return@flow
        emit(block())
    }
}
