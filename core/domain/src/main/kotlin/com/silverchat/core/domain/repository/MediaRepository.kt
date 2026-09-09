package com.silverchat.core.domain.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.model.ChatId
import kotlinx.coroutines.flow.Flow

/** Медиа: выбор, сжатие, загрузка, скачивание, запись голоса и «кружков». */
interface MediaRepository {

    suspend fun pickImage(source: MediaSource, maxCount: Int = 10): ScResult<List<LocalMedia>>

    suspend fun pickVideo(source: MediaSource): ScResult<LocalMedia>

    suspend fun pickFile(): ScResult<LocalMedia>

    /** Сжатие перед отправкой: экономит трафик и место на CDN. */
    suspend fun compressVideo(uri: String, quality: VideoQuality): ScResult<LocalMedia>

    suspend fun compressImage(uri: String, maxSidePx: Int = 2560): ScResult<LocalMedia>

    suspend fun upload(localMedia: LocalMedia, chatId: ChatId?): ScResult<RemoteMedia>

    /** Прогресс загрузки 0f..1f — рисуется поверх превью в пузыре сообщения. */
    fun observeUploadProgress(uploadId: String): Flow<Float>

    suspend fun download(url: String, destinationDir: String): ScResult<String>

    /** Запись голосового сообщения (Opus, с извлечением waveform). */
    suspend fun startVoiceRecording(): ScResult<RecordingSession>

    suspend fun stopVoiceRecording(sessionId: String): ScResult<LocalMedia>

    /** Запись видеосообщения «кружок» (квадрат, до 60 секунд). */
    suspend fun startCircleRecording(): ScResult<RecordingSession>

    suspend fun stopCircleRecording(sessionId: String): ScResult<LocalMedia>

    suspend fun cancelRecording(sessionId: String): ScResult<Unit>

    /* ── Кэш ────────────────────────────────────────────────────────────── */

    suspend fun cacheSize(): ScResult<CacheInfo>

    suspend fun clearCache(keepFavorites: Boolean = true): ScResult<Long>
}

enum class MediaSource { GALLERY, CAMERA, FILE, CONTACT, LOCATION }

enum class VideoQuality(val maxSidePx: Int, val bitrateKbps: Int) {
    LOW(480, 800),
    MEDIUM(720, 1_800),
    HIGH(1080, 3_500),
    MAX(1440, 6_000),
}

data class LocalMedia(
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    /** Нормированные амплитуды 0..1 для отрисовки дорожки голосового. */
    val waveform: List<Float> = emptyList(),
)

data class RemoteMedia(
    val url: String,
    val thumbUrl: String? = null,
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val waveform: List<Float> = emptyList(),
    val blurHash: String? = null,
)

data class RecordingSession(
    val id: String,
    val startedAt: Long,
    val maxDurationMs: Long,
)

data class CacheInfo(
    val totalBytes: Long,
    val mediaBytes: Long,
    val databaseBytes: Long,
    val thumbnailsBytes: Long,
)
