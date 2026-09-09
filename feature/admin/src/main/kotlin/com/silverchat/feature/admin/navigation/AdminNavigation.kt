package com.silverchat.feature.admin.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.silverchat.core.designsystem.navigation.AdminNavigator
import com.silverchat.core.designsystem.navigation.FeatureNavigation
import com.silverchat.core.designsystem.navigation.RouteBuilder
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.designsystem.navigation.TopLevelDestination
import com.silverchat.feature.admin.screen.AdminAuditScreen
import com.silverchat.feature.admin.screen.AdminBroadcastScreen
import com.silverchat.feature.admin.screen.AdminDashboardScreen
import com.silverchat.feature.admin.screen.AdminMarketToolsScreen
import com.silverchat.feature.admin.screen.AdminReportsScreen
import com.silverchat.feature.admin.screen.AdminUserDetailScreen
import com.silverchat.feature.admin.screen.AdminUsersScreen

/**
 * Граф админ-панели `@silver`.
 *
 * Админка — не вкладка нижней навигации, поэтому [topLevelDestination]
 * отсутствует: раздел открывается из настроек и существует как отдельная
 * ветка стека.
 *
 * ПРО БЕЗОПАСНОСТЬ. Регистрация графа не означает доступ: каждый экран
 * самостоятельно проверяет [com.silverchat.core.domain.repository.AdminAccess]
 * и рисует `AdminAccessLocked` при отсутствии прав. Это второй уровень
 * клиентской защиты (первый — скрытая ссылка в настройках), и оба существуют
 * только ради UX. Источник истины — бэкенд: каждый эндпоинт закрыт
 * middleware `AdminGuard`, а каждое действие пишется в неизменяемый
 * аудит-лог. Подмена ответа `observeAccess()` не даст прав на сервере.
 */
class AdminNavigation : FeatureNavigation {

    override val route: String = Routes.ADMIN

    /** Админка не является вкладкой нижней навигации. */
    override val topLevelDestination: TopLevelDestination? = null

    override fun NavGraphBuilder.registerGraph(navController: NavHostController) {
        val navigator: AdminNavigator = NavControllerAdminNavigator(navController)

        // ── Дашборд ─────────────────────────────────────────────────────
        composable(Routes.ADMIN) {
            AdminDashboardScreen(navigator = navigator)
        }

        // ── Поиск пользователей ─────────────────────────────────────────
        composable(Routes.ADMIN_USERS) {
            AdminUsersScreen(navigator = navigator)
        }

        // ── Карточка пользователя ───────────────────────────────────────
        composable(
            route = Routes.ADMIN_USER_DETAIL,
            arguments = listOf(navArgument(Routes.Args.USER_ID) { type = NavType.StringType }),
        ) { entry ->
            val userId = entry.arguments?.getString(Routes.Args.USER_ID).orEmpty()
            AdminUserDetailScreen(
                userId = userId,
                navigator = navigator,
            )
        }

        // ── Инструменты маркета ─────────────────────────────────────────
        composable(Routes.ADMIN_MARKET) {
            AdminMarketToolsScreen(navigator = navigator)
        }

        // ── Жалобы ──────────────────────────────────────────────────────
        composable(Routes.ADMIN_REPORTS) {
            AdminReportsScreen(navigator = navigator)
        }

        // ── Аудит-лог ───────────────────────────────────────────────────
        composable(Routes.ADMIN_AUDIT) {
            AdminAuditScreen(navigator = navigator)
        }

        // ── Рассылка ────────────────────────────────────────────────────
        composable(Routes.ADMIN_BROADCAST) {
            AdminBroadcastScreen(navigator = navigator)
        }
    }
}

internal class NavControllerAdminNavigator(
    private val navController: NavController,
) : AdminNavigator {

    override fun openUsers() = navController.navigateSafely(Routes.ADMIN_USERS)

    override fun openUserDetail(userId: String) =
        navController.navigateSafely(RouteBuilder.adminUserDetail(userId))

    override fun openMarketTools() = navController.navigateSafely(Routes.ADMIN_MARKET)

    override fun openReports() = navController.navigateSafely(Routes.ADMIN_REPORTS)

    override fun openAuditLog() = navController.navigateSafely(Routes.ADMIN_AUDIT)

    override fun openBroadcast() = navController.navigateSafely(Routes.ADMIN_BROADCAST)

    override fun openProfile(userId: String) =
        navController.navigateSafely(RouteBuilder.profileUser(userId))

    override fun back() {
        // Корень ветки админки: при пустом стеке возвращаемся на дашборд,
        // а не выходим из приложения
        if (!navController.popBackStack()) {
            navController.navigate(Routes.ADMIN) {
                popUpTo(Routes.ADMIN) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
}

/** Защита от двойного тапа по разделу. */
private fun NavController.navigateSafely(route: String) {
    navigate(route) { launchSingleTop = true }
}
