package com.silverchat.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.silverchat.core.designsystem.theme.ScAvatarSize
import com.silverchat.core.designsystem.theme.ScTheme
import com.silverchat.core.designsystem.theme.avatarGradientFor
import com.silverchat.core.model.AvatarAnimationType
import com.silverchat.core.model.AvatarMedia
import com.silverchat.core.model.User
import com.silverchat.core.model.UserPresence

/**
 * Аватар пользователя, группы или канала.
 *
 * Поддерживает все режимы из требований:
 *  1. статичное фото;
 *  2. градиент с инициалами, если фото нет — цвет детерминирован по
 *     [fallbackId], поэтому аватар не «мигает» при обновлениях списка;
 *  3. **анимированная аватарка**: GIF/WebP через Coil, видео через
 *     coil-video, Lottie через [AnimatedLottieAvatar];
 *  4. кольцо сторис (непросмотренные — градиент, просмотренные — серый);
 *  5. индикатор онлайна.
 *
 * Видео-аватар показывается только Premium: для остальных
 * [allowAnimation] = false и рендерится [AvatarMedia.staticUrl].
 *
 * @param avatarOverride аватар чата/канала, когда [User] отсутствует;
 * @param fallbackTitle  имя для инициалов, если [user] == null;
 * @param fallbackId     стабильный id для выбора градиента.
 */
@Composable
fun UserAvatar(
    modifier: Modifier = Modifier,
    user: User? = null,
    avatarOverride: AvatarMedia? = null,
    fallbackTitle: String? = null,
    fallbackId: String = user?.id?.raw ?: fallbackTitle ?: "anon",
    size: Dp = ScAvatarSize.listItem,
    showOnline: Boolean = false,
    showStoryRing: Boolean = false,
    storySeen: Boolean = true,
    allowAnimation: Boolean = true,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
) {
    val avatar = avatarOverride ?: user?.avatar
    val initials = remember(fallbackId, user?.firstName, user?.lastName, fallbackTitle) {
        user?.initials ?: fallbackTitle?.initials() ?: "?"
    }
    val gradient = remember(fallbackId) { avatarGradientFor(fallbackId) }
    val isOnline = user?.presence is UserPresence.Online

    Box(
        modifier = modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (showStoryRing) {
            StoryRing(seen = storySeen, modifier = Modifier.fillMaxSize())
        }

        val ringPadding = if (showStoryRing) 3.dp else 0.dp

        // Lottie-аватар — отдельная ветка: это JSON, а не растр
        if (allowAnimation && avatar?.animationType == AvatarAnimationType.LOTTIE &&
            !avatar.animatedUrl.isNullOrBlank()
        ) {
            LottieAvatarBody(url = avatar.animatedUrl, initials = initials, gradient = gradient, size = size, padding = ringPadding)
        } else {
            Box(
                Modifier
                    .padding(ringPadding)
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Brush.linearGradient(gradient))
                    .then(
                        if (borderWidth > 0.dp) {
                            Modifier.border(borderWidth, borderColor, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                // Crossfade: плавная смена аватара вместо резкого «прыжка»
                Crossfade(targetState = avatar, label = "avatarCrossfade") { current ->
                    val url = pickUrl(current, allowAnimation)
                    if (url.isNullOrBlank()) {
                        Initials(initials, size)
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .crossfade(200)
                                .build(),
                            contentDescription = user?.fullName ?: fallbackTitle ?: "Аватар",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        if (showOnline && isOnline) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(onlineDotSize(size))
                    .clip(CircleShape)
                    .background(ScTheme.online)
                    .border(onlineBorderSize(size), ScTheme.surface, CircleShape),
            )
        }
    }
}

/**
 * Выбирает, какой URL показывать.
 *
 * Анимация используется только если разрешена настройкой «плавные анимации»
 * и у аватара есть animated_url. Для VIDEO-типа [allowAnimation] уже
 * проставлен в false непрeмиум-пользователям (см. ProfileUseCases).
 */
private fun pickUrl(avatar: AvatarMedia?, allowAnimation: Boolean): String? {
    if (avatar == null) return null
    if (!allowAnimation || !avatar.isAnimated) return avatar.staticUrl ?: avatar.animatedUrl
    return avatar.animatedUrl ?: avatar.staticUrl
}

@Composable
private fun LottieAvatarBody(
    url: String?,
    initials: String,
    gradient: List<Color>,
    size: Dp,
    padding: Dp,
) {
    // Lottie-композиция грузится асинхронно: до загрузки показываем инициалы,
    // чтобы в списке не было «дырок» вместо аватаров
    val composition by rememberLottieComposition(LottieCompositionSpec.Url(url.orEmpty()))

    Box(
        Modifier
            .padding(padding)
            .fillMaxSize()
            .clip(CircleShape)
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                iterations = Int.MAX_VALUE,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Initials(initials, size)
        }
    }
}

@Composable
private fun Initials(initials: String, size: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp,
            maxLines = 1,
        )
    }
}

private fun String.initials(): String = buildString {
    trim().split(Regex("\\s+")).take(2).forEach { word ->
        word.firstOrNull()?.uppercaseChar()?.let(::append)
    }
}.ifBlank { "?" }

/**
 * Кольцо сторис: непросмотренные — фирменный градиент, просмотренные — серый.
 * Отдельный композабл, потому что используется и в трейе, и в списке чатов.
 */
@Composable
fun StoryRing(seen: Boolean, modifier: Modifier = Modifier) {
    val brush = if (seen) {
        Brush.linearGradient(listOf(ScTheme.storyRingSeen, ScTheme.storyRingSeen))
    } else {
        Brush.linearGradient(
            listOf(
                ScTheme.storyRingUnseen,
                ScTheme.premiumLight,
                ScTheme.accentLight,
            ),
        )
    }
    Box(
        modifier
            .clip(CircleShape)
            .background(brush)
            .padding(2.5.dp)
            .clip(CircleShape)
            .background(ScTheme.surface),
    )
}

/**
 * Анимированная аватарка/стикер на Lottie (JSON).
 *
 * Lottie выбран вместо анимированного WebP потому, что JSON весит в 10–20 раз
 * меньше растровой анимации и масштабируется без потери качества.
 */
@Composable
fun AnimatedLottieAvatar(
    lottieUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = ScAvatarSize.listItem,
    iterations: Int = Int.MAX_VALUE,
    fallbackInitials: String = "?",
) {
    if (lottieUrl.isNullOrBlank()) {
        Box(
            modifier.size(size).clip(CircleShape).background(ScTheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Initials(fallbackInitials, size) }
        return
    }

    val composition by rememberLottieComposition(LottieCompositionSpec.Url(lottieUrl))

    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                iterations = iterations,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Initials(fallbackInitials, size)
        }
    }
}

