package com.silverchat.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.domain.repository.ChatRepository
import com.silverchat.core.domain.repository.SyncRepository
import com.silverchat.core.domain.usecase.chat.ChatListFilter
import com.silverchat.core.domain.usecase.chat.ChatListState
import com.silverchat.core.domain.usecase.chat.ObserveChatListParams
import com.silverchat.core.domain.usecase.chat.ObserveChatListUseCase
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatFolder
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Список чатов.
 *
 * Ключевое решение: [filter], [folderId] и [query] — StateFlow-параметры,
 * а не аргументы разового запроса. Через [flatMapLatest] смена фильтра
 * переподписывается на источник, поэтому список обновляется реактивно:
 * пришло новое сообщение -> чат сам поднялся наверх и переехал
 * во вкладку «Непрочитанные».
 *
 * `WhileSubscribed(5_000)`: при быстром переключении вкладок не
 * пересоздаём подписку на Room, но и не держим её вечно, когда
 * пользователь ушёл в настройки.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val observeChatList: ObserveChatListUseCase,
    private val chats: ChatRepository,
    private val sync: SyncRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(ChatListFilter.ALL)
    private val folderId = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val forceUnread = MutableStateFlow<Set<ChatId>>(emptySet())

    /**
     * ID текущего пользователя нужен, чтобы помечать свои сообщения в превью.
     * Заполняется из :app при старте графа (см. [setSession]).
     */
    private val currentUserId = MutableStateFlow("")

    val uiState: StateFlow<ChatListUiState> = chatListFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ChatListUiState(),
        )

    private fun chatListFlow(): Flow<ChatListUiState> {
        val paramsFlow = combine(filter, folderId, currentUserId) { f, folder, uid ->
            ObserveChatListParams(currentUserId = uid, folderId = folder, filter = f)
        }

        val listFlow: Flow<ChatListState> = paramsFlow.flatMapLatest { params ->
            observeChatList(params)
        }

        // Соединяем четыре источника: список, поисковый запрос, локальный флаг
        // «непрочитано» и состояние соединения. Любое изменение пересчитывает UI.
        return combine(listFlow, query, forceUnread, connectionFlow()) { state, search, unread, connection ->
            val visible = if (search.isBlank()) {
                state.chats
            } else {
                state.chats.filter { it.matches(search) }
            }
            ChatListUiState(
                chats = visible.map { chat ->
                    if (chat.id in unread && chat.unreadCount == 0) {
                        chat.copy(unreadCount = 1)
                    } else {
                        chat
                    }
                },
                folders = state.folders,
                totalUnread = state.totalUnread,
                archivedCount = state.archivedCount,
                filter = filter.value,
                query = search,
                isLoading = false,
                isEmpty = visible.isEmpty(),
                connection = connection,
            )
        }
    }

    /** Полоса «Подключение…» / «Нет сети» над списком. */
    private fun connectionFlow(): Flow<ConnectionBanner> = combine(
        sync.observeConnection(),
        sync.observePendingOutgoing(),
    ) { connection, pending ->
        when (connection) {
            // Connected и Reconnecting — data class'ы, поэтому `is`, а не равенство
            is ConnectionState.Connected -> ConnectionBanner.Online
            ConnectionState.Connecting, ConnectionState.Authenticating -> ConnectionBanner.Connecting
            is ConnectionState.Reconnecting -> ConnectionBanner.Connecting
            else -> ConnectionBanner.Offline(pendingOutgoing = pending)
        }
    }

    /* ── Ввод пользователя ─────────────────────────────────────────────── */

    fun setSession(userId: String) {
        currentUserId.value = userId
    }

    fun onFilterSelected(newFilter: ChatListFilter) {
        filter.value = newFilter
    }

    fun onFolderSelected(id: String?) {
        folderId.value = id
    }

    fun onQueryChanged(value: String) {
        query.value = value
    }

    /* ── Действия из контекстного меню строки ─────────────────────────── */

    fun togglePin(chat: Chat) = viewModelScope.launch {
        // У чата `pinned` — список закреплённых СООБЩЕНИЙ, а признак
        // закреплённого чата хранится отдельно; берём его из роли в папке
        chats.pinChat(chat.id, pinned = !chat.isPinnedChat)
    }

    fun toggleMute(chat: Chat) = viewModelScope.launch {
        // Снимаем мут (null) или ставим на 8 часов — самый частый сценарий.
        // Произвольный срок выбирается в инфо-панели чата, не из списка.
        val until = if (chat.isMuted) null else System.currentTimeMillis() + MUTE_8_HOURS_MS
        chats.muteChat(chat.id, mutedUntil = until)
    }

    fun markRead(chat: Chat) = viewModelScope.launch {
        if (chat.unreadCount > 0) {
            chats.markRead(chat.id)
            forceUnread.value = forceUnread.value - chat.id
        } else {
            // «Отметить непрочитанным» — локальный флаг: серверного поля нет,
            // это чисто клиентская метка, как в Telegram
            forceUnread.value = forceUnread.value + chat.id
        }
    }

    fun archive(chat: Chat) = viewModelScope.launch {
        chats.archiveChat(chat.id, archived = !chat.archived)
    }

    fun leave(chat: Chat) = viewModelScope.launch {
        chats.leaveChat(chat.id)
    }

    fun delete(chat: Chat, forEveryone: Boolean) = viewModelScope.launch {
        chats.deleteChat(chat.id, forEveryone)
    }

    /** Пометить все видимые чаты прочитанными — действие из меню шапки. */
    fun markAllRead() = viewModelScope.launch {
        uiState.value.chats.filter { it.unreadCount > 0 }.forEach { chat ->
            chats.markRead(chat.id)
        }
        forceUnread.value = emptySet()
    }

    /** Создать группу/канал и вернуть id — навигацию делает вызывающий экран. */
    fun createGroup(title: String, memberIds: List<String>, onCreated: (ChatId) -> Unit) =
        viewModelScope.launch {
            val ids = memberIds.map(::ChatId)
            val chat = chats.createGroup(title = title, memberIds = ids, avatarUri = null)
                .getOrNull()
            chat?.let { onCreated(it.id) }
        }

    fun createChannel(title: String, about: String?, onCreated: (ChatId) -> Unit) =
        viewModelScope.launch {
            val chat = chats.createChannel(title = title, about = about, isBroadcast = false)
                .getOrNull()
            chat?.let { onCreated(it.id) }
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MUTE_8_HOURS_MS = 8 * 60 * 60 * 1000L
    }
}

