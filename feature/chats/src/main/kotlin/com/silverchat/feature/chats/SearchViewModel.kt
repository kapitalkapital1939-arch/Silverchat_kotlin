package com.silverchat.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.GlobalSearchResult
import com.silverchat.core.domain.repository.SearchRepository
import com.silverchat.core.domain.repository.SearchResolveResult
import com.silverchat.core.domain.usecase.search.GlobalSearchUseCase
import com.silverchat.core.domain.usecase.search.ResolveHandleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Поиск по экрану «Поиск».
 *
 * Решения:
 *  1. **Debounce 320 мс** перед запросом к серверу: без него каждый символ
 *     порождал бы запрос, а поиск — самый частый сценарий ввода в приложении.
 *  2. **[distinctUntilChanged]** — повторный ввод того же запроса (например,
 *     после удаления символа и возврата) не дёргает сеть.
 *  3. **Точный handle-режим**: если запрос начинается с «@» или сам является
 *     валидным юзернеймом, дополнительно дёргаем [ResolveHandleUseCase].
 *     Это требование «поиск по @username»: пользователь ожидает попасть
 *     прямо в профиль, а не в список похожих ников.
 *  4. История запросов сохраняется локально и чистится одной кнопкой.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val globalSearch: GlobalSearchUseCase,
    private val resolveHandle: ResolveHandleUseCase,
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val resolveTarget = MutableStateFlow<SearchTarget?>(null)

    val uiState: StateFlow<SearchUiState> = searchFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SearchUiState(),
        )

    private fun searchFlow() = query
        .debounce(DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) {
                // Пустой запрос: показываем историю, сеть не трогаем
                searchRepository.recentSearches().map { recents ->
                    SearchUiState(
                        query = q,
                        recentSearches = recents,
                        isSearching = false,
                        result = GlobalSearchResult(),
                        resolveTarget = null,
                    )
                }
            } else {
                combine(
                    globalSearch(q),
                    searchRepository.recentSearches(),
                    resolveFlow(q),
                ) { result, recents, target ->
                    SearchUiState(
                        query = q,
                        result = result,
                        recentSearches = recents,
                        resolveTarget = target,
                        isHandleMode = q.startsWith("@"),
                        isSearching = false,
                    )
                }
            }
        }

    /**
     * Точное разрешение handle.
     *
     * Запускается только для запросов, похожих на юзернейм: дёргать
     * `resolveHandle` на каждый обычный поиск бессмысленно и дорого.
     */
    private fun resolveFlow(q: String) =
        if (q.startsWith("@") || looksLikeHandle(q)) {
            kotlinx.coroutines.flow.flow {
                when (val result = resolveHandle(q)) {
                    is ScResult.Success -> emit(
                        when (val resolved = result.data) {
                            is SearchResolveResult.FoundUser ->
                                SearchTarget.User(resolved.user.id.raw)

                            is SearchResolveResult.FoundChat ->
                                SearchTarget.Chat(resolved.chat.id.raw)

                            is SearchResolveResult.FoundListing ->
                                SearchTarget.Listing(
                                    listingId = resolved.listing.id.raw,
                                    sellerId = resolved.listing.sellerId?.raw.orEmpty(),
                                )

                            SearchResolveResult.NotFound -> null
                        },
                    )

                    else -> emit(null)
                }
            }
        } else {
            flowOf(null)
        }

    /** Похож ли текст на юзернейм: латиница/цифры/«_», длина 4..32. */
    private fun looksLikeHandle(q: String): Boolean {
        val v = q.removePrefix("@")
        return v.length in MIN_HANDLE..MAX_HANDLE &&
            v.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
    }

    fun onQueryChanged(value: String) {
        query.value = value
    }

    fun saveRecent(value: String) {
        if (value.isBlank()) return
        viewModelScope.launch { searchRepository.saveRecentSearch(value) }
    }

    fun removeRecent(value: String) = viewModelScope.launch {
        searchRepository.removeRecentSearch(value)
    }

    fun clearRecent() = viewModelScope.launch {
        searchRepository.clearRecentSearches()
    }

    /** Точный переход выполнен — гасим цель, чтобы не сработать дважды. */
    fun consumeResolve() {
        resolveTarget.value = null
    }

    private companion object {
        const val DEBOUNCE_MS = 320L
        const val STOP_TIMEOUT_MS = 5_000L
        const val MIN_HANDLE = 4
        const val MAX_HANDLE = 32
    }
}

/** Цель точного перехода по handle (профиль, чат или лот маркета). */
sealed interface SearchTarget {
    data class User(val userId: String) : SearchTarget
    data class Chat(val chatId: String) : SearchTarget
    data class Listing(val listingId: String, val sellerId: String) : SearchTarget
}

data class SearchUiState(
    val query: String = "",
    val result: GlobalSearchResult = GlobalSearchResult(),
    val recentSearches: List<String> = emptyList(),
    val resolveTarget: SearchTarget? = null,
    val isHandleMode: Boolean = false,
    val isSearching: Boolean = false,
)
