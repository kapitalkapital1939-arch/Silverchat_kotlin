package com.silverchat.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.silverchat.core.domain.repository.AccentColor
import com.silverchat.core.domain.repository.AppTheme
import com.silverchat.core.domain.repository.ThemeMode
import com.silverchat.core.domain.repository.ThemePalette

/* =========================================================================
   ТЕМА ОФОРМЛЕНИЯ
   ------------------------------------------------------------------------
   Настраиваемые темы — требование продукта: пользователь переключает
   светлую/тёмную, выбирает одну из 8 палитр и акцентный цвет, а Premium
   дополнительно получает кастомную палитру и анимированные обои.

   Двухслойность намеренна:
    - MaterialTheme (colorScheme/typography/shapes) — для стандартных
      компонентов Material3, которые мы всё равно используем;
    - [LocalScColors] — для мессенджер-специфики (пузыри, тик-метки,
      сильверы, сторис-кольца), которой в Material нет.
   ========================================================================= */

private val LocalScColors = staticCompositionLocalOf { LightScColors }

/** Текущие семантические цвета. Используется во всех компонентах фич. */
val ScTheme: ScColors
    @Composable get() = LocalScColors.current

/** Активна ли тёмная тема — нужно для glassmorphism-скрима и обоев. */
private val LocalScIsDark = staticCompositionLocalOf { false }
val ScThemeIsDark: Boolean
    @Composable get() = LocalScIsDark.current

/** Отключены ли анимации (настройка «Плавные UI-анимации»). */
private val LocalScAnimationsEnabled = staticCompositionLocalOf { true }
val ScAnimationsEnabled: Boolean
    @Composable get() = LocalScAnimationsEnabled.current

/** Глобальный множитель размера шрифта из настроек. */
private val LocalScFontScale = staticCompositionLocalOf { 1f }
val ScFontScale: Float
    @Composable get() = LocalScFontScale.current

/**
 * Корневая тема приложения. Оборачивает ВСЕ экраны (см. :app MainActivity).
 *
 * @param theme        выбранные пользователем настройки темы
 * @param fontScale    множитель шрифта (0.8..1.6)
 * @param animations   плавные анимации вкл/выкл
 * @param forceDark    принудительный dark для полноэкранных слоёв
 *                     (сторис-плеер и звонок всегда тёмные)
 */
@Composable
fun SilverChatTheme(
    theme: AppTheme = AppTheme(),
    fontScale: Float = 1f,
    animations: Boolean = true,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = forceDark || when (theme.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val palette = theme.palette.toScPalette()
    val base = if (dark) DarkScColors else LightScColors

    val colors = base.withPalette(palette, theme.accent, theme.custom)

    val colorScheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentContainer,
            onPrimaryContainer = colors.accent,
            secondary = colors.premium,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outline,
            outlineVariant = colors.outlineVariant,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentContainer,
            onPrimaryContainer = colors.accentDark,
            secondary = colors.premium,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outline,
            outlineVariant = colors.outlineVariant,
            error = colors.danger,
        )
    }

    // Синхронизируем системные бары с фоном приложения (edge-to-edge)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalScColors provides colors,
        LocalScIsDark provides dark,
        LocalScAnimationsEnabled provides animations,
        LocalScFontScale provides fontScale,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ScTypography.scale(fontScale),
            shapes = ScMaterialShapes,
            content = content,
        )
    }
}

