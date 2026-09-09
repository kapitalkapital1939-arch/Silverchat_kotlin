package com.silverchat.app.notification

import android.content.Context
import com.silverchat.app.R
import com.silverchat.app.di.ApplicationScope
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.domain.repository.AdminRepository
import com.silverchat.core.notifications.NotificationFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Показывает админ-рассылки как уведомления.
 *
 * ── Почему не внутри фичи ───────────────────────────────────────────────
 * Рассылка адресована всем пользователям и не привязана ни к чату, ни к
 * экрану: у неё нет естественного места в UI. При этом увидеть её должен и
 * тот, кто сейчас не в приложении. Единственный корректный канал — системное
 * уведомление, а его показывает оболочка.
 *
 * ── Почему поток, а не разовая подписка ─────────────────────────────────
 * Рассылки приходят событием `admin.broadcast` по общему WebSocket-каналу.
 * Подписка живёт столько же, сколько процесс: уведомление должно появиться
 * и тогда, когда приложение в фоне, а не только при открытом экране.
 *
 * ── Кто НЕ получает рассылку ────────────────────────────────────────────
 * Сам отправитель. Сервер не возвращает событие тому, кто его инициировал,
 * иначе админ получил бы уведомление о собственном действии.
 */
@Singleton
class AdminBroadcastNotifier @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val adminRepository: AdminRepository,
    private val notifications: NotificationFactory,
    @ApplicationContext private val context: Context,
) {

    fun start() {
        adminRepository.observeAdminBroadcasts()
            .onEach { text -> publish(text) }
            .catch { error ->
                // Обрыв потока не должен ронять процесс: рассылки — не
                // критичный канал, а лог позволит найти причину.
                ScLogger.e(LogTag.ADMIN, "Поток админ-рассылок прерван", error)
            }
            .launchIn(scope)
    }

    private fun publish(text: String) {
        val message = text.trim()
        if (message.isEmpty()) return

        val title = context.getString(R.string.admin_broadcast_title)
        /* Идентификатор — хеш текста, а не константа: две разные рассылки
         * должны остаться двумя уведомлениями, а повторная доставка той же
         * самой (ретрай сокета) — обновить существующее, а не удвоить его. */
        notifications.notify(message.hashCode(), notifications.buildAdminNotification(title, message))
    }
}
