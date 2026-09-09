package com.silverchat.core.designsystem.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType

/**
 * Контракты навигации между модулями.
 *
 * Проблема многомодульного Compose-приложения: если :app импортирует экраны
 * всех фич напрямую, а фичи импортируют маршруты друг друга, граф модулей
 * становится циклическим. Решение — каждая фича реализует интерфейс
 * [FeatureNavigation], объявленный ЗДЕСЬ (в :core:designsystem), а :app
 * просто собирает список реализаций через Hilt multibinding.
 *
 * Следствие:
 *  - фичи не знают друг о друге (только о строковых маршрутах);
 *  - удаление фичи = удаление одной реализации, правки в :app не нужны;
 *  - deep link и аргументы объявлены рядом с графом фичи, а не в трёх местах.
 */
interface FeatureNavigation {

    /** Уникальный маршрут корня графа этой фичи. */
    val route: String

    /** Вкладка нижней навигации, если фича является top-level (иначе null). */
    val topLevelDestination: TopLevelDestination? get() = null

    /** Регистрация навигационного графа фичи. */
    fun NavGraphBuilder.registerGraph(navController: NavHostController)

    /** Deep links, которые обрабатывает эта фича. */
    fun deepLinks(): List<NavDeepLinkSpec> = emptyList()
}

/** Спецификация deep link (серебро: silverchat://user/alina). */
data class NavDeepLinkSpec(
    val uriPattern: String,
    val arguments: List<DeepLinkArg> = emptyList(),
)

data class DeepLinkArg(val name: String, val type: NavType<*>)

/* =========================================================================
   РЕЕСТР МАРШРУТОВ
   ------------------------------------------------------------------------
   Все строковые маршруты приложения — в одном месте. Никаких магических
   строк в фичах: опечатка в маршруте ловится компилятором, а не в проде.
   ========================================================================= */
object Routes {

    /* Top-level */
    const val CHATS = "chats"
    const val MARKET = "market"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"

    /* Чаты */
    const val CHAT_THREAD = "chats/{chatId}"
    const val CHAT_INFO = "chats/{chatId}/info"
    const val CHAT_MEMBERS = "chats/{chatId}/members"
    const val CHAT_INVITES = "chats/{chatId}/invites"
    const val CHAT_MEDIA = "chats/{chatId}/media"
    const val SEARCH = "search"
    const val NEW_GROUP = "chats/new/group"
    const val NEW_CHANNEL = "chats/new/channel"
    const val FORWARDED_PICKER = "chats/forward"

    /* Сторис */
    const val STORIES_FEED = "stories/feed"
    const val STORY_VIEWER = "stories/viewer/{authorId}/{index}"
    const val STORY_CREATE = "stories/create"

    /* Звонки. `video` — необязательный флаг: звонок стартует как голосовой,
       если параметр не передан (например, при открытии из уведомления). */
    const val CALL = "calls/{callId}?video={video}"

    /** История звонков: пропущенные, исходящие, длительность. */
    const val CALL_HISTORY = "calls/history"

    /* Маркет */
    const val MARKET_USERNAME_DETAIL = "market/usernames/{listingId}"
    const val MARKET_SELL = "market/sell"
    const val MARKET_GIFTS = "market/gifts"
    const val MARKET_GIFT_SEND = "market/gifts/{giftId}/send"
    const val MARKET_PREMIUM = "market/premium"
    const val MARKET_WALLET = "market/wallet"
    const val MARKET_OFFERS = "market/usernames/{listingId}/offers"

    /* Профиль */
    const val PROFILE_SELF = "profile/me"
    const val PROFILE_USER = "profile/{userId}"
    const val PROFILE_EDIT = "profile/edit"
    const val PROFILE_AVATAR = "profile/avatar"
    const val PROFILE_BANNER = "profile/banner"
    const val PROFILE_LOCATION = "profile/location"
    const val PROFILE_HOURS = "profile/hours"
    const val PROFILE_STORIES_ARCHIVE = "profile/stories"

    /* Настройки */
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_THEMES = "settings/themes"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_DATA = "settings/data"
    const val SETTINGS_DEVICES = "settings/devices"
    const val SETTINGS_LANGUAGE = "settings/language"
    const val SETTINGS_APP_LOCK = "settings/app-lock"

