package com.silverchat.core.network.ws

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.model.ConnectionState
import com.silverchat.core.model.SocketFrame
import com.silverchat.core.model.SocketOps
import com.silverchat.core.network.BuildConfig
import com.silverchat.core.network.HttpHeaders
import com.silverchat.core.security.TokenStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * WebSocket-клиент на OkHttp.
 *
 * Что реализовано (и почему именно так):
 *
 * 1. АВТОРИЗАЦИЯ ПРИ ПОДКЛЮЧЕНИИ. Первым кадром уходит `session.auth` с
 *    access-токеном. Пока нет `session.auth.ok`, остальные команды буферизуются.
 *    Токен НЕ передаётся в query-строке URL — он попал бы в логи прокси.
 *
 * 2. HEARTBEAT. Пинг каждые [HEARTBEAT_SEC]. OkHttp держит TCP-сокет живым,
 *    но NAT/провайдер рвёт неактивные соединения через 30–60 с, поэтому
 *    прикладной heartbeat обязателен. Пропуск двух pong -> reconnect.
 *
 * 3. ЭКСПОНЕНЦИАЛЬНЫЙ BACKOFF С ДЖИТТЕРОМ. 250мс -> 30с. Без джиттера
 *    10k клиентов после сбоя сервера reconnect'ятся одновременно и кладут его.
 *
 * 4. ОЧЕРЕДЬ ИСХОДЯЩИХ ([OutgoingQueue]). Сообщения, отправленные офлайн,
 *    уходят после восстановления соединения с тем же client_message_id,
 *    поэтому сервер дедуплицирует и не появляется «двойников».
 *
 * 5. CORRELATION ID. Каждая команда несёт id; ack приходит с тем же id,
 *    что позволяет [sendAndAwait] сопоставить ответ без состояния.
 *
 * 6. ОДНО СОЕДИНЕНИЕ. Все вызовы connect() идемпотентны: второй не создаёт
 *    параллельный сокет (частая причина «сообщения приходят дважды»).
 */
