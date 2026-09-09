package com.silverchat.app.media

import android.net.Uri

/**
 * Мост между синглтонами слоя данных и живой `Activity`.
 *
 * ── Зачем он нужен ──────────────────────────────────────────────────────
 * Выбор файла из Photo Picker, съёмка камерой и запрос runtime-разрешений в
 * Android возможны ТОЛЬКО через `ActivityResultContracts`, зарегистрированные
 * в `Activity` до её перехода в состояние `STARTED`. При этом
 * `MediaRepositoryImpl` — `@Singleton`: он переживает повороты экрана и
 * смену `Activity`, и ссылаться на неё напрямую не может (утечка + падение
 * после пересоздания).
 *
 * Поэтому [com.silverchat.core.data.repository.MediaCaptureGateway] объявлен
 * как порт в слое данных, а здесь — его Activity-половина. `MainActivity`
 * реализует интерфейс, регистрирует лаунчеры и публикует себя в
 * [ActivityResultBridgeHolder]; синглтон дёргает их через держатель.
 *
 * ── Почему suspend, а не колбэки ────────────────────────────────────────
 * Порт в слое данных suspend-ный: репозиторий ждёт результат и возвращает
 * `ScResult`. Колбэк пришлось бы оборачивать в `CompletableDeferred` на
 * стороне репозитория, то есть знание про асинхронность Activity протекло бы
 * в слой данных. Здесь оно остаётся в :app.
 *
 * ── Про отмену ──────────────────────────────────────────────────────────
 * Пустой список / `false` означают «пользователь отменил», а не ошибку:
 * шлюз превращает это в `null`, а репозиторий — в `ScError.Canceled`,
 * который UI не показывает как сбой.
 */
interface ActivityResultBridge {

    /** Есть ли на устройстве камера. Без неё съёмка невозможна, но приложение работает. */
    val hasCamera: Boolean

    /**
     * Выбор медиа из системного Photo Picker.
     *
     * Photo Picker не требует разрешения `READ_MEDIA_*`: система выдаёт
     * разовый грант на выбранные URI. Возвращает пустой список при отмене.
     */
    suspend fun pickMedia(
        mediaType: BridgeMediaType,
        allowMultiple: Boolean,
        maxCount: Int,
    ): List<Uri>

    /** Выбор произвольного файла через SAF. Пустой список — отмена. */
    suspend fun pickDocument(mimeTypes: List<String>): List<Uri>

    /** Съёмка фото системным приложением камеры в [target]. `false` — отмена. */
    suspend fun capturePhoto(target: Uri): Boolean

    /** Запись видео системным приложением камеры в [target]. `false` — отмена. */
    suspend fun captureVideo(target: Uri): Boolean

    /** Запрос runtime-разрешений. `true` — выданы все из списка. */
    suspend fun requestPermissions(permissions: List<String>): Boolean
}

/**
 * Что разрешено выбрать.
 *
 * Отдельный тип вместо строки MIME потому, что `PickVisualMedia` принимает
 * именно `PickVisualMediaRequest.MediaType`, а не произвольный MIME-список:
 * «только видео» и «только фото» — разные константы системного контракта.
 */
enum class BridgeMediaType {
    IMAGE_ONLY,
    VIDEO_ONLY,
    IMAGE_AND_VIDEO,
}
