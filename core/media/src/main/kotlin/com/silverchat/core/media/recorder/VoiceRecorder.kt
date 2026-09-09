package com.silverchat.core.media.recorder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.domain.repository.LocalMedia
import com.silverchat.core.domain.repository.RecordingSession
import com.silverchat.core.media.BuildConfig
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Запись голосовых сообщений.
 *
 * Ключевая деталь — [waveform]: амплитуды собираются ВО ВРЕМЯ записи,
 * а не вычисляются постфактум декодированием файла. Так дорожка рисуется
 * сразу, а файл не нужно перечитывать (экономия батареи и времени).
 *
 * Нормировка логарифмическая: человеческое ухо воспринимает громкость
 * логарифмически, поэтому линейная амплитуда даёт «плоский» рисунок,
 * где тихо и громко выглядит одинаково.
 */
@Singleton
class VoiceRecorder @Inject constructor(
    private val dispatchers: DispatcherProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.media)

    private var recorder: AudioRecord? = null
    private var outputFile: File? = null
    private var recordJob: Job? = null
    private val amplitudes = mutableListOf<Float>()

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun start(cacheDir: File): RecordingSession {
        if (_state.value is RecordingState.Recording) {
            return RecordingSession(currentSessionId, startedAt, MAX_DURATION_MS)
        }

        val sessionId = UUID.randomUUID().toString()
        currentSessionId = sessionId
        outputFile = File(cacheDir, "voice_$sessionId.opus").apply { parentFile?.mkdirs() }
        amplitudes.clear()
        startedAt = System.currentTimeMillis()

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBufferSize * 2, MIN_BUFFER_BYTES),
        )

        if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
            ScLogger.e(LogTag.MEDIA, "AudioRecord не инициализирован (нет разрешения?)")
            _state.value = RecordingState.Error("Микрофон недоступен")
            release()
            return RecordingSession(sessionId, startedAt, MAX_DURATION_MS)
        }

        recorder?.startRecording()
        _state.value = RecordingState.Recording(sessionId, 0L, emptyList())
        startCaptureLoop(sessionId)

        return RecordingSession(sessionId, startedAt, MAX_DURATION_MS)
    }

    private fun startCaptureLoop(sessionId: String) {
        recordJob?.cancel()
        recordJob = scope.launch {
            val buffer = ShortArray(BUFFER_SHORTS)
            while (true) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val peak = normalizePeak(buffer, read)
                    amplitudes.add(peak)
                    // Дорожка в UI ограничена по длине, иначе список растёт бесконечно
                    if (amplitudes.size > MAX_WAVEFORM_POINTS) {
                        amplitudes.removeAt(0)
                    }
                }

                val elapsed = System.currentTimeMillis() - startedAt
                _state.value = RecordingState.Recording(sessionId, elapsed, amplitudes.toList())

                if (elapsed >= MAX_DURATION_MS) {
                    ScLogger.i(LogTag.MEDIA, "Достигнут лимит записи — автоостановка")
                    break
                }
                delay(FRAME_DELAY_MS)
            }
        }
    }

    /**
     * Пик амплитуды кадра -> 0f..1f логарифмически.
     *
     * +1 к знаменателю защищает от log10(0) на полной тишине.
     */
    private fun normalizePeak(buffer: ShortArray, read: Int): Float {
        var peak = 0
        for (i in 0 until read) peak = max(peak, abs(buffer[i].toInt()))
        if (peak <= 0) return 0f
        val db = 20 * log10(peak.toDouble() / Short.MAX_VALUE + 1.0)
        return (db / DB_RANGE).toFloat().coerceIn(0f, 1f)
    }

    fun stop(): LocalMedia? {
        recordJob?.cancel()
        recordJob = null
        val file = outputFile ?: return null
        val duration = System.currentTimeMillis() - startedAt

        release()
        _state.value = RecordingState.Idle

        return LocalMedia(
            uri = "file://${file.absolutePath}",
            mimeType = "audio/opus",
            sizeBytes = file.length(),
            durationMs = duration,
            waveform = downsample(amplitudes, TARGET_WAVEFORM_POINTS),
        )
    }

    fun cancel() {
        recordJob?.cancel()
        recordJob = null
        outputFile?.delete()
        amplitudes.clear()
        release()
        _state.value = RecordingState.Idle
        ScLogger.d(LogTag.MEDIA, "Запись отменена пользователем")
    }

    /** Сжатие дорожки до фиксированного числа точек для компактного хранения. */
    private fun downsample(source: List<Float>, target: Int): List<Float> {
        if (source.size <= target) return source
        val step = source.size.toFloat() / target
        return (0 until target).map { i ->
            val from = (i * step).toInt()
            val to = ((i + 1) * step).toInt().coerceAtMost(source.size)
            if (to <= from) source[from] else source.subList(from, to).max()
        }
    }

    private fun release() {
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
    }

    @Volatile private var currentSessionId: String = ""
    @Volatile private var startedAt: Long = 0L

    private companion object {
        val SAMPLE_RATE = BuildConfig.VOICE_SAMPLE_RATE
        val MAX_DURATION_MS = 10 * 60 * 1000L // Telegram-лимит: 10 минут
        const val BUFFER_SHORTS = 1024
        const val MIN_BUFFER_BYTES = 8192
        const val FRAME_DELAY_MS = 40L
        const val MAX_WAVEFORM_POINTS = 4096
        const val TARGET_WAVEFORM_POINTS = 120
        const val DB_RANGE = -60.0 // диапазон нормировки: -60 дБ .. 0 дБ
    }
}

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val sessionId: String, val elapsedMs: Long, val waveform: List<Float>) : RecordingState
    data class Error(val message: String) : RecordingState
}
