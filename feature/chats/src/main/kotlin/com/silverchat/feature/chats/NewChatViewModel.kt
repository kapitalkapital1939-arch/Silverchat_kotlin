package com.silverchat.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.designsystem.navigation.ChatsNavigator
import com.silverchat.core.domain.repository.ChatRepository
import com.silverchat.core.domain.repository.MessageRepository
import com.silverchat.core.domain.repository.SearchRepository
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.MessageId
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Создание группы/канала и пересылка сообщений.
 *
 * Логика одна, финальный шаг разный:
 *  - [NewChatMode.CREATE] — `ChatRepository.createGroup/createChannel`
 *    и переход в новый чат;
 *  - [NewChatMode.FORWARD_PICKER] — `MessageRepository.forward` и возврат.
 *
 * Поиск с debounce: при пустом запросе показываются собеседники из
 * существующих чатов (это и есть «контакты» мессенджера — отдельного
 * адресата книжки у SilverChat нет, люди находятся по @username).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val title = MutableStateFlow("")
    private val about = MutableStateFlow("")
    private val isPrivate = MutableStateFlow(true)
    private val isChannel = MutableStateFlow(false)
    private val selectedUsers = MutableStateFlow<List<User>>(emptyList())
    private val mode = MutableStateFlow(NewChatMode.CREATE)
    private val pendingForwardIds = MutableStateFlow<List<MessageId>>(emptyList())
    private val isSubmitting = MutableStateFlow(false)
    private val errorText = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NewChatUiState> = combine(
        query
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .flatMapLatest(::searchResults),
        title,
        about,
        isPrivate,
        isChannel,
    ) { results, t, a, priv, chan ->
        NewChatUiState(
            query = query.value,
            title = t,
            about = a,
            isPrivate = priv,
            isChannel = chan,
            results = results,
            selected = selectedUsers.value,
            selectedIds = selectedUsers.value.map { it.id }.toSet(),
            mode = mode.value,
            isSubmitting = isSubmitting.value,
            errorText = errorText.value,
        )
    }
        .combine(isSubmitting) { state, submitting -> state.copy(isSubmitting = submitting) }
        .combine(errorText) { state, error -> state.copy(errorText = error) }
        .combine(selectedUsers) { state, users ->
            state.copy(selected = users, selectedIds = users.map { it.id }.toSet())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = NewChatUiState(),
        )

    /**
     * Источник списка людей.
     *
     * Пустой запрос — собеседники из текущих чатов: они уже известны локально,
     * запрос к серверу не нужен. Непустой — серверный поиск по имени и нику.
     */
    private fun searchResults(q: String) = if (q.isBlank()) {
        chatRepository.observeChats().map { chats ->
            chats.mapNotNull { it.peer }.distinctBy { it.id.raw }
        }
    } else {
        searchRepository.searchUsers(q)
    }

    fun onQueryChanged(value: String) { query.value = value }
    fun onTitleChanged(value: String) { title.value = value }
    fun onAboutChanged(value: String) { about.value = value }
    fun togglePrivate() { isPrivate.value = !isPrivate.value }

    /**
     * Выбор аватара инициирует системный пикер.
     *
     * URI приходит через Activity Result в экран и передаётся сюда;
     * ViewModel не держит `Context` и не знает о `ActivityResultContract`.
     */
    fun setAvatarUri(uri: String?) {
        avatarUri.value = uri
    }

    private val avatarUri = MutableStateFlow<String?>(null)

    fun toggleUser(user: User) {
        selectedUsers.value = if (selectedUsers.value.any { it.id == user.id }) {
            selectedUsers.value.filterNot { it.id == user.id }
        } else {
            selectedUsers.value + user
        }
    }

    /**
     * Инициализация из навигационных аргументов.
     *
     * Вызывается экраном при первом кадре. `SavedStateHandle` здесь не
     * используется, потому что один ViewModel обслуживает три маршрута
     * (NEW_GROUP, NEW_CHANNEL, FORWARDED_PICKER) с разными аргументами.
     */
    fun configure(
        mode: NewChatMode,
        isChannel: Boolean = false,
        forwardIds: List<MessageId> = emptyList(),
    ) {
        this.mode.value = mode
        this.isChannel.value = isChannel
        this.pendingForwardIds.value = forwardIds
    }

    /** Финальный шаг: создание чата или пересылка. */
    fun confirm(navigator: ChatsNavigator) {
        if (isSubmitting.value) return
        isSubmitting.value = true
        errorText.value = null
        viewModelScope.launch {
            val result = when (mode.value) {
                NewChatMode.CREATE -> create()
                NewChatMode.FORWARD_PICKER -> forward()
            }
            isSubmitting.value = false
            when (result) {
                is Outcome.NavigateToChat -> navigator.openChat(result.chatId)
                Outcome.Back -> navigator.back()
                is Outcome.Failed -> errorText.value = result.message
            }
        }
    }

    private sealed interface Outcome {
        data class NavigateToChat(val chatId: String) : Outcome
        data object Back : Outcome
        data class Failed(val message: String) : Outcome
    }

    private suspend fun create(): Outcome {
        val name = title.value.trim()
        if (name.isEmpty()) return Outcome.Failed("Введите название")

        val members = selectedUsers.value.map { it.id }
        val result = if (isChannel.value) {
            // Канал создаётся без участников: подписка — отдельное действие,
            // иначе автор канала «подписал» бы людей без их согласия
            chatRepository.createChannel(
                title = name,
                about = about.value.trim().ifBlank { null },
                isBroadcast = true,
            )
        } else {
            chatRepository.createGroup(
                title = name,
                memberIds = members,
                avatarUri = avatarUri.value,
            )
        }

        return when (result) {
            is ScResult.Success -> Outcome.NavigateToChat(result.data.id.raw)
            is ScResult.Failure -> Outcome.Failed(result.error.userMessage())
            ScResult.Loading -> Outcome.Failed("Создаём чат…")
        }
    }

    /**
     * Пересылка: один вызов на всех получателей.
     *
     * `forward(messageIds, toChatIds)` принимает списки, поэтому N получателей
     * — это один сетевой запрос, а не N. Личные чаты с получателями
     * открываются (или создаются) заранее, чтобы собрать их ID.
     */
    private suspend fun forward(): Outcome {
        val ids = pendingForwardIds.value
        val targets = selectedUsers.value
        if (ids.isEmpty()) return Outcome.Failed("Нет сообщений для пересылки")
        if (targets.isEmpty()) return Outcome.Failed("Выберите получателя")

        val chatIds = mutableListOf<ChatId>()
        targets.forEach { user ->
            when (val chat = chatRepository.openPersonalChat(user.id)) {
                is ScResult.Success -> chatIds += ChatId(chat.data.id.raw)
                is ScResult.Failure -> return Outcome.Failed(chat.error.userMessage())
                ScResult.Loading -> Unit
            }
        }

        return when (val result = messageRepository.forward(ids, chatIds)) {
            is ScResult.Success -> Outcome.Back
            is ScResult.Failure -> Outcome.Failed(result.error.userMessage())
            ScResult.Loading -> Outcome.Failed("Пересылаем…")
        }
    }

    /**
     * Человекочитаемое сообщение об ошибке.
     *
     * `ScError.message` уже содержит русский текст, но для двух случаев
     * нужна конкретика: лимит участников и отсутствие прав.
     */
    private fun ScError.userMessage(): String = when (this) {
        is ScError.Validation -> message
        is ScError.Forbidden -> "Недостаточно прав для этого действия"
        is ScError.RateLimited -> retryAfterMs
            ?.let { "Слишком часто. Повторите через ${(it / 1000) + 1} с" }
            ?: message
        is ScError.InsufficientFunds -> "Не хватает ${required - available} сильверов"
        is ScError.PremiumRequired -> "Доступно в SilverChat Premium"
        is ScError.Network -> "Нет соединения с сервером"
        else -> message
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class NewChatUiState(
    val query: String = "",
    val title: String = "",
    val about: String = "",
    val isPrivate: Boolean = true,
    val isChannel: Boolean = false,
    val results: List<User> = emptyList(),
    val selectedIds: Set<UserId> = emptySet(),
    val selected: List<User> = emptyList(),
    val mode: NewChatMode = NewChatMode.CREATE,
    val isSubmitting: Boolean = false,
    val errorText: String? = null,
)
