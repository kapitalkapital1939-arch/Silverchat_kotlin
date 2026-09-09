/*
 * :core:testing — общая тестовая инфраструктура.
 *
 * Один набор test-двойников на все модули: без него каждая фича пишет свой
 * FakeChatRepository, и через полгода они расходятся так, что тесты
 * перестают что-либо гарантировать.
 */
plugins {
    id("silverchat.android.library")
}

android {
    namespace = "com.silverchat.core.testing"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":core:domain"))

    api(libs.junit)
    api(libs.truth)
    api(libs.turbine)
    api(libs.mockk)
    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.junit)
    api(libs.androidx.espresso.core)
    api(libs.robolectric)
    api(libs.hilt.testing)
    api(libs.androidx.compose.ui.test.junit4)
}
