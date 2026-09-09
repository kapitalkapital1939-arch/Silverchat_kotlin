package com.silverchat.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.CaptureVideo
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.silverchat.app.media.ActivityResultBridge
import com.silverchat.app.media.ActivityResultBridgeHolder
import com.silverchat.app.media.BridgeMediaType
import com.silverchat.app.navigation.AppDeepLink
import com.silverchat.app.ui.AppViewModel
import com.silverchat.app.ui.SilverChatApp
import com.silverchat.core.designsystem.navigation.RouteBuilder
import com.silverchat.core.designsystem.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred

/**
 * Единственная `Activity` приложения.
 *
 * ── Одна Activity, а не несколько ───────────────────────────────────────
 * Весь интерфейс — Compose, а переходы делает `NavController`. Отдельные
 * Activity для звонка или сторис дали бы пересоздание темы, потерю состояния
 * стекла и лишний уровень в стеке задач. Полноэкранные слои (звонок,
 * просмотр сторис) остаются маршрутами того же графа.
 *
 * ── Роль [ActivityResultBridge] ─────────────────────────────────────────
 * Выбор файла, съёмка и запрос разрешений в Android возможны только из
 * `Activity`, а репозитории — `@Singleton`. Activity регистрирует лаунчеры
 * и публикует себя в [ActivityResultBridgeHolder]; слой данных дёргает их
 * через держатель и ничего не знает про `Activity`. См. KDoc
 * [com.silverchat.core.data.repository.MediaCaptureGateway].
 *
 * ── Лаунчеры регистрируются в инициализаторах полей ─────────────────────
 * `registerForActivityResult` разрешено вызывать только до перехода в
 * `STARTED`. Инициализатор поля выполняется в конструкторе, то есть гарантированно
 * раньше, а регистрация внутри `onCreate` после `super` уже могла бы не успеть
 * при восстановлении состояния.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), ActivityResultBridge {

    @Inject
    lateinit var bridges: ActivityResultBridgeHolder

    /**
     * Та же ViewModel, что и в `setContent`.
     *
     * Создаётся в Activity намеренно: состояние авторизации нужно знать до
     * первой композиции, чтобы удерживать splash и не показать на мгновение
     * экран входа вошедшему пользователю.
     */
    private val appViewModel: AppViewModel by viewModels()

    /** Переход извне: ссылка `silverchat://…` или тап по уведомлению. */
    private var pendingDeepLink by mutableStateOf<AppDeepLink?>(null)

    /* Ожидания результатов. По одному на тип контракта: два одновременных
     * выбора фото невозможны — их сериализует ActivityResultBridgeHolder. */
    private var pendingPick: CompletableDeferred<List<Uri>>? = null
    private var pendingDocument: CompletableDeferred<List<Uri>>? = null
    private var pendingPhoto: CompletableDeferred<Boolean>? = null
    private var pendingVideo: CompletableDeferred<Boolean>? = null
    private var pendingPermissions: CompletableDeferred<Boolean>? = null

    private val pickSingleLauncher = registerForActivityResult(PickVisualMedia()) { uri ->
        pendingPick?.complete(listOfNotNull(uri))
    }

    private val pickMultipleLauncher =
        registerForActivityResult(PickMultipleVisualMedia(MAX_MULTIPLE_PICK)) { uris ->
            pendingPick?.complete(uris.toList())
        }

    private val openDocumentsLauncher =
        registerForActivityResult(OpenMultipleDocuments()) { uris ->
            pendingDocument?.complete(uris.toList())
        }

    private val takePictureLauncher = registerForActivityResult(TakePicture()) { saved ->
        pendingPhoto?.complete(saved)
    }

    private val captureVideoLauncher = registerForActivityResult(CaptureVideo()) { saved ->
        pendingVideo?.complete(saved)
    }

    private val permissionsLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { grants ->
            // «Выданы все»: частичная выдача для записи «кружка» бесполезна —
            // видео без звука отправлять нельзя.
            pendingPermissions?.complete(grants.values.all { granted -> granted })
        }

    /* ── Жизненный цикл ─────────────────────────────────────────────────── */

    override fun onCreate(savedInstanceState: Bundle?) {
        /* installSplashScreen ДО super.onCreate — требование библиотеки:
         * она перехватывает тему окна, и поздний вызов не сработает. */
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        /* Держим системный splash, пока не ясно, вошёл ли пользователь.
         * Без этого первый кадр показал бы экран входа, а затем произошёл бы
         * видимый переход на список чатов. */
        splashScreen.setKeepOnScreenCondition {
            appViewModel.uiState.value.isLoggedIn == null
        }

        // targetSdk 36: приложение обязано рисоваться под системными полосами
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            /* `pendingDeepLink` — делегат к `mutableStateOf`, поэтому чтение
             * внутри композиции регистрируется как зависимость: приход ссылки
             * из уведомления пересоберёт дерево без пересоздания Activity. */
            SilverChatApp(
                viewModel = appViewModel,
                deepLink = pendingDeepLink,
                onDeepLinkConsumed = { pendingDeepLink = null },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // Лаунчеры работают только пока Activity жива: публикуем себя здесь,
        // а не в onCreate, чтобы синглтон не дёргал мёртвую Activity.
        bridges.attach(this)
    }

    override fun onStop() {
        bridges.detach(this)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Сохраняем интент: при пересоздании Activity нужно знать, откуда пришли
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        bridges.detach(this)
        /* Освобождаем ожидания. Без этого корутина в слое данных осталась бы
         * висеть на `await()` навсегда: результат уже некому доставить, а
         * репозиторий ждал бы его, удерживая ссылку на сессию. */
        pendingPick?.complete(emptyList())
        pendingDocument?.complete(emptyList())
        pendingPhoto?.complete(false)
        pendingVideo?.complete(false)
        pendingPermissions?.complete(false)
        super.onDestroy()
    }

    /* ── Переходы извне ────────────────────────────────────────────────── */

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        pendingDeepLink = when {
            // Публичная ссылка: silverchat://u/alina, silverchat://join/<token>
            intent.action == Intent.ACTION_VIEW && intent.data != null ->
                AppDeepLink.UriLink(requireNotNull(intent.data))

            // Тап по уведомлению: цель известна приложению заранее
            else -> notificationRoute(intent)?.let { AppDeepLink.Route(it) }
        }
    }

    /**
     * Маршрут из действия уведомления.
     *
     * Строки действий и ключи extras должны совпадать с
     * `NotificationIntentFactory` в :core:notifications — там они собираются
     * как `"com.silverchat.notification.$action"` и `mapOf("chat_id" to …)`.
     */
    private fun notificationRoute(intent: Intent): String? = when (intent.action) {
        ACTION_OPEN_CHAT ->
            intent.getStringExtra(EXTRA_CHAT_ID)?.let { RouteBuilder.chatThread(it) }

        ACTION_OPEN_CALL ->
            intent.getStringExtra(EXTRA_CALL_ID)?.let { RouteBuilder.call(it) }

        ACTION_ACCEPT_CALL ->
            intent.getStringExtra(EXTRA_CALL_ID)?.let { RouteBuilder.call(it, video = true) }

        // Отклонение звонка не требует перехода: его обрабатывает
        // :feature:calls по событию реалтайм-канала.
        ACTION_DECLINE_CALL -> null

        ACTION_OPEN_MARKET -> Routes.MARKET
        ACTION_OPEN_ADMIN -> Routes.ADMIN

        // OPEN_APP и REPLY не ведут на конкретный экран: первое открывает
        // текущую вкладку, второе обрабатывает ReplyReceiver.
        else -> null
    }

    /* ── ActivityResultBridge ──────────────────────────────────────────── */

    override val hasCamera: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    override suspend fun pickMedia(
        mediaType: BridgeMediaType,
        allowMultiple: Boolean,
        maxCount: Int,
    ): List<Uri> {
        val deferred = CompletableDeferred<List<Uri>>()
        pendingPick = deferred
        return try {
            val request = PickVisualMediaRequest(mediaType.toPickerMediaType())
            if (allowMultiple) {
                pickMultipleLauncher.launch(request)
            } else {
                pickSingleLauncher.launch(request)
            }
            deferred.await().take(maxCount.coerceAtLeast(1))
        } finally {
            if (pendingPick === deferred) pendingPick = null
        }
    }

    override suspend fun pickDocument(mimeTypes: List<String>): List<Uri> {
        val deferred = CompletableDeferred<List<Uri>>()
        pendingDocument = deferred
        return try {
            openDocumentsLauncher.launch(mimeTypes.toTypedArray())
            deferred.await()
        } finally {
            if (pendingDocument === deferred) pendingDocument = null
        }
    }

    override suspend fun capturePhoto(target: Uri): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingPhoto = deferred
        return try {
            // Без гранта сторонняя камера не сможет записать файл и молча
            // вернётся пустой — это выглядит как «камера сломалась».
            grantUriToCamera(target, MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureLauncher.launch(target)
            deferred.await()
        } finally {
            if (pendingPhoto === deferred) pendingPhoto = null
        }
    }

    override suspend fun captureVideo(target: Uri): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingVideo = deferred
        return try {
            grantUriToCamera(target, MediaStore.ACTION_VIDEO_CAPTURE)
            captureVideoLauncher.launch(target)
            deferred.await()
        } finally {
            if (pendingVideo === deferred) pendingVideo = null
        }
    }

    override suspend fun requestPermissions(permissions: List<String>): Boolean {
        if (permissions.isEmpty()) return true
        val deferred = CompletableDeferred<Boolean>()
        pendingPermissions = deferred
        return try {
            permissionsLauncher.launch(permissions.toTypedArray())
            deferred.await()
        } finally {
            if (pendingPermissions === deferred) pendingPermissions = null
        }
    }

    /**
     * Выдаёт права на `content://` всем приложениям, способным снять фото.
     *
     * `FileProvider` помечен `grantUriPermissions="true"`, но грант нужно
     * выдать явно каждому получателю: системная камера — это стороннее
     * приложение, и без гранта запись в наш кэш для неё запрещена.
     */
    private fun grantUriToCamera(uri: Uri, captureAction: String) {
        resolveCameraPackages(captureAction).forEach { packageName ->
            runCatching {
                grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }

    /**
     * Пакеты камер.
     *
     * Перебор вместо `resolveActivity`: с Android 11 действует пакетная
     * видимость, а манифест не объявляет `<queries>` для камеры — запрос
     * всех обработчиков через `queryIntentActivities` в этом случае надёжнее.
     * Ветвление по версии нужно, потому что int-перегрузка устарела в API 33,
     * а проверка `Deprecation` в Android Lint поднята до ошибки в
     * config/lint/lint.xml.
     */
    private fun resolveCameraPackages(captureAction: String): List<String> {
        val intent = Intent(captureAction)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return resolved.map { it.activityInfo.packageName }
    }

    private fun BridgeMediaType.toPickerMediaType(): PickVisualMediaRequest.MediaType =
        when (this) {
            BridgeMediaType.IMAGE_ONLY -> PickVisualMedia.ImageOnly
            BridgeMediaType.VIDEO_ONLY -> PickVisualMedia.VideoOnly
            BridgeMediaType.IMAGE_AND_VIDEO -> PickVisualMedia.ImageAndVideo
        }

    private companion object {
        /**
         * Потолок множественного выбора.
         *
         * `PickMultipleVisualMedia` фиксирует лимит в момент регистрации
         * лаунчера, то есть ДО того, как репозиторий попросил конкретное
         * число файлов. Поэтому регистрируем с запасом, а лишнее отрезает
         * `take(maxCount)` в [pickMedia].
         */
        const val MAX_MULTIPLE_PICK = 10

        /* Действия уведомлений: собираются в NotificationIntentFactory как
         * "com.silverchat.notification.<ACTION>". */
        const val ACTION_OPEN_CHAT = "com.silverchat.notification.OPEN_CHAT"
        const val ACTION_OPEN_CALL = "com.silverchat.notification.OPEN_CALL"
        const val ACTION_ACCEPT_CALL = "com.silverchat.notification.ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.silverchat.notification.DECLINE_CALL"
        const val ACTION_OPEN_MARKET = "com.silverchat.notification.OPEN_MARKET"
        const val ACTION_OPEN_ADMIN = "com.silverchat.notification.OPEN_ADMIN"

        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CALL_ID = "call_id"
    }
}
