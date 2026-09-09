package com.silverchat.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.model.Chat
import com.silverchat.core.model.Message
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сборка уведомлений.
 *
 * Ключевые решения:
 *  - [NotificationCompat.MessagingStyle] с кэшем истории: в шторке видно
 *    последние сообщения диалога, а не «1 новое сообщение»;
 *  - группировка по chatId (GROUP_KEY): 20 уведомлений из одного чата
 *    схлопываются в одно раскрывающееся;
 *  - содержимое скрывается на локскрине, если пользователь выключил
 *    «показывать текст в уведомлениях» (флаг из SecureStorage);
 *  - для звонков — fullScreenIntent: на заблокированном экране звонок
 *    открывается сразу, без разблокировки.
 */
@Singleton
class NotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val intentFactory: NotificationIntentFactory,
) {

    fun buildMessageNotification(
        chat: Chat,
        message: Message,
        history: List<Message>,
        showContent: Boolean,
    ): Notification {
        val sender = Person.Builder()
            .setName(message.sender?.fullName ?: chat.displayName)
            .setKey(message.senderId.raw)
            .setIcon(message.sender?.avatar?.staticUrl?.let { IconCompat.createWithContentUri(it) })
            .build()

        val style = NotificationCompat.MessagingStyle(sender).apply {
            conversationTitle = if (chat.isGroup || chat.isChannel) chat.title else null
            isGroupConversation = chat.isGroup

            history.takeLast(HISTORY_LIMIT).forEach { historic ->
                val person = if (historic.senderId == message.senderId) {
                    sender
                } else {
                    Person.Builder().setName("Вы").build()
                }
                addMessage(
                    if (showContent) historic.previewText() else HIDDEN_TEXT,
                    historic.sentAt,
                    person,
                )
            }
        }

        val channel = when {
            chat.isChannel -> NotificationChannel.CHANNELS
            chat.isGroup -> NotificationChannel.GROUPS
            else -> NotificationChannel.MESSAGES
        }

        return NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(chat.displayName)
            .setContentText(if (showContent) message.previewText() else HIDDEN_TEXT)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup("${GROUP_PREFIX}${chat.id.raw}")
            .setSilent(message.silent || chat.isMuted)
            .setContentIntent(intentFactory.openChatPendingIntent(chat.id.raw, message.id.raw))
            .addAction(buildReplyAction(chat.id.raw))
            .apply {
                if (showContent) {
                    setLargeIcon(message.sender?.avatar?.staticUrl?.let { IconCompat.createWithContentUri(it) }?.toBitmapOrNull())
                }
            }
            .setVisibility(
                if (showContent) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_SECRET,
            )
            .build()
    }

    /**
     * Уведомление входящего звонка с fullScreenIntent.
     *
     * setOngoing(true) + CATEGORY_CALL + HIGH — системные требования,
     * иначе Android не покажет полноэкранный интерфейс звонка.
     */
    fun buildIncomingCallNotification(
        chat: Chat,
        callId: String,
        isVideo: Boolean,
    ): Notification = NotificationCompat.Builder(context, NotificationChannel.CALLS.id)
        .setSmallIcon(android.R.drawable.stat_sys_phone_call)
        .setContentTitle(chat.displayName)
        .setContentText(if (isVideo) "Входящий видеозвон…" else "Входящий звонок…")
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setOngoing(true)
        .setAutoCancel(false)
        .setTimeoutAfter(CALL_NOTIFICATION_TIMEOUT_MS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setFullScreenIntent(intentFactory.openCallPendingIntent(callId), true)
        .setContentIntent(intentFactory.openCallPendingIntent(callId))
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Отклонить",
            intentFactory.declineCallPendingIntent(callId),
        )
        .addAction(
            android.R.drawable.ic_menu_call,
            "Ответить",
            intentFactory.acceptCallPendingIntent(callId),
        )
        .build()

    fun buildUploadNotification(title: String, progress: Int, uploadId: String): Notification =
        NotificationCompat.Builder(context, NotificationChannel.UPLOADS.id)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setSilent(true)
            .setGroup(GROUP_UPLOADS)
            .setContentIntent(intentFactory.openAppPendingIntent())
            .build()

    fun buildMarketNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(context, NotificationChannel.MARKET.id)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(intentFactory.openMarketPendingIntent())
            .build()

    fun buildAdminNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(context, NotificationChannel.ADMIN.id)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(intentFactory.openAdminPendingIntent())
            .build()

    /** Публикация с проверкой разрешения POST_NOTIFICATIONS (Android 13+). */
    fun notify(id: Int, notification: Notification) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            ScLogger.d(LogTag.UI, "Уведомления отключены пользователем")
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
            .onFailure {
                // На Android 13 без разрешения бросает SecurityException —
                // это штатная ситуация, а не баг: просто не показываем
                ScLogger.w(LogTag.UI, "Уведомление не опубликовано", it)
            }
    }

    fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun cancelGroup(chatId: String) =
        NotificationManagerCompat.from(context).cancel(GROUP_PREFIX + chatId)

    /**
     * Действие «Ответить» с полем ввода прямо в шторке.
     *
     * `RemoteInput` обязателен: без него кнопка просто открыла бы приложение,
     * и ответ из уведомления превратился бы в три лишних тапа. PendingIntent
     * здесь — broadcast на [DirectReplyContract.ACTION], а не Activity,
     * потому что результат ввода система передаёт через
     * `RemoteInput.getResultsFromIntent`, который обрабатывает ресивер.
     *
     * `FLAG_MUTABLE` — требование платформы: в иммутабельный PendingIntent
     * система не может дописать результаты ввода.
     */
    private fun buildReplyAction(chatId: String): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            REPLY_LABEL,
            intentFactory.replyPendingIntent(chatId),
        )
            .addRemoteInput(
                RemoteInput.Builder(DirectReplyContract.KEY_REMOTE_INPUT)
                    .setLabel(REPLY_LABEL)
                    .build(),
            )
            // Позволяет ответить повторно, не разворачивая уведомление
            .setAllowGeneratedReplies(true)
            .build()

    private fun Message.previewText(): String = when (val c = content) {
        is com.silverchat.core.model.MessageContent.Text -> c.text
        is com.silverchat.core.model.MessageContent.Photo -> c.caption ?: "📷 Фото"
        is com.silverchat.core.model.MessageContent.Video -> c.caption ?: "🎬 Видео"
        is com.silverchat.core.model.MessageContent.Voice -> "🎤 Голосовое сообщение"
        is com.silverchat.core.model.MessageContent.VideoCircle -> "⭕ Видеосообщение"
        is com.silverchat.core.model.MessageContent.File -> "📎 ${c.fileName}"
        is com.silverchat.core.model.MessageContent.Sticker -> "${c.emoji} Стикер"
        is com.silverchat.core.model.MessageContent.Gif -> "GIF"
        is com.silverchat.core.model.MessageContent.Location -> "📍 Геолокация"
        is com.silverchat.core.model.MessageContent.Contact -> "👤 Контакт"
        is com.silverchat.core.model.MessageContent.Poll -> "📊 ${c.question}"
        is com.silverchat.core.model.MessageContent.Service -> c.title
        is com.silverchat.core.model.MessageContent.Invite -> "🔗 Приглашение"
        is com.silverchat.core.model.MessageContent.Gift -> "🎁 ${c.title}"
    }

    private fun IconCompat.toBitmapOrNull(): android.graphics.Bitmap? = null

    private companion object {
        const val REPLY_LABEL = "Ответить"
        const val GROUP_PREFIX = "sc_chat_"
        const val GROUP_UPLOADS = "sc_uploads"
        const val HISTORY_LIMIT = 6
        const val HIDDEN_TEXT = "Содержимое скрыто"
        const val CALL_NOTIFICATION_TIMEOUT_MS = 45_000L
    }
}

