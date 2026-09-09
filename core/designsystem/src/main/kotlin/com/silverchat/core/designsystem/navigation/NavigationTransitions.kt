package com.silverchat.core.designsystem.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * «Плавные UI-анимации» из требований: переходы между экранами.
 *
 * Правила:
 *  - переход на вложенный экран (чат, карточка профиля) — слайд справа;
 *  - возврат — слайд обратно, а не fade (иначе теряется ощущение стека);
 *  - полноэкранные слои (сторис, звонок) — fade + scale, они перекрывают
 *    весь UI и слайд там выглядит чужеродно;
 *  - при выключенной настройке анимаций длительность обнуляется.
 */
object NavigationTransitions {

    private const val DURATION_MS = 320
    private const val FULLSCREEN_DURATION_MS = 240

    /** Маршруты, которые открываются как полноэкранный слой. */
    private val FULLSCREEN_ROUTES = setOf(
        Routes.STORY_VIEWER,
        Routes.STORY_CREATE,
        Routes.CALL,
    )

    fun enterTransition(
        animationsEnabled: Boolean,
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        val duration = if (animationsEnabled) DURATION_MS else 0
        val target = targetState.destination.route.orEmpty()

        if (FULLSCREEN_ROUTES.any { target.startsWith(it) }) {
            fadeIn(tween(if (animationsEnabled) FULLSCREEN_DURATION_MS else 0))
        } else {
            slideInHorizontally(tween(duration)) { width -> width } + fadeIn(tween(duration))
        }
    }

    fun exitTransition(
        animationsEnabled: Boolean,
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        val duration = if (animationsEnabled) DURATION_MS else 0
        fadeOut(tween(duration / 2))
    }

    fun popEnterTransition(
        animationsEnabled: Boolean,
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        val duration = if (animationsEnabled) DURATION_MS else 0
        fadeIn(tween(duration))
    }

    fun popExitTransition(
        animationsEnabled: Boolean,
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        val duration = if (animationsEnabled) DURATION_MS else 0
        slideOutHorizontally(tween(duration)) { width -> width } + fadeOut(tween(duration))
    }

    fun sizeTransform(animationsEnabled: Boolean): SizeTransform? =
        if (animationsEnabled) SizeTransform(clip = false) else null
}

/** Хелперы построения маршрутов — чтобы аргументы не собирали строками вручную. */
object RouteBuilder {

    fun chatThread(chatId: String) = "chats/$chatId"
    fun chatInfo(chatId: String) = "chats/$chatId/info"
    fun chatMembers(chatId: String) = "chats/$chatId/members"
    fun chatInvites(chatId: String) = "chats/$chatId/invites"
    fun chatMedia(chatId: String) = "chats/$chatId/media"
    fun storyViewer(authorId: String, index: Int) = "stories/viewer/$authorId/$index"
    fun call(callId: String, video: Boolean = false) = "calls/$callId?video=$video"
    fun marketListing(listingId: String) = "market/usernames/$listingId"
    fun marketOffers(listingId: String) = "market/usernames/$listingId/offers"
    fun giftSend(giftId: String) = "market/gifts/$giftId/send"
    fun profileUser(userId: String) = "profile/$userId"
    fun adminUserDetail(userId: String) = "admin/users/$userId"
    fun authOtp(phone: String) = "auth/otp/${phone.replace("+", "")}"

    /** Deep link на пользователя: silverchat://u/alina */
    fun userDeepLink(username: String) = "silverchat://u/$username"

    /** Deep link на канал/чат: silverchat://c/silverchat */
    fun chatDeepLink(username: String) = "silverchat://c/$username"

    /** Deep link на приглашение: silverchat://join/{token} */
    fun joinDeepLink(token: String) = "silverchat://join/$token"

    /** Deep link на лот маркета: silverchat://market/{listingId} */
    fun marketDeepLink(listingId: String) = "silverchat://market/$listingId"

    /** Deep link на звонок из уведомления: silverchat://call/{callId} */
    fun callDeepLink(callId: String) = "silverchat://call/$callId"
}
