package com.silverchat.core.designsystem.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverchat.core.designsystem.glass.GlassState
import com.silverchat.core.designsystem.glass.glassSurface
import com.silverchat.core.designsystem.theme.ScShapes
import com.silverchat.core.designsystem.theme.ScSpacing
import com.silverchat.core.designsystem.theme.ScTheme

/**
 * Нижняя панель навигации — «стеклянная» (Glassmorphism), Telegram-style.
 *
 * Реализованные детали:
 *  - backdrop-blur через Haze: контент чатов размывается под панелью;
 *  - индикатор активной вкладки сверху (тонкая полоска-подсветка);
 *  - бейджи непрочитанных: число для чатов, точка для маркета/профиля;
 *  - бейдж «mute» (серый) для чатов, где все диалоги заглушены;
 *  - пружинная анимация иконки при переключении;
 *  - safe-area: navigationBarsPadding для жестовых систем Android.
 *
 * ВАЖНО про производительность: [badges] и [unreadTotal] — примитивы,
 * а не объекты. Если передавать сюда весь список чатов, панель будет
 * рекомпозироваться на каждое сообщение.
 */
@Composable
fun SilverChatBottomBar(
    selected: TopLevelDestination,
    glassState: GlassState,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    unreadTotal: Int = 0,
    unreadMuted: Boolean = false,
    badges: Map<TopLevelDestination, BottomBarBadge> = emptyMap(),
    showLabels: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                state = glassState,
                shape = ScShapes.banner,
                blurRadius = 24.dp,
                tint = ScTheme.navBarBackground,
                border = ScTheme.surfaceGlassBorder,
            )
            .navigationBarsPadding()
            .height(BAR_HEIGHT),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val badge = badges[destination] ?: BottomBarBadge.None

            // Для «Чатов» бейдж берём из общего счётчика непрочитанных
            val effectiveBadge = when (destination) {
                TopLevelDestination.CHATS -> if (unreadTotal > 0) {
                    BottomBarBadge.Count(unreadTotal, muted = unreadMuted)
                } else {
                    badge
                }

                else -> badge
            }

            BottomBarItem(
                destination = destination,
                selected = destination == selected,
                badge = effectiveBadge,
                showLabel = showLabels,
                onClick = { onDestinationSelected(destination) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    badge: BottomBarBadge,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Пружинная анимация: иконка слегка приподнимается при выборе
    val lift by animateFloatAsState(
        targetValue = if (selected) -2f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "tabLift",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "tabScale",
    )

    val tint = if (selected) ScTheme.navBarSelected else ScTheme.navBarUnselected

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScShapes.cardSmall)
            .semantics { contentDescription = destination.contentDescription }
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Индикатор активной вкладки — полоска сверху, как в Telegram
            Box(
                Modifier
                    .padding(bottom = ScSpacing.xs)
                    .width(if (selected) INDICATOR_WIDTH else 0.dp)
                    .height(INDICATOR_HEIGHT)
                    .clip(CircleShape)
                    .background(if (selected) ScTheme.navBarSelected else androidx.compose.ui.graphics.Color.Transparent),
            )

            BadgedBox(
                badge = {
                    when (badge) {
                        is BottomBarBadge.Count -> Badge(
                            containerColor = if (badge.muted) ScTheme.navBarUnselected else ScTheme.navBarBadge,
                            contentColor = ScTheme.navBarBadgeText,
                        ) {
                            Text(
                                text = formatCount(badge.count),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                            )
                        }

                        BottomBarBadge.Dot -> Badge(containerColor = ScTheme.navBarBadge)
                        BottomBarBadge.None -> Unit
                    }
                },
            ) {
                Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .size(ICON_SIZE)
                        .graphicsLayer {
                            translationY = lift * 2f
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }

            if (showLabel) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = destination.labelRu,
                    color = tint,
                    fontSize = LABEL_SIZE,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (selected) {
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = LABEL_SIZE,
                        )
                    } else {
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            fontSize = LABEL_SIZE,
                        )
                    },
                )
            }
        }
    }
}

/**
 * Кликабельная область без ripple: на стеклянной панели подсветка нажатия
 * выглядит грязно, поэтому используем собственный interactionSource.
 */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}

/** Бейдж вкладки. */
sealed interface BottomBarBadge {
    data object None : BottomBarBadge
    data object Dot : BottomBarBadge
    data class Count(val count: Int, val muted: Boolean = false) : BottomBarBadge
}

/** 99+ вместо 137 — как в Telegram, чтобы бейдж не растягивался. */
private fun formatCount(count: Int): String = if (count > 99) "99+" else count.toString()

private val BAR_HEIGHT = 62.dp
private val ICON_SIZE = 24.dp
private val LABEL_SIZE = 10.5.sp
private val INDICATOR_WIDTH = 26.dp
private val INDICATOR_HEIGHT = 3.dp
