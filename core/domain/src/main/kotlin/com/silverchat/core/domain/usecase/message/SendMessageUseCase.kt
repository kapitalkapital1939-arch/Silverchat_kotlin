package com.silverchat.core.domain.usecase.message

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.base.SuspendUseCase
import com.silverchat.core.domain.repository.ChatRepository
import com.silverchat.core.domain.repository.MessageRepository
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.MessageId
import com.silverchat.core.model.MessageStatus
import com.silverchat.core.model.PremiumPerkCode
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Отправка сообщения.
 *
 * Ответственности UseCase (а НЕ ViewModel — иначе логика расползётся по экранам):
 *  1. валидация контента и прав на отправку (slow mode, мут, права канала);
 *  2. проверка premium-гейта для эксклюзивных стикеров/GIF;
 *  3. optimistic UI: сначала возвращаем сообщение со статусом SENDING и
 *     локальным ID, затем серверный ack заменяет его (см. MessageRepository);
 *  4. очистка черновика после успешной отправки;
 *  5. нормализация ошибок в [ScError] — UI не ловит исключения.
 */
class SendMessageUseCase @Inject constructor(
    private val messages: MessageRepository,
    private val chats: ChatRepository,
    private val wallet: WalletRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<SendMessageParams, Message>(dispatchers) {

    override suspend fun execute(params: SendMessageParams): ScResult<Message> {
        val chat = chats.observeChat(params.chatId).first()
            ?: return ScResult.Failure(ScError.NotFound("Чат не найден"))

        // ── 1. Права на отправку ──────────────────────────────────────────
        if (!chat.canWrite) {
            return ScResult.Failure(
                ScError.Forbidden(
                    message = when {
                        chat.isChannel -> "Писать в канал могут только администраторы"
                        chat.restricted -> "Чат ограничен администрацией"
                        else -> "Отправка сообщений недоступна"
                    },
                    requiredPermission = "chat.send",
                ),
            )
        }

        // ── 2. Slow mode ─────────────────────────────────────────────────
        val slowMode = chat.slowModeSeconds
        if (slowMode > 0 && params.lastSentAt != null) {
            val elapsed = (System.currentTimeMillis() - params.lastSentAt) / 1000
            if (elapsed < slowMode) {
                return ScResult.Failure(
                    ScError.RateLimited(
                        message = "Медленный режим: подождите ${slowMode - elapsed} с",
                        retryAfterMs = (slowMode - elapsed) * 1000,
                    ),
                )
            }
        }

        // ── 3. Premium-гейт для эксклюзивного контента ───────────────────
        val premiumPerk = params.content.requiredPremiumPerk()
        if (premiumPerk != null) {
            val allowed = wallet.observePerkAvailable(premiumPerk).first()
            if (!allowed) {
                return ScResult.Failure(
                    ScError.PremiumRequired(
                        message = "Эксклюзивные стикеры и GIF доступны в SilverChat Premium",
                        perk = premiumPerk.name,
                    ),
                )
            }
        }

        // ── 4. Валидация контента ────────────────────────────────────────
        params.content.validate().let { error ->
            if (error != null) return ScResult.Failure(error)
        }

        // ── 5. Отправка ──────────────────────────────────────────────────
        val result = messages.send(
            chatId = params.chatId,
            content = params.content,
            replyTo = params.replyTo,
            silent = params.silent,
            scheduledAt = params.scheduledAt,
        )

        return result
            .onSuccess { sent ->
                if (sent.status == MessageStatus.SENT || sent.status == MessageStatus.DELIVERED) {
                    // Черновик больше не нужен — синхронизируем на все устройства
                    chats.saveDraft(params.chatId, com.silverchat.core.model.Draft(updatedAt = System.currentTimeMillis()))
                }
            }
            .onFailure { error ->
                // Сетевая ошибка — не «провал»: сообщение остаётся в очереди
                // со статусом SENDING и уйдёт при восстановлении соединения.
                if (error is ScError.Network) {
                    messages.send(
                        chatId = params.chatId,
                        content = params.content,
                        replyTo = params.replyTo,
                        silent = params.silent,
                    )
                }
            }
    }
}

data class SendMessageParams(
    val chatId: ChatId,
    val content: MessageContent,
    val replyTo: MessageId? = null,
    val silent: Boolean = false,
    val scheduledAt: Long? = null,
    /** Время последней отправки — для контроля slow mode. */
    val lastSentAt: Long? = null,
)

/** Какие типы контента требуют Premium. */
private fun MessageContent.requiredPremiumPerk(): PremiumPerkCode? = when (this) {
    is MessageContent.Sticker -> if (isPremium) PremiumPerkCode.EXCLUSIVE_STICKERS else null
    is MessageContent.Gif -> if (isPremium) PremiumPerkCode.EXCLUSIVE_GIFS else null
    else -> null
}

private fun MessageContent.validate(): ScError? = when (this) {
    is MessageContent.Text -> when {
        text.isBlank() -> ScError.Validation("Пустое сообщение", field = "text")
        text.length > MAX_TEXT_LENGTH ->
            ScError.Validation("Сообщение длиннее $MAX_TEXT_LENGTH символов", field = "text")
        else -> null
    }

    is MessageContent.Poll -> when {
        question.isBlank() -> ScError.Validation("Введите вопрос", field = "question")
        options.size < MIN_POLL_OPTIONS ->
            ScError.Validation("Минимум $MIN_POLL_OPTIONS варианта ответа", field = "options")
        options.count { it.text.isNotBlank() } < MIN_POLL_OPTIONS ->
            ScError.Validation("Заполните минимум $MIN_POLL_OPTIONS варианта", field = "options")
        else -> null
    }

    is MessageContent.Photo ->
        if (url.isBlank()) ScError.Validation("Файл не загружен", field = "photo") else null

    is MessageContent.Voice ->
        if (durationMs <= 0) ScError.Validation("Пустая запись", field = "voice") else null

    is MessageContent.VideoCircle ->
        if (durationMs > MAX_CIRCLE_DURATION_MS) {
            ScError.Validation("Кружок длиннее ${MAX_CIRCLE_DURATION_MS / 1000} секунд", field = "circle")
        } else {
            null
        }

    else -> null
}

internal const val MAX_TEXT_LENGTH = 4096
internal const val MIN_POLL_OPTIONS = 2
internal const val MAX_CIRCLE_DURATION_MS = 60_000L
