package com.silverchat.core.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Регистрация каналов при старте приложения.
 *
 * Вызывается из [com.silverchat.app.SilverChatApplication]. Идемпотентно:
 * повторная регистрация с теми же параметрами ничего не меняет, а вот
 * изменить важность существующего канала программно НЕЛЬЗЯ — только через
 * удаление и пересоздание (поэтому id каналов никогда не переиспользуем).
 */
@Singleton
class NotificationChannelSetup @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun createChannels() {
        val manager = NotificationManagerCompat.from(context)

        // Группы нужны только для порядка в системных настройках
        manager.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_MESSAGES, "Сообщения и звонки"),
        )
        manager.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_SERVICE, "Служебные"),
        )

        NotificationChannel.entries.forEach { channel ->
            val androidChannel = NotificationChannel(
                channel.id,
                channel.nameRu,
                channel.importance,
            ).apply {
                description = channel.descriptionRu
                group = when (channel) {
                    NotificationChannel.UPLOADS,
                    NotificationChannel.ADMIN,
                    NotificationChannel.PREMIUM,
                    -> GROUP_SERVICE

                    else -> GROUP_MESSAGES
                }
                enableVibration(channel.vibrate)
                enableLights(channel.lightsEnabled)
                setShowBadge(channel != NotificationChannel.UPLOADS)

                if (channel.sound) {
                    setSound(defaultSoundUri(), audioAttributes())
                } else {
                    setSound(null, null)
                }

                if (channel.vibrate) {
                    vibrationPattern = DEFAULT_VIBRATION
                }

                // Для звонков разрешаем полноэкранный показ поверх локскрина
                if (channel == NotificationChannel.CALLS) {
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setBypassDnd(false)
                }
            }
            manager.createNotificationChannel(androidChannel)
        }

        ScLogger.i(LogTag.UI, "Каналы уведомлений зарегистрированы: ${NotificationChannel.entries.size}")
    }

    private fun defaultSoundUri(): Uri =
        Uri.parse("android.resource://${context.packageName}/${androidx.core.R.raw.notification_default_sound}")

    private fun audioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private companion object {
        const val GROUP_MESSAGES = "sc_group_messages"
        const val GROUP_SERVICE = "sc_group_service"
        val DEFAULT_VIBRATION = longArrayOf(0L, 250L, 150L, 250L)
    }
}
