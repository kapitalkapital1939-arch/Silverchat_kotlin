plugins {
    alias(libs.plugins.silverchat.android.feature)
}

android {
    namespace = "com.silverchat.feature.calls"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // WebRTC-стек и signaling живут в :core:webrtc; фича только рисует UI
    implementation(project(":core:webrtc"))
    implementation(project(":core:media"))

    // Runtime-права на микрофон и камеру до подключения к PeerConnection
    implementation(libs.accompanist.permissions)

    // Входящий звонок: полноэкранное уведомление и wake-lock
    implementation(project(":core:notifications"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
