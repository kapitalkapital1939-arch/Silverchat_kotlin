plugins {
    alias(libs.plugins.silverchat.android.feature)
}

android {
    namespace = "com.silverchat.feature.admin"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Админ-панель @silver: доступ проверяется и на клиенте, и на сервере
    implementation(project(":core:security"))
    implementation(project(":core:datastore"))

    // Графики статистики и «скелетоны» длинных списков
    implementation(libs.shimmer)
    implementation(libs.bundles.coil)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
