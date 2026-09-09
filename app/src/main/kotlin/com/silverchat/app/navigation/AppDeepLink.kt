package com.silverchat.app.navigation

import android.net.Uri

/**
 * Запрос на переход, пришедший в `MainActivity` извне.
 *
 * Два вида, потому что источники разные:
 *
 *  • [UriLink] — публичная ссылка `silverchat://…` из браузера, другого
 *    мессенджера или QR-кода. Конкретный шаблон сопоставляет `NavController`
 *    по объявлениям `navDeepLink` внутри графов фич, поэтому :app не знает,
 *    какой экран её обработает.
 *
 *  • [Route] — тап по уведомлению. Там известен не публичный URI, а цель
 *    внутри приложения (чат, звонок, маркет, админка), поэтому сразу
 *    строится маршрут через `RouteBuilder`.
 *
 * Отдельный тип вместо «просто строки» нужен, чтобы эти два случая нельзя
 * было перепутать: `navigate(String)` и `navigate(Uri)` в NavController —
 * разные методы с разной семантикой сопоставления.
 */
sealed interface AppDeepLink {

    /** Переход по маршруту приложения. */
    data class Route(val route: String) : AppDeepLink

    /** Переход по публичной ссылке. */
    data class UriLink(val uri: Uri) : AppDeepLink
}