/** Создание PendingIntent'ов изолировано: флаги MUTABLE/IMMUTABLE обязательны с API 31. */
@Singleton
class NotificationIntentFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun openAppPendingIntent(): PendingIntent =
        buildIntent("OPEN_APP", emptyMap(), REQUEST_APP)

    fun openChatPendingIntent(chatId: String, messageId: String): PendingIntent =
        buildIntent("OPEN_CHAT", mapOf("chat_id" to chatId, "message_id" to messageId), REQUEST_CHAT)

    /**
     * PendingIntent быстрого ответа — broadcast, а не Activity.
     *
     * Отличается от остальных намеренно: система доставляет текст из
     * `RemoteInput` вместе с этим интентом, и обработать его должен
     * ресивер, не поднимая приложение. `setPackage` делает рассылку
     * адресной — иначе фоновые неявные broadcast заблокированы с API 26.
     */
    fun replyPendingIntent(chatId: String): PendingIntent {
        val intent = Intent(DirectReplyContract.ACTION).apply {
            setPackage(context.packageName)
            putExtra(DirectReplyContract.KEY_CHAT_ID, chatId)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_REPLY, intent, flags)
    }

    fun openCallPendingIntent(callId: String): PendingIntent =
        buildIntent("OPEN_CALL", mapOf("call_id" to callId), REQUEST_CALL)

    fun acceptCallPendingIntent(callId: String): PendingIntent =
        buildIntent("ACCEPT_CALL", mapOf("call_id" to callId), REQUEST_ACCEPT)

    fun declineCallPendingIntent(callId: String): PendingIntent =
        buildIntent("DECLINE_CALL", mapOf("call_id" to callId), REQUEST_DECLINE)

    fun openMarketPendingIntent(): PendingIntent =
        buildIntent("OPEN_MARKET", emptyMap(), REQUEST_MARKET)

    fun openAdminPendingIntent(): PendingIntent =
        buildIntent("OPEN_ADMIN", emptyMap(), REQUEST_ADMIN)

    private fun buildIntent(
        action: String,
        extras: Map<String, String>,
        requestCode: Int,
        mutable: Boolean = false,
    ): PendingIntent {
        val intent = Intent(context, notificationReceiverClass()).apply {
            this.action = "com.silverchat.notification.$action"
            extras.forEach { (key, value) -> putExtra(key, value) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    /**
     * Класс Activity резолвим по имени, чтобы :core:notifications не зависел
     * от :app (иначе получился бы цикл в графе модулей).
     */
    private fun notificationReceiverClass(): Class<*> = runCatching {
        Class.forName("com.silverchat.app.MainActivity")
    }.getOrElse { Intent::class.java }

    private companion object {
        const val REQUEST_APP = 1000
        const val REQUEST_CHAT = 1001
        const val REQUEST_REPLY = 1002
        const val REQUEST_CALL = 1003
        const val REQUEST_ACCEPT = 1004
        const val REQUEST_DECLINE = 1005
        const val REQUEST_MARKET = 1006
        const val REQUEST_ADMIN = 1007
    }
}

/**
 * Контракт быстрого ответа из уведомления.
 *
 * Вынесен в отдельный объект, потому что его читают два модуля:
 * :core:notifications собирает действие, а :app обрабатывает результат
 * (`ReplyReceiver`). Строковые ключи в двух местах без общего источника —
 * это классический «тихий» баг: ответ просто переставал бы отправляться.
 */
object DirectReplyContract {

    /** Действие broadcast-интента. Совпадает с intent-filter в манифесте :app. */
    const val ACTION = "com.silverchat.action.REPLY"

    /** Extra с идентификатором чата, в который идёт ответ. */
    const val KEY_CHAT_ID = "chat_id"

    /** Ключ `RemoteInput`, под которым система передаёт введённый текст. */
    const val KEY_REMOTE_INPUT = "sc_reply_text"
}
