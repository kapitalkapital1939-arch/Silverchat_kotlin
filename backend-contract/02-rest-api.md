# 02. REST API

Источник правды на клиенте: `core/network/src/main/kotlin/com/silverchat/core/network/api/SilverChatApi.kt`.
Каталог ниже сгенерирован из него и содержит **147 эндпоинтов**: `GET` — 44,
`POST` — 74, `PATCH` — 14, `DELETE` — 15.

## Конвенции

### Семантика методов

| Метод | Когда используется | Идемпотентность |
|---|---|---|
| `GET` | Чтение. Не изменяет состояние | Да |
| `POST` | Создание ресурса или действие, которое нельзя выразить созданием | Нет — поэтому нужен `Idempotency-Key` |
| `PATCH` | Частичное изменение. Отсутствующее поле **не меняется** | Да |
| `DELETE` | Удаление. Отсутствие ресурса не ошибка | Да |
| `PUT` | Не используется намеренно: полная замена ресурса в проекте не нужна | — |

`PATCH` опирается на `explicitNulls = false`: клиент не отправляет поля со
значением `null`, поэтому сервер трактует отсутствие поля как «оставить как
есть», а не «очистить». Очистка выполняется отдельным `DELETE`
(`users/me/location`, `users/me/working-hours`, `users/me/username`).

### Ответ без тела

Половина эндпоинтов (`EmptyResponse`) не возвращает данных — только факт
успеха. Сервер отвечает `200 OK` с телом `{"ok":true}`. Тело обязательно:
пустой ответ ломает разбор у клиента, ожидающего объект.

### Пагинация

Единой схемы нет — и это осознанно: список сообщений, список участников и
лента маркета решают разные задачи, и общий механизм сделал бы хотя бы одну
из них неудобной. Пять вариантов ниже исчерпывают все 147 эндпоинтов.

**1. Сообщения — курсор по идентификатору, в обе стороны**

```
GET /v1/chats/{id}/messages?before=<message_id>&limit=40
GET /v1/chats/{id}/messages?after=<message_id>&limit=40
```

| Параметр | Тип | Значение |
|---|---|---|
| `before` | string | Идентификатор сообщения; вернуть более ранние |
| `after` | string | Идентификатор сообщения; вернуть более поздние |
| `limit` | int | По умолчанию 40 |

```json
{ "messages": [], "has_more": true, "chat": null }
```

Курсор, а не offset, потому что в активный чат сообщения приходят во время
чтения: offset-страница сдвинулась бы, и пользователь увидел бы дубликат или
пропуск. `before` и `after` взаимно исключаются; если переданы оба, сервер
использует `before`. Поле `chat` непустое только когда запрос впервые
открывает чат — так клиент получает метаданные чата одним запросом.

**2. Участники — offset**

```
GET /v1/chats/{id}/members?offset=0&limit=…
```

```json
{ "members": [], "total": 1250, "next_offset": 100 }
```

Offset здесь уместен: состав группы меняется на порядки медленнее, чем лента
сообщений, а экрану нужно показать общее число участников и позицию в нём.
`next_offset = null` означает конец списка.

**3. Лента маркета — страницы с фильтрами**

```
GET /v1/market/usernames?q=&sort=popular&max_price=&min_length=&max_length=&categories=&rarity=&page=0
```

```json
{
  "listings": [],
  "gifts": [],
  "premium_tiers": [],
  "trending": [],
  "volume_24h": 0,
  "total": 0
}
```

Страничная модель, потому что это каталог с набором фильтров: пользователь
меняет фильтр и возвращается к первой странице. `sort` принимает `popular`
(по умолчанию); `total` — число лотов, подходящих под фильтр, нужно для
счётчика в шапке.

**4. Список чатов — инкрементальная синхронизация, без пагинации**

```
GET /v1/chats?folder=&archived=false&since=<timestamp_ms>
```

```json
{
  "chats": [],
  "folders": [],
  "total_unread": 0,
  "server_time": 1730000000000
}
```

`since` — момент последней успешной синхронизации. Сервер возвращает чаты,
изменившиеся после него, поэтому первый вход (без `since`) получает полный
список, а последующие — только дельту. `server_time` обязателен: клиент
сохраняет его как следующий `since`, а не своё локальное время, иначе
расхождение часов на устройстве привело бы к пропуску обновлений.

