package com.silverchat.app.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.data.repository.MediaCaptureGateway
import com.silverchat.core.domain.repository.LocalMedia
import com.silverchat.core.domain.repository.MediaSource
import com.silverchat.core.domain.repository.RecordingSession
import com.silverchat.core.media.BuildConfig as MediaBuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Реализация [MediaCaptureGateway] на `ActivityResultContracts`.
 *
 * ── Разделение ответственности ──────────────────────────────────────────
 * Порт объявлен в :core:data, потому что репозиторий не должен знать про
 * `Activity`. Здесь — ровно та часть, которую нельзя сделать без интерфейса:
 * запустить системный выбор, получить URI, разобрать метаданные файла.
 * Сжатие, транскодирование и загрузка остаются в `MediaRepositoryImpl`.
 *
 * ── Почему Photo Picker, а не READ_MEDIA_* ──────────────────────────────
 * `PickVisualMedia` не требует разрешения вообще: система выдаёт разовый
 * грант на выбранные URI. Это меньше трения с пользователем и меньше
 * поверхности для утечки — приложение физически не видит всю галерею.
 * `READ_MEDIA_*` в манифесте оставлены для прямого обхода галереи.
 *
 * ── Съёмка через системную камеру ───────────────────────────────────────
 * Камера в приложении не поднимается: съёмка фото и запись «кружка» уходят в
 * системное приложение камеры через `TakePicture`/`CaptureVideo`. Собственный
 * превью-слой потребовал бы CameraX и отдельного экрана, а системная камера
 * работает на всех устройствах и умеет HDR и стабилизацию, которых у своей
 * реализации не было бы.
 *
 * Следствие для «кружка»: двухфазный контракт порта
 * (`startCircle` → `stopCircle`) отображается на однофазный системный
 * контракт. `startCircle` запускает запись и возвращает сессию сразу, а
 * `stopCircle` дожидается результата — фактически остановку делает
 * пользователь в системной камере. Отмена (`cancelCircle`) гасит ожидание.
 *
 * ── Про URI и время жизни гранта ────────────────────────────────────────
 * Грант на `content://` из Photo Picker живёт, пока жива задача приложения.
 * Поэтому репозиторий обязан передать файл на загрузку сразу после выбора,
 * а не хранить URI между сессиями: после перезапуска процесса чтение
 * упадёт с `SecurityException`.
 */
