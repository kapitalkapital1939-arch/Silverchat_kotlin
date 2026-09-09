package com.silverchat.core.model

/* =========================================================================
   РУССКИЕ ПОДПИСИ ДОМЕННЫХ ПЕРЕЧИСЛЕНИЙ
   ------------------------------------------------------------------------
   Все пользовательские строки — в одном месте и в :core:model, а не в UI.

   Почему не в designsystem и не в фичах:
    - подписи перечислений это часть домена (журнал аудита, причины жалоб,
      движения кошелька) — они одинаковы в 8 фичах;
    - если размазать их по экранам, перевод/переформулировка потребует правки
      десятка файлов и неизбежно даст расхождения («Продан» vs «Продажено»);
    - :core:model не зависит от Compose, поэтому подписи тестируются
      обычными unit-тестами без Robolectric.
   ========================================================================= */

/** Причина движения сильверов — для журнала кошелька. */
val LedgerReason.labelRu: String
    get() = when (this) {
        LedgerReason.SIGNUP_BONUS -> "Бонус за регистрацию"
        LedgerReason.DAILY_BONUS -> "Ежедневный бонус"
        LedgerReason.STREAK_REWARD -> "Награда за серию"
        LedgerReason.GIFT_RECEIVED -> "Получен подарок"
        LedgerReason.GIFT_CONVERTED -> "Подарок конвертирован"
        LedgerReason.USERNAME_SALE -> "Продажа юзернейма"
        LedgerReason.USERNAME_PURCHASE -> "Покупка юзернейма"
        LedgerReason.USERNAME_OFFER -> "Ставка за юзернейм"
        LedgerReason.GIFT_PURCHASE -> "Покупка подарка"
        LedgerReason.PREMIUM_PURCHASE -> "Покупка Premium"
        LedgerReason.ADMIN_GRANT -> "Начисление администрацией"
        LedgerReason.ADMIN_REVOKE -> "Списание администрацией"
        LedgerReason.REFUND -> "Возврат средств"
        LedgerReason.TRANSFER -> "Перевод"
        LedgerReason.REFERRAL -> "Реферальное начисление"
    }

/** Действие администратора — для журнала аудита. */
val AdminAction.labelRu: String
    get() = when (this) {
        AdminAction.GRANT_VERIFIED -> "Выдана верификация"
        AdminAction.REVOKE_VERIFIED -> "Снята верификация"
        AdminAction.GRANT_PREMIUM -> "Выдан Premium"
        AdminAction.REVOKE_PREMIUM -> "Снят Premium"
        AdminAction.GRANT_DEVELOPER -> "Выдан статус разработчика"
        AdminAction.REVOKE_DEVELOPER -> "Снят статус разработчика"
        AdminAction.GRANT_ADMIN_ROLE -> "Выдана админ-роль"
        AdminAction.REVOKE_ADMIN_ROLE -> "Снята админ-роль"
        AdminAction.CREDIT_SILVER -> "Начислены сильверы"
        AdminAction.DEBIT_SILVER -> "Списаны сильверы"
        AdminAction.RESET_WALLET -> "Сброшен кошелёк"
        AdminAction.BAN_USER -> "Бан пользователя"
        AdminAction.UNBAN_USER -> "Разбан пользователя"
        AdminAction.RESTRICT_USER -> "Ограничение пользователя"
        AdminAction.DELETE_MESSAGE -> "Удалено сообщение"
        AdminAction.DELETE_CHAT -> "Удалён чат"
        AdminAction.BLOCK_USERNAME -> "Юзернейм изъят из маркета"
        AdminAction.ADJUST_LISTING_PRICE -> "Изменена цена лота"
        AdminAction.REFUND_TRANSACTION -> "Возврат по сделке"
        AdminAction.BROADCAST -> "Рассылка"
        AdminAction.RESOLVE_REPORT -> "Обработана жалоба"
    }

/** Роль администратора. */
val AdminRole.labelRu: String
    get() = when (this) {
        AdminRole.NONE -> "Без прав"
        AdminRole.MODERATOR -> "Модератор"
        AdminRole.SUPPORT -> "Поддержка"
        AdminRole.MARKET_MANAGER -> "Менеджер маркета"
        AdminRole.FINANCE -> "Финансы"
        AdminRole.ADMIN -> "Администратор"
        AdminRole.OWNER -> "Владелец"
    }

