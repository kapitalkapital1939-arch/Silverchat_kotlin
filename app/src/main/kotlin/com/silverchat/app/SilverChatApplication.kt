package com.silverchat.app

import android.app.Application
import android.os.StrictMode
import com.silverchat.app.notification.AdminBroadcastNotifier
import com.silverchat.app.session.SessionLifecycle
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.notifications.NotificationChannelSetup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

/**
 * Точка входа приложения.
 *
 * Здесь происходит ровно четыре вещи, и все они обязаны случиться до первого
 * экрана:
 *
 *  1. диагностика (`StrictMode`) — только в debug, и только до создания
 *     компонентов, иначе первые нарушения останутся незамеченными;
 *  2. логирование — дерево Timber должно быть посажено до первого лога;
 *  3. каналы уведомлений — на Android 8+ уведомление без созданного канала
 *     молча не показывается, и входящий звонок был бы пропущен;
 *  4. фоновые задачи процесса: жизненный цикл WebSocket-сессии и показ
 *     админ-рассылок.
 *
 * ── Чего здесь НЕТ ──────────────────────────────────────────────────────
 * Никакой бизнес-логики и никакой инициализации «на всякий случай». В частности,
 * здесь не создаётся кастомный `ImageLoader` для Coil: изображения грузятся
 * по публичным и подписанным URL, заголовок авторизации им не нужен, поэтому
 * достаточно стандартного загрузчика. Общий с REST `OkHttpClient` дал бы
 * выигрыш только на переиспользовании пула соединений — это осознанно
 * отложено, а не забыто (см. ARCHITECTURE.md, раздел «Изображения»).
 *
 * ── Порядок `super.onCreate()` ──────────────────────────────────────────
 * Hilt инжектирует поля в `super.onCreate()`, поэтому всё, что использует
 * [notificationChannels], [sessionLifecycle] и [adminBroadcastNotifier],
 * обязано идти строго после него.
 */
@HiltAndroidApp
class SilverChatApplication : Application() {

    @Inject
    lateinit var notificationChannels: NotificationChannelSetup

    @Inject
    lateinit var sessionLifecycle: SessionLifecycle

    @Inject
    lateinit var adminBroadcastNotifier: AdminBroadcastNotifier

    override fun onCreate() {
        /* StrictMode до super: Hilt в super.onCreate() уже читает
         * EncryptedSharedPreferences, и это дисковая операция, которую
         * полезно увидеть в отчёте, а не пропустить. */
        if (BuildConfig.STRICT_MODE) {
            enableStrictMode()
        }

        super.onCreate()

        plantLogging()
        notificationChannels.createChannels()

        // Подключение WebSocket при входе и отключение при выходе
        sessionLifecycle.start()

        // Рассылки администрации показываются уведомлением
        adminBroadcastNotifier.start()

        ScLogger.i(
            LogTag.UI,
            "SilverChat ${BuildConfig.APP_VERSION_NAME} " +
                "(сборка ${BuildConfig.APP_VERSION_CODE}, ${BuildConfig.BUILD_TYPE})",
        )
    }

    /**
     * Логирование.
     *
     * В release деревьев нет намеренно, а не «забыли»: [ScLogger] маскирует
     * телефоны и токены, но тексты сообщений он пишет как есть, и оставлять
     * переписку в logcat на устройстве пользователя нельзя. Нужные для
     * поддержки данные собираются отдельным выгружаемым буфером.
     */
    private fun plantLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    /**
     * Диагностика в debug-сборке.
     *
     * `penaltyLog`, а не `penaltyDeath`: падение на первом же чтении диска в
     * главном потоке сделало бы отладку невозможной, а цель здесь — собрать
     * список нарушений, а не остановить разработку. Чтение диска на старте
     * легально (токены из EncryptedSharedPreferences), поэтому нарушения
     * разбираются по факту, а не автоматически.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }
}