Количество чатов у пользователя ограничено, и офлайн-кэш в Room хранит их
все, поэтому пагинация здесь не нужна принципиально.

**5. Журнал кошелька — курсор по идентификатору, только назад**

```
GET /v1/wallet/ledger?limit=50
GET /v1/wallet/ledger?limit=50&before=<ledger_entry_id>
```

```json
[ { "id": "l_91", "amount": -2500, "reason": "market_purchase", "balance_after": 1250, "created_at": 1730000000000 } ]
```

| Параметр | Тип | Значение |
|---|---|---|
| `before` | string | Идентификатор записи; вернуть более ранние. Отсутствует — вернуть самые свежие |
| `limit` | int | По умолчанию 50 |

Схема та же, что у сообщений, но только в одну сторону: журнал — хронология
операций, и «более поздние» относительно уже полученной страницы не имеют
смысла. Новые записи приходят событием `wallet.ledger.append`
([04-events-catalog.md](04-events-catalog.md)), а не повторным запросом
первой страницы.

Ответ — плоский список, без `has_more`. **Пустой список означает конец
истории.** Отдельного флага не нужно: клиент запрашивает ровно `limit` записей
и по размеру ответа понимает, стоит ли продолжать. Это дешевле, чем считать
`has_more` на сервере, и не даёт расхождения между флагом и фактическим
содержимым страницы.

Курсор, а не offset, по той же причине, что и у сообщений: журнал пополняется
непрерывно, и offset-страница сдвигается под новыми записями.

**Обязанности сервера.** `before` указывает на запись, которая клиенту уже
известна, поэтому в ответ она попадать не должна — иначе на стыке страниц
появится дубликат. Записи возвращаются строго в порядке убывания `created_at`;
при совпадении меток порядок обязан быть детерминированным (вторичная
сортировка по `id`), иначе одна и та же страница при повторном запросе может
отдаться в другом порядке.

### Поиск

Четыре эндпоинта решают разные задачи, и смешивать их нельзя: три первых —
полнотекстовый поиск по своему типу сущности, четвёртый — точное разрешение
одного идентификатора.

| Эндпоинт | Параметры | Возвращает |
|---|---|---|
| `GET search/users` | `q`, `limit` (30) | `List<UserDto>` |
| `GET search/chats` | `q` | `List<ChatDto>` |
| `GET search/messages` | `q`, `chat_id?` | `List<MessageDto>` |
| `GET search/resolve` | `handle` | `ResolveResponse` |

`search/resolve` — не агрегатор поиска, а разрешение одной «ручки»: по строке
вида `@username`, `t.me/…` или приглашающей ссылке сервер определяет, что это.

```json
{
  "kind": "user",
  "user": { },
  "chat": null,
  "listing": null
}
```

`kind` принимает `user | chat | listing | not_found`. Заполнено ровно то поле,
которое соответствует `kind`; остальные — `null`. Такой дискриминатор нужен,
потому что `@alice` может одновременно быть юзернеймом пользователя и
лотом на маркете, и выбор между ними делает сервер, а не клиент.

Минимальная длина запроса на клиенте — 2 символа (`MIN_QUERY` в
`SearchUseCases`): поиск по одной букве даёт тысячи нерелевантных результатов
и нагружает сервер впустую. Сервер обязан принимать и `q` длиной 1, но
клиент такие запросы не отправляет.

### Загрузка медиа

Двухшаговая схема, потому что бэкенд не должен принимать гигабайты через
API-сервер:

1. `POST /v1/media/upload-ticket` — клиент сообщает размер, MIME и назначение:

```json
{
  "mime_type": "video/mp4",
  "size_bytes": 8421376,
  "chat_id": "c_9f2…",
  "kind": "circle"
}
```

`kind` принимает: `photo | video | voice | circle | file | avatar | banner | story`.

2. Сервер возвращает подписанный URL хранилища:

```json
{
  "upload_id": "u_71c…",
  "upload_url": "https://cdn.silver.chat/put/u_71c…?sig=…",
  "method": "PUT",
  "headers": { "Content-Type": "video/mp4" },
  "public_url": "https://cdn.silver.chat/m/u_71c….mp4",
  "thumb_url": "https://cdn.silver.chat/t/u_71c….jpg",
  "expires_in": 900
}
```

3. Клиент кладёт байты по `upload_url` указанным `method` с указанными
   `headers` и дальше использует `public_url` в сообщении.

Требования к серверу:
- `upload_url` обязан быть короткоживущим (`expires_in` ≤ 900 с) — утёкшая
  ссылка не должна давать постоянную запись в хранилище;
- заголовки из ответа клиент передаёт дословно, менять их нельзя;
- превышение `size_bytes` отклоняется на стороне хранилища, а не молча
  обрезается;
- `thumb_url` может быть `null` — тогда клиент генерирует превью сам.

### Ограничение частоты

Ответ `429` сопровождается заголовком `Retry-After` в секундах (как требует
RFC 9110) и телом с миллисекундной точностью:

```json
{
  "error": {
    "code": "rate_limited",
    "message": "Слишком много запросов",
    "retryable": true,
    "retry_after_ms": 4200
  }
}
```

Клиент не ретраит сам — он показывает состояние и ждёт указанное время.
Автоматический повтор при `429` привёл бы к лавине.

### Идемпотентность

Заголовок `Idempotency-Key` со значением UUIDv4 обязателен для 16 операций,
двигающих серебро или создающих оплаченную сущность. Ключ привязан к учётной
записи: сервер хранит пару `(<user_id>, ключ) → результат` не менее 24 часов и
при повторе возвращает **первоначальный** ответ с тем же статусом, не выполняя
действие второй раз.

**Стабильный ключ** — операция поглощает цель, поэтому повтор в коротком окне
может быть только ретраем:

| Путь | Метод клиента | Намерение |
|---|---|---|
| `POST market/usernames/buy` | `buyUsername` | `market.buy:<listing_id>` |
| `POST market/offers/{id}/accept` | `acceptOffer` | `market.accept-offer:<offer_id>` |
| `POST market/gifts/{ownedId}/convert` | `convertGift` | `market.convert-gift:<owned_gift_id>` |
| `POST market/gifts/{ownedId}/upgrade` | `upgradeGift` | `market.upgrade-gift:<owned_gift_id>` |
| `POST wallet/streak/claim` | `claimStreak` | `wallet.claim-streak` |
| `POST premium/purchase` | `purchasePremium` | `wallet.premium:<tier_id>` |

Ключ живёт 10 минут и сбрасывается досрочно, как только исход стал
определённым. При сетевой или серверной ошибке он сохраняется: ручной
«Повторить» обязан уйти с тем же значением, иначе сервер спишет второй раз.

**Новый ключ на каждый вызов** — операцию законно повторить:

| Путь | Метод клиента |
|---|---|
| `POST market/usernames/sell` | `sellUsername` |
| `POST market/usernames/{id}/offers` | `makeOffer` |
| `POST market/gifts/send` | `sendGift` |
| `POST market/swaps` | `proposeSwap` |
| `POST wallet/transfer` | `transfer` |
| `POST wallet/premium/gift` | `giftPremium` |
| `POST admin/wallet/credit` | `adminCredit` |
| `POST admin/wallet/debit` | `adminDebit` |
| `POST admin/wallet/reset` | `adminResetWallet` |
| `POST admin/transactions/{id}/refund` | `adminRefund` |

Два одинаковых перевода одному получателю или два подарка подряд — законные
действия, и стабильный ключ молча проглотил бы второе. От потерянного ответа
такую операцию страхует автоматический повтор внутри одного вызова: ключ
создаётся до отправки и захватывается замыканием, поэтому обе попытки уходят с
одним значением.

Повторяются только ошибки с **неопределённым исходом** — `Network` (таймаут,
обрыв) и `Server` (`5xx`). Определённые ответы не повторяются: `409`,
`InsufficientFunds` и `Validation` означают, что действие не выполнено, а
`429` обрабатывается по правилу выше — паузой, а не ретраем.

**Обязанности сервера:**

