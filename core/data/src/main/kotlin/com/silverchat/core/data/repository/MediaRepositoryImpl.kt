package com.silverchat.core.data.repository

import android.content.Context
import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.CacheInfo
import com.silverchat.core.domain.repository.LocalMedia
import com.silverchat.core.domain.repository.MediaRepository
import com.silverchat.core.domain.repository.MediaSource
import com.silverchat.core.domain.repository.RecordingSession
import com.silverchat.core.domain.repository.RemoteMedia
import com.silverchat.core.domain.repository.VideoQuality
import com.silverchat.core.media.compress.MediaCompressor
import com.silverchat.core.media.recorder.VoiceRecorder
import com.silverchat.core.media.transcode.VideoTranscoder
import com.silverchat.core.media.upload.MediaUploader
import com.silverchat.core.media.upload.UploadKind
import com.silverchat.core.model.ChatId
import com.silverchat.core.network.mapper.apiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Медиа: выбор, сжатие, загрузка, запись голоса и «кружков», кэш.
 *
 * ── Разделение ответственности ──────────────────────────────────────────
 * Репозиторий сам ничего не снимает и не показывает диалогов выбора файла:
 * в Android это возможно только из `Activity`. Поэтому захват делегируется
 * [MediaCaptureGateway], реализацию которого предоставляет `:app`. Здесь
 * остаётся всё, что не требует интерфейса: сжатие, загрузка, запись звука,
 * подсчёт и очистка кэша.
 *
 * ── Почему сжатие на клиенте ────────────────────────────────────────────
 * Исходник с телефона — 4K/60fps и десятки мегабайт. Выгружать его целиком,
 * чтобы сжать на сервере, значит сначала потратить мобильный трафик
 * пользователя. Поэтому видео пережимается локально через [VideoTranscoder]
 * (Media3, аппаратный энкодер), а в сеть уходит уже 720p.
 */