/** Применяет выбранную палитру и акцент к базовому набору цветов. */
private fun ScColors.withPalette(
    palette: ScPalette,
    accentColor: AccentColor,
    custom: com.silverchat.core.domain.repository.CustomTheme?,
): ScColors {
    // Кастомная тема (Premium) имеет приоритет над пресетами
    if (custom != null) {
        val accent = Color(custom.accentArgb)
        return copy(
            accent = accent,
            accentLight = accent.copy(alpha = 0.72f),
            accentDark = accent.copy(alpha = 0.88f),
            accentContainer = accent.copy(alpha = 0.14f),
            background = Color(custom.backgroundArgb),
            surface = Color(custom.backgroundArgb).lighten(),
            bubbleIncoming = Color(custom.bubbleInArgb),
            bubbleOutgoing = Color(custom.bubbleOutArgb),
            bubbleOutgoingEnd = Color(custom.bubbleOutArgb),
            navBarSelected = accent,
            storyRingUnseen = accent,
        )
    }

    val accent = accentColor.toColor() ?: palette.accentLight
    val accentDark = if (ScThemeIsDarkCompat) accent else palette.accentDark

    return copy(
        accent = accent,
        accentLight = palette.accentLight,
        accentDark = accentDark,
        accentContainer = accent.copy(alpha = 0.14f),
        bubbleOutgoing = palette.accentLight,
        bubbleOutgoingEnd = accent,
        navBarSelected = accent,
        navBarBadge = accent,
        storyRingUnseen = accent,
    )
}

/**
 * Флаг тёмной темы вне CompositionLocal (нужен в [withPalette],
 * где CompositionLocal ещё не доступен).
 */
private val ScThemeIsDarkCompat: Boolean get() = false

/** Осветляет цвет для производных поверхностей. */
private fun Color.lighten(factor: Float = 0.06f): Color = Color(
    red = (red + factor).coerceAtMost(1f),
    green = (green + factor).coerceAtMost(1f),
    blue = (blue + factor).coerceAtMost(1f),
    alpha = alpha,
)

/** Масштабирование типографики под настройку размера шрифта. */
private fun androidx.compose.material3.Typography.scale(factor: Float): androidx.compose.material3.Typography {
    if (factor == 1f) return this
    fun androidx.compose.ui.text.TextStyle.scaled() = copy(
        fontSize = (fontSize.value * factor).sp(),
        lineHeight = (lineHeight.value * factor).sp(),
    )
    return androidx.compose.material3.Typography(
        headlineLarge = headlineLarge.scaled(),
        headlineMedium = headlineMedium.scaled(),
        headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(),
        titleMedium = titleMedium.scaled(),
        titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(),
        bodyMedium = bodyMedium.scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(),
        labelMedium = labelMedium.scaled(),
        labelSmall = labelSmall.scaled(),
        displaySmall = displaySmall.scaled(),
    )
}

private fun Float.sp() = androidx.compose.ui.unit.TextUnit(value, androidx.compose.ui.unit.TextUnitType.Sp)

private fun ThemePalette.toScPalette(): ScPalette = when (this) {
    ThemePalette.SILVER_BLUE -> ScPalette.SILVER_BLUE
    ThemePalette.GRAPHITE -> ScPalette.GRAPHITE
    ThemePalette.VIOLET -> ScPalette.VIOLET
    ThemePalette.EMERALD -> ScPalette.EMERALD
    ThemePalette.SUNSET -> ScPalette.SUNSET
    ThemePalette.ROSE -> ScPalette.ROSE
    ThemePalette.MIDNIGHT -> ScPalette.MIDNIGHT
    ThemePalette.CUSTOM -> ScPalette.SILVER_BLUE
}

private fun AccentColor.toColor(): Color? = when (this) {
    AccentColor.BLUE -> ScColorsRaw.BrandBlue
    AccentColor.VIOLET -> ScColorsRaw.Violet
    AccentColor.EMERALD -> ScColorsRaw.Emerald
    AccentColor.SUNSET -> ScColorsRaw.Sunset
    AccentColor.ROSE -> ScColorsRaw.Rose
    AccentColor.SILVER -> ScColorsRaw.Silver400
    AccentColor.CYAN -> ScColorsRaw.Midnight
}

/**
 * Детерминированный выбор градиента аватарки по id.
 * Одинаковый id всегда даёт одинаковый цвет — иначе при каждом
 * обновлении списка аватарки «мигают» разными цветами.
 */
fun avatarGradientFor(id: String): List<Color> {
    val index = (id.hashCode().toLong() and 0x7FFFFFFF).toInt() % ScColorsRaw.AvatarGradients.size
    return ScColorsRaw.AvatarGradients[index]
}

@Suppress("unused")
private fun unusedContextRef(context: android.content.Context) = Unit
