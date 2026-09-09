package com.silverchat.core.data.realtime

import com.silverchat.core.model.CallSignal
import com.silverchat.core.model.IceServer
import com.silverchat.core.model.PremiumStatus
import com.silverchat.core.model.Streak
import com.silverchat.core.model.User
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.Wallet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Внутренняя шина реалтайм-событий.
 *
 * Зачем она нужна, если есть [RealtimeDispatcher]:
 * диспетчер пишет в Room, но часть событий не имеет локального хранилища —
 * сигналинг звонков, «печатает…», пуш баланса. Их нельзя кэшировать, потому
 * что они описывают мгновенное состояние, а не данные.
 *
 * Шина развязывает диспетчер и репозитории: диспетчер не знает, кто подписан,
 * а репозитории не знают про WebSocket. Подписчики получают события через
 * `SharedFlow` с `DROP_OLDEST` — если UI не успевает, старое событие
 * важнее нового выбросить, чем заблокировать сокет-поток.
 */
@Singleton
class RealtimeBus @Inject constructor() {

    /* ── Звонки ────────────────────────────────────────────────────────── */

    private val _callSignals = MutableSharedFlow<CallSignal>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val callSignals: SharedFlow<CallSignal> = _callSignals.asSharedFlow()

    /**
     * Входящие и изменившиеся звонки.
     *
     * `replay = 1` намеренно: экран звонка может открыться на долю секунды
     * позже кадра, и потерять входящий вызов — значит потерять звонок.
     */
    private val _callEvents = MutableSharedFlow<CallEvent>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val callEvents: SharedFlow<CallEvent> = _callEvents.asSharedFlow()

    private val _iceServers = MutableSharedFlow<List<IceServer>>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val iceServers: SharedFlow<List<IceServer>> = _iceServers.asSharedFlow()

    /* ── Кошелёк и экономика ───────────────────────────────────────────── */

    private val _wallet = MutableSharedFlow<Wallet>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val wallet: SharedFlow<Wallet> = _wallet.asSharedFlow()

    private val _streak = MutableSharedFlow<Streak>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val streak: SharedFlow<Streak> = _streak.asSharedFlow()

    private val _premium = MutableSharedFlow<PremiumStatus>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val premium: SharedFlow<PremiumStatus> = _premium.asSharedFlow()

    /* ── Маркет ────────────────────────────────────────────────────────── */

    private val _listings = MutableSharedFlow<UsernameListing>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val listings: SharedFlow<UsernameListing> = _listings.asSharedFlow()

    /* ── Профиль ───────────────────────────────────────────────────────── */

    private val _profileUpdates = MutableSharedFlow<User>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val profileUpdates: SharedFlow<User> = _profileUpdates.asSharedFlow()

    /* ── Админка ───────────────────────────────────────────────────────── */

    private val _adminBroadcasts = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val adminBroadcasts: SharedFlow<String> = _adminBroadcasts.asSharedFlow()

    /* ── Публикация (вызывается только диспетчером) ────────────────────── */

    suspend fun emitCallSignal(signal: CallSignal) = _callSignals.emit(signal)

    suspend fun emitCallEvent(event: CallEvent) = _callEvents.emit(event)

    suspend fun emitIceServers(servers: List<IceServer>) = _iceServers.emit(servers)

    suspend fun emitWallet(value: Wallet) = _wallet.emit(value)

    suspend fun emitStreak(value: Streak) = _streak.emit(value)

    suspend fun emitPremium(value: PremiumStatus) = _premium.emit(value)

    suspend fun emitListing(value: UsernameListing) = _listings.emit(value)

    suspend fun emitProfileUpdate(value: User) = _profileUpdates.emit(value)

    suspend fun emitAdminBroadcast(text: String) = _adminBroadcasts.emit(text)
}
