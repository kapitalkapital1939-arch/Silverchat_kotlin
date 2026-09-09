package com.silverchat.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.format.UsernameFormatter
import com.silverchat.core.common.format.UsernameValidation
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.data.mapper.wireName
import com.silverchat.core.data.realtime.RealtimeBus
import com.silverchat.core.domain.repository.ProfileRepository
import com.silverchat.core.domain.repository.SharedMediaKind
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.LocationInfo
import com.silverchat.core.model.Message
import com.silverchat.core.model.PrivacySettings
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import com.silverchat.core.model.VisibilityRule
import com.silverchat.core.model.WorkingHours
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.dto.PrivacyRequest
import com.silverchat.core.network.dto.ReportUserRequest
import com.silverchat.core.network.dto.UpdateProfileRequest
import com.silverchat.core.network.mapper.apiCall
import com.silverchat.core.network.mapper.toDomain as dtoToDomain
import com.silverchat.core.network.mapper.toDto
import com.silverchat.core.security.AuthState
import com.silverchat.core.security.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Профиль: карточка пользователя, аватар/баннер, приватность, общие медиа.
 *
 * ── Почему кэш в памяти, а не в Room ────────────────────────────────────
 * Профили не имеют своей таблицы: чужие данные не должны оседать на диске
 * устройства (лишний след при потере телефона), а собственный профиль — один
 * объект, который дешевле держать в `StateFlow`, чем платить за запрос к БД
 * на каждой отрисовке шапки. Актуальность поддерживает событие
 * `user.updated` через [RealtimeBus.profileUpdates].
 *
 * ── Почему файлы копируются в кэш перед отправкой ───────────────────────
 * `content://` URI из Photo Picker даёт поток, который можно прочитать один
 * раз. OkHttp читает тело запроса повторно после `401`, когда
 * `TokenAuthenticator` подставляет новый токен и перезапускает вызов: прямой
 * поток на втором проходе оказался бы уже закрыт. Копия в кэш делает тело
 * перечитываемым, а удаляется она в `finally` — то есть уже после всех
 * ретраев, а не до них.
 */
