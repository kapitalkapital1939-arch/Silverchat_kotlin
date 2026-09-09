package com.silverchat.core.designsystem.component.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.silverchat.core.common.format.NumberFormatter
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme

/**
 * Голосовое сообщение с волновой формой.
 *
 * [waveform] — нормализованные амплитуды 0..1 (обычно 120 точек), сохранённые
 * вместе с сообщением: волна рисуется мгновенно, без скачивания и
 * декодирования аудио. Это критично — иначе каждый войс тянул бы файл
 * только ради картинки, а в списке из 50 голосовых это десятки мегабайт.
 *
 * Прогресс: столбики до [progress] окрашены ярко, остальные приглушены.
 *
 * @param mediaKey ключ для плеера (URL медиа) — по нему контроллер понимает,
 *   какой именно войс играет сейчас (см. AudioPlayerController).
 */
@Composable
fun VoiceMessageBody(
    durationMs: Long,
    waveform: List<Float>,
    isOutgoing: Boolean,
    mediaKey: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    progress: Float = 0f,
    onPlayPause: (String) -> Unit = {},
) {
    val idleColor = if (isOutgoing) ScTheme.bubbleOutgoingText else ScTheme.textTertiary
    val playedColor = if (isOutgoing) Color.White else ScTheme.accent

    // Кнопка слегка «дышит» во время воспроизведения — визуальный отклик
    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.08f else 1f,
        animationSpec = tween(200),
        label = "playButtonScale",
    )

    Row(
        modifier = modifier.padding(
            start = ScSpacing.sm,
            end = ScSpacing.bubblePaddingH,
            top = ScSpacing.bubblePaddingV,
            bottom = ScSpacing.bubblePaddingV,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isOutgoing) Color.White.copy(alpha = 0.22f) else ScTheme.accentContainer)
                .clickable { onPlayPause(mediaKey) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести голосовое",
                tint = if (isOutgoing) Color.White else ScTheme.accent,
                modifier = Modifier.size(22.dp * playScale),
            )
        }

        Column(Modifier.width(150.dp)) {
            Waveform(
                waveform = waveform,
                progress = progress,
                playedColor = playedColor,
                idleColor = idleColor.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().height(26.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = TimeFormatter.duration(durationMs),
                color = if (isOutgoing) Color.White.copy(alpha = 0.85f) else ScTheme.textTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Волновая форма на Canvas.
 *
 * Именно Canvas, а не 120 Box'ов: один узел отрисовки вместо сотни
 * композаблов — иначе список чата теряет кадры на скролле.
 */
@Composable
fun Waveform(
    waveform: List<Float>,
    progress: Float,
    playedColor: Color,
    idleColor: Color,
    modifier: Modifier = Modifier,
    barWidth: Float = 6f,
    gap: Float = 4f,
) {
    // Если волна не пришла с сервера — ровная линия вместо пустого места
    val data = remember(waveform) {
        if (waveform.isEmpty()) List(40) { 0.18f } else waveform
    }

    Canvas(modifier = modifier) {
        val step = barWidth + gap
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val playedCount = (count * progress.coerceIn(0f, 1f)).toInt()

        repeat(count) { index ->
            // Прореживаем волну под текущую ширину, чтобы столбики не слипались
            val sourceIndex = ((index.toFloat() / count) * data.size).toInt().coerceIn(0, data.lastIndex)
            val amplitude = data[sourceIndex].coerceIn(0.08f, 1f)
            val barHeight = size.height * amplitude

            drawRoundRect(
                color = if (index <= playedCount) playedColor else idleColor,
                topLeft = Offset(index * step, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

/** Индикатор записи голосового (в поле ввода). */
@Composable
fun RecordingIndicator(
    elapsedMs: Long,
    amplitude: Float,
    modifier: Modifier = Modifier,
    liveWaveform: List<Float> = emptyList(),
    onCancel: () -> Unit = {},
    onSend: () -> Unit = {},
) {
    // Пульс точки записи — универсальный сигнал «идёт запись»
    val transition = rememberInfiniteTransition(label = "recording")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recPulse",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ScTheme.surfaceGlass)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(ScTheme.recordingDot.copy(alpha = pulse)),
        )
        Text(
            text = TimeFormatter.duration(elapsedMs),
            color = ScTheme.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(Modifier.weight(1f).height(24.dp)) {
            Waveform(
                waveform = remember(liveWaveform, amplitude) {
                    liveWaveform.ifEmpty {
                        List(28) { (0.25f + (it % 7) * 0.1f) * amplitude.coerceAtLeast(0.15f) }
                    }
                },
                progress = 1f,
                playedColor = ScTheme.recordingDot,
                idleColor = ScTheme.recordingDot.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = "Отмена",
            color = ScTheme.danger,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(ScShapes.chip)
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            text = "Отправить",
            color = ScTheme.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(ScShapes.chip)
                .clickable(onClick = onSend)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Файловое сообщение. */
@Composable
fun FileMessageBody(
    fileName: String,
    sizeBytes: Long,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    downloadProgress: Float? = null,
    onClick: () -> Unit = {},
) {
    val textColor = if (isOutgoing) ScTheme.bubbleOutgoingText else ScTheme.textPrimary
    val subColor = if (isOutgoing) textColor.copy(alpha = 0.8f) else ScTheme.textTertiary

    Row(
        modifier = modifier
            .padding(ScSpacing.sm)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(ScShapes.cardSmall)
                .background(if (isOutgoing) Color.White.copy(alpha = 0.22f) else ScTheme.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = if (isOutgoing) Color.White else ScTheme.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.width(160.dp)) {
            Text(fileName, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(NumberFormatter.fileSize(sizeBytes), color = subColor, fontSize = 11.5.sp)
            if (downloadProgress != null && downloadProgress < 1f) {
                Spacer(Modifier.height(4.dp))
                LinearProgressMini(progress = downloadProgress, isOutgoing = isOutgoing)
            }
        }
    }
}

@Composable
private fun LinearProgressMini(progress: Float, isOutgoing: Boolean) {
    val color = if (isOutgoing) Color.White else ScTheme.accent
    Canvas(Modifier.fillMaxWidth().height(3.dp).clip(ScShapes.chip)) {
        drawRoundRect(color = color.copy(alpha = 0.25f), cornerRadius = CornerRadius(4f, 4f))
        drawRoundRect(
            color = color,
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
            cornerRadius = CornerRadius(4f, 4f),
        )
    }
}

/**
 * «Видеокружок» (video_note) — круглое видео с прогрессом по обводу.
 *
 * Прогресс рисуется дугой поверх превью: во время воспроизведения видно,
 * сколько осталось, не отвлекаясь на отдельный плеер.
 */
@Composable
fun VideoCircleMessage(
    durationMs: Long,
    thumbnailUrl: String?,
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Box(
        modifier
            .size(150.dp)
            .clickable(onClick = onClick),
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = "Видеосообщение",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(ScTheme.surfaceVariant))
        }

        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val strokeWidth = 4.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = ScTheme.accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)) {
            Text(
                text = TimeFormatter.duration(durationMs),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Полоса загрузки медиа в пузыре (фото/видео/файл).
 * Прогресс приходит из [com.silverchat.core.domain.repository.MediaRepository.observeUploadProgress].
 */
@Composable
fun MediaUploadOverlay(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(64.dp)
                    .height(3.dp)
                    .clip(ScShapes.chip)
                    .background(Color.White.copy(alpha = 0.3f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(ScShapes.chip)
                        .background(Color.White),
                )
            }
        }
    }
}
