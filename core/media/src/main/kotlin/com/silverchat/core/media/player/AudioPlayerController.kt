package com.silverchat.core.media.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Единый плеер голосовых/видео/«кружков».
 *
 * Singleton с ОДНИМ экземпляром ExoPlayer намеренно: в Telegram-подобном UI
 * пользователь жмёт на несколько голосовых подряд, и если создавать плеер
 * на каждое сообщение, звучат два сообщения одновременно и растёт память.
 * Здесь новое воспроизведение останавливает предыдущее.
 */
@Singleton
class AudioPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var player: ExoPlayer? = null

    private val _playback = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val current = _playback.value
            if (current is PlaybackState.Playing) {
                _playback.value = when (state) {
                    Player.STATE_ENDED -> PlaybackState.Completed(current.messageId)
                    Player.STATE_BUFFERING -> current.copy(buffering = true)
                    else -> current.copy(buffering = false)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            ScLogger.e(LogTag.MEDIA, "Ошибка воспроизведения", error)
            _playback.value = PlaybackState.Error(error.errorCodeName)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val current = _playback.value
            if (current is PlaybackState.Playing) {
                _playback.value = current.copy(isPlaying = isPlaying)
            }
        }
    }

    /**
     * Запуск воспроизведения конкретного сообщения.
     * Если это же сообщение уже играет — ставим на паузу (toggle).
     */
    fun play(messageId: String, url: String) {
        val current = _playback.value
        if (current is PlaybackState.Playing && current.messageId == messageId) {
            if (current.isPlaying) pause() else resume()
            return
        }

        stop()
        val instance = ensurePlayer()
        instance.setMediaItem(MediaItem.fromUri(url))
        instance.prepare()
        instance.playWhenReady = true

        _playback.value = PlaybackState.Playing(
            messageId = messageId,
            positionMs = 0L,
            durationMs = 0L,
            isPlaying = true,
        )
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun seek(messageId: String, positionMs: Long) {
        val current = _playback.value
        if (current is PlaybackState.Playing && current.messageId == messageId) {
            player?.seekTo(positionMs)
            _playback.value = current.copy(positionMs = positionMs)
        }
    }

    /** Обновление позиции — вызывается из Compose по LaunchedEffect с тиком 100 мс. */
    fun tickPosition() {
        val current = _playback.value
        val instance = player ?: return
        if (current is PlaybackState.Playing && current.isPlaying) {
            _playback.value = current.copy(
                positionMs = instance.currentPosition.coerceAtLeast(0),
                durationMs = instance.duration.coerceAtLeast(0),
            )
        }
    }

    fun stop() {
        runCatching {
            player?.stop()
            player?.clearMediaItems()
        }
        _playback.value = PlaybackState.Idle
    }

    fun release() {
        runCatching {
            player?.removeListener(listener)
            player?.release()
        }
        player = null
        _playback.value = PlaybackState.Idle
    }

    private fun ensurePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(context)
        .setSeekBackIncrementMs(SEEK_BACK_MS)
        .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
        .build()
        .also { created ->
            created.addListener(listener)
            player = created
        }

    private companion object {
        const val SEEK_BACK_MS = 5_000L
        const val SEEK_FORWARD_MS = 5_000L
    }
}

sealed interface PlaybackState {
    data object Idle : PlaybackState

    data class Playing(
        val messageId: String,
        val positionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean,
        val buffering: Boolean = false,
    ) : PlaybackState {
        val progress: Float
            get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    data class Completed(val messageId: String) : PlaybackState
    data class Error(val code: String) : PlaybackState
}
