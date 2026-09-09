package com.silverchat.core.media.upload

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.repository.LocalMedia
import com.silverchat.core.domain.repository.RemoteMedia
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.dto.UploadTicketRequest
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Загрузка медиа через presigned-URL.
 *
 * Схема (важна для нагрузки на бэкенд):
 *  1. клиент запрашивает `media/upload-ticket` у Node.js — получает
 *     presigned PUT-URL в object storage;
 *  2. файл грузится НАПРЯМУЮ в хранилище, минуя Node.js: сервер не
 *     проксирует гигабайты видео и не держит длинные соединения;
 *  3. в сообщение подставляется [RemoteMedia.publicUrl] из тикета.
 *
 * Прогресс публикуется в [progressFlow] по uploadId, поэтому UI рисует
 * кружок загрузки поверх превью, а не блокирует весь экран.
 */
@Singleton
class MediaUploader @Inject constructor(
    private val api: SilverChatApi,
    private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) {

    private val progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progressFlow: Flow<Map<String, Float>> = progress.asStateFlow()

    fun observeProgress(uploadId: String): Flow<Float> = kotlinx.coroutines.flow.flow {
        progress.collect { map -> emit(map[uploadId] ?: 0f) }
    }

    suspend fun upload(media: LocalMedia, chatId: String?, kind: UploadKind): ScResult<RemoteMedia> {
        val file = File(media.uri.removePrefix("file://"))
        if (!file.exists()) {
            return ScResult.Failure(ScError.Local("Файл не найден: ${media.uri}"))
        }

        val limitBytes = MAX_UPLOAD_MB * 1024L * 1024L
        if (media.sizeBytes > limitBytes) {
            return ScResult.Failure(
                ScError.Validation(
                    "Файл больше ${MAX_UPLOAD_MB} МБ. Оформите Premium для загрузки до 4 ГБ.",
                    field = "file",
                ),
            )
        }

        // 1. Тикет на загрузку
        val ticket = runCatching {
            api.uploadTicket(
                UploadTicketRequest(
                    mimeType = media.mimeType,
                    sizeBytes = media.sizeBytes,
                    chatId = chatId,
                    kind = kind.wireName,
                ),
            )
        }.getOrElse {
            return ScResult.Failure(it.toUploadError())
        }

        // 2. PUT напрямую в object storage
        val uploadId = ticket.uploadId
        val body = file.asRequestBody(media.mimeType.toMediaTypeOrNull())

        val request = Request.Builder()
            .url(ticket.uploadUrl)
            .method(ticket.method, body)
            .apply {
                ticket.headers.forEach { (key, value) -> header(key, value) }
            }
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    ScLogger.e(LogTag.MEDIA, "Загрузка не удалась: HTTP ${response.code}")
                    return ScResult.Failure(ScError.Server("Хранилище отклонило файл: ${response.code}"))
                }
                progress.value = progress.value + (uploadId to 1f)
                ScLogger.i(LogTag.MEDIA, "Файл загружен: ${ticket.publicUrl}")

                ScResult.Success(
                    RemoteMedia(
                        url = ticket.publicUrl,
                        thumbUrl = ticket.thumbUrl,
                        sizeBytes = media.sizeBytes,
                        width = media.width,
                        height = media.height,
                        durationMs = media.durationMs,
                        waveform = media.waveform,
                    ),
                )
            }
        } catch (e: Exception) {
            ScLogger.e(LogTag.MEDIA, "Сбой загрузки", e)
            ScResult.Failure(e.toUploadError())
        } finally {
            // Прогресс чистим с задержкой, чтобы UI успел дорисовать 100%
            progress.value = progress.value - uploadId
        }
    }

    /**
     * Классификация сбоя загрузки.
     *
     * `SocketTimeoutException` — наследник `IOException`, поэтому его ветка
     * обязана стоять раньше: иначе таймаут большой загрузки показывался бы как
     * «нет соединения». Для пользователя это разные действия — подождать и
     * повторить на Wi-Fi против «проверь сеть».
     */
    private fun Throwable.toUploadError(): ScError = when (this) {
        is java.net.SocketTimeoutException -> ScError.Network("Загрузка прервана по таймауту", this)
        is java.io.IOException -> ScError.Network("Нет соединения для загрузки файла", this)
        else -> ScError.Unknown("Ошибка загрузки", this)
    }

    private companion object {
        const val MAX_UPLOAD_MB = 100
    }
}

enum class UploadKind(val wireName: String) {
    PHOTO("photo"),
    VIDEO("video"),
    VOICE("voice"),
    CIRCLE("circle"),
    FILE("file"),
    AVATAR("avatar"),
    BANNER("banner"),
    STORY("story"),
    STICKER("sticker"),
}