/**
 * Вращающееся градиентное кольцо — «живой» аватар без внешних ресурсов.
 * Дешёвая альтернатива видео-аватарке для слабых устройств:
 * не требует сети и декодера, только один invalidate кадра.
 */
@Composable
fun AnimatedGradientRing(
    modifier: Modifier = Modifier,
    size: Dp = ScAvatarSize.profileHero,
    colors: List<Color> = listOf(ScTheme.accent, ScTheme.premiumLight, ScTheme.accentLight),
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "ringRotation")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .rotate(if (enabled) angle else 0f)
                .clip(CircleShape)
                .background(Brush.sweepGradient(colors)),
        )
        Box(Modifier.padding(3.dp).clip(CircleShape)) { content() }
    }
}

private fun onlineDotSize(avatarSize: Dp): Dp =
    (avatarSize.value * 0.26f).dp.coerceAtLeast(9.dp)

private fun onlineBorderSize(avatarSize: Dp): Dp = if (avatarSize > 40.dp) 2.5.dp else 1.8.dp

/**
 * Аватар в трейе сторис: кольцо + счётчик непросмотренных.
 *
 * Клик обрабатывает вызывающий код (в трейе ячейка кликабельна целиком,
 * вместе с подписью), поэтому здесь только визуал.
 */
@Composable
fun StoryTrayAvatar(
    user: User,
    stories: List<com.silverchat.core.model.Story>,
    modifier: Modifier = Modifier,
    size: Dp = ScAvatarSize.tray,
    allowAnimation: Boolean = true,
) {
    val unseen = stories.any { !it.seenByMe }
    UserAvatar(
        user = user,
        modifier = modifier,
        size = size,
        showOnline = false,
        showStoryRing = true,
        storySeen = !unseen,
        allowAnimation = allowAnimation,
    )
}
