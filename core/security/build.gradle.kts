/*
 * :core:security — безопасное хранение токенов и настроек приватности.
 *
 * Единственный модуль, которому разрешено трогать Keystore и
 * EncryptedSharedPreferences. Остальные модули работают с интерфейсом
 * [SecureStorage] и не знают, чем именно зашифрованы данные.
 */
plugins {
    id("silverchat.android.library")
    id("silverchat.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.silverchat.core.security"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))

    // EncryptedSharedPreferences + MasterKey (Android Keystore, AES256-GCM)
    api(libs.androidx.security.crypto)

    // Биометрический lock приложения (PIN/отпечаток/Face)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
}
