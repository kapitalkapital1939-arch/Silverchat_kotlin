package com.silverchat.core.data.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.data.mapper.toDomain
import com.silverchat.core.data.mapper.toEntity
import com.silverchat.core.data.realtime.RealtimeBus
import com.silverchat.core.database.dao.LedgerDao
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.model.LedgerEntry
import com.silverchat.core.model.PremiumPerkCode
import com.silverchat.core.model.PremiumStatus
import com.silverchat.core.model.PremiumTier
import com.silverchat.core.model.PremiumTierId
import com.silverchat.core.model.Streak
import com.silverchat.core.model.UserId
import com.silverchat.core.model.Wallet
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.dto.GiftPremiumRequest
import com.silverchat.core.network.dto.PurchasePremiumRequest
import com.silverchat.core.network.dto.TransferRequest
import com.silverchat.core.network.mapper.apiCall
import com.silverchat.core.network.mapper.apiCallIdempotent
import com.silverchat.core.network.mapper.isUnknownOutcome
import com.silverchat.core.network.mapper.toDomain as dtoToDomain
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

/**
 * Кошелёк, сильверы, стрики и Premium.
 *
 * ── Почему баланс в памяти, а история в Room ────────────────────────────
 * Баланс меняется по WebSocket (`wallet.updated`) и обязан обновляться
 * мгновенно во всех экранах сразу: бейдж в нижней панели, шапка маркета,
 * экран кошелька. `StateFlow` даёт это без запросов к БД.
 *
 * История операций длинная и читается страницами — её держим в Room, чтобы
 * прокрутка не дёргала сеть на каждый кадр.
 *
 * ── Идемпотентность Premium ─────────────────────────────────────────────
 * Покупка подписки списывает сильверы. Ретрай без ключа идемпотентности
 * продлил бы подписку дважды и списал дважды, поэтому ключ генерируется на
 * клиенте ДО отправки, а не на сервере.
 */
