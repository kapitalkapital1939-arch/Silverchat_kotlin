/*
 * :app — точка сборки SilverChat.
 *
 * Единственный модуль, который знает обо всех остальных. Его ответственность:
 *  1. собрать DI-граф (реализации портов, которые могут жить только здесь);
 *  2. построить NavHost из реализаций FeatureNavigation, пришедших из фич;
 *  3. предоставить Activity-зависимые компоненты: выбор медиа, камеру,
 *     обработку deep link и PendingIntent'ов уведомлений;
 *  4. удержать жизненный цикл сессии: подключение WebSocket при входе,
 *     отключение при выходе.
 *
 * Бизнес-логики здесь НЕТ. Экранов — тоже: они во фичах. Если в :app
 * появляется `if (user.isPremium)`, это признак того, что код уехал
 * не в свой модуль.
 */
plugins {
    id("silverchat.android.application")
    id("silverchat.android.compose")
    id("silverchat.android.hilt")
}

android {
    /* Пакет зафиксирован и НЕ может быть переименован:
     * :core:notifications резолвит MainActivity по имени через
     * Class.forName("com.silverchat.app.MainActivity") — иначе пришлось бы
     * зависеть от :app и получить цикл в графе модулей. */
    namespace = "com.silverchat.app"

    defaultConfig {
        applicationId = "com.silverchat.app"
    }

    /* Compose BOM, activity-compose, navigation-compose, lifecycle и
     * hilt-navigation-compose добавляет convention-плагины
     * silverchat.android.compose — дублировать их здесь не нужно. */
}

dependencies {
    /* ── Ядро ──────────────────────────────────────────────────────────────
     * `api` для model/common/domain: их типы попадают в публичные сигнатуры
     * компонентов :app (MediaCaptureGateway оперирует LocalMedia из :core:domain),
     * поэтому они должны быть видны на compile-classpath. */
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":core:domain"))

    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:security"))
    implementation(project(":core:media"))
    implementation(project(":core:notifications"))
    implementation(project(":core:webrtc"))

    /* ── Фичи ──────────────────────────────────────────────────────────────
     * :app знает только классы *Navigation из каждой фичи. Экраны и ViewModel
     * он не импортирует: граф собирается через FeatureNavigation, поэтому
     * удаление фичи — это удаление одной строки ниже. */
    implementation(project(":feature:auth"))
    implementation(project(":feature:chats"))
    implementation(project(":feature:stories"))
    implementation(project(":feature:calls"))
    implementation(project(":feature:market"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:admin"))

    /* ── Платформа ─────────────────────────────────────────────────────── */
    implementation(libs.androidx.core.ktx)
    // SplashScreen -> Compose без «белого кадра» между иконкой и первым экраном
    implementation(libs.androidx.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(project(":core:testing"))
}
