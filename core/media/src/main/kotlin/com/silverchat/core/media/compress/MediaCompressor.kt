package com.silverchat.core.media.compress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.common.result.scResult
import com.silverchat.core.domain.repository.LocalMedia
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Сжатие изображений и скачивание файлов.
 *
 * ── Почему фото сжимаются на клиенте ────────────────────────────────────
 * Камера телефона отдаёт JPEG на 8–12 Мп весом 4–8 МБ. В чате такое
 * разрешение не нужно: оно съедает трафик получателя и место в кэше.
 * Поэтому фото уменьшается до [DEFAULT_MAX_SIDE] px и перекодируется
 * с качеством [JPEG_QUALITY] — визуально в ленте разница неотличима,
 * а вес падает в 5–10 раз.
 *
 * ── EXIF-ориентация ─────────────────────────────────────────────────────
 * `BitmapFactory` декодирует пиксели, но игнорирует тег ориентации: снимок,
 * сделанный вертикально, после перекодирования лёг бы на бок. Поэтому
 * ориентация читается отдельно и применяется поворотом матрицы. Без этого
 * каждое второе фото в чате было бы повёрнуто — классическая ошибка
 * самописных компрессоров.
 */
@Singleton
class MediaCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) {

    /**
     * Уменьшает изображение до [maxSidePx] по длинной стороне.
     *
     * Декодирование идёт в два прохода: сначала `inJustDecodeBounds` снимает
     * размеры без выделения памяти, затем вычисляется `inSampleSize`. Это
     * защита от `OutOfMemoryError` на снимках 50+ Мп — частая причина
     * падения приложений-мессенджеров при отправке фото.
     */
    suspend fun compressImage(uri: String, maxSidePx: Int): ScResult<LocalMedia> =
        withContext(dispatchers.media) {
            scResult {
                val source = resolveFile(uri)
                    ?: return@scResult fail("Файл не найден: $uri")

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return@scResult fail("Не удалось прочитать изображение: $uri")
                }

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSidePx)
                }
                val decoded = BitmapFactory.decodeFile(source.absolutePath, options)
                    ?: return@scResult fail("Не удалось декодировать изображение")

                val corrected = applyExifOrientation(source, decoded)
                val output = File(cacheDir(), "img_${System.currentTimeMillis()}.jpg")

                FileOutputStream(output).use { stream ->
                    corrected.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                }

                if (corrected !== decoded) decoded.recycle()
                corrected.recycle()

                ScLogger.d(
                    LogTag.MEDIA,
                    "Фото сжато: ${bounds.outWidth}x${bounds.outHeight} -> " +
                        "${source.length()} Б -> ${output.length()} Б",
                )

                LocalMedia(
                    uri = "file://${output.absolutePath}",
                    mimeType = MIME_JPEG,
                    sizeBytes = output.length(),
                    width = corrected.width,
                    height = corrected.height,
                )
            }
        }

    /**
     * Скачивает файл в [destinationDir] и возвращает локальный путь.
     *
     * Отдельный `OkHttpClient` не создаётся: общий клиент уже настроен с
     * TLS, таймаутами и пулом соединений, а повторный клиент означал бы
     * второй пул сокетов и второй набор потоков.
     */
    suspend fun download(url: String, destinationDir: String): ScResult<String> =
        withContext(dispatchers.io) {
            scResult {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@scResult fail("Сервер ответил ${response.code} при загрузке")
                    }
                    val body = response.body
                        ?: return@scResult fail("Пустой ответ при загрузке файла")

                    val dir = File(destinationDir).apply { if (!exists()) mkdirs() }
                    val target = File(dir, fileNameFrom(url))
                    target.outputStream().use { out -> body.byteStream().copyTo(out) }
                    target.absolutePath
                }
            }
        }

    /* ── Внутреннее ────────────────────────────────────────────────────── */

    /**
     * Степень для `inSampleSize`: ближайшее меньшее число, кратное степени
     * двойки. `BitmapFactory` понимает только степени двойки, поэтому любое
     * иное значение было бы молча округлено — считаем честно сами.
     */
    private fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (width / sample > maxSide || height / sample > maxSide) {
            sample *= 2
        }
        return sample
    }

    /** Поворачивает bitmap согласно EXIF-тегу, если он есть. */
    private fun applyExifOrientation(source: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * `content://` копируется во временный файл: `BitmapFactory.decodeFile`
     * не умеет читать из `ContentResolver`, а тянуть `ParcelFileDescriptor`
     * в каждый вызов — лишний код на горячем пути.
     */
    private fun resolveFile(uri: String): File? {
        if (!uri.startsWith(CONTENT_SCHEME)) {
            val direct = File(uri.removePrefix(FILE_SCHEME))
            return if (direct.exists()) direct else null
        }
        return runCatching {
            val parsed = android.net.Uri.parse(uri)
            val target = File(cacheDir(), "src_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(parsed)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target
        }.getOrNull()
    }

    private fun cacheDir(): File =
        File(context.cacheDir, DIR_COMPRESSED).apply { if (!exists()) mkdirs() }

    private fun fileNameFrom(url: String): String =
        url.substringAfterLast('/').substringBefore('?').ifBlank { "download_${System.currentTimeMillis()}" }

    private fun fail(message: String): ScResult<Nothing> =
        ScResult.Failure(ScError.Local(message))

    private companion object {
        const val DIR_COMPRESSED = "compressed"
        const val DEFAULT_MAX_SIDE = 2560
        const val JPEG_QUALITY = 82
        const val MIME_JPEG = "image/jpeg"
        const val CONTENT_SCHEME = "content://"
        const val FILE_SCHEME = "file://"
    }
}
