/*
 * :core:common — утилиты и контракты, не зависящие от Android UI.
 * Не Compose-модуль: собирается как Android library только ради Timber и
 * Dispatchers.Main; логики Android здесь нет.
 */
plugins {
    id("silverchat.android.library")
    id("silverchat.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.silverchat.core.common"
}

dependencies {
    api(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
}
