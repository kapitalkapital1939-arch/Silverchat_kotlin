package com.silverchat.core.domain.usecase.message

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.base.SuspendUseCase
import com.silverchat.core.domain.repository.MessageRepository
import com.silverchat.core.domain.repository.WalletRepository
import com.silverchat.core.model.ChatId
import com.silverchat.core.model.MessageId
import com.silverchat.core.model.QuickReactions
import com.silverchat.core.model.ReactionKind
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Реакция на сообщение (toggle-семантика).
 *
 * Двойной тап по сообщению ставит [QuickReactions.DEFAULT_DOUBLE_TAP] —
 * этот сценарий идёт через тот же UseCase, чтобы не расходилась логика.
 *
 * Premium-гейт: базовый набор [ReactionKind] доступен всем, а любые эмодзи
 * (free reactions) — только с подпиской; сервер отдаёт 403, а мы показываем
 * paywall ДО запроса, чтобы не дёргать сеть.
 */
class ToggleReactionUseCase @Inject constructor(
    private val messages: MessageRepository,
    private val wallet: WalletRepository,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<ToggleReactionParams, ReactionOutcome>(dispatchers) {

    override suspend fun execute(params: ToggleReactionParams): ScResult<ReactionOutcome> {
        if (!params.reactionsEnabledInChat) {
            return ScResult.Failure(ScError.Forbidden("Реакции отключены в этом чате", "chat.react"))
        }

        val allowed = messages.availableReactions(params.chatId).getOrNull().orEmpty()
        if (allowed.isNotEmpty() && params.kind !in allowed) {
            return ScResult.Failure(
                ScError.Forbidden("Эта реакция недоступна в данном чате", "chat.react"),
            )
        }

        // Кастомная эмодзи-реакция (не из базового набора) — привилегия Premium
        if (params.isCustomEmoji && !wallet.observePerkAvailable(
                com.silverchat.core.model.PremiumPerkCode.FREE_REACTIONS,
            ).first()
        ) {
            return ScResult.Failure(
                ScError.PremiumRequired(
                    message = "Любые эмодзи-реакции доступны в SilverChat Premium",
                    perk = "FREE_REACTIONS",
                ),
            )
        }

        return messages.toggleReaction(params.chatId, params.messageId, params.kind)
            .map {
                if (params.currentReaction == params.kind) ReactionOutcome.Removed(params.kind)
                else ReactionOutcome.Added(params.kind)
            }
    }
}

data class ToggleReactionParams(
    val chatId: ChatId,
    val messageId: MessageId,
    val kind: ReactionKind,
    val currentReaction: ReactionKind?,
    val reactionsEnabledInChat: Boolean = true,
    val isCustomEmoji: Boolean = false,
)

sealed interface ReactionOutcome {
    data class Added(val kind: ReactionKind) : ReactionOutcome
    data class Removed(val kind: ReactionKind) : ReactionOutcome
}

/** Быстрая реакция по двойному тапу — без панели выбора. */
class QuickReactUseCase @Inject constructor(
    private val toggle: ToggleReactionUseCase,
) {
    suspend operator fun invoke(chatId: ChatId, messageId: MessageId, current: ReactionKind?): ScResult<ReactionOutcome> =
        toggle(
            ToggleReactionParams(
                chatId = chatId,
                messageId = messageId,
                kind = QuickReactions.DEFAULT_DOUBLE_TAP,
                currentReaction = current,
            ),
        )
}
