/*
 * :core:designsystem — вся визуальная система SilverChat.
 *
 * Здесь живут: темы (8 палитр + light/dark + dynamic color), эффект стекла,
 * нижняя навигация, аватарки/баннеры, пузыри сообщений, реакции, карточки
 * маркета и админки.
 *
 * Правило: фичи НЕ пишут свои цвета и отступы — только берут токены отсюда.
 * Иначе темы перестают переключаться согласованно.
 */
plugins {
    id("silverchat.android.compose")
    id("silverchat.android.hilt")
}

android {
    namespace = "com.silverchat.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":core:domain"))

    // Backdrop-blur для glassmorphism (RenderEffect на API 31+, scrim ниже)
    api(libs.haze)
    api(libs.haze.materials)

    // Анимированные аватарки/баннеры и стикеры (Lottie JSON)
    api(libs.lottie.compose)

    // Изображения, GIF, видео-превью
    api(libs.bundles.coil)

    // Shimmer-скелетоны при загрузке
    api(libs.shimmer)

    api(libs.androidx.core.ktx)
    api(libs.androidx.core.splashscreen)
    api(libs.bundles.serialization)
    api(libs.timber)

    debugApi(libs.androidx.compose.ui.tooling)
}
