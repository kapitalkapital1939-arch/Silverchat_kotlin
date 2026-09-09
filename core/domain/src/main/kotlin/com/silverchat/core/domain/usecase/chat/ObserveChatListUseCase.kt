package com.silverchat.core.domain.usecase.chat

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.domain.base.FlowUseCase
import com.silverchat.core.domain.repository.ChatRepository
import com.silverchat.core.model.Chat
import com.silverchat.core.model.ChatFolder
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.MessageStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Лента списка чатов.
 *
 * Делает всю работу, которую в Telegram-подобном приложении иначе пришлось бы
 * дублировать в каждом экране:
 *  - сортировка (закреплённые -> по времени последнего сообщения);
 *  - архив отдельным потоком;
 *  - группировка по папкам;
 *  - превью последнего сообщения с пометкой «Вы:» для исходящих;
 *  - фильтр «только непрочитанные».
 */
class ObserveChatListUseCase @Inject constructor(
    private val repository: ChatRepository,
    dispatchers: DispatcherProvider,
) : FlowUseCase<ObserveChatListParams, ChatListState>(dispatchers) {

    override fun execute(params: ObserveChatListParams): Flow<ChatListState> =
        combine(
            repository.observeChats(params.folderId, params.archived),
            repository.observeFolders(),
            repository.observeTotalUnread(),
        ) { chats, folders, unread ->
            val prepared = chats
                .filter { chat -> params.filter.matches(chat) }
                .map { it.withPreview(currentUserId = params.currentUserId) }
                .sortedWith(CHAT_COMPARATOR)

            ChatListState(
                chats = prepared,
                folders = folders,
                totalUnread = unread,
                archivedCount = chats.count { it.archived },
                isEmpty = prepared.isEmpty(),
            )
        }

    private companion object {
        val CHAT_COMPARATOR = compareByDescending<Chat> { it.pinned.isNotEmpty() }
            .thenByDescending { it.lastMessage?.sentAt ?: it.createdAt }
    }
}

data class ObserveChatListParams(
    val currentUserId: String,
    val folderId: String? = null,
    val archived: Boolean = false,
    val filter: ChatListFilter = ChatListFilter.ALL,
)

enum class ChatListFilter {
    ALL, UNREAD, PERSONAL, GROUPS, CHANNELS, BOTS, MUTED;

    fun matches(chat: Chat): Boolean = when (this) {
        ALL -> true
        UNREAD -> chat.unreadCount > 0 || chat.mentionsCount > 0
        PERSONAL -> chat.isPersonal || chat.isSecret
        GROUPS -> chat.isGroup
        CHANNELS -> chat.isChannel
        BOTS -> chat.isBot
        MUTED -> chat.isMuted
    }
}

data class ChatListState(
    val chats: List<Chat>,
    val folders: List<ChatFolder>,
    val totalUnread: Int,
    val archivedCount: Int,
    val isEmpty: Boolean,
)

/**
 * Формирует строку превью для списка чатов.
 *
 * Правила (как в Telegram):
 *  - исходящее сообщение -> префикс «Вы: » (кроме канала);
 *  - в группе перед текстом идёт имя отправителя;
 *  - медиа показывается подписью типа: «Фото», «Видео», «Голосовое», «Кружок»…;
 *  - черновик перекрывает превью пометкой «Черновик:».
 */
private fun Chat.withPreview(currentUserId: String): Chat {
    val draftText = draft?.takeIf { !it.isEmpty }?.text
    if (draftText != null) {
        return copy(lastMessage = lastMessage?.let { it.withSnippetOverride("Черновик: $draftText") } ?: lastMessage)
    }
    val message = lastMessage ?: return this
    val prefix = when {
        isChannel -> ""
        message.senderId.raw == currentUserId -> "Вы: "
        isGroup -> (message.sender?.firstName ?: "").let { if (it.isBlank()) "" else "$it: " }
        else -> ""
    }
    return copy(lastMessage = message.withSnippetOverride(prefix + message.snippet()))
}

private fun Message.withSnippetOverride(text: String): Message =
    copy(content = MessageContent.Text(text = text))

/** Текстовое представление контента для превью и для поиска. */
fun Message.snippet(): String = when (val c = content) {
    is MessageContent.Text -> c.text
    is MessageContent.Photo -> c.caption ?: "Фото"
    is MessageContent.Video -> c.caption ?: "Видео"
    is MessageContent.Voice -> "Голосовое сообщение"
    is MessageContent.VideoCircle -> "Видеосообщение"
    is MessageContent.File -> c.fileName
    is MessageContent.Sticker -> "Стикер ${c.emoji}"
    is MessageContent.Gif -> "GIF"
    is MessageContent.Location -> c.title ?: "Геолокация"
    is MessageContent.Contact -> "${c.firstName} ${c.lastName.orEmpty()}"
    is MessageContent.Poll -> "Опрос: ${c.question}"
    is MessageContent.Service -> c.title
    is MessageContent.Invite -> "Приглашение в «${c.chatTitle}»"
    is MessageContent.Gift -> "Подарок «${c.title}»"
}

/** Итоговый статус для галочек в пузыре. */
val Message.ticksState: MessageStatus get() = status
