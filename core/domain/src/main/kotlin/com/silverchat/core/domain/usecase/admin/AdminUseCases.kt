package com.silverchat.core.domain.usecase.admin

import com.silverchat.core.common.dispatcher.DispatcherProvider
import com.silverchat.core.common.result.ScError
import com.silverchat.core.common.result.ScResult
import com.silverchat.core.domain.base.SuspendUseCase
import com.silverchat.core.domain.repository.AdminAccess
import com.silverchat.core.domain.repository.AdminRepository
import com.silverchat.core.model.AdminAction
import com.silverchat.core.model.AdminGrantRequest
import com.silverchat.core.model.AdminRole
import com.silverchat.core.model.MasterAccount
import com.silverchat.core.model.User
import com.silverchat.core.model.UserId
import com.silverchat.core.model.Wallet
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Политика доступа к админ-панели.
 *
 * Клиентская проверка нужна ИСКЛЮЧИТЕЛЬНО для UX (не показывать раздел,
 * не рисовать кнопки). Источник истины — бэкенд: каждый admin-эндпоинт
 * закрыт middleware AdminGuard, а действия пишутся в неизменяемый аудит-лог.
 *
 * Мастер-аккаунт [MasterAccount.USERNAME] (@silver) получает роль OWNER
 * и полный набор [AdminAction].
 */
class ObserveAdminAccessUseCase @Inject constructor(
    private val repository: AdminRepository,
) {
    operator fun invoke(): Flow<AdminAccess> = repository.observeAccess().map { access ->
        // Дополнительная страховка: даже если сервер по ошибке вернул OWNER
        // для не-мастер-аккаунта, набор действий урезается по роли.
        access.copy(capabilities = capabilitiesFor(access.role))
    }

    companion object {
        fun capabilitiesFor(role: AdminRole): Set<AdminAction> = when (role) {
            AdminRole.NONE -> emptySet()

            AdminRole.MODERATOR -> setOf(
                AdminAction.DELETE_MESSAGE,
                AdminAction.DELETE_CHAT,
                AdminAction.RESTRICT_USER,
                AdminAction.BAN_USER,
                AdminAction.UNBAN_USER,
                AdminAction.RESOLVE_REPORT,
            )

            AdminRole.SUPPORT -> setOf(AdminAction.RESOLVE_REPORT)

            AdminRole.MARKET_MANAGER -> setOf(
                AdminAction.BLOCK_USERNAME,
                AdminAction.ADJUST_LISTING_PRICE,
                AdminAction.RESOLVE_REPORT,
            )

            AdminRole.FINANCE -> setOf(
                AdminAction.CREDIT_SILVER,
                AdminAction.DEBIT_SILVER,
                AdminAction.REFUND_TRANSACTION,
                AdminAction.RESET_WALLET,
            )

            AdminRole.ADMIN -> setOf(
                AdminAction.GRANT_VERIFIED, AdminAction.REVOKE_VERIFIED,
                AdminAction.GRANT_PREMIUM, AdminAction.REVOKE_PREMIUM,
                AdminAction.GRANT_DEVELOPER, AdminAction.REVOKE_DEVELOPER,
                AdminAction.CREDIT_SILVER, AdminAction.DEBIT_SILVER,
                AdminAction.BAN_USER, AdminAction.UNBAN_USER,
                AdminAction.RESTRICT_USER, AdminAction.DELETE_MESSAGE,
                AdminAction.RESOLVE_REPORT, AdminAction.BLOCK_USERNAME,
                AdminAction.ADJUST_LISTING_PRICE, AdminAction.BROADCAST,
            )

            // OWNER = @silver: вообще всё, включая выдачу ролей
            AdminRole.OWNER -> AdminAction.entries.toSet()
        }
    }
}

/**
 * Выдача статуса пользователю: верификация, премиум, разработчик, роль.
 *
 * Обязательное поле `reason` — без обоснования действие отклоняется:
 * аудит-лог должен объяснять, почему у пользователя появилась галочка.
 */
