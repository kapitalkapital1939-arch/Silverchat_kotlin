package com.silverchat.core.data.repository

import com.silverchat.core.common.result.ScResult
import com.silverchat.core.data.mapper.wireName
import com.silverchat.core.data.mapper.wireNamesOrNull
import com.silverchat.core.data.realtime.RealtimeBus
import com.silverchat.core.domain.repository.MarketRepository
import com.silverchat.core.domain.repository.SwapProposal
import com.silverchat.core.domain.repository.UsernameAvailability
import com.silverchat.core.model.Gift
import com.silverchat.core.model.GiftTransaction
import com.silverchat.core.model.ListingId
import com.silverchat.core.model.MarketFeed
import com.silverchat.core.model.MarketQuery
import com.silverchat.core.model.Message
import com.silverchat.core.model.OwnedGift
import com.silverchat.core.model.PurchaseRequest
import com.silverchat.core.model.PurchaseResult
import com.silverchat.core.model.SaleRequest
import com.silverchat.core.model.UserId
import com.silverchat.core.model.UsernameListing
import com.silverchat.core.model.UsernameOffer
import com.silverchat.core.network.api.SilverChatApi
import com.silverchat.core.network.dto.BuyRequest
import com.silverchat.core.network.dto.OfferRequest
import com.silverchat.core.network.dto.SellRequest
import com.silverchat.core.network.dto.SendGiftRequest
import com.silverchat.core.network.dto.SwapRequest
import com.silverchat.core.network.dto.SwapRespondRequest
import com.silverchat.core.network.mapper.apiCall
import com.silverchat.core.network.mapper.apiCallIdempotent
import com.silverchat.core.network.mapper.isUnknownOutcome
import com.silverchat.core.network.mapper.toDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json

/**
 * Маркет юзернеймов: витрина, покупка, продажа, офферы, свопы, подарки.
 *
 * ── Почему витрина не кэшируется в Room ─────────────────────────────────
 * Лот живёт минуты: его могут купить, пока пользователь смотрит карточку.
 * Показ устаревшего лота здесь дороже, чем лишний запрос, поэтому
 * `observeFeed` — холодный поток с одним обращением к сети, а живые
 * изменения приходят через [RealtimeBus.listings] и накладываются поверх.
 *
 * ── Идемпотентность денежных операций ───────────────────────────────────
 * Покупка, принятие оффера и конвертация подарка двигают сильверы. Обрыв
 * связи после такого запроса оставляет исход НЕОПРЕДЕЛЁННЫМ: сервер мог
 * списать деньги, а ответ — потеряться. Повтор без ключа идемпотентности
 * списал бы второй раз.
 *
 * Ключ генерируется на клиенте ДО отправки и уходит заголовком
 * `Idempotency-Key` (см. `HttpHeaders`). Дальше стратегии две:
 *
 *  - операция ПОГЛОЩАЕТ цель (купленный лот, принятый оффер, конвертированный
 *    подарок) — ключ берётся из [IdempotencyKeyStore] и живёт дольше вызова,
 *    поэтому ручной «Повторить» после таймаута уходит с тем же ключом;
 *  - операция законно повторяется (два одинаковых подарка, два лота) — ключ
 *    создаётся на вызов, а от потерянного ответа страхует автоматический
 *    повтор внутри одного вызова (`apiCallIdempotent`).
 *
 * Стабильный ключ для второй группы был бы ошибкой: он проглотил бы
 * намеренное повторное действие, приняв его за ретрай.
 */
