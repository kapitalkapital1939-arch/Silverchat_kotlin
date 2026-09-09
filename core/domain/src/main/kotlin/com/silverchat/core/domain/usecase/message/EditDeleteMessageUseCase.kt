package com.silverchat.core.domain.usecase.message

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.base.SuspendUseCase
import com.silverchat.core.domain.repository.MessageRepository
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageId
import javax.inject.Inject

/**
 * Редактирование сообщения.
 *
 * Ограничения продукта (совпадают с правилами Telegram):
 *  - редактировать можно только свои сообщения;
 *  - текст не должен совпадать с текущим (иначе сервер вернёт 409);
 *  - после редактирования в пузыре появляется метка «изменено»;
 *  - медиа не редактируется, только подпись (сервер поддерживает caption-only edit).
 */
class EditMessageUseCase @Inject constructor(
    private val repository: MessageRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<EditMessageParams, Message>(dispatchers) {

    override suspend fun execute(params: EditMessageParams): ScResult<Message> {
        val newText = params.newText.trim()

        if (newText.isEmpty()) {
            return ScResult.Failure(ScError.Validation("Сообщение не может быть пустым", "text"))
        }
        if (newText.length > MAX_TEXT_LENGTH) {
            return ScResult.Failure(
                ScError.Validation("Превышен лимит в $MAX_TEXT_LENGTH символов", "text"),
            )
        }
        if (newText == params.currentText) {
            return ScResult.Failure(ScError.Conflict("Текст не изменился"))
        }

        return repository.edit(params.chatId, params.messageId, newText)
    }
}

data class EditMessageParams(
    val chatId: ChatId,
    val messageId: MessageId,
    val currentText: String,
    val newText: String,
)

/**
 * Удаление сообщения — для себя или для всех.
 *
 * «Для всех» доступно:
 *  - всегда для своих сообщений в личных чатах;
 *  - для любых сообщений — владельцу/админу группы или канала;
 *  - модератору админ-панели (отдельный UseCase в :feature:admin).
 */
class DeleteMessageUseCase @Inject constructor(
    private val repository: MessageRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<DeleteMessageParams, Unit>(dispatchers) {

    override suspend fun execute(params: DeleteMessageParams): ScResult<Unit> {
        if (params.messageIds.isEmpty()) {
            return ScResult.Failure(ScError.Validation("Не выбраны сообщения", "messageIds"))
        }

        val forEveryone = params.forEveryone && params.canDeleteForEveryone
        return repository.delete(params.chatId, params.messageIds, forEveryone)
    }
}

data class DeleteMessageParams(
    val chatId: ChatId,
    val messageIds: List<MessageId>,
    val forEveryone: Boolean,
    /** Права текущего пользователя в этом чате (считаются в ChatInfoUseCase). */
    val canDeleteForEveryone: Boolean,
)
