package com.silverchat.core.data.realtime

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import com.silverchat.core.data.mapper.DataJson
import com.silverchat.core.data.mapper.toEntity
import com.silverchat.core.database.dao.ChatDao
import com.silverchat.core.database.dao.DraftDao
import com.silverchat.core.database.dao.LedgerDao
import com.silverchat.core.database.dao.MessageDao
import com.silverchat.core.database.dao.StoryDao
import com.silverchat.core.model.CallSignal
import com.silverchat.core.model.Draft
import com.silverchat.core.model.Message
import com.silverchat.core.model.MessageContent
import com.silverchat.core.model.MessageStatus
import com.silverchat.core.network.mapper.toDomain as dtoToDomain
import com.silverchat.core.network.ws.CallSignalPayload
import com.silverchat.core.network.ws.IncomingFrame
import com.silverchat.core.network.ws.RealtimeSocket
import com.silverchat.core.network.ws.SocketEvent
import com.silverchat.core.network.ws.SocketEventDecoder
import com.silverchat.core.security.AuthState
import com.silverchat.core.security.TokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Мост WebSocket -> Room.
 *
 * Единственное место в приложении, где входящий кадр сокета превращается в
 * запись локальной БД. Архитектурно это важно: UI никогда не подписывается на
 * сокет напрямую, он подписывается на Room. Благодаря этому
 *  - офлайн и онлайн рисуются одним и тем же кодом;
 *  - потерянное во время дисконнекта событие догоняется `chat.list.sync`;
 *  - тестировать можно на уровне DAO, не поднимая сокет.
 *
 * События без локального хранилища (сигналинг звонков, баланс, профиль)
 * уходят в [RealtimeBus].
 *
 * Обработка строго последовательная: `onEach` над `SharedFlow` в одном
 * коллекторе гарантирует, что `message.new` применится раньше, чем
 * `message.read` на то же сообщение. Параллельные коллекторы дали бы гонку.
 */