@Singleton
class MarketRepositoryImpl @Inject constructor(
    private val api: SilverChatApi,
    private val bus: RealtimeBus,
    private val json: Json,
    private val idempotencyKeys: IdempotencyKeyStore,
) : MarketRepository {

    private val scope = CoroutineScope(SupervisorJob())

    /**
     * Лоты, изменённые по WebSocket после последней загрузки витрины.
     * Наложение идёт поверх сетевого ответа: серверная витрина — снимок на
     * момент запроса, а шина несёт то, что случилось позже.
     */
    private val liveListings = MutableStateFlow<Map<ListingId, UsernameListing>>(emptyMap())

    init {
        bus.listings
            .onEach { listing ->
                liveListings.value = liveListings.value + (listing.id to listing)
            }
            .launchIn(scope)
    }

    /* ── Витрина ───────────────────────────────────────────────────────── */

    override fun observeFeed(query: MarketQuery): Flow<MarketFeed> = flow {
        val feed = api.marketListings(
            query = query.search,
            // Сортировка и фильтры уходят проводными именами из @SerialName:
            // `PRICE_ASC` -> "price_asc", а не "price_asc" из lowercase.
            sort = query.sort.wireName(json),
            maxPrice = query.maxPriceSilver,
            minLength = query.minLength,
            maxLength = query.maxLength,
            categories = query.categories.wireNamesOrNull(json),
            rarity = query.rarityParam(json),
            page = query.page,
        ).toDomain()
        emit(feed.overlayLive(liveListings.value))
    }

    override fun observeListing(listingId: ListingId): Flow<UsernameListing?> = flow {
        val live = liveListings.value[listingId]
        // Живая версия свежее серверной: если лот только что купили,
        // показывать карточку «в продаже» из кэша — значит провоцировать
        // пользователя на покупку, которая гарантированно провалится.
        emit(live ?: runCatching { api.listing(listingId.raw).toDomain() }.getOrNull())
    }

    override fun observeMyListings(): Flow<List<UsernameListing>> = flow {
        val live = liveListings.value
        emit(api.myListings().map { dto ->
            val listing = dto.toDomain()
            live[listing.id] ?: listing
        })
    }

    override fun observeOffers(listingId: ListingId): Flow<List<UsernameOffer>> = flow {
        emit(api.offers(listingId.raw).map { it.toDomain() })
    }

    override fun observePurchaseHistory(): Flow<List<UsernameListing>> = flow {
        emit(api.purchaseHistory().map { it.toDomain() })
    }

    /* ── Сделки ────────────────────────────────────────────────────────── */

    override suspend fun buy(request: PurchaseRequest): ScResult<PurchaseResult> {
        val intent = "market.buy:${request.listingId.raw}"
        val key = idempotencyKeys.keyFor(intent)
        val result = apiCallIdempotent {
            api.buyUsername(
                BuyRequest(
                    listingId = request.listingId.raw,
                    offerSilver = request.offerSilver,
                ),
                idempotencyKey = key,
            ).toDomain()
        }
        if (!result.isUnknownOutcome()) idempotencyKeys.forget(intent)
        return result
    }

    override suspend fun sell(request: SaleRequest): ScResult<UsernameListing> = apiCallIdempotent {
        // Новый ключ на вызов: выставить один и тот же юзернейм дважды подряд
        // пользователь не может, но продать ДВА разных с одинаковой ценой — да.
        val listing = api.sellUsername(
            SellRequest(
                username = request.username.trim().removePrefix("@"),
                priceSilver = request.priceSilver,
                acceptOffers = request.acceptOffers,
                minOfferSilver = request.minOfferSilver,
                auction = request.auction,
                durationHours = request.durationHours,
            ),
            idempotencyKey = IdempotencyKeyStore.freshKey(),
        ).toDomain()
        liveListings.value = liveListings.value + (listing.id to listing)
        listing
    }

    override suspend fun makeOffer(listingId: ListingId, amountSilver: Long): ScResult<Unit> =
        apiCallIdempotent {
            // Оффер замораживает сильверы, поэтому ключ обязателен; но два
            // оффера по одному лоту — законное действие, ключ создаётся на вызов.
            api.makeOffer(
                listingId.raw,
                OfferRequest(amountSilver),
                idempotencyKey = IdempotencyKeyStore.freshKey(),
            )
            Unit
        }

    override suspend fun acceptOffer(offerId: String): ScResult<Unit> {
        // Принятый оффер поглощается: повтор в пределах окна — это ретрай, а не
        // второе намерение, поэтому ключ стабилен.
        val intent = "market.accept-offer:$offerId"
        val result = apiCallIdempotent {
            api.acceptOffer(offerId, idempotencyKeys.keyFor(intent))
            Unit
        }
        if (!result.isUnknownOutcome()) idempotencyKeys.forget(intent)
        return result
    }

    override suspend fun declineOffer(offerId: String): ScResult<Unit> = apiCall {
        api.declineOffer(offerId)
        Unit
    }

    override suspend fun cancelListing(listingId: ListingId): ScResult<Unit> = apiCall {
        api.cancelListing(listingId.raw)
        liveListings.value = liveListings.value - listingId
        Unit
    }

    /**
     * Проверка занятости юзернейма — до попытки выставить его на продажу.
     *
     * Ответ различает четыре исхода, и UI обязан их разделять: «свободен»,
     * «занят другим» (можно предложить своп), «зарезервирован администрацией»
     * (предлагать бессмысленно) и «недопустимое имя» (правила нейминга).
     */
    override suspend fun checkAvailability(username: String): ScResult<UsernameAvailability> =
        apiCall {
            val response = api.checkAvailability(username.trim().removePrefix("@"))
            // Порядок проверок важен: имя может быть одновременно недопустимым
            // и формально свободным — сообщать свободу в таком случае нельзя.
            when {
                !response.invalidReason.isNullOrBlank() ->
                    UsernameAvailability.Invalid(response.invalidReason)

                !response.reservedReason.isNullOrBlank() ->
                    UsernameAvailability.Reserved(response.reservedReason)

                !response.available -> UsernameAvailability.Taken(
                    ownerId = response.ownerId?.let(::UserId),
                    listingId = response.listingId?.let(::ListingId),
                )

                else -> UsernameAvailability.Available
            }
        }

    /* ── Свопы ─────────────────────────────────────────────────────────── */

    override suspend fun proposeSwap(
        myUsername: String,
        theirUsername: String,
        theirUserId: UserId,
    ): ScResult<SwapProposal> = apiCallIdempotent {
        val dto = api.proposeSwap(
            SwapRequest(
                myUsername = myUsername.removePrefix("@"),
                theirUsername = theirUsername.removePrefix("@"),
                theirUserId = theirUserId.raw,
            ),
            idempotencyKey = IdempotencyKeyStore.freshKey(),
        )
        SwapProposal(
            id = dto.id,
            myUsername = dto.myUsername,
            theirUsername = dto.theirUsername,
            theirUserId = UserId(dto.theirUserId),
            createdAt = dto.createdAt,
            expiresAt = dto.expiresAt,
        )
    }

    override suspend fun respondToSwap(swapId: String, accept: Boolean): ScResult<Unit> = apiCall {
        api.respondToSwap(swapId, SwapRespondRequest(accept))
        Unit
    }

    /* ── Подарки ───────────────────────────────────────────────────────── */

    override fun observeGifts(premiumOnly: Boolean): Flow<List<Gift>> = flow {
        emit(api.gifts(premiumOnly).map { it.toDomain() })
    }

    override fun observeMyGifts(): Flow<List<OwnedGift>> = flow {
        emit(api.myGifts().map { it.toDomain() })
    }

    /**
     * Отправка подарка создаёт сообщение в чате, поэтому возвращает [Message]:
     * UI показывает не просто «успех», а сам пузырь с анимацией подарка.
     */
    override suspend fun sendGift(transaction: GiftTransaction): ScResult<Message> =
        apiCallIdempotent {
            // Один и тот же подарок тому же получателю можно отправить дважды
            // намеренно, поэтому ключ создаётся на вызов, а не хранится.
            api.sendGift(
                SendGiftRequest(
                    giftId = transaction.giftId,
                    receiverId = transaction.receiverId.raw,
                    message = transaction.message?.trim()?.ifBlank { null },
                    anonymous = transaction.anonymous,
                ),
                idempotencyKey = IdempotencyKeyStore.freshKey(),
            ).toDomain()
        }

    override suspend fun convertGiftToSilver(ownedGiftId: String): ScResult<Long> {
        val intent = "market.convert-gift:$ownedGiftId"
        val result = apiCallIdempotent {
            // Возвращаем именно начисленную сумму, а не новый баланс: баланс мог
            // измениться параллельной транзакцией, и показывать его как «получено»
            // было бы обманом.
            api.convertGift(ownedGiftId, idempotencyKeys.keyFor(intent)).silverGained
        }
        if (!result.isUnknownOutcome()) idempotencyKeys.forget(intent)
        return result
    }

    override suspend fun upgradeGift(ownedGiftId: String): ScResult<OwnedGift> {
        val intent = "market.upgrade-gift:$ownedGiftId"
        val result = apiCallIdempotent {
            api.upgradeGift(ownedGiftId, idempotencyKeys.keyFor(intent)).toDomain()
        }
        if (!result.isUnknownOutcome()) idempotencyKeys.forget(intent)
        return result
    }

    /* ── Внутреннее ────────────────────────────────────────────────────── */

    /** Подменяет в витрине лоты, изменённые по WebSocket после снимка. */
    private fun MarketFeed.overlayLive(
        live: Map<ListingId, UsernameListing>,
    ): MarketFeed = if (live.isEmpty()) {
        this
    } else {
        copy(listings = listings.map { live[it.id] ?: it })
    }

    /**
     * Фильтр редкости — проводными именами.
     *
     * Вынесен в функцию, а не инлайнится в вызов API, чтобы пустой список
     * не отправлялся как `rarity=` (пустая строка): сервер интерпретировал бы
     * это как «фильтр задан, но ничего не подходит» вместо «фильтра нет».
     */
}

/** См. [MarketRepositoryImpl.observeFeed]: редкость отдельным query-параметром. */
private fun MarketQuery.rarityParam(json: Json): String? = rarity.wireNamesOrNull(json)
