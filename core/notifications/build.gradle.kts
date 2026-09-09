/*
 * :core:notifications — push (FCM), каналы уведомлений, входящий звонок,
 * foreground-сервисы звонка и загрузки медиа.
 */
plugins {
    id("silverchat.android.library")
    id("silverchat.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.silverchat.core.notifications"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.serialization.json)
}