/** Категория подарка. */
val GiftCategory.labelRu: String
    get() = when (this) {
        GiftCategory.CLASSIC -> "Классический"
        GiftCategory.ANIMATED -> "Анимированный"
        GiftCategory.LUXURY -> "Люкс"
        GiftCategory.SEASONAL -> "Сезонный"
        GiftCategory.COLLECTIBLE -> "Коллекционный"
    }

/** Редкость юзернейма в маркете. */
val UsernameRarity.labelRu: String
    get() = when (this) {
        UsernameRarity.COMMON -> "Обычный"
        UsernameRarity.RARE -> "Редкий"
        UsernameRarity.EPIC -> "Эпический"
        UsernameRarity.LEGENDARY -> "Легендарный"
        UsernameRarity.GRAIL -> "Граль"
    }

/** Категория юзернейма (поисковые фильтры маркета). */
val UsernameCategory.labelRu: String
    get() = when (this) {
        UsernameCategory.SHORT -> "Короткие"
        UsernameCategory.WORD -> "Слова"
        UsernameCategory.NAME -> "Имена"
        UsernameCategory.PALINDROME -> "Палиндромы"
        UsernameCategory.REPEATED -> "Повторы"
        UsernameCategory.PREMIUM_ONLY -> "Премиум"
    }

/** Статус лота маркета. */
val ListingStatus.labelRu: String
    get() = when (this) {
        ListingStatus.AVAILABLE -> "Доступен"
        ListingStatus.RESERVED -> "Забронирован"
        ListingStatus.SOLD -> "Продан"
        ListingStatus.AUCTION -> "Аукцион"
        ListingStatus.BLOCKED -> "Изъят администрацией"
        ListingStatus.OWNED_BY_ME -> "Ваш юзернейм"
    }

/** Статус встречного предложения. */
val OfferStatus.labelRu: String
    get() = when (this) {
        OfferStatus.PENDING -> "На рассмотрении"
        OfferStatus.ACCEPTED -> "Принята"
        OfferStatus.DECLINED -> "Отклонена"
        OfferStatus.EXPIRED -> "Истекла"
    }

/** Причина жалобы. */
val ReportReason.labelRu: String
    get() = when (this) {
        ReportReason.SPAM -> "Спам"
        ReportReason.SCAM -> "Мошенничество"
        ReportReason.ABUSE -> "Оскорбления"
        ReportReason.NUDITY -> "Неприемлемый контент"
        ReportReason.VIOLENCE -> "Насилие"
        ReportReason.IMPERSONATION -> "Выдаёт себя за другого"
        ReportReason.ILLEGAL -> "Незаконная деятельность"
        ReportReason.OTHER -> "Другое"
    }

/** Статус модерационного кейса. */
val ModerationStatus.labelRu: String
    get() = when (this) {
        ModerationStatus.OPEN -> "Открыта"
        ModerationStatus.IN_REVIEW -> "В работе"
        ModerationStatus.RESOLVED -> "Решена"
        ModerationStatus.REJECTED -> "Отклонена"
    }

/** Тип объекта жалобы. */
val ModerationTargetType.labelRu: String
    get() = when (this) {
        ModerationTargetType.USER -> "Пользователь"
        ModerationTargetType.MESSAGE -> "Сообщение"
        ModerationTargetType.CHAT -> "Чат"
        ModerationTargetType.STORY -> "Сторис"
        ModerationTargetType.USERNAME -> "Юзернейм"
        ModerationTargetType.GIFT -> "Подарок"
    }

