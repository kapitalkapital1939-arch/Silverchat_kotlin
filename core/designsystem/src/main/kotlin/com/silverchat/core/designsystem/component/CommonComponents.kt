package com.silverchat.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme

/* =========================================================================
   КНОПКИ
   ========================================================================= */

enum class SilverButtonVariant {
    /** Главная: градиент акцента, белый текст. */
    PRIMARY,

    /** Вторичная: прозрачная с рамкой. */
    SECONDARY,

    /** Текстовая без фона — для неглавных действий. */
    TEXT,

    /** Опасная: бан, удаление, выход из чата. */
    DESTRUCTIVE,

    /** Premium: золотой градиент, только для платных функций. */
    PREMIUM,
}

enum class SilverButtonSize { SMALL, MEDIUM, LARGE }

/**
 * Основная кнопка приложения.
 *
 * Все кнопки в одном композабле — иначе в 8 фичах появится 8 вариантов
 * кнопки с разными отступами, и дизайн «поплывёт».
 *
 * [loading] блокирует повторные нажатия: критично для финансовых операций
 * (покупка юзернейма), где двойной тап = двойное списание.
 */
@Composable
fun SilverButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: SilverButtonVariant = SilverButtonVariant.PRIMARY,
    size: SilverButtonSize = SilverButtonSize.MEDIUM,
    leadingIcon: ImageVector? = null,
    fullwidth: Boolean = true,
) {
    val clickable = enabled && !loading
    val alphaValue by animateFloatAsState(
        targetValue = if (clickable) 1f else 0.5f,
        animationSpec = tween(180),
        label = "buttonAlpha",
    )

    val backgroundModifier = when (variant) {
        SilverButtonVariant.PRIMARY -> Modifier.background(
            Brush.horizontalGradient(listOf(ScTheme.accent, ScTheme.accentLight)),
        )

        SilverButtonVariant.SECONDARY -> Modifier
            .background(Color.Transparent)
            .border(1.dp, ScTheme.outline, ScShapes.chip)

        SilverButtonVariant.TEXT -> Modifier.background(Color.Transparent)

        SilverButtonVariant.DESTRUCTIVE -> Modifier.background(ScTheme.danger)

        SilverButtonVariant.PREMIUM -> Modifier.background(
            Brush.horizontalGradient(listOf(ScTheme.premiumGradientStart, ScTheme.premiumGradientEnd)),
        )
    }

    val textColor = when (variant) {
        SilverButtonVariant.PRIMARY -> Color.White
        SilverButtonVariant.SECONDARY -> ScTheme.textPrimary
        SilverButtonVariant.TEXT -> ScTheme.accent
        SilverButtonVariant.DESTRUCTIVE -> Color.White
        SilverButtonVariant.PREMIUM -> Color(0xFF3B2A06)
    }

    val (vPad, hPad, fontSize) = when (size) {
        SilverButtonSize.SMALL -> Triple(8.dp, 14.dp, 13.sp)
        SilverButtonSize.MEDIUM -> Triple(12.dp, 20.dp, 14.5.sp)
        SilverButtonSize.LARGE -> Triple(15.dp, 24.dp, 16.sp)
    }

    Box(
        modifier = modifier
            .then(if (fullwidth) Modifier.fillMaxWidth() else Modifier)
            .clip(ScShapes.chip)
            .then(backgroundModifier)
            .alpha(alphaValue)
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm)) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = textColor,
                )
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Круглая иконочная кнопка (FAB, действия в поле ввода). */
@Composable
fun SilverIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = ScTheme.textSecondary,
    background: Color = Color.Transparent,
    size: Int = 40,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** FAB «Новый чат» — стеклянный, с градиентом. */
@Composable
fun NewChatFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val width by animateFloatAsState(if (expanded) 1f else 0f, tween(220), label = "fabExpand")
    Row(
        modifier = modifier
            .clip(ScShapes.chip)
            .background(Brush.horizontalGradient(listOf(ScTheme.accent, ScTheme.accentLight)))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        AnimatedVisibility(
            visible = expanded && width > 0.5f,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text("Новый чат", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/* =========================================================================
   ВЕРХНЯЯ ПАНЕЛЬ
   ========================================================================= */

/**
 * Верхняя панель экрана.
 *
 * Не используем Material3 TopAppBar: у него фиксированный контраст и он
 * плохо сочетается со стеклянными поверхностями. Своя реализация даёт
 * полный контроль над blur-фоном и содержимым (в чате это аватар + статус,
 * а не просто заголовок).
 */
@Composable
fun SilverTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = ScTheme.textTertiary,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    titleContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ScTheme.navBarBackground)
            .padding(horizontal = ScSpacing.sm, vertical = ScSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            SilverIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                onClick = onBack,
                tint = ScTheme.textPrimary,
            )
        }

        if (leadingContent != null) {
            leadingContent()
        }

        Column(Modifier.weight(1f).padding(horizontal = ScSpacing.sm)) {
            if (titleContent != null) {
                titleContent()
            } else {
                Text(
                    text = title,
                    color = ScTheme.textPrimary,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            subtitle?.let {
                Text(
                    text = it,
                    color = subtitleColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (actions != null) {
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

/** Заголовок экрана без панели навигации (для вкладок нижней навигации). */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ScTheme.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = ScTheme.textTertiary, fontSize = 13.sp) }
        }
        trailing?.invoke()
    }
}

/* =========================================================================
   ПОЛЯ ВВОДА
   ========================================================================= */

/**
 * Поле ввода в стеклянном стиле.
 *
 * [error] показывает подсказку под полем сразу (а не только после сабмита):
 * для юзернейма это критично — пользователь должен видеть «занято» до
 * отправки формы.
 */
@Composable
fun SilverTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLength: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    leadingIcon: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        label?.let {
            Text(
                text = it,
                color = ScTheme.textSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(ScShapes.cardSmall)
                .background(ScTheme.surface)
                .border(
                    width = 1.dp,
                    color = if (error != null) ScTheme.danger else ScTheme.outline,
                    shape = ScShapes.cardSmall,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = ScTheme.textTertiary,
                        modifier = Modifier.padding(start = 14.dp).size(18.dp),
                    )
                }
                TextField(
                    value = value,
                    onValueChange = { newValue ->
                        if (maxLength == null || newValue.length <= maxLength) onValueChange(newValue)
                    },
                    placeholder = placeholder?.let { { Text(it, color = ScTheme.textTertiary, fontSize = 14.5.sp) } },
                    enabled = enabled,
                    singleLine = singleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    visualTransformation = visualTransformation,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = ScTheme.accent,
                        focusedTextColor = ScTheme.textPrimary,
                        unfocusedTextColor = ScTheme.textPrimary,
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = if (leadingIcon != null) 8.dp else 14.dp),
                )
                trailingContent?.invoke()
            }
        }
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = error.orEmpty(),
                color = ScTheme.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 5.dp, start = 4.dp),
            )
        }
        if (maxLength != null) {
            Text(
                text = "${value.length}/$maxLength",
                color = ScTheme.textTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp).align(Alignment.End),
            )
        }
    }
}

