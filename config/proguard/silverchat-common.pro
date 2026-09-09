# ===========================================================================
# SilverChat — общие правила R8/ProGuard для ВСЕХ модулей.
# Подключается автоматически convention-плагинами (consumerProguardFiles),
# поэтому правила не дублируются в каждом модуле и не расходятся.
# ===========================================================================

# --- kotlinx-serialization --------------------------------------------------
# Сериализуемые модели — это DTO WebSocket/REST протокола. Обфускация имён
# полей ломает контракт с бэкендом молча, без ошибки компиляции.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class com.silverchat.core.model.** {
    *** Companion;
    static ** INSTANCE;
}
-keepclasseswithmembers class com.silverchat.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.silverchat.core.network.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.silverchat.core.network.dto.**$$serializer { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}

# --- OkHttp / Okio ----------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Платформенные TLS-провайдеры отсутствуют на части устройств — это норма
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Retrofit ---------------------------------------------------------------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
# Generic-сигнатуры suspend-функций Retrofit нужны для kotlinx-serialization
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- WebRTC (звонки) --------------------------------------------------------
# JNI-слой ищет классы по имени — обфускация ломает нативную часть.
-keep class org.webrtc.** { *; }
-keep class io.getstream.webrtc.** { *; }
-dontwarn org.webrtc.**

# --- Room -------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ----------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keepclassmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <init>(...);
}

# --- Compose ----------------------------------------------------------------
# Compose-классы не нуждаются в keep-правилах, но stability-конфигурация
# и @Immutable/@Stable должны пережить shrinking.
-keep class androidx.compose.runtime.Immutable
-keep class androidx.compose.runtime.Stable
-dontwarn androidx.compose.**

# --- Coil / Lottie / Media3 -------------------------------------------------
-dontwarn coil3.**
-keep class com.airbnb.lottie.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Google Play Services / FCM ---------------------------------------------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# --- EncryptedSharedPreferences / Tink --------------------------------------
# Tink использует reflection для регистрации примитивов
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**

# --- Timber -----------------------------------------------------------------
-assumenosideeffects class timber.log.Timber$Forest {
    public *** v(...);
    public *** d(...);
    public *** i(...);
}

# --- Сохраняем номера строк для краш-репортов -------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kotlin metadata (нужен kotlinx-serialization и Hilt) --------------------
-keep class kotlin.Metadata { *; }