- повтор с тем же ключом и тем же телом → первоначальный ответ, `200`;
- повтор с тем же ключом и **другим** телом → `409` с кодом
  `idempotency_conflict` (см. [07-errors.md](07-errors.md)). Выполнять второй
  вариант нельзя: неизвестно, какой из них имел в виду клиент;
- ключ, полученный во время выполнения первого запроса, → ожидать завершения
  или ответить `409`, но не начинать вторую операцию параллельно;
- отсутствие заголовка на перечисленных путях → `400` с кодом
  `missing_idempotency_key`. Молчаливое выполнение без защиты от повтора
  хуже явного отказа.


## Серверные лимиты

`GET /v1/settings` возвращает параметры, которые клиент не должен хардкодить:
лимиты меняются без релиза приложения.

```json
{
  "max_message_length": 4096,
  "max_upload_mb": 100,
  "premium_max_upload_mb": 4096,
  "story_max_duration_sec": 60,
  "circle_max_duration_sec": 60,
  "min_username_length": 4,
  "max_group_members": 200000,
  "market_fee_percent": 5,
  "streak_base_reward": 50,
  "features": { "stories_enabled": true }
}
```

| Поле | Что ограничивает на клиенте |
|---|---|
| `max_message_length` | Валидация ввода в `:feature:chats` до отправки |
| `max_upload_mb` / `premium_max_upload_mb` | Отказ в загрузке до начала передачи байтов |
| `story_max_duration_sec` | Длительность записи сторис |
| `circle_max_duration_sec` | Длительность «кружка»; дублируется в `BuildConfig.CIRCLE_MAX_SECONDS` как запасной вариант, если сервер недоступен |
| `min_username_length` | Валидация юзернейма и фильтр маркета |
| `max_group_members` | Блокировка добавления участников сверх лимита |
| `market_fee_percent` | Показ комиссии в карточке покупки — сумма должна совпадать с фактическим списанием |
| `streak_base_reward` | Текст награды за серию дней |
| `features` | Feature flags: отключение целых разделов без релиза |

Требование к серверу: `market_fee_percent` обязан совпадать с фактически
списанной комиссией. Расхождение — это не косметический баг, а введение
пользователя в заблуждение относительно суммы сделки.

`PATCH /v1/settings` доступен только аккаунту `@silver` и меняет те же поля
для всех клиентов — см. [06-security.md](06-security.md).

## Каталог эндпоинтов

Пути относительны к `BACKEND_URL`, то есть уже включают `/v1/`.
Фигурные скобки — параметры пути. Колонка «Метод клиента» — имя функции в
`SilverChatApi`, по ней находится реализация в `:core:data`.

Заголовки групп воспроизводят структуру исходного файла дословно, включая
дописанные позже русские разделы («Чаты: папки, тип, гео…»). Они не заменяют
базовые группы выше, а расширяют их: `SilverChatApi` рос итерациями, и
сохранение его разбиения позволяет находить эндпоинт в коде по номеру строки,
не переписывая каталог заново.
### Auth

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `POST` | `auth/otp/send` | `sendOtp` | `SendOtpResponse` |
| `POST` | `auth/otp/verify` | `verifyOtp` | `AuthResponse` |
| `POST` | `auth/refresh` | `refreshTokens` | `AuthResponse` |
| `POST` | `auth/logout` | `logout` | `EmptyResponse` |
| `GET` | `auth/sessions` | `sessions` | `List<SessionDto>` |
| `DELETE` | `auth/sessions/{id}` | `revokeSession` | `EmptyResponse` |

### Profile

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `users/me` | `me` | `UserDto` |
| `PATCH` | `users/me` | `updateMe` | `UserDto` |
| `GET` | `users/{id}` | `user` | `UserDto` |
| `GET` | `users/by-username/{username}` | `userByUsername` | `UserDto` |
| `POST` | `users/me/avatar` | `uploadAvatar` | `` |
| `POST` | `users/me/banner` | `uploadBanner` | `` |
| `PATCH` | `users/me/location` | `setLocation` | `UserDto` |
| `DELETE` | `users/me/location` | `clearLocation` | `UserDto` |
| `DELETE` | `users/me/working-hours` | `clearWorkingHours` | `UserDto` |
| `PATCH` | `users/me/working-hours` | `setWorkingHours` | `UserDto` |
| `DELETE` | `users/me/username` | `clearUsername` | `UserDto` |
| `GET` | `users/me/privacy` | `privacy` | `PrivacyDto` |
| `PATCH` | `users/me/privacy` | `setPrivacy` | `UserDto` |

