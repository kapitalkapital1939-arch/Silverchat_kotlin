package com.silverchat.feature.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.domain.repository.ChatRepository
import com.silverchat.core.domain.repository.MediaRepository
import com.silverchat.core.domain.repository.MessageRepository
import com.silverchat.core.domain.usecase.message.DeleteMessageParams
import com.silverchat.core.domain.usecase.message.DeleteMessageUseCase
import com.silverchat.core.domain.usecase.message.EditMessageParams
import com.silverchat.core.domain.usecase.message.EditMessageUseCase
import com.silverchat.core.domain.usecase.message.QuickReactUseCase
import com.silverchat.core.domain.usecase.message.SendMessageParams
import com.silverchat.core.domain.usecase.message.SendMessageUseCase
import com.silverchat.core.domain.usecase.message.ToggleReactionParams
import com.silverchat.core.domain.usecase.message.ToggleReactionUseCase
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.Draft
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.MessageId
import com.silverchat.core.model.MessageStatus
import com.silverchat.core.model.QuickReactions
import com.silverchat.core.model.ReactionKind
import com.silverchat.core.model.UserId
import com.silverchat.core.model.UserPresence
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Экран диалога.
 *
 * Архитектурные решения:
 *
 * 1. **chatId берётся из SavedStateHandle**, а не из конструктора: после
 *    смерти процесса на экране диалога приложение должно открыться в том же
 *    чате. Hilt кладёт аргументы навигации в SavedStateHandle автоматически.
 *
 * 2. **Offline-first**: лента сообщений — это Room-Flow, а не сетевой запрос.
 *    Экран открывается мгновенно, новые сообщения дописывает WebSocket.
 *
 * 3. **Оптимистичная отправка**: сообщение сразу появляется в ленте со
 *    статусом SENDING (это делает SendMessageUseCase через репозиторий).
 *    Если сервер ответил ошибкой, статус меняется на FAILED и у пузыря
 *    появляется кнопка повтора. Пользователь никогда не ждёт ответа сервера.
 *
 * 4. **Дебаунс «печатает…»**: событие typing уходит не на каждый символ,
 *    а раз в 3 секунды — иначе WebSocket-канал забивается мусором.
 *
 * 5. **Черновик сохраняется** при каждом изменении поля ввода и при уходе
 *    с экрана: черновики видны в списке чатов.
 */
