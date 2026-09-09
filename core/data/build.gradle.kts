/*
 * :core:data — реализации репозиториев из :core:domain.
 *
 * Единственный модуль, который «склеивает» три источника правды:
 *   1. :core:network  — REST + WebSocket (данные сервера);
 *   2. :core:database — Room (оффлайн-кэш, мгновенная отрисовка);
 *   3. :core:security — токены, EncryptedSharedPreferences.
 *
 * Стратегия каждого репозитория: offline-first.
 *   observe*()  -> Room.Flow (UI никогда не ждёт сеть);
 *   suspend     -> сеть + запись в Room + ScResult.
 *
 * Фичам модуль НЕДОСТУПЕН напрямую: они видят только интерфейсы из :core:domain.
 */
plugins {
    id("silverchat.android.library")
    id("silverchat.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.silverchat.core.data"
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:model"))
    api(project(":core:common"))

    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":core:media"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