### Search

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `search/users` | `searchUsers` | `` |
| `GET` | `search/chats` | `searchChats` | `List<ChatDto>` |
| `GET` | `search/messages` | `searchMessages` | `` |
| `GET` | `search/resolve` | `resolveHandle` | `ResolveResponse` |

### Chats

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `chats` | `chats` | `` |
| `GET` | `chats/{id}` | `chat` | `ChatDto` |
| `POST` | `chats/personal` | `openPersonalChat` | `ChatDto` |
| `POST` | `chats/group` | `createGroup` | `ChatDto` |
| `POST` | `chats/channel` | `createChannel` | `ChatDto` |
| `PATCH` | `chats/{id}` | `updateChat` | `ChatDto` |
| `GET` | `chats/{id}/members` | `members` | `` |
| `POST` | `chats/{id}/members` | `addMembers` | `EmptyResponse` |
| `PATCH` | `chats/{id}/members/{userId}` | `updateMember` | `` |
| `DELETE` | `chats/{id}/members/{userId}` | `kickMember` | `` |
| `POST` | `chats/{id}/invite` | `createInviteLink` | `InviteLinkDto` |
| `DELETE` | `chats/{id}/invite/{token}` | `revokeInviteLink` | `EmptyResponse` |
| `POST` | `chats/join/{token}` | `joinByInvite` | `ChatDto` |
| `PATCH` | `chats/{id}/read` | `markChatRead` | `EmptyResponse` |
| `PATCH` | `chats/{id}/draft` | `saveDraft` | `EmptyResponse` |

### Messages

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `chats/{id}/messages` | `messages` | `` |
| `POST` | `chats/{id}/messages` | `sendMessage` | `MessageDto` |
| `PATCH` | `messages/{id}` | `editMessage` | `MessageDto` |
| `DELETE` | `messages/{id}` | `deleteMessage` | `` |
| `POST` | `messages/{id}/reactions` | `react` | `MessageDto` |
| `DELETE` | `messages/{id}/reactions` | `clearReactions` | `MessageDto` |
| `POST` | `messages/{id}/pin` | `pinMessage` | `EmptyResponse` |
| `POST` | `messages/forward` | `forward` | `List<MessageDto>` |
| `POST` | `messages/{id}/vote` | `vote` | `MessageDto` |
| `POST` | `media/upload-ticket` | `uploadTicket` | `UploadTicketResponse` |

### Stories

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `stories/feed` | `storiesFeed` | `List<StoryClusterDto>` |
| `POST` | `stories` | `publishStory` | `StoryDto` |
| `POST` | `stories/{id}/view` | `viewStory` | `EmptyResponse` |
| `POST` | `stories/{id}/reaction` | `reactStory` | `EmptyResponse` |
| `POST` | `stories/{id}/reply` | `replyStory` | `MessageDto` |
| `DELETE` | `stories/{id}` | `deleteStory` | `EmptyResponse` |

### Calls

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `POST` | `calls/start` | `startCall` | `CallSessionDto` |
| `POST` | `calls/{id}/accept` | `acceptCall` | `CallSessionDto` |
| `POST` | `calls/{id}/decline` | `declineCall` | `EmptyResponse` |
| `POST` | `calls/{id}/end` | `endCall` | `EmptyResponse` |
| `GET` | `calls/{id}/ice` | `iceServers` | `IceServersResponse` |
| `GET` | `calls/{id}` | `callSession` | `CallSessionDto` |
| `GET` | `calls/history` | `callHistory` | `List<CallHistoryDto>` |

