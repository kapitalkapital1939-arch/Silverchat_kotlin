package com.silverchat.core.designsystem.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Вкладки нижней навигации (Telegram-style).
 *
 * Ровно четыре, как в требованиях: Чаты, Маркет, Настройки, Профиль.
 *
 * [route] совпадает с графом навигации :app — это единственный способ
 * сохранить state вкладки при переключении (см. saveState/restoreState
 * в [com.silverchat.core.designsystem.navigation.SilverChatBottomBar]).
 *
 * Админ-панель (@silver) намеренно НЕ вынесена во вкладку:
 * она доступна из «Настроек» и только при наличии прав, поэтому
 * в список табов не попадает.
 */
enum class TopLevelDestination(
    val route: String,
    val labelRu: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String,
) {
    CHATS(
        route = "chats",
        labelRu = "Чаты",
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        contentDescription = "Список чатов и каналов",
    ),
    MARKET(
        route = "market",
        labelRu = "Маркет",
        selectedIcon = Icons.Filled.Storefront,
        unselectedIcon = Icons.Outlined.Storefront,
        contentDescription = "Маркет юзернеймов, подарки и Premium",
    ),
    SETTINGS(
        route = "settings",
        labelRu = "Настройки",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        contentDescription = "Настройки приложения, темы и приватность",
    ),
    PROFILE(
        route = "profile",
        labelRu = "Профиль",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.PersonOutline,
        contentDescription = "Ваш профиль и карточка пользователя",
    ),
    ;

    companion object {
        /** Вкладка по умолчанию при запуске приложения. */
        val START = CHATS

        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }

        /** Маршруты, на которых FAB «создать чат» имеет смысл. */
        fun showFab(destination: TopLevelDestination): Boolean = when (destination) {
            CHATS -> true
            MARKET -> false
            SETTINGS -> false
            PROFILE -> true
        }
    }
}
