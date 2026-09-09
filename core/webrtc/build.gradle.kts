/*
 * :core:webrtc — транспорт звонков.
 *
 * WebRTC-стек изолирован здесь целиком: ни один другой модуль не импортирует
 * org.webrtc.*, благодаря чему замена реализации (например, на LiveKit или
 * собственный SFU) не затрагивает UI и домен.
 */
plugins {
    id("silverchat.android.library")
    id("silverchat.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.silverchat.core.webrtc"

    defaultConfig {
        buildConfigField("int", "CALL_TIMEOUT_SEC", "45")
        buildConfigField("int", "MAX_CALL_BITRATE_KBPS", "1200")
        buildConfigField("int", "PREMIUM_CALL_BITRATE_KBPS", "2500")
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))

    // WebRTC-биндинги (единый артефакт, чтобы не собирать libwebrtc вручную)
    api(libs.webrtc)

    // Foreground service типа microphone/camera требует этих зависимостей
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
