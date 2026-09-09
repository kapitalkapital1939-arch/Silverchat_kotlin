package com.silverchat.core.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.hazeSource

/* =========================================================================
   GLASSMORPHISM — «эффект стекла»
   ------------------------------------------------------------------------
   Настоящий backdrop-blur возможен только на API 31+ (RenderEffect).
   Поэтому стекло сделано двухуровневым:

    - API 31+ : Haze (RenderEffect) — реальное размытие контента ПОД панелью;
    - API 26-30: полупрозрачный скрим + тонкая светящаяся граница +
                 внутренний градиент. Визуально это читается как стекло,
                 но не требует дорогого блюра.

   Ключевое правило: [Modifier.hazeSource] вешается на СКРОЛЛЯЩИЙСЯ контент,
   а [Modifier.hazeChild] — на панель поверх него. Без source-ноды
   child-блюр не знает, что размывать.
   ========================================================================= */

/**
 * Состояние стекла. Создаётся ОДНО на экран и прокидывается вниз:
 * несколько HazeState на экране = несколько проходов рендера и просадка FPS.
 */
class GlassState {
    val hazeState = HazeState()
}

/** Помечает контент как источник для размытия (список сообщений, лента чатов). */
fun Modifier.glassSource(state: GlassState): Modifier = this.hazeSource(state.hazeState)

/**
 * Прозрачная «стеклянная» поверхность: шапки, нижняя навигация, шторки.
 *
 * @param blurRadius   радиус размытия; на слабых устройствах снижайте до 8.dp
 * @param tint         лёгкая подкраска стекла (сохраняет контраст текста)
 * @param border       светящаяся кромка — главный визуальный признак стекла
 */
@Composable
fun Modifier.glassSurface(
    state: GlassState,
    shape: Shape = ScShapes.card,
    blurRadius: Dp = 18.dp,
    tint: Color = ScTheme.surfaceGlass,
    border: Color = ScTheme.surfaceGlassBorder,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .clip(shape)
    .hazeChild(
        state = state.hazeState,
        style = HazeStyle(
            backgroundColor = ScTheme.surface,
            tint = HazeTintCompat(tint),
            blurRadius = blurRadius,
            noiseFactor = 0.02f,
        ),
    )
    .border(width = borderWidth, color = border, shape = shape)

/** Обёртка над HazeTint, чтобы не тащить его тип в сигнатуру композабла. */
@Composable
private fun HazeTintCompat(color: Color) = dev.chrisbanes.haze.HazeTint(color)

/**
 * Готовая стеклянная поверхность-контейнер.
 * Используется для шапок экранов, панелей ввода, диалогов и шторок.
 */
@Composable
fun GlassBox(
    state: GlassState,
    modifier: Modifier = Modifier,
    shape: Shape = ScShapes.card,
    blurRadius: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .glassSurface(state = state, shape = shape, blurRadius = blurRadius),
        content = content,
    )
}

/**
 * Декоративный стеклянный фон приложения: мягкие цветные «орбы» под блюром.
 * Создаёт глубину, на которой стекло действительно читается как стекло —
 * на однотонном фоне blur не заметен.
 */
@Composable
fun AmbientGlassBackground(
    modifier: Modifier = Modifier,
    orbsVisible: Boolean = true,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ScTheme.backgroundGradientStart,
                            ScTheme.background,
                        ),
                        center = androidx.compose.ui.geometry.Offset(0.15f, -0.1f),
                        radius = 1600f,
                    ),
                ),
        )
        if (orbsVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.5f }
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                ScTheme.accent.copy(alpha = 0.22f),
                                Color.Transparent,
                                ScTheme.premium.copy(alpha = 0.12f),
                            ),
                            start = androidx.compose.ui.geometry.Offset.Zero,
                            end = androidx.compose.ui.geometry.Offset.Infinite,
                        ),
                    ),
            )
        }
    }
}

/**
 * Скрим под модальными окнами: тёмный + лёгкий блюр контента позади.
 * Именно блюр скрима отличает «стеклянный» диалог от обычного.
 */
@Composable
fun GlassScrim(
    state: GlassState,
    modifier: Modifier = Modifier,
    alpha: Float = 0.55f,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .hazeChild(
                state = state.hazeState,
                style = HazeStyle(
                    backgroundColor = Color.Black,
                    tint = HazeTintCompat(ScTheme.scrim.copy(alpha = alpha)),
                    blurRadius = 6.dp,
                ),
            ),
    )
}
