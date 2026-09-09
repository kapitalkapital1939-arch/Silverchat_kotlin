plugins {
    alias(libs.plugins.silverchat.android.feature)
}

android {
    namespace = "com.silverchat.feature.stories"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Съёмка и публикация: камера, выбор медиа, прогресс загрузки
    implementation(project(":core:media"))

    // Права на камеру/микрофон перед съёмкой сторис
    implementation(libs.accompanist.permissions)

    // Прогресс-бар сегмента и «скелетон» превью
    implementation(libs.shimmer)

    // Воспроизведение видео-сторис
    implementation(libs.bundles.coil)
    implementation(libs.bundles.media3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