/** Что именно даёт Premium — подпись perk'а, если сервер не прислал [PremiumPerk.title]. */
val PremiumPerkCode.labelRu: String
    get() = when (this) {
        PremiumPerkCode.EXCLUSIVE_STICKERS -> "Эксклюзивные стикеры"
        PremiumPerkCode.EXCLUSIVE_GIFS -> "Эксклюзивные GIF"
        PremiumPerkCode.ANIMATED_AVATAR -> "Анимированная аватарка"
        PremiumPerkCode.ANIMATED_BANNER -> "Анимированный баннер"
        PremiumPerkCode.CUSTOM_THEMES -> "Своя тема оформления"
        PremiumPerkCode.FREE_REACTIONS -> "Любые эмодзи-реакции"
        PremiumPerkCode.NO_ADS -> "Без рекламы"
        PremiumPerkCode.LARGER_UPLOADS -> "Файлы до 4 ГБ"
        PremiumPerkCode.VOICE_TO_TEXT -> "Расшифровка голосовых"
        PremiumPerkCode.STORY_STEALTH -> "Скрытный просмотр сторис"
        PremiumPerkCode.USERNAME_DISCOUNT -> "Скидка в маркете юзернеймов"
        PremiumPerkCode.MORE_FOLDERS -> "Больше папок чатов"
        PremiumPerkCode.HD_CALLS -> "HD-качество звонков"
    }

/** Видимость «был(а) в сети». */
val VisibilityRule.labelRu: String
    get() = when (this) {
        VisibilityRule.EVERYBODY -> "Все"
        VisibilityRule.CONTACTS -> "Мои контакты"
        VisibilityRule.NOBODY -> "Никто"
    }

/** Приватность сторис. */
val StoryPrivacy.labelRu: String
    get() = when (this) {
        StoryPrivacy.EVERYONE -> "Все"
        StoryPrivacy.CONTACTS -> "Контакты"
        StoryPrivacy.CLOSE_FRIENDS -> "Близкие друзья"
        StoryPrivacy.SELECTED -> "Выбранные"
        StoryPrivacy.PRIVATE -> "Только я"
    }

/** Служебное действие в чате (системное сообщение). */
val ServiceAction.labelRu: String
    get() = when (this) {
        ServiceAction.CHAT_CREATED -> "Чат создан"
        ServiceAction.MEMBER_JOINED -> "Участник присоединился"
        ServiceAction.MEMBER_LEFT -> "Участник покинул чат"
        ServiceAction.MEMBER_INVITED -> "Участник приглашён"
        ServiceAction.MEMBER_KICKED -> "Участник исключён"
        ServiceAction.TITLE_CHANGED -> "Название изменено"
        ServiceAction.AVATAR_CHANGED -> "Аватар изменён"
        ServiceAction.PINNED_MESSAGE -> "Сообщение закреплено"
        ServiceAction.CALL_MISSED -> "Пропущенный звонок"
        ServiceAction.CALL_ENDED -> "Звонок завершён"
        ServiceAction.GIFT_SENT -> "Отправлен подарок"
        ServiceAction.PREMIUM_GIFTED -> "Подарен Premium"
        ServiceAction.USERNAME_PURCHASED -> "Куплен юзернейм"
        ServiceAction.STORY_PUBLISHED -> "Опубликована сторис"
    }

/** Причина завершения звонка. */
val CallEndReason.labelRu: String
    get() = when (this) {
        CallEndReason.HANGUP -> "Звонок завершён"
        CallEndReason.DECLINED -> "Звонок отклонён"
        CallEndReason.MISSED -> "Пропущенный звонок"
        CallEndReason.BUSY -> "Абонент занят"
        CallEndReason.TIMEOUT -> "Нет ответа"
        CallEndReason.NETWORK_ERROR -> "Ошибка соединения"
        CallEndReason.MEDIA_ERROR -> "Ошибка медиаустройств"
        CallEndReason.FORBIDDEN -> "Пользователь запретил звонки"
    }

/** Тип чата — подпись в инфо-панели. */
val ChatType.labelRu: String
    get() = when (this) {
        ChatType.PERSONAL -> "Личный чат"
        ChatType.SECRET -> "Секретный чат"
        ChatType.GROUP -> "Группа"
        ChatType.SUPERGROUP -> "Супергруппа"
        ChatType.CHANNEL -> "Канал"
        ChatType.BROADCAST -> "Канал-рассылка"
        ChatType.BOT -> "Бот"
        ChatType.SAVED -> "Избранное"
    }