@HiltViewModel
class ChatThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chats: ChatRepository,
    private val messages: MessageRepository,
    private val media: MediaRepository,
    private val sendMessage: SendMessageUseCase,
    private val editMessage: EditMessageUseCase,
    private val deleteMessage: DeleteMessageUseCase,
    private val toggleReaction: ToggleReactionUseCase,
    private val quickReact: QuickReactUseCase,
) : ViewModel() {

    val chatId: ChatId = ChatId(
        savedStateHandle.get<String>(Routes.Args.CHAT_ID)
            ?: error("chatId обязателен для маршрута ${Routes.CHAT_THREAD}"),
    )

    /** ID текущего пользователя заполняется из :app — нужен для isOutgoing. */
    private val currentUserId = MutableStateFlow("")

    /** Текст поля ввода. */
    private val inputText = MutableStateFlow("")

    /** Сообщение, на которое отвечаем. */
    private val replyTarget = MutableStateFlow<Message?>(null)

    /** Сообщение, которое сейчас редактируем. */
    private val editingTarget = MutableStateFlow<Message?>(null)

    /** Сообщения, выбранные для пересылки/удаления. */
    private val selectedIds = MutableStateFlow<Set<MessageId>>(emptySet())

    /** Сообщение, для которого открыта панель реакций. */
    private val reactionTarget = MutableStateFlow<MessageId?>(null)

    /** Режим записи голосового. */
    private val recording = MutableStateFlow<RecordingUi?>(null)

    private val _events = MutableStateFlow<ThreadEvent?>(null)
    val events: StateFlow<ThreadEvent?> = _events

    val uiState: StateFlow<ChatThreadUiState> = combine(
        chats.observeChat(chatId),
        messages.observeMessages(chatId),
        chats.observeTyping(chatId),
        inputText,
        replyTarget,
    ) { chat, messageList, typingUsers, input, reply ->
        buildState(chat, messageList, typingUsers, input, reply)
    }.combine(
        combine(editingTarget, selectedIds, reactionTarget) { editing, selected, reactionTargetId ->
            Triple(editing, selected, reactionTargetId)
        },
    ) { base, (editing, selected, reactionTargetId) ->
        base.copy(
            editingMessage = editing,
            selectedIds = selected,
            reactionPanelFor = reactionTargetId,
            recording = recording.value,
            selectionMode = selected.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ChatThreadUiState(),
    )

    private var lastSentAt: Long? = null
    private var typingSentAt = 0L
    private var recordingJob: Job? = null

    fun setSession(userId: String) {
        currentUserId.value = userId
    }

    /* ── Ввод текста ──────────────────────────────────────────────────── */

    fun onInputChanged(text: String) {
        inputText.value = text
        sendTypingDebounced(typing = text.isNotBlank())
        saveDraftDebounced(text)
    }

    /**
     * «Печатает…» — не чаще раза в [TYPING_THROTTLE_MS].
     *
     * Сервер сам снимает статус по таймауту, поэтому стоп-сигнал
     * отправлять не нужно: лишний трафик без пользы.
     */
    private fun sendTypingDebounced(typing: Boolean) {
        if (!typing) return
        val now = System.currentTimeMillis()
        if (now - typingSentAt < TYPING_THROTTLE_MS) return
        typingSentAt = now
        viewModelScope.launch { chats.sendTyping(chatId, typing = true) }
    }

    private var draftJob: Job? = null

    /** Черновик сохраняется с задержкой: не пишем в БД на каждый символ. */
    private fun saveDraftDebounced(text: String) {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MS)
            // Draft не хранит chatId: он принадлежит конкретному чату
            // и сохраняется в Room по ключу chatId (см. DraftDao)
            chats.saveDraft(chatId, Draft(text = text, updatedAt = System.currentTimeMillis()))
        }
    }

    /* ── Отправка ─────────────────────────────────────────────────────── */

    fun sendText() {
        val text = inputText.value.trim()
        if (text.isEmpty()) return

        val editing = editingTarget.value
        if (editing != null) {
            submitEdit(editing, text)
            return
        }

        val replyTo = replyTarget.value?.id
        clearComposer()

        viewModelScope.launch {
            val result = sendMessage(
                SendMessageParams(
                    chatId = chatId,
                    content = MessageContent.Text(text = text),
                    replyTo = replyTo,
                    lastSentAt = lastSentAt,
                ),
            )
            when (result) {
                is ScResult.Success -> lastSentAt = result.data.sentAt
                is ScResult.Failure -> _events.value = ThreadEvent.Error(result.error.message)
                ScResult.Loading -> Unit
            }
        }
    }

    fun retryFailed(message: Message) = viewModelScope.launch {
        when (val result = messages.retryFailed(message.id)) {
            is ScResult.Failure -> _events.value = ThreadEvent.Error(result.error.message)
            else -> Unit
        }
    }

    /* ── Ответ / редактирование / пересылка ───────────────────────────── */

    fun startReply(message: Message) {
        editingTarget.value = null
        replyTarget.value = message
    }

    fun startEdit(message: Message) {
        val content = message.content
        // Редактируется только текст: у медиа меняется лишь подпись,
        // и это отдельный сценарий (см. editCaption)
        val text = when (content) {
            is MessageContent.Text -> content.text
            is MessageContent.Photo -> content.caption.orEmpty()
            is MessageContent.Video -> content.caption.orEmpty()
            else -> return
        }
        replyTarget.value = null
        editingTarget.value = message
        inputText.value = text
    }

    private fun submitEdit(message: Message, newText: String) {
        editingTarget.value = null
        inputText.value = ""
        viewModelScope.launch {
            val result = editMessage(
                EditMessageParams(
                    chatId = chatId,
                    messageId = message.id,
                    currentText = (message.content as? MessageContent.Text)?.text.orEmpty(),
                    newText = newText,
                ),
            )
            if (result is ScResult.Failure) {
                _events.value = ThreadEvent.Error(result.error.message)
            }
        }
    }

    fun cancelComposer() {
        clearComposer()
    }

    private fun clearComposer() {
        editingTarget.value = null
        replyTarget.value = null
        inputText.value = ""
    }

    /* ── Выделение сообщений ──────────────────────────────────────────── */

    fun toggleSelection(message: Message) {
        selectedIds.update { current ->
            if (message.id in current) current - message.id else current + message.id
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun deleteSelected(forEveryone: Boolean) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        val canDeleteForAll = uiState.value.chat?.canEditInfo == true
        clearSelection()
        viewModelScope.launch {
            val result = deleteMessage(
                DeleteMessageParams(
                    chatId = chatId,
                    messageIds = ids,
                    forEveryone = forEveryone,
                    canDeleteForEveryone = canDeleteForAll,
                ),
            )
            if (result is ScResult.Failure) {
                _events.value = ThreadEvent.Error(result.error.message)
            } else {
                _events.value = ThreadEvent.Deleted(ids.size)
            }
        }
    }

    fun forwardSelected() {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        clearSelection()
        _events.value = ThreadEvent.Forward(ids.map { it.raw })
    }

    /* ── Реакции ──────────────────────────────────────────────────────── */

    fun openReactionPanel(message: Message) {
        reactionTarget.value = message.id
    }

    fun closeReactionPanel() {
        reactionTarget.value = null
    }

    fun react(message: Message, kind: ReactionKind) {
        reactionTarget.value = null
        val chat = uiState.value.chat ?: return
        viewModelScope.launch {
            val result = toggleReaction(
                ToggleReactionParams(
                    chatId = chatId,
                    messageId = message.id,
                    kind = kind,
                    currentReaction = message.myReaction,
                    // Реакции в канале отключаются настройками канала
                    reactionsEnabledInChat = !chat.restricted,
                ),
            )
            if (result is ScResult.Failure) {
                _events.value = ThreadEvent.Error(result.error.message)
            }
        }
    }

    /** Двойной тап по сообщению — быстрая реакция «сердце». */
    fun doubleTapReact(message: Message) {
        viewModelScope.launch {
            quickReact(chatId, message.id, message.myReaction)
        }
    }

    /* ── Закрепление ──────────────────────────────────────────────────── */

    fun pinMessage(message: Message, notifyMembers: Boolean) = viewModelScope.launch {
        messages.pin(chatId, message.id, notifyMembers)
    }

    fun unpinMessage(message: Message) = viewModelScope.launch {
        messages.unpin(chatId, message.id)
    }

    /* ── Голосовые сообщения ──────────────────────────────────────────── */

    fun startVoiceRecording() {
        if (recording.value != null) return
        viewModelScope.launch {
            val session = media.startVoiceRecording().getOrNull() ?: return@launch
            recording.value = RecordingUi(sessionId = session.id, elapsedMs = 0L, amplitude = 0.2f)
            recordingJob?.cancel()
            recordingJob = viewModelScope.launch {
                val startedAt = System.currentTimeMillis()
                while (recording.value != null) {
                    delay(RECORD_TICK_MS)
                    recording.update { current ->
                        current?.copy(
                            elapsedMs = System.currentTimeMillis() - startedAt,
                            // Амплитуда приходит из рекордера; здесь — плейсхолдер,
                            // реальное значение подставляет MediaRepository через Flow
                            amplitude = current.amplitude,
                        )
                    }
                }
            }
        }
    }

    fun stopVoiceRecordingAndSend() {
        val session = recording.value ?: return
        recording.value = null
        recordingJob?.cancel()
        viewModelScope.launch {
            val local = media.stopVoiceRecording(session.sessionId).getOrNull() ?: return@launch
            val remote = media.upload(local, chatId).getOrNull() ?: run {
                _events.value = ThreadEvent.Error("Не удалось загрузить голосовое сообщение")
                return@launch
            }
            sendMessage(
                SendMessageParams(
                    chatId = chatId,
                    content = MessageContent.Voice(
                        url = remote.url,
                        durationMs = remote.durationMs,
                        waveform = remote.waveform,
                        sizeBytes = remote.sizeBytes,
                    ),
                    lastSentAt = lastSentAt,
                ),
            ).onFailure { _events.value = ThreadEvent.Error(it.message) }
        }
    }

    fun cancelVoiceRecording() {
        val session = recording.value ?: return
        recording.value = null
        recordingJob?.cancel()
        viewModelScope.launch { media.cancelRecording(session.sessionId) }
    }

    /* ── Медиа ────────────────────────────────────────────────────────── */

    /** Отправка фото/видео: сжатие -> загрузка -> сообщение. */
    fun sendMedia(localUri: String, isVideo: Boolean) = viewModelScope.launch {
        val compressed = if (isVideo) {
            media.compressVideo(localUri, com.silverchat.core.domain.repository.VideoQuality.MEDIUM).getOrNull()
        } else {
            media.compressImage(localUri).getOrNull()
        } ?: return@launch

        val remote = media.upload(compressed, chatId).getOrNull() ?: run {
            _events.value = ThreadEvent.Error("Не удалось загрузить файл")
            return@launch
        }

        val content: MessageContent = if (isVideo) {
            MessageContent.Video(
                url = remote.url,
                posterUrl = remote.thumbUrl,
                durationMs = remote.durationMs,
                width = remote.width,
                height = remote.height,
                sizeBytes = remote.sizeBytes,
                caption = inputText.value.trim().ifBlank { null },
            )
        } else {
            MessageContent.Photo(
                url = remote.url,
                thumbUrl = remote.thumbUrl,
                width = remote.width,
                height = remote.height,
                sizeBytes = remote.sizeBytes,
                caption = inputText.value.trim().ifBlank { null },
            )
        }
        if (content is MessageContent.Photo || content is MessageContent.Video) {
            inputText.value = ""
        }

        sendMessage(
            SendMessageParams(chatId = chatId, content = content, lastSentAt = lastSentAt),
        ).onSuccess { lastSentAt = it.sentAt }
            .onFailure { _events.value = ThreadEvent.Error(it.message) }
    }

    /* ── Прочтение и подгрузка истории ────────────────────────────────── */

    /** Отмечаем прочитанным при открытии диалога и при каждом новом сообщении. */
    fun markVisibleRead(messageIds: List<MessageId>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch { messages.markRead(chatId, messageIds) }
    }

    fun loadOlder(before: MessageId) = viewModelScope.launch {
        messages.loadOlder(chatId, before)
    }

    fun consumeEvent() {
        _events.value = null
    }

    /**
     * Сохранить черновик при уходе с экрана.
     *
     * Вызывается из UI в `DisposableEffect.onDispose`, а НЕ из [onCleared]:
     * в onCleared viewModelScope уже отменён, и suspend-запись в Room
     * просто не выполнится. Это частая причина «пропавших» черновиков.
     */
    fun flushDraft() {
        val text = inputText.value
        draftJob?.cancel()
        if (text.isBlank()) return
        viewModelScope.launch {
            chats.saveDraft(chatId, Draft(text = text, updatedAt = System.currentTimeMillis()))
        }
    }

    override fun onCleared() {
        draftJob?.cancel()
        recordingJob?.cancel()
        super.onCleared()
    }

    /* ── Сборка состояния ─────────────────────────────────────────────── */

    private fun buildState(
        chat: Chat?,
        messageList: List<Message>,
        typingUsers: List<UserId>,
        input: String,
        reply: Message?,
    ): ChatThreadUiState {
        val uid = currentUserId.value
        val sorted = messageList.sortedBy { it.sentAt }

        // Группировка: подряд идущие сообщения одного автора в пределах
        // GROUP_WINDOW_MS рисуются без аватара и без «хвоста» пузыря
        val bubbles = sorted.mapIndexed { index, message ->
            val previous = sorted.getOrNull(index - 1)
            val next = sorted.getOrNull(index + 1)
            BubbleUi(
                message = message,
                isOutgoing = uid.isNotEmpty() && message.senderId.raw == uid,
                groupedWithPrevious = previous != null &&
                    previous.senderId == message.senderId &&
                    message.sentAt - previous.sentAt < GROUP_WINDOW_MS,
                groupedWithNext = next != null &&
                    next.senderId == message.senderId &&
                    next.sentAt - message.sentAt < GROUP_WINDOW_MS,
                showDaySeparator = previous == null || !sameDay(previous.sentAt, message.sentAt),
            )
        }

        val unreadDividerIndex = sorted.indexOfLast { it.isUnreadFor(uid) }

        return ChatThreadUiState(
            chat = chat,
            bubbles = bubbles,
            inputText = input,
            replyTo = reply,
            typingUsers = typingUsers,
            typingNames = typingUsers
                .mapNotNull { id -> chat?.peer?.takeIf { it.id == id }?.firstName }
                .let { names -> if (names.isEmpty() && typingUsers.isNotEmpty()) listOf("кто-то") else names },
            unreadDividerIndex = unreadDividerIndex,
            canWrite = chat?.canWrite ?: true,
            isChannel = chat?.isChannel == true,
            slowModeSeconds = chat?.slowModeSeconds ?: 0,
            onlineSubtitle = presenceSubtitle(chat),
            isLoading = chat == null,
        )
    }

    /**
     * Непрочитанное входящее сообщение.
     *
     * Точный признак «прочитано мной» сервер отдаёт в статусе только для
     * исходящих; для входящих считаем непрочитанными все, что новее
     * последней отметки о прочтении (её ведёт ChatRepository.markRead).
     */
    private fun Message.isUnreadFor(currentUserId: String): Boolean =
        senderId.raw != currentUserId && status != MessageStatus.READ

    private fun presenceSubtitle(chat: Chat?): String? {
        if (chat == null) return null
        if (chat.isChannel) {
            return "${chat.membersCount} подписчиков"
        }
        if (chat.isGroup) {
            val online = chat.onlineCount
            return if (online > 0) {
                "${chat.membersCount} участников, $online в сети"
            } else {
                "${chat.membersCount} участников"
            }
        }
        val presence = chat.peer?.presence ?: return null
        return when (presence) {
            UserPresence.Online -> "в сети"
            is UserPresence.Offline ->
                if (presence.lastSeenAt <= 0L) {
                    "был(а) недавно"
                } else {
                    "был(а) ${TimeFormatter.lastSeen(presence.lastSeenAt)}"
                }
            UserPresence.Recently -> "был(а) недавно"
            UserPresence.WithinWeek -> "был(а) на этой неделе"
            UserPresence.WithinMonth -> "был(а) в этом месяце"
            UserPresence.LongAgo -> "был(а) давно"
            is UserPresence.Typing -> "печатает…"
        }
    }

    /**
     * Один ли день у двух таймштампов.
     *
     * Целочисленное деление вместо Calendar: функция вызывается на каждое
     * сообщение ленты, и аллокация двух Calendar на сообщение заметна
     * при скролле длинной истории.
     */
    private fun sameDay(a: Long, b: Long): Boolean {
        val zoneOffsetMs = java.util.TimeZone.getDefault().getOffset(a).toLong()
        return (a + zoneOffsetMs) / DAY_MS == (b + zoneOffsetMs) / DAY_MS
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TYPING_THROTTLE_MS = 3_000L
        const val DRAFT_DEBOUNCE_MS = 600L
        const val GROUP_WINDOW_MS = 5 * 60 * 1000L
        const val RECORD_TICK_MS = 100L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/** Одно сообщение вместе с признаками группировки для отрисовки пузыря. */
data class BubbleUi(
    val message: Message,
    val isOutgoing: Boolean,
    val groupedWithPrevious: Boolean,
    val groupedWithNext: Boolean,
    val showDaySeparator: Boolean,
)

data class RecordingUi(
    val sessionId: String,
    val elapsedMs: Long,
    val amplitude: Float,
    val waveform: List<Float> = emptyList(),
)

data class ChatThreadUiState(
    val chat: Chat? = null,
    val bubbles: List<BubbleUi> = emptyList(),
    val inputText: String = "",
    val replyTo: Message? = null,
    val editingMessage: Message? = null,
    val selectedIds: Set<MessageId> = emptySet(),
    val selectionMode: Boolean = false,
    val reactionPanelFor: MessageId? = null,
    val typingUsers: List<UserId> = emptyList(),
    val typingNames: List<String> = emptyList(),
    val unreadDividerIndex: Int = -1,
    val recording: RecordingUi? = null,
    val canWrite: Boolean = true,
    val isChannel: Boolean = false,
    val slowModeSeconds: Int = 0,
    val onlineSubtitle: String? = null,
    val isLoading: Boolean = true,
) {
    val title: String get() = chat?.displayName ?: "Чат"
    val composerHint: String
        get() = when {
            editingMessage != null -> "Измените сообщение…"
            replyTo != null -> "Ответить ${replyTo?.sender?.firstName?.let { "для $it" } ?: ""}"
            !canWrite -> "Отправка недоступна"
            isChannel -> "Комментировать можно в обсуждении"
            else -> "Сообщение"
        }
}

/** Одноразовые события экрана (снекбар, навигация пересылки). */
sealed interface ThreadEvent {
    data class Error(val message: String) : ThreadEvent
    data class Deleted(val count: Int) : ThreadEvent
    data class Forward(val messageIds: List<String>) : ThreadEvent
}

/** Набор быстрых реакций для панели — из модели, чтобы не дублировать список. */
val QuickReactionKinds: List<ReactionKind> = QuickReactions.PANEL
