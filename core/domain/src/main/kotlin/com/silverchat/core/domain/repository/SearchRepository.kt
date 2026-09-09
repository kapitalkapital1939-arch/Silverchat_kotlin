package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.Message
import com.silverchat.core.model.User
import com.silverchat.core.model.UsernameListing
import kotlinx.coroutines.flow.Flow

/**
 * Поиск: пользователи по нику и @username, чаты, сообщения, юзернеймы маркета.
 *
 * Все методы — холодные Flow: подписка запускает запрос с debounce,
 * отписка отменяет его. Это защищает бэкенд от шторма запросов при вводе.
 */
interface SearchRepository {

    fun searchUsers(query: String): Flow<List<User>>

    fun searchChats(query: String): Flow<List<Chat>>

    fun searchMessages(query: String, chatId: ChatId? = null): Flow<List<Message>>

    /** Поиск свободных/продающихся юзернеймов в маркете. */
    fun searchUsernames(query: String): Flow<List<UsernameListing>>

    /** Глобальный поиск по всем сущностям сразу (вкладка «Глобальный»). */
    fun searchGlobal(query: String): Flow<GlobalSearchResult>

    /**
     * Точное разрешение handle: `@silver` -> профиль или канал.
     * Используется для deep link `silverchat://@username` и клика по упоминанию.
     */
    suspend fun resolveHandle(handle: String): ScResult<SearchResolveResult>

    fun recentSearches(): Flow<List<String>>

    suspend fun saveRecentSearch(query: String): ScResult<Unit>

    suspend fun removeRecentSearch(query: String): ScResult<Unit>

    suspend fun clearRecentSearches(): ScResult<Unit>
}

sealed interface SearchResolveResult {
    data class FoundUser(val user: User) : SearchResolveResult
    data class FoundChat(val chat: Chat) : SearchResolveResult
    data class FoundListing(val listing: UsernameListing) : SearchResolveResult
    data object NotFound : SearchResolveResult
}

/** Результат глобального поиска — сгруппирован по секциям для UI. */
data class GlobalSearchResult(
    val users: List<User> = emptyList(),
    val chats: List<Chat> = emptyList(),
    val channels: List<Chat> = emptyList(),
    val messages: List<Message> = emptyList(),
    val listings: List<UsernameListing> = emptyList(),
    val query: String = "",
) {
    val isEmpty: Boolean
        get() = users.isEmpty() && chats.isEmpty() && channels.isEmpty() &&
            messages.isEmpty() && listings.isEmpty()
}