@Singleton
class ActivityCaptureGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridges: ActivityResultBridgeHolder,
    private val dispatchers: DispatcherProvider,
) : MediaCaptureGateway {

    private val scope = CoroutineScope(SupervisorJob())

    /**
     * Ожидания записи «кружка» по идентификатору сессии.
     *
     * `ConcurrentHashMap`, а не обычный `HashMap`: сессию создаёт UI-поток,
     * а забирает и отменяет — поток репозитория.
     */
    private val pendingCircles = ConcurrentHashMap<String, Deferred<LocalMedia?>>()

    /* ── Выбор изображений ─────────────────────────────────────────────── */

    /**
     * @param maxCount больше 1 включает множественный выбор; Photo Picker
     *   сам ограничивает число выбранных файлов.
     */
    override suspend fun pickImages(
        source: MediaSource,
        maxCount: Int,
    ): List<LocalMedia>? {
        if (source == MediaSource.CAMERA) {
            return capturePhoto()?.let { listOf(it) }
        }
        val uris = bridges.exclusive { bridge ->
            bridge.pickMedia(
                mediaType = BridgeMediaType.IMAGE_ONLY,
                allowMultiple = maxCount > 1,
                maxCount = maxCount.coerceAtLeast(1),
            )
        }.orEmpty()
        // Пустой список после разбора означает, что ни один файл не прочитался:
        // возвращаем null, чтобы репозиторий сообщил «отменено», а не отправил
        // пустой альбом.
        return uris.mapNotNull { probe(it) }.takeIf { it.isNotEmpty() }
    }

    override suspend fun capturePhoto(): LocalMedia? {
        val bridge = bridges.current ?: return null
        if (!bridge.hasCamera) {
            ScLogger.i(LogTag.MEDIA, "Съёмка недоступна: на устройстве нет камеры")
            return null
        }
        if (!ensurePermission(Manifest.permission.CAMERA)) return null

        val target = newCaptureFile("photo", "jpg")
        val uri = target.toProviderUri()
        val captured = bridges.exclusive { it.capturePhoto(uri) } ?: false
        if (!captured) {
            deleteQuietly(target)
            return null
        }
        return probe(uri) ?: run {
            // Файл остался, но не читается: оставлять мусор в кэше нельзя.
            deleteQuietly(target)
            null
        }
    }

    /* ── Видео и файлы ─────────────────────────────────────────────────── */

    override suspend fun pickVideo(source: MediaSource): LocalMedia? {
        if (source == MediaSource.CAMERA) return recordVideo()

        val uri = bridges.exclusive { bridge ->
            bridge.pickMedia(
                mediaType = BridgeMediaType.VIDEO_ONLY,
                allowMultiple = false,
                maxCount = 1,
            )
        }.orEmpty().firstOrNull() ?: return null

        return probe(uri)
    }

    override suspend fun pickFile(): LocalMedia? {
        val uri = bridges.exclusive { bridge ->
            bridge.pickDocument(listOf(ANY_MIME))
        }.orEmpty().firstOrNull() ?: return null

        return probe(uri)
    }

    /** Запись видео системной камерой — используется и для «кружка». */
    private suspend fun recordVideo(): LocalMedia? {
        val bridge = bridges.current ?: return null
        if (!bridge.hasCamera) return null
        if (!ensurePermission(Manifest.permission.CAMERA)) return null

        val target = newCaptureFile("video", "mp4")
        val uri = target.toProviderUri()
        val captured = bridges.exclusive { it.captureVideo(uri) } ?: false
        if (!captured) {
            deleteQuietly(target)
            return null
        }
        return probe(uri) ?: run {
            deleteQuietly(target)
            null
        }
    }

    /* ── «Кружки» (видеосообщения) ─────────────────────────────────────── */

    /**
     * Запуск записи видеосообщения.
     *
     * Возвращает сессию немедленно, как только системная камера запущена:
     * UI должен успеть показать индикатор записи до перехода в другое
     * приложение. Файл забирается в [stopCircle].
     */
    override suspend fun startCircle(): RecordingSession? {
        val bridge = bridges.current ?: return null
        if (!bridge.hasCamera) {
            ScLogger.i(LogTag.MEDIA, "«Кружок» недоступен: на устройстве нет камеры")
            return null
        }
        if (!ensureCapturePermissions()) return null

        val sessionId = UUID.randomUUID().toString()
        val target = newCaptureFile("circle_$sessionId", "mp4")
        val uri = target.toProviderUri()

        val deferred = scope.async {
            val recorded = bridges.exclusive { it.captureVideo(uri) } ?: false
            if (!recorded) {
                deleteQuietly(target)
                return@async null
            }
            probe(uri) ?: run {
                deleteQuietly(target)
                null
            }
        }
        pendingCircles[sessionId] = deferred

        return RecordingSession(
            id = sessionId,
            startedAt = System.currentTimeMillis(),
            // Лимит задаёт :core:media: он же режет файл при транскодировании,
            // поэтому значение обязано быть одним и тем же.
            maxDurationMs = MediaBuildConfig.CIRCLE_MAX_SECONDS * 1000L,
        )
    }

    override suspend fun stopCircle(sessionId: String): LocalMedia? =
        pendingCircles.remove(sessionId)?.await()

    override fun cancelCircle(sessionId: String) {
        // Отменяем ожидание; сам файл удалит системная камера либо очистка кэша.
        pendingCircles.remove(sessionId)?.cancel()
    }

    /* ── Разрешения ────────────────────────────────────────────────────── */

    /**
     * Камера и микрофон для видеосообщений.
     *
     * Проверяем сначала локально: если разрешения уже выданы, диалог не
     * показывается вовсе — иначе каждый «кружок» начинался бы с системного
     * окна, даже когда пользователь всё разрешил месяц назад.
     */
    override suspend fun ensureCapturePermissions(): Boolean {
        val required = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filterNot { isGranted(it) }
        if (required.isEmpty()) return true
        return bridges.exclusive { it.requestPermissions(required) } ?: false
    }

    private suspend fun ensurePermission(permission: String): Boolean {
        if (isGranted(permission)) return true
        return bridges.exclusive { it.requestPermissions(listOf(permission)) } ?: false
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /* ── Разбор файла ──────────────────────────────────────────────────── */

    /**
     * Превращает URI в [LocalMedia]: MIME, размер, длительность и габариты.
     *
     * Всё это нужно ДО загрузки:
     *  • MIME — сервер кладёт файл в object storage под конкретный тип;
     *  • размер — чтобы отказать в отправке файла больше лимита, не потратив
     *    трафик пользователя на загрузку;
     *  • габариты и длительность — для выбора качества транскодирования и
     *    для правильного aspect-ratio пузырька в ленте.
     *
     * Работает в IO-диспетчере: `MediaMetadataRetriever` извлекает кадр,
     * а это десятки миллисекунд на больших видео.
     */
    private suspend fun probe(uri: Uri): LocalMedia? = withContext(dispatchers.io) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: guessMimeFromPath(uri.toString())
            val sizeBytes = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            val dimensions = probeDimensions(uri, mimeType)
            LocalMedia(
                uri = uri.toString(),
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                durationMs = dimensions.durationMs,
                width = dimensions.width,
                height = dimensions.height,
            )
        }.getOrElse { error ->
            ScLogger.w(LogTag.MEDIA, "Не удалось разобрать медиа ${uri.lastPathSegment}", error)
            null
        }
    }

    private fun probeDimensions(uri: Uri, mimeType: String): Dimensions = when {
        mimeType.startsWith("image/") -> probeImageBounds(uri)
        mimeType.startsWith("video/") || mimeType.startsWith("audio/") -> probeAvMetadata(uri)
        else -> Dimensions()
    }

    /**
     * Габариты картинки без декодирования пикселей.
     *
     * `inJustDecodeBounds` читает только заголовок: полный `decodeStream`
     * на фото 48 Мп выделил бы сотни мегабайт и уронил бы процесс с OOM
     * ещё до начала загрузки.
     */
    private fun probeImageBounds(uri: Uri): Dimensions {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }
        // -1 означает «формат не распознан» — в модель идёт 0.
        return Dimensions(
            durationMs = 0L,
            width = options.outWidth.coerceAtLeast(0),
            height = options.outHeight.coerceAtLeast(0),
        )
    }

    private fun probeAvMetadata(uri: Uri): Dimensions {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Dimensions(
                durationMs = retriever.intMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION,
                ).toLong(),
                width = retriever.intMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
                ),
                height = retriever.intMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
                ),
            )
        } catch (error: RuntimeException) {
            // Повреждённый или неподдерживаемый контейнер: медиа всё равно
            // можно отправить как файл, поэтому не роняем выбор целиком.
            ScLogger.w(LogTag.MEDIA, "Метаданные недоступны, отправим без них", error)
            Dimensions()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.intMetadata(key: Int): Int =
        extractMetadata(key)?.toIntOrNull() ?: 0

    private fun guessMimeFromPath(path: String): String =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path))
            ?: "application/octet-stream"

    /* ── Файлы в кэше ──────────────────────────────────────────────────── */

    /**
     * Файл для съёмки в `cacheDir/captures`.
     *
     * Именно кэш, а не `filesDir` или внешнее хранилище: снятое сразу уходит
     * на сервер и удаляется, а кэш система может очистить сама при нехватке
     * места. Каталог совпадает с `<cache-path name="captures">` в
     * `res/xml/file_paths.xml` — без этого FileProvider откажется выдать URI.
     */
    private fun newCaptureFile(prefix: String, extension: String): File {
        val dir = File(context.cacheDir, CAPTURE_DIR_NAME).apply { mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}.$extension")
    }

    /**
     * `content://` URI для передачи в системную камеру.
     *
     * `file://` с API 24 бросает `FileUriExposedException` — StrictMode
     * запрещает передавать путь к приватному файлу наружу.
     */
    private fun File.toProviderUri(): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.$FILE_PROVIDER_SUFFIX", this)

    private fun deleteQuietly(file: File) {
        runCatching { if (file.exists()) file.delete() }
    }

    private data class Dimensions(
        val durationMs: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
    )

    private companion object {
        const val CAPTURE_DIR_NAME = "captures"
        const val FILE_PROVIDER_SUFFIX = "fileprovider"
        const val ANY_MIME = "*/*"
    }
}