@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val api: SilverChatApi,
    private val bus: RealtimeBus,
    private val tokenStore: TokenStore,
    private val json: Json,
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : ProfileRepository {

    private val scope = CoroutineScope(SupervisorJob())

    private val meState = MutableStateFlow<User?>(null)
    private val usersState = MutableStateFlow<Map<String, User>>(emptyMap())
    private val blockedState = MutableStateFlow<List<User>>(emptyList())

    /**
     * До первой загрузки отдаём значения по умолчанию — они совпадают с
     * серверными дефолтами [PrivacySettings], поэтому экран приватности
     * показывает корректную картину, а не пустоту.
     */
    private val privacyState = MutableStateFlow(PrivacySettings())

    /**
     * Дедупликация фоновых подкачек — см. [InFlightGuard].
     *
     * Переподписка `Flow` в Compose происходит постоянно, а каждый `onStart`
     * тянет сетевой запрос: без ключа поворот устройства отправил бы их
     * десяток.
     */
    private val inFlight = InFlightGuard(scope, LogTag.PROFILE)

    init {
        bus.profileUpdates.onEach { cacheUser(it) }.launchIn(scope)
    }

    /* ── Наблюдение за пользователями ──────────────────────────────────── */

    /**
     * Свой профиль.
     *
     * Привязан к [AuthState]: после выхода поток обязан дать `null`, иначе
     * шапка настроек продолжила бы показывать данные предыдущего аккаунта.
     */
    override fun observeMe(): Flow<User?> =
        combine(tokenStore.authState, meState) { auth, cached ->
            if (auth.isLoggedIn) cached else null
        }.onStart { refreshMeAsync() }

    override fun observeUser(userId: UserId): Flow<User?> =
        usersState
            .map { it[userId.raw] }
            .onStart { refreshUserAsync(userId) }

    /**
     * Поиск по нику — точка входа диплинка `silverchat://u/{username}`.
     *
     * Холодный поток: ник приходит извне и может не соответствовать ни одному
     * пользователю, поэтому кэшировать «не найдено» смысла нет.
     */
    override fun observeUserByUsername(username: String): Flow<User?> = flow {
        val handle = UsernameFormatter.strip(username)
        if (handle.isNullOrEmpty()) {
            emit(null)
            return@flow
        }
        val cached = usersState.value.values.firstOrNull { it.username == handle }
        if (cached != null) emit(cached)

        val loaded = runCatching { api.userByUsername(handle).dtoToDomain() }.getOrNull()
        when {
            loaded != null -> {
                cacheUser(loaded)
                emit(loaded)
            }
            // `null` здесь означает «пользователь не найден», а не «ещё грузим»:
            // иначе экран диплинка вечно висел бы в состоянии загрузки.
            cached == null -> emit(null)
        }
    }

    /* ── Редактирование профиля ────────────────────────────────────────── */

    override suspend fun updateName(firstName: String, lastName: String?): ScResult<User> {
        val name = firstName.trim()
        if (name.isEmpty()) {
            return ScResult.Failure(
                ScError.Validation(message = "Имя не может быть пустым", field = "first_name"),
            )
        }
        if (name.length > MAX_NAME_LENGTH) {
            return ScResult.Failure(
                ScError.Validation(
                    message = "Имя не длиннее $MAX_NAME_LENGTH символов",
                    field = "first_name",
                ),
            )
        }
        // `lastName = null` сериализатор опустит (`explicitNulls = false`),
        // поэтому PATCH затронет только переданные поля.
        return apiCall {
            api.updateMe(
                UpdateProfileRequest(firstName = name, lastName = lastName?.trim()),
            ).dtoToDomain().also { cacheUser(it) }
        }
    }

    override suspend fun updateBio(bio: String): ScResult<User> {
        val text = bio.trim()
        if (text.length > MAX_BIO_LENGTH) {
            return ScResult.Failure(
                ScError.Validation(
                    message = "Описание не длиннее $MAX_BIO_LENGTH символов",
                    field = "bio",
                ),
            )
        }
        return apiCall {
            api.updateMe(UpdateProfileRequest(bio = text)).dtoToDomain().also { cacheUser(it) }
        }
    }

    /**
     * Смена или снятие юзернейма.
     *
     * `null` — именно снятие ника, и оно идёт отдельным `DELETE`: PATCH с
     * `username = null` клиентский Json просто не записал бы в тело запроса,
     * и сервер истолковал бы это как «поле не трогаем».
     */
    override suspend fun updateUsername(username: String?): ScResult<User> {
        if (username == null) {
            return apiCall { api.clearUsername().dtoToDomain().also { cacheUser(it) } }
        }
        val clean = UsernameFormatter.strip(username).orEmpty()
        when (val validation = UsernameFormatter.validate(clean)) {
            is UsernameValidation.Invalid -> return ScResult.Failure(
                ScError.Validation(message = validation.reason, field = "username"),
            )

            is UsernameValidation.Reserved -> return ScResult.Failure(
                ScError.Conflict(validation.reason),
            )

            is UsernameValidation.Valid -> Unit
        }
        return apiCall {
            api.updateMe(UpdateProfileRequest(username = clean))
                .dtoToDomain()
                .also { cacheUser(it) }
        }
    }

    override suspend fun setLocation(location: LocationInfo?): ScResult<User> = apiCall {
        val updated = if (location == null) {
            api.clearLocation()
        } else {
            api.setLocation(location.toDto())
        }
        updated.dtoToDomain().also { cacheUser(it) }
    }

    override suspend fun setWorkingHours(hours: WorkingHours?): ScResult<User> = apiCall {
        val updated = if (hours == null) {
            api.clearWorkingHours()
        } else {
            api.setWorkingHours(hours.toDto())
        }
        updated.dtoToDomain().also { cacheUser(it) }
    }

    /* ── Аватар и баннер ───────────────────────────────────────────────── */

    /**
     * @param animated true — грузим видео/Lottie/GIF. Проверку на Premium
     *   выполняет [com.silverchat.core.domain.usecase.profile.UploadAvatarUseCase]
     *   до вызова репозитория, а сервер дублирует её и отвечает
     *   [ScError.PremiumRequired]: клиентский гейт легко обойти.
     */
    override suspend fun uploadAvatar(localUri: String, animated: Boolean): ScResult<User> =
        uploadProfileMedia(localUri, animated, banner = false)

    override suspend fun uploadBanner(localUri: String, animated: Boolean): ScResult<User> =
        uploadProfileMedia(localUri, animated, banner = true)

    private suspend fun uploadProfileMedia(
        localUri: String,
        animated: Boolean,
        banner: Boolean,
    ): ScResult<User> {
        val staged = stageForUpload(localUri)
            ?: return ScResult.Failure(ScError.Local("Не удалось прочитать файл"))
        return try {
            apiCall {
                val part = MultipartBody.Part.createFormData(
                    "file",
                    staged.file.name,
                    staged.file.asRequestBody(staged.mimeType.toMediaTypeOrNull()),
                )
                val updated = if (banner) {
                    api.uploadBanner(part, animated)
                } else {
                    api.uploadAvatar(part, animated)
                }
                updated.dtoToDomain().also { cacheUser(it) }
            }
        } finally {
            // Копия в кэше нужна ровно на время вызова: после неё остаётся
            // мусор, который никто не удалит, если не сделать это здесь.
            withContext(dispatchers.io) { runCatching { staged.file.delete() } }
        }
    }

    /* ── Приватность ───────────────────────────────────────────────────── */

    override fun observePrivacy(): Flow<PrivacySettings> =
        privacyState.onStart { refreshPrivacyAsync() }

    /**
     * Запись настроек приватности.
     *
     * Частичного PATCH на сервере нет — объект уходит целиком. Ответ
     * `setPrivacy` возвращает обновлённый профиль, поэтому кешируем и его:
     * видимость аватара влияет на карточку пользователя.
     */
    override suspend fun updatePrivacy(settings: PrivacySettings): ScResult<Unit> = apiCall {
        api.setPrivacy(
            PrivacyRequest(
                lastSeenVisibility = settings.lastSeenVisibility.wireName(json),
                phoneVisibility = settings.phoneVisibility.wireName(json),
                avatarVisibility = settings.avatarVisibility.wireName(json),
                callsAllowed = settings.callsAllowed.wireName(json),
                storiesAllowed = settings.storiesAllowed.wireName(json),
                addToGroups = settings.addToGroups.wireName(json),
                readReceipts = settings.readReceiptsEnabled,
                exceptions = settings.exceptions.map { it.raw },
            ),
        ).dtoToDomain().let { cacheUser(it) }
        privacyState.value = settings
    }

    /**
     * Быстрая смена «был в сети».
     *
     * Текущие настройки дочитываются с сервера ОБЯЗАТЕЛЬНО: если взять
     * дефолты из незагруженного `privacyState`, остальные правила ушли бы
     * значениями по умолчанию и молча сбросили бы настройки пользователя.
     */
    override suspend fun setLastSeenVisibility(rule: VisibilityRule): ScResult<Unit> {
        val current = runCatching { api.privacy().dtoToDomain() }
            .onSuccess { privacyState.value = it }
            .getOrElse {
                return ScResult.Failure(
                    ScError.Network("Не удалось прочитать текущие настройки приватности"),
                )
            }
        return updatePrivacy(current.copy(lastSeenVisibility = rule))
    }

    /* ── Общие медиа ───────────────────────────────────────────────────── */

    /**
     * Вкладка «Медиа» в карточке пользователя.
     *
     * Фильтрация по типу выполняется на сервере, а не в локальной истории:
     * сообщения чата хранятся в Room страницами по 40 штук, и поиск
     * по ней показал бы только последние фото вместо всех. Интерфейс
     * возвращает `Flow<List<Message>>` без канала ошибок, поэтому сбой сети
     * логируется и отдаёт пустой список — вкладка останется пустой, но
     * экран не упадёт.
     */
    override fun observeSharedMedia(chatId: ChatId, kind: SharedMediaKind): Flow<List<Message>> =
        flow {
            val wireKind = kind.name.lowercase()
            val messages = runCatching {
                api.sharedMedia(chatId.raw, wireKind).map { it.dtoToDomain() }
            }.getOrElse { error ->
                ScLogger.w(LogTag.PROFILE, "Общие медиа $chatId/$wireKind недоступны", error)
                emptyList()
            }
            emit(messages)
        }

    /* ── Жалобы и чёрный список ────────────────────────────────────────── */

    override suspend fun reportUser(
        userId: UserId,
        reason: String,
        comment: String?,
    ): ScResult<Unit> = apiCall {
        api.reportUser(
            userId.raw,
            ReportUserRequest(
                reason = reason.trim(),
                comment = comment?.trim()?.ifBlank { null },
            ),
        )
    }.map { }

    override suspend fun blockUser(userId: UserId): ScResult<Unit> = apiCall {
        api.blockUser(userId.raw)
        refreshBlocked()
    }

    /**
     * Оптимистичная разблокировка с откатом.
     *
     * Список чёрных правится одним тапом, и ждать ответа сервера здесь
     * заметно неприятнее, чем в остальных операциях: строка должна исчезнуть
     * сразу. При ошибке возвращаем снимок списка обратно.
     */
    override suspend fun unblockUser(userId: UserId): ScResult<Unit> {
        val snapshot = blockedState.value
        blockedState.value = snapshot.filterNot { it.id == userId }
        val result = apiCall { api.unblockUser(userId.raw) }.map { }
        if (result.isFailure) {
            blockedState.value = snapshot
        } else {
            refreshBlocked()
        }
        return result
    }

    override fun observeBlockedUsers(): Flow<List<User>> =
        blockedState.onStart { refreshBlockedAsync() }

    /* ── Внутреннее ────────────────────────────────────────────────────── */

    private fun cacheUser(user: User) {
        usersState.update { current -> current + (user.id.raw to user) }
        if (user.id.raw == currentTokenUserId()) meState.value = user
    }

    private fun currentTokenUserId(): String? =
        (tokenStore.authState.value as? AuthState.Authenticated)?.userId

    private fun refreshMeAsync() {
        val tokenUserId = currentTokenUserId() ?: return
        // Сверяем с токеном, а не просто с null: после смены аккаунта в кэше
        // лежит предыдущий пользователь, и его нельзя показать новому.
        if (meState.value?.id?.raw == tokenUserId) return
        inFlight.launch("me") {
            api.me().dtoToDomain().also { cacheUser(it) }
        }
    }

    private fun refreshUserAsync(userId: UserId) {
        if (usersState.value.containsKey(userId.raw)) return
        inFlight.launch("user:${userId.raw}") {
            api.user(userId.raw).dtoToDomain().also { cacheUser(it) }
        }
    }

    private fun refreshPrivacyAsync() = inFlight.launch("privacy") {
        api.privacy().dtoToDomain().also { privacyState.value = it }
    }

    private fun refreshBlockedAsync() = inFlight.launch("blocked") {
        api.blockedUsers().map { it.dtoToDomain() }.also { blockedState.value = it }
    }

    private suspend fun refreshBlocked() {
        runCatching { api.blockedUsers().map { it.dtoToDomain() } }
            .onSuccess { blockedState.value = it }
    }

    /**
     * Копирует `content://` в кэш и определяет MIME.
     *
     * MIME обязателен: сервер кладёт файл в object storage, а там
     * `Content-Type` загрузки должен совпадать с типом, под который выдан
     * билет. Если ContentResolver тип не знает (частый случай для SAF),
     * определяем его по расширению.
     */
    private suspend fun stageForUpload(localUri: String): StagedMedia? =
        withContext(dispatchers.io) {
            runCatching {
                val parsed = Uri.parse(localUri)
                val resolver = context.contentResolver
                val mimeType = resolver.getType(parsed)
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        MimeTypeMap.getFileExtensionFromUrl(localUri),
                    )
                    ?: "application/octet-stream"

                val target = File(
                    context.cacheDir,
                    "profile_${System.nanoTime()}_${resolver.queryDisplayName(parsed)}",
                )
                resolver.openInputStream(parsed)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null

                // Пустой файл сервер отверг бы с 422 уже после загрузки —
                // дешевле заметить это на клиенте.
                if (target.length() <= 0L) {
                    target.delete()
                    return@runCatching null
                }
                StagedMedia(target, mimeType)
            }.getOrNull()
        }

    private fun ContentResolver.queryDisplayName(uri: Uri): String = runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getString(index)
            } else {
                null
            }
        }
    }.getOrNull().orEmpty().toSafeFileName()

    /**
     * Имя из SAF приходит произвольное и используется как часть пути в кэше,
     * поэтому всё лишнее заменяется, а ведущие точки срезаются: иначе
     * «../../x» позволил бы записать файл за пределы `cacheDir`.
     */
    private fun String.toSafeFileName(maxLength: Int = MAX_FILE_NAME_LENGTH): String {
        val cleaned = buildString(length) {
            this@toSafeFileName.forEach { char ->
                append(if (char.isLetterOrDigit() || char in ALLOWED_NAME_CHARS) char else '_')
            }
        }.trimStart('.')
        return cleaned.ifBlank { "upload.bin" }.takeLast(maxLength)
    }

    private data class StagedMedia(val file: File, val mimeType: String)

    private companion object {
        const val MAX_NAME_LENGTH = 64
        const val MAX_BIO_LENGTH = 280
        const val MAX_FILE_NAME_LENGTH = 48
        val ALLOWED_NAME_CHARS = charArrayOf('.', '_', '-')
    }
}
