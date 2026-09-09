package com.silverchat.feature.chats.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.silverchat.core.designsystem.glass.GlassState
import com.silverchat.core.designsystem.navigation.ChatsNavigator
import com.silverchat.core.designsystem.navigation.FeatureNavigation
import com.silverchat.core.designsystem.navigation.NavDeepLinkSpec
import com.silverchat.core.designsystem.navigation.RouteBuilder
import com.silverchat.core.designsystem.navigation.Routes
import com.silverchat.core.designsystem.navigation.TopLevelDestination
import com.silverchat.core.model.Message
import com.silverchat.feature.chats.ChatInfoTab
import com.silverchat.feature.chats.ChatListViewModel
import com.silverchat.feature.chats.ChatThreadViewModel
import com.silverchat.feature.chats.NewChatMode
import com.silverchat.feature.chats.screen.ChatInfoScreen
import com.silverchat.feature.chats.screen.ChatListScreen
import com.silverchat.feature.chats.screen.ChatThreadScreen
import com.silverchat.feature.chats.screen.NewChatScreen
import com.silverchat.feature.chats.screen.SearchScreen

/**
 * Граф фичи «Чаты».
 *
 * Реализует [FeatureNavigation], поэтому :app подключает её одной строкой
 * и не знает ни об одном конкретном экране фичи.
 *
 * Deep links:
 *  - `silverchat://c/{username}` — открыть канал/чат по публичному нику;
 *  - `silverchat://join/{token}` — вход по защищённой пригласительной ссылке.
 *    Именно deep link, а не сканирование QR: ссылка должна открываться
 *    из браузера и из другого мессенджера.
 */
class ChatsNavigation : FeatureNavigation {

    override val route: String = Routes.CHATS

    override val topLevelDestination: TopLevelDestination = TopLevelDestination.CHATS

    override fun deepLinks(): List<NavDeepLinkSpec> = listOf(
        NavDeepLinkSpec(uriPattern = "silverchat://c/{username}"),
        NavDeepLinkSpec(uriPattern = "silverchat://join/{token}"),
    )

    override fun NavGraphBuilder.registerGraph(navController: NavHostController) {

        // registerGraph не является @Composable, поэтому remember здесь
        // недоступен. Навигатор — лёгкая обёртка над NavController, её
        // создание на каждый вызов графа ничего не стоит.
        val navigator: ChatsNavigator = NavControllerChatsNavigator(navController)

        // ── Список чатов (вкладка) ──────────────────────────────────────
        composable(Routes.CHATS) {
            val viewModel: ChatListViewModel = hiltViewModel()
            val glassState = remember { GlassState() }
            ChatListScreen(
                viewModel = viewModel,
                navigator = navigator,
                glassState = glassState,
                onOpenSearch = navigator::openSearch,
                onLongPressChat = { chat, actions ->
                    // Меню строки показывает :app (общий BottomSheet),
                    // чтобы не дублировать его реализацию в каждой фиче
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_CHAT_MENU, chat.id.raw to actions.map { it.name })
                },
            )
        }

