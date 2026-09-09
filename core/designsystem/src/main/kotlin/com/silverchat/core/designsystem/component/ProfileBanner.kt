package com.silverchat.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.model.BannerMedia

/**
 * Баннер профиля / канала с поддержкой анимации.
 *
 * Три режима:
 *  1. статичное изображение;
 *  2. **анимированный баннер** (GIF/WebP/зацикленное видео) — Premium;
 *  3. градиент с «бликом», если баннера нет — дефолт, выглядит не пусто.
 *
 * Parallax при скролле профиля: [scrollProgress] 0..1 сдвигает баннер
 * медленнее контента — стандартный приём для hero-блока.
 */
@Composable
fun ProfileBanner(
    banner: BannerMedia?,
    modifier: Modifier = Modifier,
    height: Dp = BANNER_HEIGHT,
    allowAnimation: Boolean = true,
    scrollProgress: Float = 0f,
    overlayContent: (@Composable () -> Unit)? = null,
) {
    val parallax by animateFloatAsState(
        targetValue = scrollProgress * PARALLAX_MAX_DP,
        animationSpec = tween(durationMillis = 120),
        label = "bannerParallax",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(ScShapes.banner),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -parallax.dp.toPx() },
        ) {
            val url = if (allowAnimation) {
                banner?.animatedUrl ?: banner?.staticUrl
            } else {
                banner?.staticUrl
            }

            if (url.isNullOrBlank()) {
                DefaultBannerGradient()
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(250)
                        // Зацикливание GIF/WebP задаёт сам декодер Coil:
                        // повтор включён по умолчанию, отдельного флага нет
                        .build(),
                    contentDescription = "Баннер профиля",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Затемнение снизу: текст имени должен быть читаем на любом баннере
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.45f to Color.Transparent,
                                1f to BANNER_SCRIM,
                            ),
                        ),
                    ),
            )
        }

        if (overlayContent != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) { overlayContent() }
        }
    }
}

/**
 * Дефолтный градиент + бегущий «блик».
 * Именно блик делает баннер живым даже без медиа — стекло и градиенты
 * без движения выглядят мёртвыми.
 */
@Composable
private fun DefaultBannerGradient() {
    val transition = rememberInfiniteTransition(label = "bannerShine")
    val shine by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shine",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ScTheme.accent,
                        ScTheme.accentLight,
                        ScTheme.premiumLight.copy(alpha = 0.85f),
                    ),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.22f }
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White, Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(shine * 1000f, 0f),
                        end = androidx.compose.ui.geometry.Offset(shine * 1000f + 400f, 600f),
                    ),
                ),
        )
    }
}

/** Анимированный градиентный фон для карточек Premium в маркете. */
@Composable
fun PremiumGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "premiumShift")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shift",
    )

    Box(
        modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    ScTheme.premiumGradientStart,
                    ScTheme.premiumGradientEnd,
                    ScTheme.accent,
                ),
                start = androidx.compose.ui.geometry.Offset(shift * 400f, 0f),
                end = androidx.compose.ui.geometry.Offset(shift * 400f + 800f, 600f),
            ),
        ),
    ) { content() }
}

private val BANNER_HEIGHT = 172.dp
private const val PARALLAX_MAX_DP = 40f
private val BANNER_SCRIM = Color(0x66050A12)
