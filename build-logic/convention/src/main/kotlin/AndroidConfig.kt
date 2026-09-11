import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Единая конфигурация Android/Kotlin для ВСЕХ модулей SilverChat.
 *
 * compileSdk, minSdk, Java/Kotlin target, R8, lint и packaging задаются здесь
 * и только здесь. Ни один модуль не может «уехать» по версиям — а значит в
 * Gradle Module Metadata (.module) не появляется несколько несовместимых
 * вариантов одного артефакта.
 */

internal const val SILVERCHAT_NAMESPACE_ROOT = "com.silverchat"

private const val COMMON_PROGUARD = "config/proguard/silverchat-common.pro"

/* Адреса бэкенда. Переопределяются в CI (-P…) или local.properties;
 * значения по умолчанию соответствуют продакшен-контракту из
 * backend-contract/01-overview.md. */
private const val BACKEND_URL = "https://api.silver.chat/v1/"
private const val WS_URL = "wss://api.silver.chat/v1/realtime"
private const val CDN_URL = "https://cdn.silver.chat/"
private const val PROTOCOL_VERSION = 1
private const val WS_HEARTBEAT_SEC = 25
private const val WS_MAX_BACKOFF_SEC = 30

internal val Project.libs
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.version(name: String): String =
    libs.findVersion(name).get().requiredVersion

internal fun Project.intVersion(name: String): Int = version(name).toInt()

internal fun Project.dependency(alias: String): MinimalExternalModuleDependency =
    libs.findLibrary(alias).get().get()

/* ── Версия приложения: один источник для :app и для BuildConfig всех модулей ──
 *
 * CI передаёт значения переменными окружения; локально их можно задать в
 * gradle.properties. Дефолты совпадают, поэтому сборка не зависит от того,
 * где её запустили.
 */
internal fun Project.appVersionName(): String =
    System.getenv("SILVERCHAT_VERSION_NAME")
        ?: project.findProperty("silverchat.versionName")?.toString()
        ?: "0.1.0"

internal fun Project.appVersionCode(): Int =
    System.getenv("SILVERCHAT_VERSION_CODE")?.toIntOrNull()
        ?: project.findProperty("silverchat.versionCode")?.toString()?.toIntOrNull()
        ?: 1

/* ── Пины сертификатов: пустая строка означает «пиннинг выключен» ──────────
 *
 * Пиннинг обязан включаться ТОЛЬКО реальными значениями. Подстановка
 * заглушек в release-сборку делает приложение нерабочим: OkHttp отклоняет
 * каждый ответ с SSLPeerUnverifiedException, а пользователь видит вечное
 * «Нет соединения» без внятной причины. Отлаживать такое на чужом устройстве
 * почти невозможно.
 *
 * Поэтому источник один — секрет CI (`SILVERCHAT_CERT_PINS`) или
 * gradle.properties (`silverchat.certPins`). Формат: пины через запятую,
 * каждый в виде `sha256/<base64>`, минимум два (основной и резервный ключ —
 * иначе ротация сертификата отключает всех клиентов разом).
 */
internal fun Project.certPins(): String =
    System.getenv("SILVERCHAT_CERT_PINS")
        ?: project.findProperty("silverchat.certPins")?.toString()
        ?: ""

/* ── Android: общий блок ─────────────────────────────────────────────────── */

