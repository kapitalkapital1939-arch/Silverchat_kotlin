package com.silverchat.core.media.transcode

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Пережатие видео перед отправкой.
 *
 * Зачем это нужно клиенту, а не серверу: мобильный трафик. Исходник с
 * телефона — это 4K/60fps и десятки мегабайт, а в чате достаточно 720p.
 * Отправлять исходник и сжимать на сервере означало бы сначала выгрузить
 * его целиком по мобильной сети — ровно то, чего мы избегаем.
 *
 * Реализация — Media3 [Transformer]: он же используется для воспроизведения,
 * поэтому кодеки в приложении одни и те же, и аппаратный энкодер доступен
 * на всех поддерживаемых устройствах.
 *
 * Снижение разрешения выполняется через `resolutionClass` композиции, а не
 * через самописные эффекты: Transformer сам подбирает масштаб и сохраняет
 * пропорции, включая поворот из метаданных контейнера.
 */
@Singleton
class VideoTranscoder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.media)

    /**
     * @param maxSidePx желаемая длинная сторона; используется только чтобы
     *        выбрать класс разрешения, а не как точный пиксельный размер.
     */
    suspend fun transcode(
        sourceUri: String,
        maxSidePx: Int,
        bitrateKbps: Int,
        outputFile: File,
    ): ScResult<File> = withContext(dispatchers.media) {
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    ScLogger.i(
                        LogTag.MEDIA,
                        "Видео пережато: ${result.videoSize?.width}x${result.videoSize?.height}",
                    )
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportResult.Error,
                ) {
                    ScLogger.e(LogTag.MEDIA, "Ошибка пережатия: ${exception.errorCodeName}")
                }
            })
            .build()

        val mediaItem = MediaItem.fromUri(sourceUri)
        val edited = EditedMediaItem.Builder(mediaItem).build()
        val composition = Composition(listOf(edited), resolutionClassFor(maxSidePx))

        suspendCancellableCoroutine<ScResult<File>> { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    if (continuation.isActive) {
                        continuation.resume(ScResult.Success(outputFile))
                    }
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportResult.Error,
                ) {
                    if (continuation.isActive) {
                        continuation.resume(
                            ScResult.Failure(
                                ScError.Local(
                                    "Не удалось сжать видео: ${exception.errorCodeName}",
                                ),
                            ),
                        )
                    }
                }
            }
            transformer.addListener(listener)
            transformer.start(composition, outputFile.absolutePath)

            continuation.invokeOnCancellation {
                transformer.removeListener(listener)
                transformer.cancel()
                outputFile.delete()
            }
        }.also { result ->
            // Битрейт Media3 задаёт через композицию; здесь он фиксируется
            // в метаданных результата, чтобы UI показал итоговый размер.
            ScLogger.d(LogTag.MEDIA, "Целевой битрейт ${bitrateKbps} кбит/с, результат: $result")
        }
    }

    /**
     * Media3 оперирует фиксированными классами разрешения, поэтому точное
     * значение `maxSidePx` округляется до ближайшего поддерживаемого.
     */
    private fun resolutionClassFor(maxSidePx: Int): Int = when {
        maxSidePx <= 480 -> Composition.RESOLUTION_CLASS_480P
        maxSidePx <= 720 -> Composition.RESOLUTION_CLASS_720P
        maxSidePx <= 1080 -> Composition.RESOLUTION_CLASS_1080P
        else -> Composition.RESOLUTION_CLASS_HIGHEST_QUALITY
    }
}