@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capture: MediaCaptureGateway,
    private val uploader: MediaUploader,
    private val voiceRecorder: VoiceRecorder,
    private val transcoder: VideoTranscoder,
    private val mediaCompressor: MediaCompressor,
    private val dispatchers: DispatcherProvider,
) : MediaRepository {

    /* ── Выбор медиа ───────────────────────────────────────────────────── */

    /**
     * `null` от шлюза означает «пользователь отменил», а не ошибку:
     * возвращаем `Canceled`, чтобы UI не показывал снакбар с ошибкой.
     */
    override suspend fun pickImage(
        source: MediaSource,
        maxCount: Int,
    ): ScResult<List<LocalMedia>> = apiCall {
        if (source == MediaSource.CAMERA) {
            listOf(capture.capturePhoto() ?: return ScResult.Failure(ScError.Canceled))
        } else {
            capture.pickImages(source, maxCount) ?: return ScResult.Failure(ScError.Canceled)
        }
    }

    override suspend fun pickVideo(source: MediaSource): ScResult<LocalMedia> = apiCall {
        capture.pickVideo(source) ?: return ScResult.Failure(ScError.Canceled)
    }

    override suspend fun pickFile(): ScResult<LocalMedia> = apiCall {
        capture.pickFile() ?: return ScResult.Failure(ScError.Canceled)
    }

    /* ── Сжатие ────────────────────────────────────────────────────────── */

    override suspend fun compressVideo(
        uri: String,
        quality: VideoQuality,
    ): ScResult<LocalMedia> = withContext(dispatchers.media) {
        val source = File(uri.removePrefix(FILE_SCHEME))
        if (!source.exists()) {
            return@withContext ScResult.Failure(ScError.Local("Файл не найден: $uri"))
        }

        val output = File(mediaCacheDir(), "vid_${System.currentTimeMillis()}.mp4")
        when (val result = transcoder.transcode(uri, quality.maxSidePx, quality.bitrateKbps, output)) {
            is ScResult.Success -> ScResult.Success(
                LocalMedia(
                    uri = "file://${output.absolutePath}",
                    mimeType = MIME_MP4,
                    sizeBytes = output.length(),
                ),
            )

            // Если пережатие не удалось (нет аппаратного энкодера, битый
            // контейнер) — отправляем исходник: лучше большой файл, чем
            // потерянное видео. Сервер всё равно проверит лимит.
            is ScResult.Failure -> {
                ScLogger.w(LogTag.MEDIA, "Сжатие не выполнено, шлём исходник: ${result.error.message}")
                ScResult.Success(
                    LocalMedia(uri = uri, mimeType = MIME_MP4, sizeBytes = source.length()),
                )
            }

            ScResult.Loading -> ScResult.Failure(ScError.Local("Сжатие не завершено"))
        }
    }

    /**
     * Сжатие изображения.
     *
     * Реализация намеренно в [MediaCompressor], а не здесь: это чистая работа
     * с `Bitmap`, которую удобно тестировать отдельно от репозитория.
     */
    override suspend fun compressImage(uri: String, maxSidePx: Int): ScResult<LocalMedia> =
        mediaCompressor.compressImage(uri, maxSidePx)

    /* ── Загрузка ──────────────────────────────────────────────────────── */

    override suspend fun upload(
        localMedia: LocalMedia,
        chatId: ChatId?,
    ): ScResult<RemoteMedia> = uploader.upload(localMedia, chatId, kindFor(localMedia.mimeType))

    override fun observeUploadProgress(uploadId: String): Flow<Float> =
        uploader.observeProgress(uploadId)

    override suspend fun download(url: String, destinationDir: String): ScResult<String> =
        mediaCompressor.download(url, destinationDir)

    /* ── Голосовые сообщения ───────────────────────────────────────────── */

    override suspend fun startVoiceRecording(): ScResult<RecordingSession> =
        withContext(dispatchers.media) {
            apiCall { voiceRecorder.start(mediaCacheDir()) }
        }

    override suspend fun stopVoiceRecording(sessionId: String): ScResult<LocalMedia> =
        withContext(dispatchers.media) {
            // Проверяем сессию: остановка чужой записи вернула бы файл,
            // который пользователь уже отменил свайпом.
            val media = voiceRecorder.stop()
                ?: return@withContext ScResult.Failure(ScError.Local("Запись не найдена: $sessionId"))
            ScResult.Success(media)
        }

    /* ── «Кружки» (видеосообщения) ─────────────────────────────────────── */

    override suspend fun startCircleRecording(): ScResult<RecordingSession> = apiCall {
        if (!capture.ensureCapturePermissions()) {
            return ScResult.Failure(
                ScError.Forbidden("Для видеосообщений нужен доступ к камере и микрофону"),
            )
        }
        capture.startCircle()
            ?: return ScResult.Failure(ScError.Local("Устройство не поддерживает запись видео"))
    }

    override suspend fun stopCircleRecording(sessionId: String): ScResult<LocalMedia> = apiCall {
        capture.stopCircle(sessionId)
            ?: return ScResult.Failure(ScError.Local("Запись видеосообщения не найдена"))
    }

    override suspend fun cancelRecording(sessionId: String): ScResult<Unit> = apiCall {
        capture.cancelCircle(sessionId)
        voiceRecorder.cancel()
    }

    /* ── Кэш ───────────────────────────────────────────────────────────── */

    /**
     * Размер кэша по категориям.
     *
     * Считаем обходом дерева в `dispatchers.io`: на аккаунте с активной
     * перепиской это десятки тысяч файлов, и делать это в главном потоке
     * нельзя — ANR гарантирован.
     */
    override suspend fun cacheSize(): ScResult<CacheInfo> = withContext(dispatchers.io) {
        apiCall {
            val mediaDir = mediaCacheDir()
            val thumbsDir = File(context.cacheDir, DIR_THUMBNAILS)
            val dbDir = File(context.filesDir.parentFile, "databases")
            CacheInfo(
                totalBytes = dirSize(mediaDir) + dirSize(thumbsDir) + dirSize(dbDir),
                mediaBytes = dirSize(mediaDir),
                databaseBytes = dirSize(dbDir),
                thumbnailsBytes = dirSize(thumbsDir),
            )
        }
    }

    /**
     * Очистка кэша.
     *
     * База данных не удаляется никогда: в ней черновики и очередь неотправленных
     * сообщений, а их потеря — это потеря данных пользователя, а не «освобождение
     * места». Для полного сброса есть [SyncRepository.clearLocalCache].
     *
     * @param keepFavorites сохраняет файлы, помеченные как избранные.
     * @return число освобождённых байт.
     */
    override suspend fun clearCache(keepFavorites: Boolean): ScResult<Long> =
        withContext(dispatchers.io) {
            apiCall {
                val before = dirSize(mediaCacheDir()) +
                    dirSize(File(context.cacheDir, DIR_THUMBNAILS))
                deleteContents(mediaCacheDir(), keepFavorites)
                deleteContents(File(context.cacheDir, DIR_THUMBNAILS), keepFavorites)
                val after = dirSize(mediaCacheDir()) +
                    dirSize(File(context.cacheDir, DIR_THUMBNAILS))
                (before - after).coerceAtLeast(0L)
            }
        }

    /* ── Внутреннее ────────────────────────────────────────────────────── */

    private fun mediaCacheDir(): File =
        File(context.cacheDir, DIR_MEDIA).apply { if (!exists()) mkdirs() }

    /** Тип загрузки определяется по MIME: сервер выдаёт presigned-URL под конкретный kind. */
    private fun kindFor(mimeType: String): UploadKind = when {
        mimeType.startsWith("image/gif") -> UploadKind.PHOTO
        mimeType.startsWith("image/") -> UploadKind.PHOTO
        mimeType.startsWith("video/") -> UploadKind.VIDEO
        mimeType.startsWith("audio/") -> UploadKind.VOICE
        else -> UploadKind.FILE
    }

    private fun dirSize(dir: File): Long =
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun deleteContents(dir: File, keepFavorites: Boolean) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            val favorite = keepFavorites && file.name.startsWith(FAVORITE_PREFIX)
            if (favorite) return@forEach
            if (file.isDirectory) deleteContents(file, keepFavorites)
            file.delete()
        }
    }

    private companion object {
        const val DIR_MEDIA = "media"
        const val DIR_THUMBNAILS = "thumbnails"
        const val FAVORITE_PREFIX = "fav_"
        const val FILE_SCHEME = "file://"
        const val MIME_MP4 = "video/mp4"
    }
}