internal fun Project.configureAndroid(extension: CommonExtension<*, *, *, *, *, *>, isApplication: Boolean) {
    with(extension) {
        compileSdk = intVersion("compileSdk")

        defaultConfig {
            minSdk = intVersion("minSdk")
            val target = intVersion("targetSdk")
            when (extension) {
                is com.android.build.api.dsl.LibraryExtension -> extension.defaultConfig.targetSdk = target
                is com.android.build.api.dsl.ApplicationExtension -> extension.defaultConfig.targetSdk = target
            }
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            vectorDrawables.useSupportLibrary = true

            /* Конфигурация окружения объявлена ЗДЕСЬ, а не в отдельных модулях.
             *
             * Причина — направление зависимостей: «Экран настроек» показывает
             * адрес бэкенда и версию, а :core:datastore не зависит от
             * :core:network и не имеет права зависеть. Если поля живут в одном
             * модуле, другому остаётся либо тянуть лишнюю зависимость, либо
             * дублировать значения. Общие поля решают это без компромиссов.
             *
             * AGP генерирует для библиотек только DEBUG, BUILD_TYPE и
             * LIBRARY_PACKAGE_NAME — VERSION_NAME/VERSION_CODE среди них нет,
             * поэтому версия приложения проброшена явными полями.
             */
            buildConfigField("String", "APP_VERSION_NAME", "\"${appVersionName()}\"")
            buildConfigField("int", "APP_VERSION_CODE", "${appVersionCode()}")
            buildConfigField("String", "BACKEND_URL", "\"$BACKEND_URL\"")
            buildConfigField("String", "WS_URL", "\"$WS_URL\"")
            buildConfigField("String", "CDN_URL", "\"$CDN_URL\"")
            buildConfigField("int", "PROTOCOL_VERSION", "$PROTOCOL_VERSION")
            buildConfigField("int", "WS_HEARTBEAT_SEC", "$WS_HEARTBEAT_SEC")
            buildConfigField("int", "WS_MAX_BACKOFF_SEC", "$WS_MAX_BACKOFF_SEC")
            buildConfigField("String", "CERT_PINS", "\"${certPins()}\"")
        }

        buildFeatures {
            // Без этого флага buildConfigField молча игнорируется, а класс
            // BuildConfig не генерируется — в AGP 8+ для библиотек он выключен.
            buildConfig = true
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            isCoreLibraryDesugaringEnabled = true
        }

        buildTypes {
            getByName("debug") {
                isMinifyEnabled = false
                isShrinkResources = false
                buildConfigField("boolean", "WS_HEARTBEAT_VERBOSE", "true")
                buildConfigField("boolean", "STRICT_MODE", "true")
            }
            getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = isApplication
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    rootProject.file(COMMON_PROGUARD).absolutePath,
                )
                /* Локальные правила модуля подключаем, только если файл создан.
                 *
                 * Обязательный `proguard-rules.pro` в каждом из 22 модулей — это
                 * 22 копии одного и того же пустого файла, а отсутствующий файл
                 * в списке proguardFiles роняет release-сборку. Общие правила
                 * живут в config/proguard/silverchat-common.pro и подключаются
                 * всегда; локальный файл нужен лишь там, где есть правила,
                 * специфичные для модуля (сейчас это только :app). */
                val moduleRules = project.file("proguard-rules.pro")
                if (moduleRules.exists()) {
                    proguardFiles(moduleRules.absolutePath)
                }
                buildConfigField("boolean", "WS_HEARTBEAT_VERBOSE", "false")
                buildConfigField("boolean", "STRICT_MODE", "false")
            }
        }

        testOptions {
            unitTests {
                isIncludeAndroidResources = true
                isReturnDefaultValues = true
            }
            animationsDisabled = true
        }

        lint {
            // ОШИБКИ блокируют сборку, предупреждения — нет.
            //
            // `warningsAsErrors = true` здесь был бы ловушкой: Android Lint
            // выдаёт предупреждение `GradleDependency` всякий раз, когда
            // доступна более новая версия библиотеки, то есть CI становился бы
            // красным через неделю после любого релиза AndroidX, не имея
            // отношения к коду. Вместо этого нужный набор проверок поднят до
            // severity=error в config/lint/lint.xml — список явный и
            // пересматриваемый, а не «всё подряд».
            abortOnError = true
            warningsAsErrors = false
            checkDependencies = true
            lintConfig = rootProject.file("config/lint/lint.xml")
            htmlReport = true
            xmlReport = true
            // Базлайн не используется намеренно: он прячет накопленные
            // проблемы, а lint.xml уже отделяет шум от сути.
        }

        packaging {
            resources {
                excludes += setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/LICENSE*",
                    "/META-INF/LICENSE.txt",
                    "/META-INF/DEPENDENCIES",
                    "/META-INF/INDEX.LIST",
                    "META-INF/*.kotlin_module",
                    "DebugProbesKt.bin",
                    "kotlin-tooling-metadata.json",
                )
            }
        }
    }
}

internal fun Project.configureApplication(extension: ApplicationExtension) {
    configureAndroid(extension, isApplication = true)
    with(extension) {
        defaultConfig {
            // Те же значения, что попадают в BuildConfig всех модулей:
            // «версия в настройках» и «версия в APK» не могут разойтись.
            versionCode = appVersionCode()
            versionName = appVersionName()
        }

        signingConfigs {
            // Ключи берём из env CI или gradle.properties. В git их нет никогда.
            maybeCreate("release").apply {
                val path = System.getenv("SILVERCHAT_KEYSTORE_PATH")
                    ?: project.findProperty("silverchat.keystorePath")?.toString()
                if (!path.isNullOrBlank()) {
                    storeFile = file(path)
                    storePassword = System.getenv("SILVERCHAT_KEYSTORE_PASSWORD")
                        ?: project.findProperty("silverchat.keystorePassword")?.toString()
                    keyAlias = System.getenv("SILVERCHAT_KEY_ALIAS")
                        ?: project.findProperty("silverchat.keyAlias")?.toString()
                    keyPassword = System.getenv("SILVERCHAT_KEY_PASSWORD")
                        ?: project.findProperty("silverchat.keyPassword")?.toString()
                    enableV3Signing = true
                    enableV4Signing = true
                }
            }
        }

        buildTypes {
            getByName("release") {
                val release = signingConfigs.findByName("release")
                signingConfig = if (release?.storeFile != null) release else signingConfigs.getByName("debug")
            }
        }
    }
}

internal fun Project.configureLibrary(extension: LibraryExtension, modulePath: String) {
    configureAndroid(extension, isApplication = false)
    // ":core:designsystem" -> "com.silverchat.core.designsystem"
    extension.namespace = SILVERCHAT_NAMESPACE_ROOT + modulePath.replace(':', '.')
    extension.defaultConfig {
        // Правила R8, которые модуль отдаёт потребителю (WebSocket DTO, kotlinx-serialization, WebRTC)
        consumerProguardFiles(rootProject.file(COMMON_PROGUARD).absolutePath)
    }
}

/* ── Kotlin ──────────────────────────────────────────────────────────────── */

internal fun Project.configureKotlin() {
    extensions.findByType(KotlinAndroidProjectExtension::class.java)?.apply {
        jvmToolchain(17)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            )
        }
    }
}

/* ── Compose ─────────────────────────────────────────────────────────────── */

internal fun Project.configureCompose(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.buildFeatures {
        compose = true
        // buildConfig включён для всех модулей в configureAndroid
    }
}
