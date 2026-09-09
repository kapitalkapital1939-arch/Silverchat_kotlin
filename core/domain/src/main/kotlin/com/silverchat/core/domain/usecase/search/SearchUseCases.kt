package com.silverchat.core.domain.usecase.search

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.format.UsernameFormatter
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.base.FlowUseCase
import com.silverchat.core.domain.base.SuspendUseCase
import com.silverchat.core.domain.repository.SearchRepository
import com.silverchat.core.domain.repository.SearchResolveResult
import com.silverchat.core.model.Chat
import com.silverchat.core.model.User
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Поиск пользователей по нику и @username.
 *
 * Поведение как в Telegram:
 *  - пустой запрос -> недавние поиски и контакты;
 *  - запрос с «@» -> точный поиск handle;
 *  - обычный текст -> нечёткий поиск по имени/username.
 */
class SearchUsersUseCase @Inject constructor(
    private val repository: SearchRepository,
    dispatchers: DispatcherProvider,
) : FlowUseCase<String, SearchUsersState>(dispatchers) {

    override fun execute(params: String): Flow<SearchUsersState> {
        val query = params.trim()

        if (query.isEmpty()) {
            return repository.recentSearches().map { recents ->
                SearchUsersState(results = emptyList(), recents = recents, isHandleMode = false)
            }
        }

        val isHandle = query.startsWith("@") || UsernameFormatter.validate(query).isValid
        val results = if (isHandle) {
            repository.searchUsers(UsernameFormatter.strip(query).orEmpty())
        } else {
            repository.searchUsers(query)
        }

        return results.map { users ->
            SearchUsersState(
                results = users.sortedWith(USER_COMPARATOR),
                recents = emptyList(),
                isHandleMode = isHandle,
            )
        }
    }

    private companion object {
        /** Сначала точное совпадение username, затем по имени. */
        val USER_COMPARATOR = compareByDescending<User> { it.badges.verified }
            .thenBy { it.username?.length ?: Int.MAX_VALUE }
            .thenBy { it.fullName }
    }
}

data class SearchUsersState(
    val results: List<User>,
    val recents: List<String>,
    val isHandleMode: Boolean,
) {
    val isEmpty: Boolean get() = results.isEmpty() && recents.isEmpty()
}

/** Переход по точному handle (клик по @упоминанию или deep link). */
class ResolveHandleUseCase @Inject constructor(
    private val repository: SearchRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<String, SearchResolveResult>(dispatchers) {

    override suspend fun execute(params: String): ScResult<SearchResolveResult> {
        val handle = UsernameFormatter.strip(params)
            ?: return ScResult.Success(SearchResolveResult.NotFound)

        repository.saveRecentSearch(handle)
        return repository.resolveHandle(handle)
    }
}

/** Единый поиск по экрану: чаты + пользователи + сообщения + юзернеймы маркета. */
class GlobalSearchUseCase @Inject constructor(
    private val repository: SearchRepository,
    dispatchers: DispatcherProvider,
) : FlowUseCase<String, com.silverchat.core.domain.repository.GlobalSearchResult>(dispatchers) {

    override fun execute(params: String): Flow<com.silverchat.core.domain.repository.GlobalSearchResult> {
        val query = params.trim()
        if (query.length < MIN_QUERY) {
            return kotlinx.coroutines.flow.flowOf(
                com.silverchat.core.domain.repository.GlobalSearchResult(query = query),
            )
        }
        return repository.searchGlobal(query)
    }

    private companion object {
        const val MIN_QUERY = 2
    }
}

/** Подсветка найденного совпадения в UI: возвращает диапазоны [start, end). */
object SearchHighlighter {

    fun ranges(text: String, query: String): List<IntRange> {
        if (query.isBlank()) return emptyList()
        val result = mutableListOf<IntRange>()
        var index = text.lowercase().indexOf(query.lowercase())
        while (index >= 0) {
            result += index until (index + query.length)
            index = text.lowercase().indexOf(query.lowercase(), index + query.length)
        }
        return result
    }

    fun isMatch(chat: Chat, query: String): Boolean =
        chat.displayName.contains(query, ignoreCase = true) ||
            chat.username?.contains(query, ignoreCase = true) == true
}