class GrantPrivilegeUseCase @Inject constructor(
    private val repository: AdminRepository,
    private val access: ObserveAdminAccessUseCase,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<GrantPrivilegeParams, User>(dispatchers) {

    override suspend fun execute(params: GrantPrivilegeParams): ScResult<User> {
        val current = access().first()
        val action = params.action

        if (!current.can(action)) {
            return ScResult.Failure(
                ScError.Forbidden(
                    message = "Недостаточно прав для действия ${action.name}",
                    requiredPermission = action.name,
                ),
            )
        }

        if (params.reason.isBlank() || params.reason.length < MIN_REASON_LENGTH) {
            return ScResult.Failure(
                ScError.Validation(
                    "Укажите причину (минимум $MIN_REASON_LENGTH символа) — она попадёт в аудит-лог",
                    field = "reason",
                ),
            )
        }

        // Защита от понижения самого себя и от изменения прав мастер-аккаунта
        if (params.targetUserId == params.actorUserId) {
            return ScResult.Failure(ScError.Validation("Нельзя изменить собственные права", "target"))
        }
        if (params.targetIsMasterAccount) {
            return ScResult.Failure(
                ScError.Forbidden("Права мастер-аккаунта ${MasterAccount.HANDLE} неизменяемы"),
            )
        }

        return repository.grant(
            AdminGrantRequest(
                targetUserId = params.targetUserId,
                action = action,
                amountSilver = params.amountSilver,
                durationDays = params.durationDays,
                role = params.role,
                reason = params.reason.trim(),
            ),
        )
    }

    private companion object {
        const val MIN_REASON_LENGTH = 4
    }
}

data class GrantPrivilegeParams(
    val actorUserId: UserId,
    val targetUserId: UserId,
    val targetIsMasterAccount: Boolean,
    val action: AdminAction,
    val reason: String,
    val amountSilver: Long? = null,
    val durationDays: Int? = null,
    val role: AdminRole? = null,
)

/**
 * Начисление сильверов любому пользователю в неограниченном количестве.
 *
 * Требование продукта — без лимита суммы. Поэтому единственная защита:
 * роль FINANCE/ADMIN/OWNER + обязательная причина + запись в аудит-лог.
 * Сумма всё равно валидируется на переполнение Long.
 */
class CreditSilverUseCase @Inject constructor(
    private val repository: AdminRepository,
    private val access: ObserveAdminAccessUseCase,
    dispatchers: DispatcherProvider,
) : SuspendUseCase<CreditSilverParams, Wallet>(dispatchers) {

    override suspend fun execute(params: CreditSilverParams): ScResult<Wallet> {
        val current = access().first()
        val action = if (params.amount >= 0) AdminAction.CREDIT_SILVER else AdminAction.DEBIT_SILVER

        if (!current.can(action)) {
            return ScResult.Failure(ScError.Forbidden("Нет прав на операции с валютой", action.name))
        }
        if (params.amount == 0L) {
            return ScResult.Failure(ScError.Validation("Сумма не может быть нулевой", "amount"))
        }
        if (params.reason.isBlank()) {
            return ScResult.Failure(ScError.Validation("Причина обязательна", "reason"))
        }

        val amount = params.amount.toLong()
        // Переполнение Long при «неограниченном» начислении — реальный риск
        if (amount == Long.MAX_VALUE || amount == Long.MIN_VALUE) {
            return ScResult.Failure(ScError.Validation("Сумма слишком велика", "amount"))
        }

        return if (amount >= 0) {
            repository.creditSilver(params.targetUserId, amount, params.reason.trim())
        } else {
            repository.debitSilver(params.targetUserId, -amount, params.reason.trim())
        }
    }
}

data class CreditSilverParams(
    val targetUserId: UserId,
    /** Число (не Long) намеренно: админ вводит сумму в поле ввода, парсим строго. */
    val amount: Long,
    val reason: String,
)
