package com.silverchat.feature.calls.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.silverchat.core.designsystem.navigation.CallsNavigator
import com.silverchat.core.designsystem.navigation.FeatureNavigation
import com.silverchat.core.designsystem.navigation.NavDeepLinkSpec
import com.silverchat.core.designsystem.navigation.RouteBuilder
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.feature.calls.screen.CallHistoryScreen
import com.silverchat.feature.calls.screen.CallScreen

/**
 * Граф фичи «Звонки».
 *
 * Звонки не являются вкладкой нижней навигации: экран открывается из чата,
 * из уведомления о входящем и по deep link `silverchat://call/{callId}`.
 *
 * Deep link критичен именно для входящего звонка: FCM-уведомление содержит
 * ссылку, и тап по нему должен открыть полноэкранный звонок даже когда
 * приложение было убито. Без deep link пришлось бы поднимать приложение
 * и вручную искать активную сессию.
 *
 * `video` — необязательный аргумент с `defaultValue = false`: звонок из
 * уведомления приходит без него и открывается как голосовой, а решение
 * «включить камеру» принимает уже [com.silverchat.core.webrtc.CallController]
 * по типу сессии, пришедшему с сервера.
 */
class CallsNavigation : FeatureNavigation {

    override val route: String = Routes.CALL

    override fun deepLinks(): List<NavDeepLinkSpec> = listOf(
        NavDeepLinkSpec(uriPattern = "silverchat://call/{callId}"),
    )

    override fun NavGraphBuilder.registerGraph(navController: NavHostController) {
        val navigator: CallsNavigator = NavControllerCallsNavigator(navController)

        // ── Полноэкранный звонок ────────────────────────────────────────
        composable(
            route = Routes.CALL,
            arguments = listOf(
                navArgument(Routes.Args.CALL_ID) { type = NavType.StringType },
                navArgument(Routes.Args.CALL_VIDEO) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
            deepLinks = listOf(
                androidx.navigation.navDeepLink { uriPattern = "silverchat://call/{callId}" },
            ),
        ) {
            CallScreen(navigator = navigator)
        }

        // ── История звонков ─────────────────────────────────────────────
        composable(Routes.CALL_HISTORY) {
            CallHistoryScreen(navigator = navigator)
        }
    }
}

internal class NavControllerCallsNavigator(
    private val navController: NavController,
) : CallsNavigator {

    override fun openCall(callId: String) =
        navController.navigateSafely(RouteBuilder.call(callId))

    override fun openChat(chatId: String) =
        navController.navigateSafely(RouteBuilder.chatThread(chatId))

    override fun openProfile(userId: String) =
        navController.navigateSafely(RouteBuilder.profileUser(userId))

    override fun back() {
        // popBackStack возвращает false при пустом стеке (звонок из уведомления
        // при убитом приложении) — тогда уходим в список чатов, а не в никуда
        if (!navController.popBackStack()) {
            navController.navigate(Routes.CHATS) { launchSingleTop = true }
        }
    }

    /** Защита от двойного тапа: два быстрых нажатия не должны плодить экраны. */
    private fun navigateSafely(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }
}