@Singleton
class OkHttpRealtimeSocket @Inject constructor(
    private val client: OkHttpClient,
    private val tokenStore: TokenStore,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
    private val outgoingQueue: OutgoingQueue,
) : RealtimeSocket {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.realtime)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: Flow<ConnectionState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<IncomingFrame>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val frames: SharedFlow<IncomingFrame> = _frames.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var connectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectAttempt = AtomicInteger(0)
    private val missedPongs = AtomicInteger(0)
    private val awaiting = ConcurrentHashMap<String, kotlinx.coroutines.CancellableContinuation<JsonElement?>>()

    @Volatile private var authenticated = false

    override val isConnected: Boolean get() = _state.value.isOnline && authenticated

    /* ── Подключение ─────────────────────────────────────────────────────── */

    override suspend fun connect() {
        if (_state.value is ConnectionState.Connecting || _state.value.isOnline) return
        val token = tokenStore.accessToken
        if (token.isNullOrBlank()) {
            ScLogger.w(LogTag.WS, "Нет токена — WebSocket не подключаем")
            _state.value = ConnectionState.Disconnected
            return
        }
        connectJob?.cancel()
        connectJob = scope.launch { openSocket(token) }
    }

    private suspend fun openSocket(token: String) {
        _state.value = ConnectionState.Connecting

        val request = Request.Builder()
            .url(BuildConfig.WS_URL)
            // Заголовки, а не query-параметры: URL с токеном утёк бы в логи
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header(HttpHeaders.PROTOCOL_VERSION, BuildConfig.PROTOCOL_VERSION.toString())
            .header(HttpHeaders.PLATFORM, HttpHeaders.PLATFORM_ANDROID)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                ScLogger.i(LogTag.WS, "Соединение установлено, авторизуемся")
                reconnectAttempt.set(0)
                missedPongs.set(0)
                _state.value = ConnectionState.Authenticating

                // Первым кадром — авторизация сессии
                sendRaw(
                    SocketOps.SESSION_AUTH,
                    json.encodeToJsonElement(
                        AuthPayload.serializer(),
                        AuthPayload(
                            token = token,
                            deviceId = tokenStore.sessionId ?: "unknown",
                            protocol = BuildConfig.PROTOCOL_VERSION,
                        ),
                    ),
                )
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                handleFrame(bytes.utf8())
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ScLogger.i(LogTag.WS, "Сервер закрывает соединение: $code $reason")
                ws.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                authenticated = false
                stopHeartbeat()
                _state.value = ConnectionState.Disconnected
                if (code == GOING_AWAY || code == NORMAL_CLOSURE) {
                    scope.launch { scheduleReconnect("closed:$code") }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                authenticated = false
                stopHeartbeat()
                ScLogger.e(LogTag.WS, "Сбой WebSocket (${response?.code})", t)
                _state.value = ConnectionState.Failed(t.message ?: "connection failure")
                scope.launch { scheduleReconnect("failure") }
            }
        })
    }

    /* ── Обработка входящих кадров ───────────────────────────────────────── */

    private fun handleFrame(text: String) {
        val frame = runCatching { json.decodeFromString(SocketFrame.serializer(), text) }
            .getOrElse {
                ScLogger.w(LogTag.WS_FRAME, "Не удалось разобрать кадр: ${it.message}")
                return
            }

        if (BuildConfig.WS_HEARTBEAT_VERBOSE) {
            ScLogger.d(LogTag.WS_FRAME, "<- ${frame.op}")
        }

        when (frame.op) {
            SocketOps.SESSION_AUTH_OK -> onAuthenticated(frame)
            SocketOps.SESSION_AUTH_FAIL -> onAuthFailed()
            SocketOps.SESSION_PONG -> missedPongs.set(0)
            SocketOps.SESSION_KICK -> {
                // Сервер отозвал сессию (вход с другого устройства / бан)
                tokenStore.onSessionInvalidated("session.kick")
                scope.launch { disconnect() }
            }
            else -> {
                // Correlation: будим ждущую suspend-функцию
                frame.id.let { id ->
                    awaiting.remove(id)?.let { cont ->
                        if (cont.isActive) cont.resume(frame.payload)
                    }
                }
                scope.launch {
                    _frames.emit(
                        IncomingFrame(
                            id = frame.id,
                            op = frame.op,
                            timestamp = frame.timestamp,
                            payload = frame.payload,
                        ),
                    )
                }
            }
        }
    }

    private fun onAuthenticated(frame: SocketFrame) {
        authenticated = true
        val sessionId = frame.payload?.toString()?.trim('"') ?: UUID.randomUUID().toString()
        _state.value = ConnectionState.Connected(sessionId = sessionId)
        ScLogger.i(LogTag.WS, "Сессия авторизована: ${ScLogger.mask(sessionId)}")

        startHeartbeat()
        flushQueue()
    }

    private fun onAuthFailed() {
        authenticated = false
        ScLogger.w(LogTag.WS, "Авторизация WebSocket отклонена — токены недействительны")
        tokenStore.onSessionInvalidated("ws.auth.fail")
        _state.value = ConnectionState.Failed("unauthorized")
        // Бесконечный reconnect при 401 бессмысленен — нужен повторный логин
    }

    /* ── Heartbeat ───────────────────────────────────────────────────────── */

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_SEC * 1000L)
                if (missedPongs.incrementAndGet() > MISSED_PONG_LIMIT) {
                    ScLogger.w(LogTag.WS, "Сервер не отвечает на ping — принудительный reconnect")
                    webSocket?.cancel()
                    scheduleReconnect("heartbeat timeout")
                    break
                }
                sendRaw(SocketOps.SESSION_PING, null)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /* ── Переподключение ─────────────────────────────────────────────────── */

    private suspend fun scheduleReconnect(reason: String) {
        val attempt = reconnectAttempt.incrementAndGet()
        val exp = INITIAL_BACKOFF_MS * BACKOFF_FACTOR.pow(attempt - 1)
        val capped = exp.toLong().coerceAtMost(MAX_BACKOFF_MS)
        val jitter = (0..(capped / 4)).random()
        val delayMs = capped + jitter

        ScLogger.i(LogTag.WS, "Переподключение #$attempt через $delayMs мс ($reason)")
        _state.value = ConnectionState.Reconnecting(
            attempt = attempt,
            nextRetryInMs = delayMs,
            reason = reason,
        )

        delay(delayMs)
        val token = tokenStore.accessToken
        if (token.isNullOrBlank()) {
            _state.value = ConnectionState.Disconnected
            return
        }
        openSocket(token)
    }

    private fun Double.pow(exp: Int): Double {
        var r = 1.0
        repeat(exp) { r *= this }
        return r
    }

    /* ── Отправка ────────────────────────────────────────────────────────── */

    override suspend fun send(op: String, payload: JsonElement?): String {
        val id = UUID.randomUUID().toString()
        if (!isConnected) {
            // Офлайн: ставим в очередь, UI показывает статус SENDING
            outgoingQueue.enqueue(op, id, payload)
            ScLogger.d(LogTag.WS, "-> $op поставлен в очередь (нет соединения)")
            return id
        }
        publish(op, id, payload)
        return id
    }

    override suspend fun <T> sendAndAwait(
        op: String,
        payload: JsonElement?,
        timeoutMs: Long,
        deserializer: DeserializationStrategy<T>,
    ): T? {
        val id = UUID.randomUUID().toString()
        if (!isConnected) return null

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                awaiting[id] = cont
                cont.invokeOnCancellation { awaiting.remove(id) }
                publish(op, id, payload)
            }?.let { element -> json.decodeFromJsonElement(deserializer, element) }
        }
    }

    private fun publish(op: String, id: String, payload: JsonElement?) {
        val frame = SocketFrame(
            version = BuildConfig.PROTOCOL_VERSION,
            id = id,
            op = op,
            timestamp = System.currentTimeMillis(),
            payload = payload,
        )
        sendRaw(op, json.encodeToJsonElement(SocketFrame.serializer(), frame))
    }

    private fun sendRaw(op: String, encoded: JsonElement) {
        val text = json.encodeToString(JsonElement.serializer(), encoded)
        if (BuildConfig.WS_HEARTBEAT_VERBOSE) ScLogger.d(LogTag.WS_FRAME, "-> $op")
        val sent = webSocket?.send(text) ?: false
        if (!sent) ScLogger.w(LogTag.WS, "Кадр $op не отправлен: сокет закрыт")
    }

    private fun flushQueue() {
        val pending = outgoingQueue.drain()
        if (pending.isEmpty()) return
        ScLogger.i(LogTag.WS, "Отправляем ${pending.size} кадр(ов) из очереди")
        pending.forEach { item -> publish(item.op, item.id, item.payload) }
    }

    /* ── Прочее ──────────────────────────────────────────────────────────── */

    override suspend fun updateToken(accessToken: String) {
        // Переподключаемся с новым токеном: сервер валидирует сессию заново
        if (isConnected) {
            webSocket?.close(NORMAL_CLOSURE, "token rotated")
        }
        connect()
    }

    override suspend fun subscribe(chatIds: List<String>) {
        send(
            SocketOps.SESSION_SUBSCRIBE,
            json.encodeToJsonElement(
                SubscribePayload.serializer(),
                SubscribePayload(chatIds),
            ),
        )
    }

    override suspend fun disconnect() {
        ScLogger.i(LogTag.WS, "Штатное отключение от WebSocket")
        connectJob?.cancel()
        stopHeartbeat()
        authenticated = false
        webSocket?.close(NORMAL_CLOSURE, "client disconnect")
        webSocket = null
        _state.value = ConnectionState.Disconnected
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val GOING_AWAY = 1001
        const val INITIAL_BACKOFF_MS = 250.0
        const val MAX_BACKOFF_MS = 30_000L
        const val BACKOFF_FACTOR = 2.0
        const val MISSED_PONG_LIMIT = 2
        val HEARTBEAT_SEC = BuildConfig.WS_HEARTBEAT_SEC
    }
}

@kotlinx.serialization.Serializable
private data class AuthPayload(
    @kotlinx.serialization.SerialName("token") val token: String,
    @kotlinx.serialization.SerialName("device_id") val deviceId: String,
    @kotlinx.serialization.SerialName("protocol") val protocol: Int,
)

@kotlinx.serialization.Serializable
private data class SubscribePayload(
    @kotlinx.serialization.SerialName("chat_ids") val chatIds: List<String>,
)
