package com.silverchat.app.media

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Держатель текущего [ActivityResultBridge].
 *
 * ── Один на процесс ─────────────────────────────────────────────────────
 * `ActivityCaptureGateway` и `ActivityProfileMediaPicker` — синглтоны, а
 * `MainActivity` пересоздаётся при каждом повороте экрана и смене темы.
 * Держатель разводит эти жизненные циклы: Activity публикует себя в
 * `onCreate`/`onStart` и отписывается в `onDestroy`, а потребители просто
 * читают `current` и получают `null`, если Activity сейчас нет.
 *
 * `null` — нормальное состояние, а не ошибка: например, входящее сообщение
 * может потребовать открыть камеру в момент, когда приложение в фоне.
 * Вызов в этот момент корректно завершится отменой, а не падением.
 *
 * ── Зачем [exclusive] ───────────────────────────────────────────────────
 * Два одновременных `startActivityForResult` в одну Activity — гонка:
 * система доставит результат только последнему лаунчеру, а первый останется
 * висеть в `suspendCancellableCoroutine` навсегда. Мьютекс сериализует
 * обращения, поэтому «кружок» не может стартовать поверх выбора фото.
 */
@Singleton
class ActivityResultBridgeHolder @Inject constructor() {

    private val bridge = MutableStateFlow<ActivityResultBridge?>(null)
    private val mutex = Mutex()

    /** Activity публикует себя. Вызывается из `onStart`. */
    fun attach(value: ActivityResultBridge) {
        bridge.value = value
    }

    /**
     * Activity отписывается.
     *
     * `compareAndSet`, а не присваивание `null`: к моменту `onDestroy`
     * старой Activity новая могла уже зарегистрироваться, и безусловная
     * запись затёрла бы живую ссылку.
     */
    fun detach(value: ActivityResultBridge) {
        bridge.compareAndSet(value, null)
    }

    val current: ActivityResultBridge?
        get() = bridge.value

    /**
     * Выполняет [block] с живой Activity, удерживая блокировку.
     *
     * `null` — Activity недоступна. Проверка повторяется ПОСЛЕ захвата
     * блокировки: пока ждали, Activity могла быть уничтожена, и лаунчеры
     * мёртвой Activity результата не вернут.
     */
    suspend fun <T> exclusive(block: suspend (ActivityResultBridge) -> T): T? {
        if (bridge.value == null) return null
        return mutex.withLock {
            val live = bridge.value ?: return@withLock null
            block(live)
        }
    }
}