/** Поле поиска — отдельный композабл, используется в 5 экранах. */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Поиск",
    focused: Boolean = false,
    onClear: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScShapes.chip)
            .background(ScTheme.surfaceGlass)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.sm),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = ScTheme.textTertiary, modifier = Modifier.size(19.dp))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder, color = ScTheme.textTertiary, fontSize = 14.5.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = ScTheme.accent,
                focusedTextColor = ScTheme.textPrimary,
                unfocusedTextColor = ScTheme.textPrimary,
            ),
            modifier = Modifier.weight(1f).padding(0.dp),
        )
        if (query.isNotEmpty()) {
            SilverIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Очистить поиск",
                onClick = onClear,
                size = 30,
            )
        }
    }
}

/* =========================================================================
   СТРОКИ НАСТРОЕК / МЕНЮ
   ========================================================================= */

/**
 * Строка настроек: иконка + заголовок + подпись + переключатель/стрелка.
 *
 * Единый компонент для всех 8 экранов настроек — иначе каждый экран
 * изобретает свой вариант строки с другими отступами.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = ScTheme.textSecondary,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    danger: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScShapes.cardSmall)
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(ScSpacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (danger) ScTheme.danger else ScTheme.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            subtitle?.let {
                Text(it, color = ScTheme.textTertiary, fontSize = 12.5.sp, maxLines = 2)
            }
        }
        trailing?.invoke()
    }
}

/** Разделитель секций настроек с заголовком. */
@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        color = ScTheme.accent,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(start = ScSpacing.md, top = ScSpacing.lg, bottom = ScSpacing.xs),
    )
}

/* =========================================================================
   СОСТОЯНИЯ ЭКРАНА
   ========================================================================= */

/** Загрузка на весь экран — стеклянный оверлей со спиннером. */
@Composable
fun LoadingOverlay(message: String? = null, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ScTheme.surfaceGlass)
            .padding(ScSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ScTheme.accent, strokeWidth = 3.dp, modifier = Modifier.size(38.dp))
            message?.let {
                Spacer(Modifier.height(ScSpacing.md))
                Text(it, color = ScTheme.textSecondary, fontSize = 13.5.sp)
            }
        }
    }
}

/** Состояние ошибки с кнопкой повтора. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Повторить",
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(ScSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(ScTheme.danger.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = ScTheme.danger, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(ScSpacing.md))
        Text(message, color = ScTheme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (onRetry != null) {
            Spacer(Modifier.height(ScSpacing.lg))
            SilverButton(text = retryLabel, onClick = onRetry, fullwidth = false, variant = SilverButtonVariant.SECONDARY)
        }
    }
}

/** Пустое состояние списка. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(ScSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(ScTheme.surfaceGlass),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ScTheme.textTertiary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(ScSpacing.md))
        Text(title, color = ScTheme.textPrimary, fontSize = 16.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(ScSpacing.xs))
        Text(
            text = subtitle,
            color = ScTheme.textTertiary,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(ScSpacing.lg))
            SilverButton(text = actionLabel, onClick = onAction, fullwidth = false)
        }
    }
}

/** Снекбар-уведомление внутри экрана (не системный Snackbar). */
@Composable
fun InlineSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScShapes.card)
            .background(ScTheme.surfaceElevated)
            .border(1.dp, ScTheme.outline, ScShapes.card)
            .padding(horizontal = ScSpacing.md, vertical = ScSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScSpacing.md),
    ) {
        Text(message, color = ScTheme.textPrimary, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        actionLabel?.let {
            Text(
                text = it,
                color = ScTheme.accent,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(ScShapes.chip).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Индикатор загрузки контента в конце списка (подгрузка страниц). */
@Composable
fun PaginationLoader(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(ScSpacing.md), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ScTheme.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
    }
}
