package com.silverchat.core.data.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.LocalMedia
import com.silverchat.core.domain.repository.MediaSource
import com.silverchat.core.domain.repository.RecordingSession
import java.util.UUID

/**
 * Шлюз захвата медиа.
 *
 * ── Почему он вообще нужен ──────────────────────────────────────────────
 * Выбор файла из галереи и съёмка «кружка» в Android возможны только через
 * `ActivityResultContracts` или Surface от CameraX — и то, и другое требует
 * живую `Activity`/Compose-иерархию. Репозиторий живёт в `@Singleton` и не
 * имеет к ним доступа, а тянуть `Activity` в слой данных означало бы сломать
 * тестируемость и получить утечку.
 *
 * Поэтому слой данных объявляет контракт, а реализацию предоставляет `:app`
 * (`PickVisualMedia` + CameraX). В unit-тестах реализация подменяется фейком, и
 * `MediaRepositoryImpl` проверяется без Robolectric.
 *
 * Все методы — `suspend` и возвращают `null` при отмене: отмена выбора
 * файла это нормальный сценарий, а не ошибка, и показывать снакбар
 * «Произошла ошибка» после того, как пользователь нажал «Назад», нельзя.
 */
interface MediaCaptureGateway {

    /** Выбор изображений. `maxCount > 1` включает множественный выбор. */
    suspend fun pickImages(source: MediaSource, maxCount: Int): List<LocalMedia>?

    /** Съёмка фото камерой. Результат — уже сохранённый в кэш файл. */
    suspend fun capturePhoto(): LocalMedia?

    /** Выбор видео из галереи. */
    suspend fun pickVideo(source: MediaSource): LocalMedia?

    /** Выбор произвольного файла (SAF). */
    suspend fun pickFile(): LocalMedia?

    /**
     * Запись «кружка» (видеосообщения).
     *
     * Возвращает сессию сразу, как только камера готова; сам файл забирается
     * в [stopCircle]. Если устройство не поддерживает запись, возвращает null.
     */
    suspend fun startCircle(): RecordingSession?

    /** Остановить запись «кружка» и вернуть файл. Null — если запись не шла. */
    suspend fun stopCircle(sessionId: String): LocalMedia?

    /** Прервать запись без сохранения файла. */
    suspend fun cancelCircle(sessionId: String)

    /** Запросить разрешения на камеру/микрофон. False — пользователь отказал. */
    suspend fun ensureCapturePermissions(): Boolean
}

/** Заглушка для превью и unit-тестов: ничего не умеет, но и не падает. */
object NoOpMediaCaptureGateway : MediaCaptureGateway {
    override suspend fun pickImages(source: MediaSource, maxCount: Int): List<LocalMedia>? = null
    override suspend fun capturePhoto(): LocalMedia? = null
    override suspend fun pickVideo(source: MediaSource): LocalMedia? = null
    override suspend fun pickFile(): LocalMedia? = null
    override suspend fun startCircle(): RecordingSession? = null
    override suspend fun stopCircle(sessionId: String): LocalMedia? = null
    override suspend fun cancelCircle(sessionId: String) = Unit
    override suspend fun ensureCapturePermissions(): Boolean = false
}

/** Генерация идентификатора сессии записи — общий для голосовых и «кружков». */
internal fun newRecordingSessionId(): String = UUID.randomUUID().toString()

/** Маркер результата, который шлюз возвращает при успешном захвате. */
internal typealias CaptureResult = ScResult<LocalMedia>
