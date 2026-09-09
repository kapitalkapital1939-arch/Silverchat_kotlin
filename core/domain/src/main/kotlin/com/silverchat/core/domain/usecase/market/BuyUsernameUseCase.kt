package com.silverchat.core.domain.usecase.market

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.base.SuspendUseCase
import com.silverchat.core.domain.repository.MarketRepository
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.model.ListingId
import com.silverchat.core.model.PurchaseRequest
import com.silverchat.core.model.PurchaseResult
import com.silverchat.core.model.UsernameRarity
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Покупка юзернейма в маркете за сильверы.
 *
 * Порядок проверок (важен: каждая следующая экономит запрос к серверу):
 *  1. листинг существует и доступен;
 *  2. цена с учётом скидки Premium;
 *  3. хватает ли сильверов;
 *  4. валиден ли username (не зарезервирован администрацией);
 *  5. отправка заявки -> escrow на бэкенде -> [PurchaseResult].
 *
 * Баланс никогда не меняется локально: новый баланс приходит в ответе сервера.
 */
class BuyUsernameUseCase @Inject constructor(
    private val market: MarketRepository,
    private val wallet: WalletRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<BuyUsernameParams, PurchaseResult>(dispatchers) {

    override suspend fun execute(params: BuyUsernameParams): ScResult<PurchaseResult> {
        val listing = market.observeListing(params.listingId).first()
            ?: return ScResult.Failure(ScError.NotFound("Лот не найден"))

        if (listing.status != com.silverchat.core.model.ListingStatus.AVAILABLE) {
            return ScResult.Failure(
                ScError.Conflict(
                    when (listing.status) {
                        com.silverchat.core.model.ListingStatus.SOLD -> "Юзернейм уже куплен"
                        com.silverchat.core.model.ListingStatus.RESERVED -> "Юзернейм забронирован другим покупателем"
                        com.silverchat.core.model.ListingStatus.BLOCKED -> "Юзернейм заблокирован администрацией"
                        com.silverchat.core.model.ListingStatus.OWNED_BY_ME -> "Это ваш юзернейм"
                        else -> "Лот недоступен для покупки"
                    },
                ),
            )
        }

        // Скидка Premium в маркете (перк USERNAME_DISCOUNT)
        val hasDiscount = wallet.observePerkAvailable(
            com.silverchat.core.model.PremiumPerkCode.USERNAME_DISCOUNT,
        ).first()
        val finalPrice = if (hasDiscount) {
            (listing.priceSilver * (100 - PREMIUM_DISCOUNT_PERCENT) / 100).coerceAtLeast(MIN_PRICE)
        } else {
            listing.priceSilver
        }

        val balance = wallet.observeWallet().first()
            ?: return ScResult.Failure(ScError.Network("Кошелёк ещё не загружен"))

        val offer = params.offerSilver ?: finalPrice
        if (offer > balance.available) {
            return ScResult.Failure(
                ScError.InsufficientFunds(
                    message = "Недостаточно сильверов: нужно ${offer - balance.available} ещё",
                    required = offer,
                    available = balance.available,
                ),
            )
        }

        if (offer < (listing.minOfferSilver ?: finalPrice)) {
            return ScResult.Failure(
                ScError.Validation(
                    "Минимальная ставка — ${listing.minOfferSilver ?: finalPrice} сильверов",
                    field = "offer",
                ),
            )
        }

        return market.buy(
            PurchaseRequest(
                listingId = params.listingId,
                buyerId = params.buyerId,
                offerSilver = if (params.offerSilver != null) offer else null,
                useBalance = true,
            ),
        )
    }

    private companion object {
        const val PREMIUM_DISCOUNT_PERCENT = 10
        const val MIN_PRICE = 1L
    }
}

data class BuyUsernameParams(
    val listingId: ListingId,
    val buyerId: com.silverchat.core.model.UserId,
    /** null = купить по цене; не null = сделать ставку/оффер. */
    val offerSilver: Long? = null,
)

/**
 * Оценка редкости юзернейма.
 * Дублирует серверную логику, чтобы показывать бейдж мгновенно,
 * до ответа API (сервер всё равно пересчитывает и имеет последнее слово).
 */
object UsernameRarityCalculator {

    private val DICTIONARY_PREFIXES = setOf(
        "gold", "silver", "king", "queen", "star", "moon", "sun", "wolf",
        "fire", "ice", "dark", "light", "crypto", "coin", "token", "bank",
    )

    fun calculate(username: String): UsernameRarity {
        val value = username.trim().removePrefix("@").lowercase()
        if (value.isEmpty()) return UsernameRarity.COMMON

        var score = 0.0

        // Длина — главный фактор: короткие имена редки
        score += when (value.length) {
            1 -> 60.0
            2 -> 50.0
            3 -> 38.0
            4 -> 26.0
            5 -> 16.0
            6 -> 10.0
            7 -> 6.0
            else -> 2.0
        }

        // Словарное слово
        if (DICTIONARY_PREFIXES.any { value.startsWith(it) || value == it }) score += 12.0

        // Только буквы (без цифр и подчёркиваний) — «чистый» ник
        if (value.all { it.isLetter() }) score += 8.0

        // Палиндром
        if (value.length >= 4 && value == value.reversed()) score += 15.0

        // Повторяющиеся символы: aaa, xxxx
        if (value.length >= 3 && value.toSet().size == 1) score += 18.0

        // Без гласных или короткие паттерны типа "btc"
        if (value.length <= 4 && value.none { it in "aeiou" }) score += 10.0

        return when {
            score >= 55 -> UsernameRarity.GRAIL
            score >= 40 -> UsernameRarity.LEGENDARY
            score >= 25 -> UsernameRarity.EPIC
            score >= 14 -> UsernameRarity.RARE
            else -> UsernameRarity.COMMON
        }
    }

    /** Базовая цена по редкости — используется как подсказка при продаже. */
    fun suggestedPrice(rarity: UsernameRarity, length: Int): Long {
        val base = when (rarity) {
            UsernameRarity.COMMON -> 500L
            UsernameRarity.RARE -> 2_500L
            UsernameRarity.EPIC -> 12_000L
            UsernameRarity.LEGENDARY -> 60_000L
            UsernameRarity.GRAIL -> 250_000L
        }
        val lengthBonus = ((8 - length).coerceAtLeast(0)) * 400L
        return base + lengthBonus
    }
}

/** Проверка и предложение цены при выставлении собственного username на продажу. */
class PrepareSaleUseCase @Inject constructor(
    private val market: MarketRepository,
) {
    suspend operator fun invoke(username: String): ScResult<SaleSuggestion> {
        val availability = market.checkAvailability(username).getOrNull()
            ?: return ScResult.Failure(ScError.Network("Не удалось проверить username"))

        val rarity = UsernameRarityCalculator.calculate(username)
        val clean = username.trim().removePrefix("@").lowercase()

        return ScResult.Success(
            SaleSuggestion(
                username = clean,
                rarity = rarity,
                suggestedPrice = UsernameRarityCalculator.suggestedPrice(rarity, clean.length),
                isOwn = availability is com.silverchat.core.domain.repository.UsernameAvailability.Available,
            ),
        )
    }
}

data class SaleSuggestion(
    val username: String,
    val rarity: UsernameRarity,
    val suggestedPrice: Long,
    val isOwn: Boolean,
)