@Singleton
class RealtimeDispatcher @Inject constructor(
    private val socket: RealtimeSocket,
    private val decoder: SocketEventDecoder,
    private val bus: RealtimeBus,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val draftDao: DraftDao,
    private val storyDao: StoryDao,
    private val ledgerDao: LedgerDao,
    private val tokenStore: TokenStore,
    private val dispatchers: DispatcherProvider,
) {

    private val _appliedFrames = MutableStateFlow(0)

    /** Сколько кадров применено — для отладочного экрана и метрик синка. */
    val appliedFrames: StateFlow<Int> = _appliedFrames.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.realtime)

    private var started = false

    /**
     * Запускается один раз при установке соединения.
     *
     * Повторный вызов игнорируется намеренно: два коллектора на одном сокете
     * дали бы двойное применение каждого кадра и, как следствие, удвоение
     * счётчика непрочитанных.
     */
    fun start() {
        if (started) return
        started = true

        socket.frames
            .onEach { frame ->
                runCatching { handle(frame) }
                    .onFailure {
                        ScLogger.e(LogTag.WS, "Кадр op=${frame.op} не обработан", it)
                    }
                _appliedFrames.value += 1
            }
            .launchIn(scope)
    }

    private suspend fun handle(frame: IncomingFrame) {
        when (val event = decoder.decode(frame)) {
            /* ── Сообщения ─────────────────────────────────────────────── */

            is SocketEvent.MessageNew -> {
                val message = event.message.dtoToDomain()
                messageDao.upsert(message.toEntity())
                chatDao.applyIncomingChatUpdate(
                    chatId = message.chatId,
                    lastMessageJson = encodeMessage(message),
                    sentAt = message.sentAt,
                    // Своё сообщение не должно увеличивать собственный счётчик.
                    incrementUnread = message.senderId != currentUserId(),
                )
            }

            is SocketEvent.MessageAck -> {
                val server = event.message.dtoToDomain()
                // Кадр ack не возвращает client_message_id, поэтому сверяемся
                // с кэшем: если REST-ответ ещё не пришёл, строка живёт под
                // временным id и её надо перенести на серверный.
                val pending = messageDao.findByClientMessageId(server.id)
                if (pending != null) {
                    messageDao.acknowledge(server.id, server.id, server.sentAt, MessageStatus.SENT)
                } else {
                    messageDao.upsert(server.toEntity())
                }
            }

            is SocketEvent.MessageEdited -> {
                val edited = event.message.dtoToDomain()
                messageDao.updateContent(
                    messageId = edited.id,
                    contentJson = encodeContent(edited),
                    editedAt = edited.editedAt ?: frame.timestamp,
                )
            }

            is SocketEvent.MessageDeleted -> messageDao.markDeleted(event.payload.messageIds)

            is SocketEvent.ReactionUpdated -> {
                val message = event.message.dtoToDomain()
                messageDao.updateReactions(message.id, message.reactions)
            }

            is SocketEvent.DeliveryUpdated ->
                event.payload.messageIds.forEach {
                    messageDao.updateStatus(it, MessageStatus.DELIVERED)
                }

            is SocketEvent.ReadUpdated ->
                event.payload.messageIds.forEach {
                    messageDao.updateStatus(it, MessageStatus.READ)
                }

            /* ── Чаты ──────────────────────────────────────────────────── */

            is SocketEvent.ChatUpdated -> upsertChat(event.chat.dtoToDomain())

            is SocketEvent.ChatCreated -> upsertChat(event.chat.dtoToDomain())

            is SocketEvent.PinnedUpdated -> upsertChat(event.chat.dtoToDomain())

            is SocketEvent.ChatListSync ->
                chatDao.upsertAll(event.payload.chats.map { it.dtoToDomain().toEntity() })

            is SocketEvent.DraftUpdated ->
                draftDao.upsert(
                    Draft(
                        text = event.payload.text,
                        replyTo = event.payload.replyTo,
                        updatedAt = frame.timestamp,
                    ).toEntity(event.payload.chatId),
                )

            is SocketEvent.MemberChanged ->
                // Кадр роли не содержит данных пользователя, нужных для строки
                // списка, поэтому участник просто изымается из кэша: следующий
                // members-запрос подтянет его целиком и уже с новой ролью.
                messageDao.deleteMember(event.payload.chatId, event.payload.userId)

            is SocketEvent.InviteUpdated -> Unit // список ссылок читается по запросу

            /* ── Сторис ────────────────────────────────────────────────── */

            is SocketEvent.StoryNew -> storyDao.upsert(event.story.dtoToDomain().toEntity())

            is SocketEvent.StoryViewed -> storyDao.markSeen(event.payload.storyId)

            is SocketEvent.StoryExpired -> storyDao.delete(event.payload.storyId)

            is SocketEvent.StoryDeleted -> storyDao.delete(event.payload.storyId)

            /* ── Присутствие и «печатает…» ─────────────────────────────── */

            is SocketEvent.PresenceChanged -> Unit // кэшируется в User при следующем синке

            is SocketEvent.Typing -> Unit // обрабатывается ChatRepositoryImpl через шину

            /* ── Звонки: только шина, локального кэша нет ──────────────── */

            is SocketEvent.CallIncoming,
            is SocketEvent.CallStateChanged,
            -> bus.emitCallEvent(event.toCallEvent(frame.timestamp))

            is SocketEvent.CallSignalReceived ->
                event.payload.toCallSignal()?.let { bus.emitCallSignal(it) }

            is SocketEvent.CallIceServers ->
                bus.emitIceServers(event.payload.servers.map { it.dtoToDomain() })

            /* ── Экономика: только шина ────────────────────────────────── */

            is SocketEvent.WalletUpdated -> bus.emitWallet(event.wallet.dtoToDomain())

            /* Журнал, в отличие от баланса, имеет локальное хранилище, поэтому
             * идёт в Room напрямую, минуя шину: подписчик `observeLedger` и так
             * наблюдает за `LedgerDao`, и второй канал доставки привёл бы к тому,
             * что одна запись приходит дважды. `upsert` по первичному ключу
             * делает повторную доставку безопасной — а она возможна, потому что
             * сервер вправе повторно прислать строку после переподключения. */
            is SocketEvent.LedgerAppended -> ledgerDao.upsert(event.entry.dtoToDomain().toEntity())

            is SocketEvent.StreakUpdated -> bus.emitStreak(event.streak.dtoToDomain())

            is SocketEvent.PremiumUpdated -> bus.emitPremium(event.premium.dtoToDomain())

            is SocketEvent.PremiumGranted -> Unit // придёт следом как PremiumUpdated

            is SocketEvent.ListingNew -> bus.emitListing(event.listing.dtoToDomain())

            is SocketEvent.ListingUpdated -> bus.emitListing(event.listing.dtoToDomain())

            is SocketEvent.PurchaseCompleted -> bus.emitWallet(event.payload.wallet.dtoToDomain())

            /* ── Профиль ───────────────────────────────────────────────── */

            is SocketEvent.ProfileUpdated -> bus.emitProfileUpdate(event.user.dtoToDomain())

            is SocketEvent.AvatarUpdated -> bus.emitProfileUpdate(event.user.dtoToDomain())

            is SocketEvent.BannerUpdated -> bus.emitProfileUpdate(event.user.dtoToDomain())

            /* ── Админка и служебное ───────────────────────────────────── */

            is SocketEvent.AdminLogAppended -> Unit // читается через observeAuditLog

            is SocketEvent.AdminBroadcast -> bus.emitAdminBroadcast(event.payload.text)

            is SocketEvent.ServerError ->
                ScLogger.w(LogTag.WS, "Ошибка сервера ${event.payload.code}: ${event.payload.message}")

            is SocketEvent.Ack -> Unit

            is SocketEvent.Unknown -> ScLogger.d(LogTag.WS, "Неизвестный op: ${event.op}")

            is SocketEvent.DecodeFailure ->
                ScLogger.w(LogTag.WS, "Не декодирован op=${event.op}: ${event.reason}")
        }
    }

    /* ── Вспомогательное ───────────────────────────────────────────────── */

    private suspend fun upsertChat(chat: com.silverchat.core.model.Chat) {
        // Сохраняем прежний updatedAt: иначе каждое событие сдвигало бы чат
        // в списке, даже если это мьют или смена аватара.
        chatDao.upsert(chat.toEntity(existingUpdatedAt = chatDao.getChat(chat.id)?.updatedAt))
    }

    private fun currentUserId(): String? =
        (tokenStore.authState.value as? AuthState.Authenticated)?.userId

    private fun encodeMessage(message: Message): String =
        DataJson.encodeToString(Message.serializer(), message)

    private fun encodeContent(message: Message): String =
        DataJson.encodeToString(MessageContent.serializer(), message.content)

    /** `call.incoming` и `call.state` несут один и тот же payload. */
    private fun SocketEvent.toCallEvent(receivedAt: Long): CallEvent {
        val payload = when (this) {
            is SocketEvent.CallIncoming -> payload
            is SocketEvent.CallStateChanged -> payload
            else -> error("toCallEvent применим только к событиям звонка")
        }
        return CallEvent(
            callId = payload.callId,
            chatId = payload.chatId,
            type = payload.type,
            state = payload.state,
            fromUserId = payload.fromUserId,
            reason = payload.reason,
            receivedAt = receivedAt,
        )
    }

    /**
     * Кадр сигналинга -> типизированный [CallSignal].
     *
     * Возвращает null, если в кадре нет обязательных полей для своего типа:
     * лучше пропустить битый сигнал, чем передать в WebRTC offer без SDP.
     */
    private fun CallSignalPayload.toCallSignal(): CallSignal? = when (kind) {
        "offer" -> sdp?.let { CallSignal.Offer(sdp = it, callId = callId) }
        "answer" -> sdp?.let { CallSignal.Answer(sdp = it, callId = callId) }
        "candidate" -> {
            val rawCandidate = candidate
            val mLineIndex = sdpMLineIndex
            if (rawCandidate != null && mLineIndex != null) {
                CallSignal.Candidate(
                    callId = callId,
                    candidate = rawCandidate,
                    sdpMid = sdpMid,
                    sdpMLineIndex = mLineIndex,
                )
            } else {
                null
            }
        }
        else -> {
            ScLogger.d(LogTag.CALL, "Неизвестный тип сигналинга: $kind")
            null
        }
    }
}