/** Локальный поиск по названию, юзернейму и имени собеседника. */
private fun Chat.matches(query: String): Boolean {
    val q = query.trim().lowercase().removePrefix("@")
    if (q.isEmpty()) return true
    return title.lowercase().contains(q) ||
        username?.lowercase()?.contains(q) == true ||
        peer?.fullName?.lowercase()?.contains(q) == true ||
        peer?.username?.lowercase()?.contains(q) == true
}

/** Признак закреплённого чата (не путать с закреплёнными сообщениями). */
private val Chat.isPinnedChat: Boolean
    get() = folderIds.contains(PINNED_FOLDER_ID)

private const val PINNED_FOLDER_ID = "pinned"

/** Состояние полосы соединения над списком чатов. */
sealed interface ConnectionBanner {
    data object Online : ConnectionBanner
    data object Connecting : ConnectionBanner
    data class Offline(val pendingOutgoing: Int = 0) : ConnectionBanner

    val textRu: String?
        get() = when (this) {
            Online -> null
            Connecting -> "Подключение…"
            is Offline -> if (pendingOutgoing > 0) {
                "Нет сети · $pendingOutgoing в очереди"
            } else {
                "Нет соединения"
            }
        }
}

data class ChatListUiState(
    val chats: List<Chat> = emptyList(),
    val folders: List<ChatFolder> = emptyList(),
    val totalUnread: Int = 0,
    val archivedCount: Int = 0,
    val filter: ChatListFilter = ChatListFilter.ALL,
    val query: String = "",
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val connection: ConnectionBanner = ConnectionBanner.Online,
)