### Market

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `market/usernames` | `marketListings` | `` |
| `GET` | `market/usernames/{id}` | `listing` | `MarketListingDto` |
| `POST` | `market/usernames/buy` | `buyUsername` | `BuyResponse` |
| `POST` | `market/usernames/sell` | `sellUsername` | `MarketListingDto` |
| `POST` | `market/usernames/{id}/offers` | `makeOffer` | `EmptyResponse` |
| `GET` | `market/usernames/{id}/offers` | `offers` | `List<OfferDto>` |
| `GET` | `market/gifts` | `gifts` | `List<GiftDto>` |
| `POST` | `market/gifts/send` | `sendGift` | `MessageDto` |
| `POST` | `market/gifts/{ownedId}/convert` | `convertGift` | `ConvertGiftResponse` |

### Wallet / Premium

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `wallet` | `wallet` | `WalletDto` |
| `GET` | `wallet/ledger` | `ledger` | `List<LedgerDto>` (курсор `before`) |
| `GET` | `wallet/streak` | `streak` | `StreakDto` |
| `POST` | `wallet/streak/claim` | `claimStreak` | `StreakDto` |
| `POST` | `wallet/transfer` | `transfer` | `LedgerDto` |
| `GET` | `premium/tiers` | `premiumTiers` | `List<PremiumTierDto>` |
| `POST` | `premium/purchase` | `purchasePremium` | `PremiumStatusDto` |

### Admin (@silver)

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `admin/access` | `adminAccess` | `AdminAccessDto` |
| `GET` | `admin/stats` | `adminStats` | `AdminStatsDto` |
| `GET` | `admin/users` | `adminUsers` | `` |
| `GET` | `admin/users/{id}` | `adminUserDetails` | `AdminUserDetailsDto` |
| `POST` | `admin/grant` | `adminGrant` | `UserDto` |
| `POST` | `admin/wallet/credit` | `adminCredit` | `WalletDto` |
| `POST` | `admin/wallet/debit` | `adminDebit` | `WalletDto` |
| `POST` | `admin/ban` | `adminBan` | `EmptyResponse` |
| `GET` | `admin/reports` | `adminReports` | `List<ReportDto>` |
| `POST` | `admin/reports/{id}/resolve` | `resolveReport` | `EmptyResponse` |
| `GET` | `admin/audit` | `auditLog` | `` |
| `POST` | `admin/broadcast` | `broadcast` | `EmptyResponse` |

### Settings

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `settings` | `serverSettings` | `ServerSettingsDto` |
| `PATCH` | `settings` | `pushSettings` | `EmptyResponse` |

### Чаты: папки, тип, гео, график, выход, заявки, закрепление

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `POST` | `chats/folders` | `createFolder` | `ChatFolderDto` |
| `PATCH` | `chats/{id}/type` | `setChatType` | `ChatDto` |
| `PATCH` | `chats/{id}/location` | `setChatLocation` | `ChatDto` |
| `PATCH` | `chats/{id}/working-hours` | `setChatWorkingHours` | `` |
| `POST` | `chats/{id}/leave` | `leaveChat` | `EmptyResponse` |
| `POST` | `chats/{id}/join-requests/{userId}` | `approveJoinRequest` | `` |
| `POST` | `chats/{id}/pin` | `pinChat` | `EmptyResponse` |
| `POST` | `chats/{id}/mute` | `muteChat` | `EmptyResponse` |
| `POST` | `chats/{id}/archive` | `archiveChat` | `EmptyResponse` |
| `DELETE` | `chats/{id}` | `deleteChat` | `` |
| `GET` | `chats/{id}/invite-links` | `inviteLinks` | `List<InviteLinkDto>` |

### Сообщения: открепление, доставка, прочтение, опросы

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `DELETE` | `messages/{id}/pin` | `unpinMessage` | `EmptyResponse` |
| `POST` | `messages/unpin-all` | `unpinAll` | `EmptyResponse` |
| `POST` | `messages/delivered` | `markDelivered` | `EmptyResponse` |
| `POST` | `messages/read` | `markMessagesRead` | `EmptyResponse` |
| `POST` | `messages/{id}/poll/close` | `closePoll` | `MessageDto` |
| `GET` | `chats/{id}/reactions` | `availableReactions` | `List<String>` |

