package com.silverchat.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Палитра SilverChat.
 *
 * Все цвета объявлены как токены, а не разбросаны по фичам: смена темы
 * сводится к подмене [ScColors], а не к правке 200 вызовов Color(0xFF…).
 *
 * Префиксы:
 *  - silver*  — фирменные «серебряные» оттенки бренда и валюты;
 *  - accent*  — акцент текущей темы (переключается в настройках);
 *  - surface* — фоны и карточки;
 *  - chat*    — специфика экрана диалога (пузыри, фон чата).
 */
object ScColorsRaw {

    /* ── Бренд ──────────────────────────────────────────────────────────── */
    val Silver100 = Color(0xFFF4F7FB)
    val Silver200 = Color(0xFFE2E8F2)
    val Silver300 = Color(0xFFC3CCDA)
    val Silver400 = Color(0xFF9AA7B8)
    val Silver500 = Color(0xFF71809A)
    val Silver600 = Color(0xFF4C5A6E)
    val Silver700 = Color(0xFF333D4C)
    val Silver800 = Color(0xFF22293A)
    val Silver900 = Color(0xFF141A26)
    val Silver950 = Color(0xFF0B0F17)

    /* ── Фирменный синий (тема по умолчанию) ───────────────────────────── */
    val BrandBlue = Color(0xFF3E82F7)
    val BrandBlueLight = Color(0xFF6FA8FF)
    val BrandBlueDark = Color(0xFF2B6FE0)

    /* ── Акценты остальных тем ─────────────────────────────────────────── */
    val Violet = Color(0xFF8B5CF6)
    val VioletLight = Color(0xFFB79BFF)
    val Emerald = Color(0xFF10B981)
    val EmeraldLight = Color(0xFF4EDBA6)
    val Sunset = Color(0xFFF97316)
    val SunsetLight = Color(0xFFFBBF6A)
    val Rose = Color(0xFFE5467F)
    val RoseLight = Color(0xFFFF8FB4)
    val Midnight = Color(0xFF38BDF8)
    val MidnightLight = Color(0xFF7DD8FB)
    val Graphite = Color(0xFF6B7280)
    val GraphiteLight = Color(0xFF9CA3AF)

    /* ── Семантика ─────────────────────────────────────────────────────── */
    val Success = Color(0xFF22B573)
    val Warning = Color(0xFFF5A524)
    val Danger = Color(0xFFF04141)
    val Info = Color(0xFF3E82F7)

    /* ── Premium / валюта ──────────────────────────────────────────────── */
    val PremiumGold = Color(0xFFF5A524)
    val PremiumGoldLight = Color(0xFFFFD479)
    val PremiumGradientStart = Color(0xFFF7B733)
    val PremiumGradientEnd = Color(0xFFE2603B)

    /* ── Онлайн-индикатор (как в Telegram) ─────────────────────────────── */
    val OnlineGreen = Color(0xFF31D158)

    /* ── Аватарки: градиенты по умолчанию, если фото нет ───────────────── */
    val AvatarGradients = listOf(
        listOf(Color(0xFFFF885E), Color(0xFFFF516A)),
        listOf(Color(0xFFFFCD6A), Color(0xFFFFA85C)),
        listOf(Color(0xFF82B1FF), Color(0xFF665FFF)),
        listOf(Color(0xFFA0DE7E), Color(0xFF54CB68)),
        listOf(Color(0xFF53EDFF), Color(0xFF2CC7E8)),
        listOf(Color(0xFFFF8A8A), Color(0xFFE85C5C)),
        listOf(Color(0xFFB79BFF), Color(0xFF8B5CF6)),
        listOf(Color(0xFFC3CCDA), Color(0xFF71809A)),
    )
}

/**
 * Семантический набор цветов текущей темы.
 *
 * Compose-идиома: фичи обращаются к [LocalScColors], а не к MaterialTheme.colorScheme,
 * потому что нам нужны токены мессенджера (пузыри, тик-метки, сильверы),
 * которых в Material нет.
 */
data class ScColors(
    /* Акцент */
    val accent: Color,
    val accentLight: Color,
    val accentDark: Color,
    val accentContainer: Color,
    val onAccent: Color,

    /* Поверхности */
    val background: Color,
    val backgroundGradientStart: Color,
    val backgroundGradientEnd: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val surfaceGlass: Color,
    val surfaceGlassBorder: Color,

    /* Текст */
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,

    /* Разделители */
    val outline: Color,
    val outlineVariant: Color,

    /* Чат */
    val chatBackground: Color,
    val chatBackgroundPattern: Color,
    val bubbleIncoming: Color,
    val bubbleIncomingText: Color,
    val bubbleOutgoing: Color,
    val bubbleOutgoingEnd: Color,
    val bubbleOutgoingText: Color,
    val bubbleTimestamp: Color,
    val tickRead: Color,
    val tickSent: Color,

    /* Семантика */
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val online: Color,

    /* Premium и валюта */
    val premium: Color,
    val premiumLight: Color,
    val premiumGradientStart: Color,
    val premiumGradientEnd: Color,
    val silver: Color,
    val silverCoinStart: Color,
    val silverCoinEnd: Color,

    /* Навигация */
    val navBarBackground: Color,
    val navBarSelected: Color,
    val navBarUnselected: Color,
    val navBarBadge: Color,
    val navBarBadgeText: Color,

    /* Прочее */
    val scrim: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
    val storyRingUnseen: Color,
    val storyRingSeen: Color,
    val recordingDot: Color,
)

