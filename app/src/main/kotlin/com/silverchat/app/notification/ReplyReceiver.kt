package com.silverchat.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.silverchat.app.di.ApplicationScope
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.MessageRepository
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.MessageContent
import com.silverchat.core.notifications.DirectReplyContract
import com.silverchat.core.notifications.NotificationFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Быстрый ответ из шторки уведомлений.
 *
 * Пользователь вводит текст прямо в уведомлении, не открывая приложение.
 * Система передаёт его через `RemoteInput` вместе с broadcast-интентом,
 * который собирает `NotificationIntentFactory.replyPendingIntent`.
 *
 * ── Почему ресивер, а не Activity ───────────────────────────────────────
 * Ответ должен уходить в фоне: поднимать `MainActivity` значило бы мелькнуть
 * интерфейсом и потерять контекст, в котором человек находился. Ресивер
 * отрабатывает, отправляет сообщение и гаснет.
 *
 * ── Про [goAsync] ───────────────────────────────────────────────────────
 * `onReceive` обязан вернуться за ~10 секунд, а отправка сообщения — это
 * запись в Room и сетевой запрос. `goAsync()` продлевает жизнь интента до
 * `finish()`, поэтому корутина запускается в области приложения и корректно
 * завершает обработку. Без `finish()` система считает обработку зависшей и
 * в следующий раз доставит broadcast с задержкой.
 *
 * ── Про офлайн ──────────────────────────────────────────────────────────
 * Отправка оптимистичная: `MessageRepositoryImpl` пишет сообщение в Room и
 * в очередь исходящих до ответа сервера, поэтому ответ из шторки уходит даже
 * без сети и появится в диалоге сразу после переподключения.
 */
@AndroidEntryPoint
class ReplyReceiver : BroadcastReceiver() {

    @Inject
    lateinit var messageRepository: MessageRepository

    @Inject
    lateinit var notifications: NotificationFactory

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DirectReplyContract.ACTION) return

        val chatId = intent.getStringExtra(DirectReplyContract.KEY_CHAT_ID)
        if (chatId.isNullOrBlank()) {
            ScLogger.w(LogTag.CHAT, "Быстрый ответ без идентификатора чата — игнорируем")
            return
        }

        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(DirectReplyContract.KEY_REMOTE_INPUT)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) {
            // Пустое поле — не ошибка: пользователь мог случайно нажать кнопку
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                sendReply(chatId, text)
            } finally {
                // Обязательно даже при сбое: иначе система будет считать
                // обработку незавершённой.
                pendingResult.finish()
            }
        }
    }

    private suspend fun sendReply(chatId: String, text: String) {
        when (val result = messageRepository.send(ChatId(chatId), MessageContent.Text(text))) {
            is ScResult.Success -> {
                // Ответ отправлен — диалог больше не «непрочитан», гасим группу
                notifications.cancelGroup(chatId)
            }

            is ScResult.Failure -> ScLogger.w(
                LogTag.CHAT,
                "Быстрый ответ не отправлен: ${result.error.message}",
            )

            ScResult.Loading -> Unit
        }
    }
}
