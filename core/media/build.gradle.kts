/*
 * :core:media — работа с медиа: выбор, сжатие, загрузка, запись.
 * Голосовые сообщения, видео, «кружки», стикеры, GIF, анимированные аватарки.
 */
plugins {
    id("silverchat.android.library")
    id("silverchat.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.silverchat.core.media"

    defaultConfig {
        buildConfigField("int", "VOICE_SAMPLE_RATE", "48000")
        buildConfigField("int", "VOICE_BITRATE", "24000")
        buildConfigField("int", "CIRCLE_MAX_SECONDS", "60")
        buildConfigField("int", "CIRCLE_SIZE_PX", "720")
        buildConfigField("int", "MAX_UPLOAD_MB", "100")
        buildConfigField("int", "PREMIUM_MAX_UPLOAD_MB", "4096")
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))

    // ExoPlayer: воспроизведение голосовых/видео, «кружки», GIF
    api(libs.bundles.media3)

    // Транскодирование и сжатие видео перед отправкой
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.palette)
    implementation(libs.coil.compose)

    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