        // ── Диалог ──────────────────────────────────────────────────────
        composable(
            route = Routes.CHAT_THREAD,
            arguments = listOf(navArgument(Routes.Args.CHAT_ID) { type = NavType.StringType }),
            deepLinks = listOf(
                androidx.navigation.navDeepLink { uriPattern = "silverchat://c/{username}" },
            ),
        ) {
            val viewModel: ChatThreadViewModel = hiltViewModel()
            val glassState = remember { GlassState() }
            ChatThreadScreen(
                viewModel = viewModel,
                navigator = navigator,
                glassState = glassState,
                onOpenMessageMenu = { message: Message ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_MESSAGE_MENU, message.id.raw)
                },
            )
        }

        // ── Информация о чате / канале ──────────────────────────────────
        composable(
            route = Routes.CHAT_INFO,
            arguments = listOf(navArgument(Routes.Args.CHAT_ID) { type = NavType.StringType }),
        ) {
            ChatInfoScreen(
                chatId = it.arguments?.getString(Routes.Args.CHAT_ID).orEmpty(),
                navigator = navigator,
                onOpenMembers = { chatId -> navigator.openChatMembers(chatId) },
                onOpenInvites = { chatId -> navigator.openChatInvites(chatId) },
                onOpenMedia = { chatId -> navigator.openChatMedia(chatId) },
            )
        }

        // ── Участники ───────────────────────────────────────────────────
        composable(
            route = Routes.CHAT_MEMBERS,
            arguments = listOf(navArgument(Routes.Args.CHAT_ID) { type = NavType.StringType }),
        ) {
            ChatInfoScreen(
                chatId = it.arguments?.getString(Routes.Args.CHAT_ID).orEmpty(),
                navigator = navigator,
                initialTab = ChatInfoTab.MEMBERS,
                onOpenMembers = {},
                onOpenInvites = {},
                onOpenMedia = {},
            )
        }

        // ── Приглашения ─────────────────────────────────────────────────
        composable(
            route = Routes.CHAT_INVITES,
            arguments = listOf(navArgument(Routes.Args.CHAT_ID) { type = NavType.StringType }),
            deepLinks = listOf(
                androidx.navigation.navDeepLink { uriPattern = "silverchat://join/{token}" },
            ),
        ) {
            ChatInfoScreen(
                chatId = it.arguments?.getString(Routes.Args.CHAT_ID).orEmpty(),
                navigator = navigator,
                initialTab = ChatInfoTab.INVITES,
                onOpenMembers = {},
                onOpenInvites = {},
                onOpenMedia = {},
            )
        }

        // ── Общие медиа ─────────────────────────────────────────────────
        composable(
            route = Routes.CHAT_MEDIA,
            arguments = listOf(navArgument(Routes.Args.CHAT_ID) { type = NavType.StringType }),
        ) {
            ChatInfoScreen(
                chatId = it.arguments?.getString(Routes.Args.CHAT_ID).orEmpty(),
                navigator = navigator,
                initialTab = ChatInfoTab.MEDIA,
                onOpenMembers = {},
                onOpenInvites = {},
                onOpenMedia = {},
            )
        }

        // ── Поиск ───────────────────────────────────────────────────────
        composable(Routes.SEARCH) {
            SearchScreen(
                navigator = navigator,
                onBack = navigator::back,
            )
        }

        // ── Новая группа / канал ────────────────────────────────────────
        composable(Routes.NEW_GROUP) {
            NewChatScreen(isChannel = false, navigator = navigator)
        }
        composable(Routes.NEW_CHANNEL) {
            NewChatScreen(isChannel = true, navigator = navigator)
        }

        // ── Выбор чата для пересылки ────────────────────────────────────
        composable(Routes.FORWARDED_PICKER) {
            NewChatScreen(
                isChannel = false,
                navigator = navigator,
                mode = NewChatMode.FORWARD_PICKER,
            )
        }
    }

    companion object {
        /** Ключи savedStateHandle для обмена с общим меню в :app. */
        const val KEY_CHAT_MENU = "chats_menu_target"
        const val KEY_MESSAGE_MENU = "chats_message_menu_target"
    }
}

/**
 * Реализация [ChatsNavigator] поверх NavController.
 *
 * Отдельный класс, а не лямбды в каждом экране: фича не зависит от типа
 * NavController в сигнатурах композаблов, поэтому экраны тестируются
 * с фейковым навигатором без Robolectric.
 */
internal class NavControllerChatsNavigator(
    private val navController: NavController,
) : ChatsNavigator {

    override fun openChat(chatId: String) = navController.navigateSafely(RouteBuilder.chatThread(chatId))

    override fun openChatInfo(chatId: String) = navController.navigateSafely(RouteBuilder.chatInfo(chatId))

    override fun openChatMembers(chatId: String) = navController.navigateSafely(RouteBuilder.chatMembers(chatId))

    override fun openChatInvites(chatId: String) = navController.navigateSafely(RouteBuilder.chatInvites(chatId))

    override fun openChatMedia(chatId: String) = navController.navigateSafely(RouteBuilder.chatMedia(chatId))

    override fun openSearch() = navController.navigateSafely(Routes.SEARCH)

    override fun openNewGroup() = navController.navigateSafely(Routes.NEW_GROUP)

    override fun openNewChannel() = navController.navigateSafely(Routes.NEW_CHANNEL)

    override fun openStoryViewer(authorId: String, index: Int) =
        navController.navigateSafely(RouteBuilder.storyViewer(authorId, index))

    override fun openStoryCreate() = navController.navigateSafely(Routes.STORY_CREATE)

    override fun openProfile(userId: String) = navController.navigateSafely(RouteBuilder.profileUser(userId))

    override fun startCall(chatId: String, video: Boolean) =
        navController.navigateSafely(RouteBuilder.call(chatId, video))

    override fun forwardMessages(messageIds: List<String>) {
        // ID передаются одним параметром через запятую: список в маршруте
        // нельзя представить как navArgument-массив без сериализации
        val encoded = messageIds.joinToString(FORWARD_SEPARATOR)
        navController.navigateSafely("${Routes.FORWARDED_PICKER}?ids=$encoded")
    }

    override fun back() {
        // popBackStack возвращает false, если стек пуст — тогда выходим из приложения
        if (!navController.popBackStack()) {
            navController.navigate(Routes.CHATS) {
                popUpTo(Routes.CHATS) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    /**
     * Навигация с защитой от двойного тапа.
     *
     * Без `launchSingleTop` два быстрых тапа по строке чата открывают
     * два экземпляра экрана — классический баг Compose-навигации.
     */
    private fun navigateSafely(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }

    private companion object {
        const val FORWARD_SEPARATOR = ","
    }
}
