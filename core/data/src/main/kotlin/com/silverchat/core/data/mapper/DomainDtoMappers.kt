package com.silverchat.core.data.mapper

import com.silverchat.core.domain.repository.AdminAccess
import com.silverchat.core.domain.repository.AdminUserDetails
import com.silverchat.core.domain.repository.DeviceSession
import com.silverchat.core.model.AdminAction
import com.silverchat.core.network.dto.AdminAccessDto
import com.silverchat.core.network.dto.AdminUserDetailsDto
import com.silverchat.core.network.dto.SessionDto
import com.silverchat.core.network.mapper.toDomain
import com.silverchat.core.network.mapper.toDomainRole

/* =========================================================================
   МАППЕРЫ В ТИПЫ :core:domain
   ------------------------------------------------------------------------
   :core:network намеренно НЕ зависит от :core:domain — иначе получился бы
   цикл (domain объявляет интерфейсы репозиториев, data их реализует, а
   network нужен data). Поэтому все DTO, чей доменный аналог объявлен в
   :core:domain, маппятся здесь.

   В :core:network остаются только мапперы в :core:model — он виден всем.
   ========================================================================= */

/** Активные сессии устройства для экрана «Настройки -> Устройства». */
fun SessionDto.toDomainSession(): DeviceSession = DeviceSession(
    id = id,
    deviceName = deviceName,
    platform = platform,
    appVersion = appVersion,
    lastActiveAt = lastActiveAt,
    ip = ip,
    locationHint = locationHint,
    isCurrent = isCurrent,
)

/**
 * Доступ к админ-панели.
 *
 * `capabilities` приходят проводными именами (`"grant_verified"`), которые
 * совпадают с `AdminAction.name.lowercase()` — расхождений в этом enum нет,
 * в отличие от `UsernameCategory`. Неизвестное действие отбрасывается:
 * сервер может добавить новую способность раньше обновления клиента, и
 * падать на этом нельзя.
 */
fun AdminAccessDto.toDomain(): AdminAccess = AdminAccess(
    allowed = allowed,
    role = toDomainRole(),
    isMasterAccount = isMasterAccount,
    capabilities = capabilities.mapNotNull { raw ->
        AdminAction.entries.firstOrNull { it.name.equals(raw, true) }
    }.toSet(),
)

/** Полная карточка пользователя для админки. */
fun AdminUserDetailsDto.toDomain(): AdminUserDetails = AdminUserDetails(
    user = user.toDomain(),
    wallet = wallet.toDomain(),
    streak = streak.toDomain(),
    premium = premium.toDomain(),
    ownedUsernames = ownedUsernames,
    activeListings = activeListings,
    reportsAgainst = reportsAgainst,
    sessions = sessions.map { it.toDomainSession() },
    recentAudit = recentAudit.map { it.toDomain() },
)