    /* Админка (@silver) */
    const val ADMIN = "admin"
    const val ADMIN_USERS = "admin/users"
    const val ADMIN_USER_DETAIL = "admin/users/{userId}"
    const val ADMIN_MARKET = "admin/market"
    const val ADMIN_REPORTS = "admin/reports"
    const val ADMIN_AUDIT = "admin/audit"
    const val ADMIN_BROADCAST = "admin/broadcast"

    /* Авторизация */
    const val AUTH_PHONE = "auth/phone"
    const val AUTH_OTP = "auth/otp/{phone}"
    const val AUTH_REGISTER = "auth/register"

    /* Аргументы */
    object Args {
        const val CHAT_ID = "chatId"
        const val USER_ID = "userId"
        const val MESSAGE_ID = "messageId"
        const val CALL_ID = "callId"

        /** Флаг видеозвонка в маршруте `calls/{callId}?video={video}`. */
        const val CALL_VIDEO = "video"
        const val LISTING_ID = "listingId"
        const val GIFT_ID = "giftId"
        const val STORY_AUTHOR_ID = "authorId"
        const val STORY_INDEX = "index"
        const val PHONE = "phone"
    }
}

/* =========================================================================
   НАВИГАЦИОННЫЕ ДЕЙСТВИЯ
   ------------------------------------------------------------------------
   Фичи НЕ вызывают navController.navigate("строка") напрямую. Они получают
   эти интерфейсы через конструктор ViewModel/экрана, поэтому:
    - навигация тестируется (можно подсунуть fake и проверить переходы);
    - фича не зависит от NavHostController и не знает про :app.
   ========================================================================= */

/** Навигация внутри фичи чатов. */
interface ChatsNavigator {
    fun openChat(chatId: String)
    fun openChatInfo(chatId: String)
    fun openChatMembers(chatId: String)
    fun openChatInvites(chatId: String)
    fun openChatMedia(chatId: String)
    fun openSearch()
    fun openNewGroup()
    fun openNewChannel()
    fun openStoryViewer(authorId: String, index: Int = 0)
    fun openStoryCreate()
    fun openProfile(userId: String)
    fun startCall(chatId: String, video: Boolean)
    fun forwardMessages(messageIds: List<String>)
    fun back()
}

/** Навигация сторис. */
interface StoriesNavigator {
    fun openViewer(authorId: String, index: Int = 0)
    fun openCreate()
    fun openProfile(userId: String)
    fun openChat(chatId: String)
    fun back()
}

/** Навигация звонков. */
interface CallsNavigator {
    fun openCall(callId: String)
    fun openChat(chatId: String)
    fun openProfile(userId: String)
    fun back()
}

/** Навигация маркета. */
interface MarketNavigator {
    fun openListing(listingId: String)
    fun openOffers(listingId: String)
    fun openSell()
    fun openGifts()
    fun openGiftSend(giftId: String)
    fun openPremium()
    fun openWallet()
    fun openProfile(userId: String)
    fun back()
}

/** Навигация настроек. */
interface SettingsNavigator {
    fun openAppearance()
    fun openThemes()
    fun openPrivacy()
    fun openNotifications()
    fun openDataAndStorage()
    fun openDevices()
    fun openLanguage()
    fun openAppLock()
    /** Открывается только при наличии админ-прав. */
    fun openAdminPanel()
    fun openProfile(userId: String)
    fun back()
}

/** Навигация профиля. */
interface ProfileNavigator {
    fun openEditProfile()
    fun openAvatarEditor()
    fun openBannerEditor()
    fun openLocationEditor()
    fun openWorkingHoursEditor()
    fun openStoriesArchive()
    fun openChat(chatId: String)
    fun startCall(chatId: String, video: Boolean)
    fun openPremium()
    fun openMarket()
    fun back()
}

/** Навигация админ-панели. */
interface AdminNavigator {
    fun openUsers()
    fun openUserDetail(userId: String)
    fun openMarketTools()
    fun openReports()
    fun openAuditLog()
    fun openBroadcast()
    fun openProfile(userId: String)
    fun back()
}