/** Светлая тема (фирменный синий). */
val LightScColors = ScColors(
    accent = ScColorsRaw.BrandBlue,
    accentLight = ScColorsRaw.BrandBlueLight,
    accentDark = ScColorsRaw.BrandBlueDark,
    accentContainer = Color(0x1F3E82F7),
    onAccent = Color.White,

    background = Color(0xFFEDF1F7),
    backgroundGradientStart = Color(0xFFDFE9FB),
    backgroundGradientEnd = Color(0xFFE7E2FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFF4F6FA),
    surfaceElevated = Color.White,
    surfaceGlass = Color(0xA8FFFFFF),
    surfaceGlassBorder = Color(0xB3FFFFFF),

    textPrimary = Color(0xFF0F1720),
    textSecondary = Color(0xFF5A6879),
    textTertiary = Color(0xFF8C99A9),
    textOnAccent = Color.White,

    outline = Color(0x170F1720),
    outlineVariant = Color(0x0D0F1720),

    chatBackground = Color(0xFFE9EFF9),
    chatBackgroundPattern = Color(0x143E82F7),
    bubbleIncoming = Color.White,
    bubbleIncomingText = Color(0xFF0F1720),
    bubbleOutgoing = ScColorsRaw.BrandBlueLight,
    bubbleOutgoingEnd = ScColorsRaw.BrandBlue,
    bubbleOutgoingText = Color.White,
    bubbleTimestamp = Color(0x9EFFFFFF),
    tickRead = Color.White,
    tickSent = Color(0xCCFFFFFF),

    success = ScColorsRaw.Success,
    warning = ScColorsRaw.Warning,
    danger = ScColorsRaw.Danger,
    info = ScColorsRaw.Info,
    online = ScColorsRaw.OnlineGreen,

    premium = Color(0xFFE09A17),
    premiumLight = ScColorsRaw.PremiumGoldLight,
    premiumGradientStart = ScColorsRaw.PremiumGradientStart,
    premiumGradientEnd = ScColorsRaw.PremiumGradientEnd,
    silver = ScColorsRaw.Silver400,
    silverCoinStart = Color(0xFFF4F7FB),
    silverCoinEnd = ScColorsRaw.Silver400,

    navBarBackground = Color(0xE6FFFFFF),
    navBarSelected = ScColorsRaw.BrandBlue,
    navBarUnselected = Color(0xFF8C99A9),
    navBarBadge = ScColorsRaw.BrandBlue,
    navBarBadgeText = Color.White,

    scrim = Color(0x800C1422),
    shimmerBase = Color(0xFFE4E9F1),
    shimmerHighlight = Color(0xFFF7F9FC),
    storyRingUnseen = ScColorsRaw.BrandBlue,
    storyRingSeen = Color(0xFFC9D2DE),
    recordingDot = ScColorsRaw.Danger,
)

/** Тёмная тема. */
val DarkScColors = LightScColors.copy(
    accent = ScColorsRaw.BrandBlueLight,
    accentLight = Color(0xFF9CC4FF),
    accentDark = ScColorsRaw.BrandBlue,
    accentContainer = Color(0x296FA8FF),

    background = Color(0xFF0B1017),
    backgroundGradientStart = Color(0xFF16202F),
    backgroundGradientEnd = Color(0xFF1A1730),
    surface = Color(0xFF141B25),
    surfaceVariant = Color(0xFF1A2230),
    surfaceElevated = Color(0xFF1C2531),
    surfaceGlass = Color(0xAD141B25),
    surfaceGlassBorder = Color(0x1AFFFFFF),

    textPrimary = Color(0xFFEAF0F8),
    textSecondary = Color(0xFF98A6B8),
    textTertiary = Color(0xFF6C7B8D),

    outline = Color(0x17FFFFFF),
    outlineVariant = Color(0x0DFFFFFF),

    chatBackground = Color(0xFF0C121B),
    chatBackgroundPattern = Color(0x1F6FA8FF),
    bubbleIncoming = Color(0xFF1B2431),
    bubbleIncomingText = Color(0xFFEAF0F8),
    bubbleOutgoing = Color(0xFF4C8DF6),
    bubbleOutgoingEnd = Color(0xFF2B6FE0),
    bubbleOutgoingText = Color.White,

    navBarBackground = Color(0xE6141B25),
    navBarSelected = ScColorsRaw.BrandBlueLight,
    navBarUnselected = Color(0xFF6C7B8D),
    navBarBadge = ScColorsRaw.BrandBlueLight,

    scrim = Color(0xA603070D),
    shimmerBase = Color(0xFF1A2230),
    shimmerHighlight = Color(0xFF243044),
    storyRingSeen = Color(0xFF2C3746),
)

/**
 * Палитры-акценты. Меняют только accent-группу, поэтому тема остаётся
 * консистентной в light и dark одновременно.
 */
enum class ScPalette(val accentLight: Color, val accentDark: Color, val displayName: String) {
    SILVER_BLUE(ScColorsRaw.BrandBlueLight, ScColorsRaw.BrandBlueDark, "Silver Blue"),
    GRAPHITE(ScColorsRaw.GraphiteLight, Color(0xFF4B5563), "Графит"),
    VIOLET(ScColorsRaw.VioletLight, Color(0xFF7C3AED), "Фиолет"),
    EMERALD(ScColorsRaw.EmeraldLight, Color(0xFF059669), "Изумруд"),
    SUNSET(ScColorsRaw.SunsetLight, Color(0xFFEA580C), "Закат"),
    ROSE(ScColorsRaw.RoseLight, Color(0xFFDB2777), "Розовый"),
    MIDNIGHT(ScColorsRaw.MidnightLight, Color(0xFF0284C7), "Полночь"),
    SILVER(ScColorsRaw.Silver300, ScColorsRaw.Silver600, "Серебро"),
}
