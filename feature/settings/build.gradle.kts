plugins {
    alias(libs.plugins.silverchat.android.feature)
}

android {
    namespace = "com.silverchat.feature.settings"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Настройки хранятся в DataStore + EncryptedSharedPreferences
    implementation(project(":core:datastore"))
    implementation(project(":core:security"))

    // Биометрия для «замка приложения»
    implementation(libs.androidx.biometric)

    // Предпросмотр обоев и палитр темы
    implementation(libs.bundles.coil)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
