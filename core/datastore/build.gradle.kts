/*
 * :core:datastore — НЕсекретные настройки приложения (темы, шрифты, язык,
 * уведомления, обои, автовоспроизведение).
 *
 * Разделение ответственности с :core:security принципиально:
 *  - DataStore Preferences  — то, что не страшно потерять и что нужно
 *    наблюдать как Flow (тема применяется мгновенно, без перезапуска);
 *  - EncryptedSharedPreferences (:core:security) — токены, PIN, флаги приватности.
 */
plugins {
    id("silverchat.android.library")
    id("silverchat.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.silverchat.core.datastore"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":core:domain"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
}
