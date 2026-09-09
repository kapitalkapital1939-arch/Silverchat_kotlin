package com.silverchat.core.domain.usecase.call

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.base.FlowUseCase
import com.silverchat.core.domain.base.SuspendUseCase
import com.silverchat.core.domain.repository.CallRepository
import com.silverchat.core.domain.repository.ChatRepository
import com.silverchat.core.domain.repository.ProfileRepository
import com.silverchat.core.model.CallSession
import com.silverchat.core.model.CallType
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.VisibilityRule
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Инициирование звонка.
 *
 * Проверяет приватность собеседника ДО отправки сигнала: если у него
 * «звонки: только контакты», сервер ответит 403, а пользователь получит
 * неприятный «звонок сорвался». Лучше показать причину сразу.
 */
class StartCallUseCase @Inject constructor(
    private val calls: CallRepository,
    private val chats: ChatRepository,
    private val profiles: ProfileRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<StartCallParams, CallSession>(dispatchers) {

    override suspend fun execute(params: StartCallParams): ScResult<CallSession> {
        val chat = chats.observeChat(params.chatId).first()
            ?: return ScResult.Failure(ScError.NotFound("Чат не найден"))

        // Групповой звонок доступен только с правами администратора
        if (params.type == CallType.GROUP_AUDIO || params.type == CallType.GROUP_VIDEO) {
            if (!chat.isGroup && !chat.isChannel) {
                return ScResult.Failure(ScError.Validation("Групповой звонок возможен только в группе", "chat"))
            }
            val canStart = chat.canEditInfo || params.type == CallType.GROUP_AUDIO
            if (!canStart) {
                return ScResult.Failure(ScError.Forbidden("Групповые звонки запускают администраторы", "chat.call"))
            }
        }

        // Приватность собеседника в личном чате
        if (chat.isPersonal) {
            val peer = chat.peer
            if (peer != null) {
                val privacy = profiles.observePrivacy().first()
                if (privacy.callsAllowed == VisibilityRule.NOBODY) {
                    return ScResult.Failure(
                        ScError.Forbidden("Пользователь не принимает звонки", "user.calls"),
                    )
                }
            }
        }

        // Нет сети — звонок невозможен, но не молча: отдаём понятную причину
        val connection = calls.observeConnectionState().first()
        if (!connection.isOnline) {
            return ScResult.Failure(ScError.Network("Нет соединения — звонок невозможен"))
        }

        return calls.startCall(params.chatId, params.type)
    }
}

data class StartCallParams(
    val chatId: ChatId,
    val type: CallType,
)

/**
 * Приём входящего звонка.
 * Запрашивает ICE/TURN-креденшелы и переводит сессию в CONNECTING.
 */
class AcceptCallUseCase @Inject constructor(
    private val calls: CallRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<com.silverchat.core.model.CallId, CallSession>(dispatchers) {

    override suspend fun execute(params: com.silverchat.core.model.CallId): ScResult<CallSession> {
        val ice = calls.requestIceServers(params)
        if (ice is ScResult.Failure) return ice

        return calls.acceptCall(params).onSuccess { session ->
            // Креденшелы живут только в памяти CallController: в EncryptedSharedPreferences
            // их писать нельзя — TTL 5 минут, и утечка файла prefs не должна давать доступ к TURN.
            calls.sendSignal(
                com.silverchat.core.model.CallSignal.Offer(
                    sdp = session.iceServers.firstOrNull()?.username.orEmpty(),
                    callId = params,
                ),
            )
        }
    }
}

/** Завершение звонка с фиксацией причины для истории. */
class EndCallUseCase @Inject constructor(
    private val calls: CallRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<com.silverchat.core.model.CallId, Unit>(dispatchers) {
    override suspend fun execute(params: com.silverchat.core.model.CallId): ScResult<Unit> =
        calls.endCall(params)
}

/**
 * Наблюдение за активной сессией звонка.
 *
 * Возвращает сырую [CallSession], а НЕ собственный UI-state: полное
 * состояние звонка (Idle/Active/Incoming/Error) ведёт CallController
 * в :core:webrtc — там же живёт state machine и таймер. Дублировать
 * sealed-иерархию здесь означало два источника истины об одном звонке.
 */
class ObserveActiveCallUseCase @Inject constructor(
    private val calls: CallRepository,
    dispatchers: DispatcherProvider,
) : FlowUseCase<Unit, CallSession?>(dispatchers) {
    override fun execute(params: Unit): Flow<CallSession?> = calls.observeActiveCall()
}

/**
 *StateMachine-проверка переходов состояния звонка.
 * Используется в :core:webrtc, чтобы UI не отрисовывал невозможные состояния
 * (например, «активен» после «завершён»).
 */
object CallStateMachine {

    private val ALLOWED: Map<com.silverchat.core.model.CallState, Set<com.silverchat.core.model.CallState>> = mapOf(
        com.silverchat.core.model.CallState.IDLE to setOf(
            com.silverchat.core.model.CallState.PREPARING,
        ),
        com.silverchat.core.model.CallState.PREPARING to setOf(
            com.silverchat.core.model.CallState.OUTGOING_RINGING,
            com.silverchat.core.model.CallState.INCOMING_RINGING,
            com.silverchat.core.model.CallState.FAILED,
        ),
        com.silverchat.core.model.CallState.OUTGOING_RINGING to setOf(
            com.silverchat.core.model.CallState.CONNECTING,
            com.silverchat.core.model.CallState.ENDED,
            com.silverchat.core.model.CallState.FAILED,
        ),
        com.silverchat.core.model.CallState.INCOMING_RINGING to setOf(
            com.silverchat.core.model.CallState.CONNECTING,
            com.silverchat.core.model.CallState.ENDED,
        ),
        com.silverchat.core.model.CallState.CONNECTING to setOf(
            com.silverchat.core.model.CallState.ACTIVE,
            com.silverchat.core.model.CallState.RECONNECTING,
            com.silverchat.core.model.CallState.ENDED,
            com.silverchat.core.model.CallState.FAILED,
        ),
        com.silverchat.core.model.CallState.ACTIVE to setOf(
            com.silverchat.core.model.CallState.ON_HOLD,
            com.silverchat.core.model.CallState.RECONNECTING,
            com.silverchat.core.model.CallState.ENDED,
        ),
        com.silverchat.core.model.CallState.ON_HOLD to setOf(
            com.silverchat.core.model.CallState.ACTIVE,
            com.silverchat.core.model.CallState.ENDED,
        ),
        com.silverchat.core.model.CallState.RECONNECTING to setOf(
            com.silverchat.core.model.CallState.ACTIVE,
            com.silverchat.core.model.CallState.ENDED,
            com.silverchat.core.model.CallState.FAILED,
        ),
        com.silverchat.core.model.CallState.ENDED to emptySet(),
        com.silverchat.core.model.CallState.FAILED to emptySet(),
    )

    fun canTransition(from: com.silverchat.core.model.CallState, to: com.silverchat.core.model.CallState): Boolean =
        ALLOWED[from]?.contains(to) == true
}