### Сторис: свои, архив, зрители, закрепление, stealth

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `stories/mine` | `myStories` | `List<StoryDto>` |
| `GET` | `stories/archive` | `storiesArchive` | `List<StoryDto>` |
| `GET` | `stories/{id}/viewers` | `storyViewers` | `List<UserDto>` |
| `POST` | `stories/{id}/pin` | `pinStory` | `EmptyResponse` |
| `POST` | `stories/{id}/stealth` | `viewStoryStealth` | `EmptyResponse` |

### Звонки: состояние медиа

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `POST` | `calls/{id}/mute` | `setCallMuted` | `EmptyResponse` |
| `POST` | `calls/{id}/camera` | `setCallCamera` | `EmptyResponse` |
| `POST` | `calls/{id}/switch-camera` | `switchCallCamera` | `EmptyResponse` |
| `POST` | `calls/{id}/screen` | `setCallScreenSharing` | `` |

### Маркет: мои лоты, история, отмена, свопы, офферы, подарки

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `market/my-listings` | `myListings` | `List<MarketListingDto>` |
| `GET` | `market/purchases` | `purchaseHistory` | `List<MarketListingDto>` |
| `DELETE` | `market/listings/{id}` | `cancelListing` | `EmptyResponse` |
| `GET` | `market/availability/{username}` | `checkAvailability` | `AvailabilityDto` |
| `POST` | `market/swaps` | `proposeSwap` | `SwapDto` |
| `POST` | `market/swaps/{id}` | `respondToSwap` | `` |
| `POST` | `market/offers/{id}/accept` | `acceptOffer` | `EmptyResponse` |
| `POST` | `market/offers/{id}/decline` | `declineOffer` | `EmptyResponse` |
| `GET` | `market/gifts/mine` | `myGifts` | `List<OwnedGiftDto>` |
| `POST` | `market/gifts/{ownedId}/upgrade` | `upgradeGift` | `OwnedGiftDto` |

### Кошелёк: Premium-статус, отмена автопродления, подарок

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `wallet/premium` | `premiumStatus` | `PremiumStatusDto` |
| `POST` | `wallet/premium/cancel-renew` | `cancelAutoRenew` | `EmptyResponse` |
| `POST` | `wallet/premium/gift` | `giftPremium` | `PremiumStatusDto` |

### Профиль: общие медиа, жалобы, чёрный список

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `GET` | `chats/{id}/media` | `sharedMedia` | `` |
| `POST` | `users/{id}/report` | `reportUser` | `EmptyResponse` |
| `POST` | `users/{id}/block` | `blockUser` | `EmptyResponse` |
| `DELETE` | `users/{id}/block` | `unblockUser` | `EmptyResponse` |
| `GET` | `users/blocked` | `blockedUsers` | `List<UserDto>` |

### Админка: точечные действия вместо универсального grant

| Метод | Путь | Метод клиента | Возвращает |
|---|---|---|---|
| `POST` | `admin/users/{id}/verified` | `adminSetVerified` | `` |
| `POST` | `admin/users/{id}/premium` | `adminSetPremium` | `` |
| `POST` | `admin/users/{id}/developer` | `adminSetDeveloper` | `` |
| `POST` | `admin/users/{id}/role` | `adminSetRole` | `UserDto` |
| `POST` | `admin/users/{id}/unban` | `adminUnban` | `EmptyResponse` |
| `POST` | `admin/users/{id}/restrict` | `adminRestrict` | `` |
| `DELETE` | `admin/chats/{chatId}/messages/{messageId}` | `adminDeleteMessage` | `` |
| `DELETE` | `admin/chats/{id}` | `adminDeleteChat` | `` |
| `POST` | `admin/wallet/reset` | `adminResetWallet` | `WalletDto` |
| `POST` | `admin/transactions/{id}/refund` | `adminRefund` | `` |
| `POST` | `admin/usernames/{username}/block` | `adminBlockUsername` | `` |
| `PATCH` | `admin/listings/{id}/price` | `adminAdjustListingPrice` | `` |

## Типы ответов

`EmptyResponse` — это `{"ok": true}`; встречается 50 раз и означает «действие
выполнено, данных нет». Остальные типы соответствуют моделям из
[05-data-models.md](05-data-models.md) и определены в
`core/network/src/main/kotlin/com/silverchat/core/network/dto/`.

