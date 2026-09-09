package com.silverchat.feature.stories.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.silverchat.core.designsystem.navigation.FeatureNavigation
import com.silverchat.core.designsystem.navigation.NavDeepLinkSpec
import com.silverchat.core.designsystem.navigation.RouteBuilder
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.designsystem.navigation.StoriesNavigator
import com.silverchat.feature.stories.screen.StoryPublishScreen
import com.silverchat.feature.stories.screen.StoryViewerScreen

/**
 * Граф фичи «Сторис».
 *
 * Сторис не являются вкладкой нижней навигации: они живут в трее над списком
 * чатов, поэтому [topLevelDestination] не переопределён (остаётся `null`).
 *
 * Просмотрщик принимает `authorId` и стартовый `index`. Переход к соседнему
 * автору выполняется подменой этих аргументов через `replace`, а не стеком
 * маршрутов: иначе просмотр десяти авторов оставил бы десять записей в
 * back stack, и кнопка «назад» листала бы их по одной.
 *
 * Deep link `silverchat://story/{authorId}` открывает сторис конкретного
 * автора извне — например, из пуш-уведомления.
 */
class StoriesNavigation : FeatureNavigation {

    override val route: String = Routes.STORIES_FEED

    override fun deepLinks(): List<NavDeepLinkSpec> = listOf(
        NavDeepLinkSpec(uriPattern = "silverchat://story/{authorId}"),
    )

    override fun NavGraphBuilder.registerGraph(navController: NavHostController) {
        val navigator: StoriesNavigator = NavControllerStoriesNavigator(navController)

        // ── Просмотрщик ─────────────────────────────────────────────────
        composable(
            route = Routes.STORY_VIEWER,
            arguments = listOf(
                navArgument(Routes.Args.STORY_AUTHOR_ID) { type = NavType.StringType },
                navArgument(Routes.Args.STORY_INDEX) {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) {
            StoryViewerScreen(
                navigator = navigator,
                // Переход к соседнему автору — замена аргументов текущего
                // маршрута, чтобы не раздувать back stack
                onAuthorChange = { offset -> shiftAuthor(navController, offset) },
            )
        }

        // ── Публикация ──────────────────────────────────────────────────
        composable(Routes.STORY_CREATE) {
            StoryPublishScreen(navigator = navigator)
        }

        // ── Лента (архив собственных сторис) ────────────────────────────
        composable(Routes.STORIES_FEED) {
            StoriesFeedScreenHost(navigator = navigator)
        }
    }

    /**
     * Сдвиг к соседнему автору внутри просмотрщика.
     *
     * Читаем текущий стек, находим позицию автора и подменяем аргументы.
     * `launchSingleTop` обязателен: без него Compose создал бы второй
     * экземпляр экрана поверх текущего, и жест «назад» сломался бы.
     */
    private fun shiftAuthor(navController: NavController, offset: Int) {
        val entry = navController.currentBackStackEntry ?: return
        val current = entry.arguments?.getString(Routes.Args.STORY_AUTHOR_ID) ?: return
        val order = entry.savedStateHandle.get<List<String>>(KEY_AUTHOR_ORDER).orEmpty()
        val index = order.indexOf(current)
        if (index < 0) return

        val target = index + offset
        if (target !in order.indices) {
            navController.popBackStack()
            return
        }

        navController.navigate(RouteBuilder.storyViewer(order[target], 0)) {
            launchSingleTop = true
            // Предыдущего автора из стека убираем: возврат должен вести
            // в ленту, а не проходить всех просмотренных подряд
            popUpTo(Routes.STORY_VIEWER) { inclusive = true }
        }
    }

    companion object {
        /** Порядок авторов ленты — кладётся в savedStateHandle из :app. */
        const val KEY_AUTHOR_ORDER = "stories_author_order"
    }
}

internal class NavControllerStoriesNavigator(
    private val navController: NavController,
) : StoriesNavigator {

    override fun openViewer(authorId: String, index: Int) =
        navController.navigateSafely(RouteBuilder.storyViewer(authorId, index))

    override fun openCreate() = navController.navigateSafely(Routes.STORY_CREATE)

    override fun openProfile(userId: String) =
        navController.navigateSafely(RouteBuilder.profileUser(userId))

    override fun openChat(chatId: String) = navController.navigateSafely(RouteBuilder.chatThread(chatId))

    override fun back() {
        navController.popBackStack()
    }

    /** Защита от двойного тапа: два быстрых нажатия не должны плодить экраны. */
    private fun navigateSafely(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }
}
