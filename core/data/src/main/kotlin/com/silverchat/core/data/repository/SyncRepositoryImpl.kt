package com.silverchat.core.data.repository

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.data.realtime.RealtimeDispatcher
import com.silverchat.core.database.SilverChatDatabase
import com.silverchat.core.database.dao.PendingOutgoingDao
import com.silverchat.core.domain.repository.SyncRepository
import com.silverchat.core.model.ConnectionState
import com.silverchat.core.model.SyncPhase
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.mapper.apiCall
import com.silverchat.core.network.ws.RealtimeSocket
import com.silverchat.core.security.AuthState
import com.silverchat.core.security.TokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Синхронизация: одно WebSocket-соединение на всё приложение.
 *
 * ── Зачем фазы ──────────────────────────────────────────────────────────
 * Холодный старт нельзя свести к «подключились и ждём событий»: пока клиент
 * был офлайн, сервер накопил изменения. Поэтому после рукопожатия идёт
 * упорядоченная догрузка — [SyncPhase] — и UI показывает честный прогресс
 * («Загружаем чаты…», «Загружаем сторис…») вместо пустого экрана.
 *
 * Порядок фаз не случаен:
 *   AUTH     -> без токена остальные запросы вернут 401;
 *   CHATS    -> список чатов даёт id, нужные для подписки на сообщения;
 *   MESSAGES -> догружаются только по видимым чатам, иначе это гигабайты;
 *   STORIES  -> отдельная лента, не блокирует переписку;
 *   WALLET   -> экономика нужна маркету, но не переписке;
 *   COMPLETE -> сокет переведён в рабочий режим.
 *
 * ── Очередь отправки ────────────────────────────────────────────────────
 * Всё, что пользователь набрал офлайн, лежит в `pending_outgoing` и уходит
 * сразу после восстановления соединения — с исходным `client_message_id`,
 * поэтому сервер идемпотентно схлопывает повторы и дублей не возникает.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val socket: RealtimeSocket,
    private val dispatcher: RealtimeDispatcher,
    private val api: SilverChatApi,
    private val tokenStore: TokenStore,
    private val pendingOutgoingDao: PendingOutgoingDao,
    private val database: SilverChatDatabase,
    private val chatRepository: ChatRepositoryImpl,
    private val storyRepository: StoryRepositoryImpl,
    private val messageRepository: MessageRepositoryImpl,
    private val dispatchers: DispatcherProvider,
    private val idempotencyKeys: IdempotencyKeyStore,
) : SyncRepository {

    private val _syncPhase = MutableStateFlow(SyncPhase.IDLE)

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.realtime)

    init {
        // При каждом восстановлении соединения — догнать пропущенное.
        // Без этого пользователь, вернувшийся из самолёта, увидел бы список
        // чатов недельной давности до первого ручного pull-to-refresh.
        socket.state
            .onEach { state ->
                if (state is ConnectionState.Connected) {
                    dispatcher.start()
                    scope.launch { runSyncSequence(fullResync = false) }
                }
            }
            .launchIn(scope)
    }

    override fun observeConnection(): Flow<ConnectionState> = socket.state

    override fun observeSyncPhase(): Flow<SyncPhase> = _syncPhase.asStateFlow()

    override fun observePendingOutgoing(): Flow<Int> = pendingOutgoingDao.observeCount()

    override suspend fun connect(): ScResult<Unit> = apiCall {
        val state = tokenStore.authState.value
        if (state !is AuthState.Authenticated) {
            ScLogger.w(LogTag.SYNC, "Соединение не установлено: пользователь не авторизован")
            return@apiCall
        }
        // updateToken до connect: сокет должен уйти на рукопожатие уже с
        // живым access-токеном, иначе сервер отклонит session.auth.
        socket.updateToken(state.token)
        socket.connect()
    }

    /**
     * Разрыв реалтайм-канала.
     *
     * Сюда сходятся оба пути завершения сессии — явный выход (`LogoutUseCase`)
     * и принудительный (`SessionLifecycle` по `session.kick` или провалу
     * refresh), поэтому сброс ключей идемпотентности стоит именно здесь.
     *
     * Сбрасывать их обязательно: хранилище — синглтон, и без очистки ключ
     * покупки предыдущего пользователя доживал бы до следующего входа. Если
     * сервер ограничивает ключи рамками аккаунта, это лишь второй рубеж; если
     * нет — первый ключ вошедшего следом совпал бы с чужим, и сервер вернул бы
     * ему результат ЧУЖОЙ покупки.
     */
    override suspend fun disconnect(): ScResult<Unit> = apiCall {
        socket.disconnect()
        _syncPhase.value = SyncPhase.IDLE
        idempotencyKeys.reset()
    }

    override suspend fun fullResync(): ScResult<Unit> = apiCall {
        runSyncSequence(fullResync = true)
    }

    /**
     * Очистка кэша без выхода из аккаунта.
     *
     * Токены и настройки не трогаем — это «забыть данные», а не «выйти».
     * Сразу после очистки запускаем полный ресинк, иначе пользователь
     * увидит пустое приложение без объяснения.
     */
    override suspend fun clearLocalCache(): ScResult<Unit> = apiCall {
        database.clearAllTables()
        runSyncSequence(fullResync = true)
    }

    /* ── Внутреннее ────────────────────────────────────────────────────── */

    /**
     * Последовательная догрузка. Каждая фаза изолирована `runCatching`:
     * упавшие сторис не должны блокировать кошелёк, а упавший кошелёк —
     * сообщения. Пользователь получает рабочую переписку даже при
     * частичном отказе сервера.
     */
    private suspend fun runSyncSequence(fullResync: Boolean) {
        if (_syncPhase.value == SyncPhase.CHATS || _syncPhase.value == SyncPhase.MESSAGES) {
            // Синк уже идёт: параллельный запуск удвоил бы запросы и бейджи.
            return
        }

        _syncPhase.value = SyncPhase.AUTH
        val authed = tokenStore.authState.value is AuthState.Authenticated
        if (!authed) {
            _syncPhase.value = SyncPhase.FAILED
            return
        }

        _syncPhase.value = SyncPhase.CHATS
        val chatsOk = runPhase("chats") {
            chatRepository.syncChatList(archived = false)
            chatRepository.syncChatList(archived = true)
        }
        if (!chatsOk) {
            _syncPhase.value = SyncPhase.FAILED
            return
        }

        // Подписываемся только на видимые чаты: на аккаунте с тысячей диалогов
        // подписка на все сразу означала бы бессмысленный трафик.
        runPhase("subscribe") {
            val ids = pendingOutgoingDao.all().map { it.chatId }.distinct()
            if (ids.isNotEmpty()) socket.subscribe(ids)
        }

        _syncPhase.value = SyncPhase.MESSAGES
        runPhase("outgoing") { flushPendingOutgoing() }

        _syncPhase.value = SyncPhase.STORIES
        runPhase("stories") { storyRepository.syncFeed() }

        _syncPhase.value = SyncPhase.WALLET
        runPhase("wallet") { api.wallet() }

        _syncPhase.value = SyncPhase.COMPLETE
        ScLogger.i(LogTag.SYNC, "Синхронизация завершена (полная=$fullResync)")
    }

    /** Возвращает true, если фаза прошла без исключения. */
    private suspend fun runPhase(name: String, block: suspend () -> Unit): Boolean =
        runCatching { block() }
            .onFailure { ScLogger.w(LogTag.SYNC, "Фаза '$name' упала: ${it.message}") }
            .isSuccess

    /**
     * Разбор очереди неотправленных сообщений.
     *
     * Отправляем строго по одному и в порядке создания: параллельная отправка
     * перемешала бы сообщения в диалоге, и собеседник прочитал бы их не в том
     * порядке, в каком их набирали.
     */
    private suspend fun flushPendingOutgoing() {
        val pending = pendingOutgoingDao.all().sortedBy { it.createdAt }
        pending.forEach { item ->
            val result = messageRepository.flushPendingViaSocket(item.chatId, item.payloadJson)
            if (result.isBlank()) {
                pendingOutgoingDao.markAttempt(
                    item.clientMessageId,
                    System.currentTimeMillis(),
                    "Пустой ответ сервера",
                )
            }
        }
        // Сообщения, которые сервер так и не принял, не должны копиться вечно.
        pendingOutgoingDao.purgeFailed(maxAttempts = MAX_ATTEMPTS)
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
