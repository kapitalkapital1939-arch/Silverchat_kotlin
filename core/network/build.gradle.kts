/*
 * :core:network — единственный модуль, который знает про HTTP и WebSocket.
 *
 * Содержит:
 *  - Retrofit API (REST) + kotlinx-serialization;
 *  - OkHttp WebSocket-клиент с heartbeat, экспоненциальным backoff и очередью;
 *  - DTO и мапперы в :core:model;
 *  - интерцепторы авторизации и ротации токенов.
 *
 * Фичам этот модуль НЕДОСТУПЕН (см. silverchat.android.feature).
 */
plugins {
    id("silverchat.android.library")
    id("silverchat.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.silverchat.core.network"

    /* Адреса бэкенда, версия протокола и тайминги WebSocket объявлены
     * ЦЕНТРАЛЬНО в build-logic/convention/AndroidConfig.kt и попадают в
     * BuildConfig каждого модуля: экран настроек показывает адрес бэкенда,
     * а :core:datastore не должен зависеть от :core:network ради строки. */
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(project(":core:security"))

    api(libs.bundles.network)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