@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val api: SilverChatApi,
    private val bus: RealtimeBus,
    private val ledgerDao: LedgerDao,
    private val idempotencyKeys: IdempotencyKeyStore,
) : WalletRepository {

    private val scope = CoroutineScope(SupervisorJob())

    private val walletState = MutableStateFlow<Wallet?>(null)
    private val streakState = MutableStateFlow<Streak?>(null)
    private val premiumState = MutableStateFlow<PremiumStatus>(PremiumStatus.None)
    private val tiersState = MutableStateFlow<List<PremiumTier>>(emptyList())

    init {
        bus.wallet.onEach { walletState.value = it }.launchIn(scope)
        bus.streak.onEach { streakState.value = it }.launchIn(scope)
        bus.premium.onEach { premiumState.value = it }.launchIn(scope)
    }

    /* ── Наблюдение ────────────────────────────────────────────────────── */

    /**
     * Баланс с ленивой подкачкой.
     *
     * Первый подписчик инициирует сетевой запрос; дальше изменения приходят
     * по WebSocket. `null` до первой загрузки — UI показывает скелетон, а не
     * «0 серебра», потому что ноль и «ещё не знаем» — разные состояния.
     */
    override fun observeWallet(): Flow<Wallet?> = flow {
        if (walletState.value == null) {
            runCatching { api.wallet().dtoToDomain() }.onSuccess { walletState.value = it }
        }
        walletState.collect { emit(it) }
    }

    /**
     * История операций: кэш отдаётся немедленно, сеть догоняет его в фоне.
     *
     * `onStart` выполняет запрос в контексте подписчика, поэтому медленная
     * сеть не блокирует первую отрисовку — пользователь видит сохранённые
     * проводки сразу.
     */
    override fun observeLedger(): Flow<List<LedgerEntry>> =
        ledgerDao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            .onStart { refreshLedger() }

    /**
     * Догрузка страницы истории.
     *
     * Курсор берётся из кэша, а не хранится отдельно: идентификатор самой
     * старой записи уже лежит в Room, а отдельный курсор пришлось бы
     * переживать смерть процесса и рассинхронизировать с таблицей.
     *
     * Пустой кэш означает, что догружать не от чего — нужен первый запрос,
     * который сделает [observeLedger] при подписке. Возвращаем `false`, а не
     * ошибку: состояние «нечего догружать» не требует сообщения пользователю.
     */
    override suspend fun loadMoreLedger(): ScResult<Boolean> = apiCall {
        val cursor = ledgerDao.oldestId() ?: return@apiCall false
        val page = api.ledger(limit = LEDGER_PAGE, before = cursor)
        if (page.isEmpty()) return@apiCall false
        ledgerDao.upsertAll(page.map { it.dtoToDomain().toEntity() })
        // Полная страница — значит история, скорее всего, продолжается.
        // Сервер вправе вернуть меньше `limit` и на середине, тогда следующая
        // догрузка просто получит пустой список и остановится.
        page.size >= LEDGER_PAGE
    }

    override fun observeStreak(): Flow<Streak?> = flow {
        if (streakState.value == null) {
            runCatching { api.streak().dtoToDomain() }.onSuccess { streakState.value = it }
        }
        streakState.collect { emit(it) }
    }

    override suspend fun claimDailyStreak(): ScResult<Streak> {
        val intent = INTENT_CLAIM_STREAK
        val result = apiCallIdempotent {
            api.claimStreak(idempotencyKeys.keyFor(intent)).dtoToDomain()
        }
        if (!result.isUnknownOutcome()) idempotencyKeys.forget(intent)
        return result.onSuccess { claimed ->
            streakState.value = claimed
            // Начисление за стрик приходит отдельной проводкой: без обновления
            // истории кошелёк разошёлся бы с балансом. Побочные эффекты стоят
            // ЗДЕСЬ, а не внутри повторяемого блока, иначе ретрай обновил бы
            // журнал дважды.
            refreshLedger()
        }
    }

    override suspend fun transfer(
        toUserId: UserId,
        amount: Long,
        comment: String?,
    ): ScResult<LedgerEntry> = apiCallIdempotent {
        val entry = api.transfer(
            TransferRequest(
                toUserId = toUserId.raw,
                amount = amount,
                comment = comment?.trim()?.ifBlank { null },
            ),
            // Ключ на вызов: два одинаковых перевода одному получателю —
            // законное действие, стабильный ключ молча проглотил бы второе.
            idempotencyKey = IdempotencyKeyStore.freshKey(),
        ).dtoToDomain()
        ledgerDao.upsert(entry.toEntity())
        // Баланс изменился, но `wallet.updated` может прийти с задержкой —
        // обновляем локально, чтобы счётчик в шапке не отставал от перевода.
        walletState.value?.let { current ->
            walletState.value = current.copy(
                balance = current.balance - amount,
                spentTotal = current.spentTotal + amount,
                updatedAt = System.currentTimeMillis(),
            )
        }
        entry
    }

    /* ── Premium ───────────────────────────────────────────────────────── */

    override fun observePremiumStatus(): Flow<PremiumStatus> = flow {
        if (premiumState.value == PremiumStatus.None) {
            runCatching { api.premiumStatus().dtoToDomain() }
                .onSuccess { premiumState.value = it }
        }
        premiumState.collect { emit(it) }
    }

    override fun observePremiumTiers(): Flow<List<PremiumTier>> = flow {
        if (tiersState.value.isEmpty()) {
            runCatching { api.premiumTiers().map { it.dtoToDomain() } }
                .onSuccess { tiersState.value = it }
        }
        tiersState.collect { emit(it) }
    }

    override suspend fun purchasePremium(tierId: PremiumTierId): ScResult<PremiumStatus> {
        // Подписка поглощает тариф: купить тот же тариф второй раз в пределах
        // окна нельзя, значит повтор — это ретрай, и ключ стабилен.
        val intent = "wallet.premium:${tierId.raw}"
        val result = apiCallIdempotent {
            api.purchasePremium(
                PurchasePremiumRequest(tierId = tierId.raw),
                idempotencyKey = idempotencyKeys.keyFor(intent),
            ).dtoToDomain()
        }
        if (!result.isUnknownOutcome()) idempotencyKeys.forget(intent)
        return result.onSuccess { status -> premiumState.value = status }
    }

    override suspend fun giftPremium(
        toUserId: UserId,
        tierId: PremiumTierId,
    ): ScResult<PremiumStatus> = apiCallIdempotent {
        // Ответ описывает статус ПОЛУЧАТЕЛЯ, а не дарящего: записывать его в
        // локальный premiumState нельзя, иначе UI показал бы подписку,
        // которой у текущего пользователя нет.
        // Ключ на вызов: подарить подписку дважды подряд — законное действие.
        api.giftPremium(
            GiftPremiumRequest(
                toUserId = toUserId.raw,
                tierId = tierId.raw,
            ),
            idempotencyKey = IdempotencyKeyStore.freshKey(),
        ).dtoToDomain()
    }

    override suspend fun cancelAutoRenew(): ScResult<Unit> = apiCall {
        api.cancelAutoRenew()
        // Подписка остаётся активной до конца оплаченного периода — меняем
        // только флаг автопродления, а не сбрасываем статус целиком.
        premiumState.value = (premiumState.value as? PremiumStatus.Active)
            ?.copy(autoRenew = false)
            ?: premiumState.value
    }

    /**
     * Доступна ли конкретная премиум-возможность.
     *
     * Проверяется по перкам активного тарифа, а не по факту наличия подписки:
     * тарифы различаются набором возможностей, и «Premium есть» не означает
     * «доступны анимированные аватары».
     */
    override fun observePerkAvailable(perk: PremiumPerkCode): Flow<Boolean> =
        combine(premiumState, tiersState) { status, tiers ->
            when {
                // Lifetime даёт всё: у него нет тарифа с урезанным набором.
                status == PremiumStatus.Lifetime -> true
                !status.isActive -> false
                else -> {
                    val tierId = (status as? PremiumStatus.Active)?.tier ?: return@combine false
                    tiers.firstOrNull { it.id == tierId }
                        ?.perks
                        ?.any { it.code == perk } == true
                }
            }
        }

    /* ── Внутреннее ────────────────────────────────────────────────────── */

    /**
     * Первая страница истории.
     *
     * Ошибка сети не должна ронять поток подписчика: в кэше уже есть то, что
     * было загружено раньше, и показать устаревший журнал лучше, чем пустой
     * экран. Запрос идёт без курсора — сервер отдаёт самые свежие записи.
     */
    private suspend fun refreshLedger() {
        runCatching { api.ledger(limit = LEDGER_PAGE).map { it.dtoToDomain().toEntity() } }
            .onSuccess { ledgerDao.upsertAll(it) }
    }

    private companion object {
        /** Размер страницы журнала: и первый запрос, и каждая догрузка. */
        const val LEDGER_PAGE = 50

        /**
         * Намерение получения награды за стрик.
         *
         * Без идентификатора дня: сервер начисляет стрик не чаще раза в сутки,
         * поэтому в пределах клиентского окна (10 минут) повтор может быть
         * только ретраем. Дата в ключе не успела бы смениться.
         */
        const val INTENT_CLAIM_STREAK = "wallet.claim-streak"
    }
}
