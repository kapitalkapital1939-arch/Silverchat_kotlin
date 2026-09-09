package com.silverchat.feature.calls.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silverchat.core.common.format.TimeFormatter
import com.silverchat.core.designsystem.component.SilverButton
import com.silverchat.core.designsystem.component.SilverButtonVariant
import com.silverchat.core.designsystem.component.UserAvatar
import com.silverchat.core.designsystem.navigation.CallsNavigator
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.CallState
import com.silverchat.core.model.ConnectionQuality
import com.silverchat.core.model.User
import com.silverchat.core.webrtc.CallUiState
import com.silverchat.core.webrtc.WebRtcClient
import com.silverchat.feature.calls.CallUi
import com.silverchat.feature.calls.CallViewModel
import com.silverchat.feature.calls.peerUser
import com.silverchat.feature.calls.titleRu
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer

/**
 * Полноэкранный звонок.
 *
 * Три визуальных состояния в одном экране:
 *  1. **входящий** — крупные кнопки «принять» / «отклонить», управление скрыто;
 *  2. **активный** — видео собеседника на весь экран, своё видео —
 *     небольшим окном (picture-in-picture), панель управления внизу;
 *  3. **завершённый/ошибка** — причина и возврат в чат.
 *
 * WebRTC-рендер подключается через [AndroidView]: `SurfaceViewRenderer` —
 * нативная View, иCompose-аналога у неё нет. Критично освобождать её в
 * `DisposableEffect.onDispose` — WebRTC держит нативную память, и без
 * `release()` процесс падает с SIGSEGV после нескольких звонков.
 *
 * Звук и состояние звонка живут в `CallController` (`@Singleton`), поэтому
 * поворот экрана или переход в mini-player не рвёт соединение.
 */
@Composable
fun CallScreen(
    navigator: CallsNavigator,
    modifier: Modifier = Modifier,
    currentUserId: String = "",
    webrtc: WebRtcClient? = null,
    viewModel: CallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val controllerState = state.controllerState

    // Звонок завершился со стороны собеседника или сети — закрываем экран.
    // Слушаем именно контроллер, а не локальный флаг: завершение приходит
    // сигналом WebSocket в любое время, включая фоновый режим.
    //
    // Задержка обязательна: `CallController.finishCall` сначала переводит
    // сессию в ENDED (экран показывает причину и длительность), и лишь затем
    // выставляет Idle. Уход без задержки обрезал бы финальное состояние,
    // и пользователь не понял бы, кто завершил звонок и почему.
    LaunchedEffect(controllerState) {
        if (controllerState is CallUiState.Idle && state.session != null) {
            delay(END_SCREEN_HOLD_MS)
            navigator.back()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CALL_BACKGROUND_TOP, CALL_BACKGROUND_BOTTOM),
                ),
            ),
    ) {
        // ── Видео-слой ───────────────────────────────────────────────────
        if (state.isVideo && webrtc != null) {
            RemoteVideo(webrtc = webrtc, modifier = Modifier.fillMaxSize())
            LocalVideoPip(
                webrtc = webrtc,
                visible = state.isCameraOn,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(ScSpacing.md),
            )
        } else {
            // Затемнение поверх видео: текст должен читаться на любом кадре
            AvatarPlaceholder(
                peer = state.session?.peerUser(currentUserId),
                state = state,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(Modifier.fillMaxSize()) {
            CallHeader(state = state, modifier = Modifier.statusBarsPadding())

            Spacer(Modifier.weight(1f))

            when {
                state.isIncoming -> IncomingControls(
                    peer = state.session?.peerUser(currentUserId),
                    isVideo = state.isVideo,
                    onAccept = viewModel::accept,
                    onDecline = viewModel::decline,
                )

                state.isEnded -> EndedControls(
                    state = state,
                    onBackToChat = {
                        state.session?.let { navigator.openChat(it.chatId.raw) }
                    },
                )

                else -> ActiveControls(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        }

        // Предупреждение о качестве связи — только когда оно мешает
        viewModel.qualityHint(state.quality)?.let { hint ->
            QualityWarning(
                text = hint,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = HEADER_HEIGHT_DP.dp + ScSpacing.md),
            )
        }
    }
}

/** Градиент фона звонка: тёмный, чтобы видео и белый текст читались. */
private val CALL_BACKGROUND_TOP = Color(0xFF10141C)
private val CALL_BACKGROUND_BOTTOM = Color(0xFF1E2531)
private const val HEADER_HEIGHT_DP = 96

/** Сколько показываем финальное состояние перед закрытием экрана. */
private const val END_SCREEN_HOLD_MS = 1_600L

/* =========================================================================
   ШАПКА
   ========================================================================= */

@Composable
private fun CallHeader(state: CallUi, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(ScSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.session?.let { it.state.titleRu } ?: "Звонок",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = state.subtitle,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = ScSpacing.sm),
        ) {
            // Шифрование показываем всегда: это требование безопасности
            if (state.isEncrypted) {
                HeaderTag(icon = Icons.Filled.Lock, label = "Шифровано")
            }
            if (state.hdAvailable && state.isVideo) {
                HeaderTag(icon = Icons.Filled.Star, label = "HD", tint = ScTheme.premium)
            }
            // UNKNOWN не подписываем: пустой тег выглядит как баг вёрстки
            if (state.quality != ConnectionQuality.UNKNOWN) {
                HeaderTag(icon = Icons.Filled.SignalCellularAlt, label = state.quality.titleRu())
            }
        }
    }
}

@Composable
private fun HeaderTag(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White.copy(alpha = 0.75f),
) {
    Row(
        Modifier
            .clip(ScShapes.chip)
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(label, color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun ConnectionQuality.titleRu(): String = when (this) {
    ConnectionQuality.EXCELLENT -> "Отличная связь"
    ConnectionQuality.GOOD -> "Хорошая связь"
    ConnectionQuality.FAIR -> "Средняя связь"
    ConnectionQuality.POOR -> "Слабая связь"
    ConnectionQuality.LOST -> "Нет связи"
    ConnectionQuality.UNKNOWN -> ""
}

/* =========================================================================
   ВИДЕО-СЛОЙ (WebRTC)
   ========================================================================= */

/**
 * Видео собеседника на весь экран.
 *
 * `SurfaceViewRenderer` инициализируется один раз на композицию и
 * освобождается в `onDispose`. `remember` обязателен: без него Compose
 * создал бы новый рендерер на каждую recomposition, и нативная память
 * вытекла бы за считанные секунды.
 */
@Composable
private fun RemoteVideo(webrtc: WebRtcClient, modifier: Modifier = Modifier) {
    val remoteStream by webrtc.remoteStream.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val renderer = remember(context) { SurfaceViewRenderer(context) }
    DisposableEffect(webrtc, renderer) {
        webrtc.initRenderer(renderer)
        onDispose {
            webrtc.releaseRenderer(renderer)
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier,
        update = { view ->
            // Поток приходит асинхронно: привязываем при каждом изменении
            remoteStream?.videoTracks?.firstOrNull()?.addSink(view)
        },
    )
}

/** Своё видео — небольшое окно в углу (picture-in-picture). */
@Composable
private fun LocalVideoPip(
    webrtc: WebRtcClient,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val localStream by webrtc.localStream.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        val context = LocalContext.current
        val renderer = remember(context) { SurfaceViewRenderer(context) }
        DisposableEffect(webrtc, renderer) {
            webrtc.initRenderer(renderer)
            onDispose { webrtc.releaseRenderer(renderer) }
        }

        Box(
            Modifier
                .width(PIP_WIDTH_DP.dp)
                .height(PIP_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        ) {
            AndroidView(
                factory = { renderer },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    localStream?.videoTracks?.firstOrNull()?.addSink(view)
                },
            )
        }
    }
}

private const val PIP_WIDTH_DP = 108
private const val PIP_HEIGHT_DP = 168

/* =========================================================================
   ЗАГЛУШКА ДЛЯ ГОЛОСОВОГО ЗВОНКА
   ========================================================================= */

@Composable
private fun AvatarPlaceholder(
    peer: User?,
    state: CallUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(ScSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(HEADER_HEIGHT_DP.dp))
        UserAvatar(
            user = peer,
            size = ScAvatarSize.call,
            allowAnimation = state.session?.state == CallState.CONNECTING,
        )
        Spacer(Modifier.height(ScSpacing.lg))
        Text(
            text = peer?.fullName ?: "Неизвестный абонент",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        peer?.handle?.let {
            Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
    }
}

/* =========================================================================
   УПРАВЛЕНИЕ
   ========================================================================= */

@Composable
private fun IncomingControls(
    peer: User?,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(ScSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isVideo) "Входящий видеозвонок" else "Входящий звонок",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.5.sp,
        )
        Spacer(Modifier.height(ScSpacing.xl))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundCallButton(
                icon = Icons.Filled.CallEnd,
                label = "Отклонить",
                background = ScTheme.danger,
                onClick = onDecline,
                size = 72,
            )
            RoundCallButton(
                icon = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Mic,
                label = "Принять",
                background = ScTheme.success,
                onClick = onAccept,
                size = 72,
            )
        }
        peer?.let {
            Spacer(Modifier.height(ScSpacing.md))
            Text(
                text = it.fullName,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ActiveControls(
    state: CallUi,
    viewModel: CallViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(ScSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Длительность — главный индикатор «звонок жив»
        Text(
            text = TimeFormatter.callTimer(state.elapsedMs),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(ScSpacing.lg))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RoundCallButton(
                icon = if (state.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = if (state.isMuted) "Включить" else "Выключить",
                background = Color.White.copy(alpha = 0.14f),
                active = state.isMuted,
                onClick = viewModel::toggleMute,
                enabled = state.canControl,
            )
            if (state.isVideo) {
                RoundCallButton(
                    icon = if (state.isCameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    label = "Камера",
                    background = Color.White.copy(alpha = 0.14f),
                    active = !state.isCameraOn,
                    onClick = viewModel::toggleCamera,
                    enabled = state.canControl,
                )
                RoundCallButton(
                    icon = Icons.Filled.FlipCameraAndroid,
                    label = "Переключить",
                    background = Color.White.copy(alpha = 0.14f),
                    onClick = viewModel::switchCamera,
                    enabled = state.canControl && state.isCameraOn,
                )
            } else {
                RoundCallButton(
                    icon = Icons.Filled.VolumeUp,
                    label = "Динамик",
                    background = Color.White.copy(alpha = 0.14f),
                    active = state.isSpeakerOn,
                    onClick = viewModel::toggleSpeaker,
                    enabled = state.canControl,
                )
            }
            RoundCallButton(
                icon = Icons.Filled.Cast,
                label = "Экран",
                background = Color.White.copy(alpha = 0.14f),
                active = state.isScreenSharing,
                onClick = viewModel::toggleScreenSharing,
                enabled = state.canControl,
            )
        }

        Spacer(Modifier.height(ScSpacing.xl))

        RoundCallButton(
            icon = Icons.Filled.CallEnd,
            label = "Завершить",
            background = ScTheme.danger,
            onClick = viewModel::hangUp,
            size = 72,
        )
        Spacer(Modifier.height(ScSpacing.sm))
    }
}

@Composable
private fun EndedControls(
    state: CallUi,
    onBackToChat: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(ScSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.endReasonRu ?: "Звонок завершён",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        state.session?.durationMs?.let { duration ->
            Spacer(Modifier.height(ScSpacing.xs))
            Text(
                text = "Длительность: ${TimeFormatter.duration(duration)}",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(ScSpacing.lg))
        SilverButton(
            text = "Вернуться в чат",
            onClick = onBackToChat,
            variant = SilverButtonVariant.SECONDARY,
            fullwidth = false,
        )
    }
}

/* =========================================================================
   КРУГЛАЯ КНОПКА ЗВОНКА
   ========================================================================= */

/**
 * Круглая кнопка управления звонком.
 *
 * Единый компонент для всех кнопок: в звонке их пять-шесть, и раздельная
 * вёрстка каждой неизбежно разъехалась бы по размерам и отступам.
 */
@Composable
private fun RoundCallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Int = 60,
) {
    Column(
        modifier.clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(if (active) ScTheme.accent else background)
                .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size((size * 0.42f).dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 0.75f else 0.35f),
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private const val DISABLED_ALPHA = 0.4f

@Composable
private fun QualityWarning(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.xl)
            .clip(ScShapes.chip)
            .background(ScTheme.warning.copy(alpha = 0.85f))
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.sm),
    )
}
