# 05. Модели данных

Нормативный источник на клиенте — каталог
`core/network/src/main/kotlin/com/silverchat/core/network/dto/`. Формы ниже
описывают то, что передаётся по сети; доменные модели клиента
(`core/model`) отличаются от них и связаны мапперами в `core/data/mapper/`.

## Общие правила

| Правило | Значение |
|---|---|
| Именование полей | `snake_case` всегда |
| Время | Миллисекунды epoch UTC, `long` |
| Идентификаторы | Непрозрачные строки |
| Деньги и счётчики | Целые числа, без дробных типов |
| Полиморфизм | Дискриминатор `kind` |
| Необязательные поля | Отсутствуют в JSON, а не передаются как `null` |

Последнее правило важнее, чем выглядит. Клиент собран с
`explicitNulls = false`, поэтому не отправляет `null`-поля вовсе, и сервер
трактует отсутствие поля в `PATCH` как «не изменять». Если сервер начнёт
требовать явный `null` для очистки поля — очистка станет невозможной.

## Пользователь

```json
{
  "id": "u_7f2c",
  "first_name": "Алина",
  "last_name": "Ковалёва",
  "username": "alina",
  "phone": "+37360123456",
  "bio": "Дизайнер интерфейсов",
  "avatar": {
    "static_url": "https://cdn.silver.chat/a/u_7f2c.jpg",
    "animated_url": "https://cdn.silver.chat/a/u_7f2c.webm",
    "animation_type": "lottie",
    "dominant_color": 4283215696
  },
  "banner": {
    "static_url": "https://cdn.silver.chat/b/u_7f2c.jpg",
    "animated_url": null,
    "animation_type": "none",
    "blur_hash": "LEHV6nWB2yk8pyoJadR*.7kCMdnj"
  },
  "badges": {
    "verified": true,
    "developer": false,
    "premium": true,
    "admin": false,
    "owner": false
  },
  "flags": {
    "banned": false,
    "restricted": false,
    "bot": false,
    "is_master_account": false,
    "deleted": false
  },
  "presence": { "kind": "online", "last_seen": null, "chat_id": "c_1" },
  "premium": {
    "kind": "active",
    "tier": "year",
    "expires_at": 1760000000000,
    "auto_renew": true,
    "gifted_by": null,
    "expired_at": null
  },
  "created_at": 1690000000000
}
```

### Анимированные аватары и баннеры

Оба объекта несут пару `static_url` / `animated_url` и `animation_type`.
Это обязательная схема, а не оптимизация:

- **`static_url` нужен всегда**, даже если анимация есть. Он показывается
  мгновенно, до загрузки видео, и используется в списках, где анимация
  отключена ради батареи.
- **`animation_type`** — `none | lottie | video`. Клиент выбирает загрузчик
  по нему: Lottie (JSON-вектор) и видео (WebM/MP4) декодируются по-разному,
  и попытка определить тип по расширению URL ненадёжна из-за CDN-переписывания
  путей.
- **`dominant_color`** у аватара и **`blur_hash`** у баннера — заполнители
  до загрузки. Без них интерфейс «прыгает», когда изображение появляется.
  `dominant_color` — ARGB как целое число, не строка `#RRGGBB`.

### Флаги и бейджи — разные вещи

`badges` — то, что видит пользователь (галочка верификации, значок
разработчика). `flags` — административное состояние, которое влияет на
поведение: `banned` блокирует отправку, `restricted` ограничивает её,
`is_master_account` отмечает аккаунт `@silver` и открывает админ-панель.

Смешивать их нельзя: `admin` в `badges` показывает значок, а доступ к
`/v1/admin/*` сервер обязан проверять по своей базе, а не по отправленному
клиенту бейджу.

### Телефон

`phone` возвращается только в `users/me` и в ответах админ-панели. В
публичном профиле другого пользователя поля быть не должно — его утечка
позволяет перебором номеров определять, кто зарегистрирован в SilverChat.

## Чат

```json
{
  "id": "c_9f2",
  "type": "personal",
  "title": "Алина Ковалёва",
  "about": null,
  "avatar": { },
  "username": null,
  "peer": { },
  "members_count": 2,
  "online_count": 1,
  "last_message": { },
  "draft": { "text": "привет", "reply_to": null },
  "unread_count": 3,
  "mentions_count": 0,
  "pinned": ["m_1", "m_4"],
  "pinned_story_id": null,
  "muted": false,
  "muted_until": null,
  "archived": false,
  "folder_ids": [],
  "verified": false,
  "restricted": false,
  "permissions": { "can_send_messages": true, "can_send_media": true },
  "my_role": "member",
  "invite_links": [],
  "join_requests": 0,
  "location": null,
  "working_hours": null,
  "slow_mode_seconds": 0,
  "created_at": 1700000000000
}
```

`type` — `personal | group | channel | broadcast`.

Для личного чата обязателен `peer` — объект собеседника: у такого чата нет
собственного названия и аватара, они берутся из второго участника.

`my_role` описывает права **запрашивающего** пользователя, поэтому один и тот
же чат в ответах разным людям содержит разное значение. Это не кэшируемое
поле общего доступа.

`permissions` — права по умолчанию для участников; `my_role` может их
расширять. Клиент блокирует ввод, когда запрещает и то и другое.

## Сообщение

```json
{
  "id": "m_3a1",
  "chat_id": "c_9f2",
  "sender_id": "u_7f2c",
  "sender": { },
  "content": { "kind": "text", "text": "Привет", "entities": [] },
  "status": "sent",
  "reply_to": null,
  "forward": null,
  "edited_at": null,
  "reactions": [{ "kind": "heart", "count": 2, "reacted_by_me": true, "recent_users": ["u_1"] }],
  "my_reaction": "heart",
  "sent_at": 1730000000000,
  "scheduled_at": null,
  "pinned": false,
  "silent": false,
  "protected": false,
  "views_count": 0,
  "deleted": false,
  "client_message_id": "cmid_8d2e"
}
```

`status` — `pending | sent | delivered | read | failed`. Сервер возвращает
`sent` и дальше продвигает статус событиями `message.delivered` и
`message.read`; `pending` и `failed` существуют только на клиенте.

`sender` может отсутствовать в списке сообщений чата, где отправитель уже
пришёл в другом сообщении, — но в событии `message.new` он обязан быть: иначе
клиент не сможет показать имя и аватар без дополнительного запроса.

`client_message_id` возвращается без изменений — по нему клиент сопоставляет
оптимистичную локальную запись с серверной. Подробнее в
[03-websocket.md](03-websocket.md), раздел «Дедупликация».

### Виды контента

Дискриминатор `kind`. Поля каждого вида:

| `kind` | Поля |
|---|---|
| `text` | `text`, `entities[]`, `link_preview?` |
| `photo` | `url`, `thumb_url`, `width`, `height`, `size_bytes`, `caption?`, `album_id?`, `spoiler` |
| `video` | `url`, `poster_url`, `duration_ms`, `width`, `height`, `size_bytes`, `caption?`, `album_id?` |
| `voice` | `url`, `duration_ms`, `waveform`, `size_bytes` |
| `circle` | `url`, `poster_url`, `duration_ms`, `size_bytes` |
| `file` | `url`, `name`, `mime`, `size_bytes` |
| `sticker` | `pack_id`, `emoji`, `url`, `animated`, `is_premium` |
| `gif` | `url`, `thumb_url`, `width`, `height`, `is_premium` |
| `location` | `lat`, `lng`, `live_period_seconds`, `title?` |
| `contact` | `user_id?`, `first_name`, `last_name?`, `phone` |
| `poll` | `question`, `options[]`, `anonymous`, `multiple`, `quiz`, `closed` |
| `service` | `action`, `title` |
| `gift` | `gift_id`, `title`, `emoji`, `price_silver`, `message?` |

**`waveform`** у голосового сообщения — массив целых чисел от 0 до 100,
нормированная громкость по длительности. Клиент рисует по нему волну; если
поле пустое, рисуется ровная полоса. Сервер его не вычисляет — массив
приходит от клиента при загрузке.

**`album_id`** связывает фото и видео в группу, отправленную одним действием.
Сообщения альбома остаются отдельными записями: это позволяет удалять и
пересылать их поодиночке.

**`service`** — системное сообщение («Алина добавила Игоря»). `action`
определяет текст, `title` — отображаемую строку. Клиент не даёт на него
отвечать и реагировать.

Неизвестный `kind` не ошибка: он декодируется в
`MessageContentDto.Unknown(raw_kind)` и показывается как «Сообщение не
поддерживается вашей версией». Это и есть механизм обратной совместимости.

### Форматирование текста

`entities` — массив диапазонов, а не разметка внутри строки:

```json
{ "type": "bold", "offset": 0, "length": 6, "url": null, "language": null }
```

`type` — `bold | italic | underline | strikethrough | code | pre | link |
mention | hashtag | phone | spoiler`. `offset` и `length` — в символах UTF-16,
потому что клиент отображает текст через `Spannable`, который считает
именно так. Для `pre` заполняется `language`, для `link` — `url`.

Хранение диапазонов вместо HTML или Markdown обязательно: разметку внутри
строки пришлось бы экранировать, а пользовательский текст может содержать
любые символы.

## Сторис

```json
{
  "id": "s_5",
  "author_id": "u_7f2c",
  "author": { },
  "media": { },
  "caption": null,
  "created_at": 1730000000000,
  "expires_at": 1730086400000,
  "privacy": "everyone",
  "viewers_count": 12,
  "reactions": [],
  "seen_by_me": false,
  "pinned_to_profile": true,
  "replies_enabled": true,
  "location": null,
  "mention_ids": []
}
```

`expires_at` обязателен: срок жизни сторис определяет сервер, а не клиент.
Иначе устройство с неверными часами показывало бы просроченное или удаляло
свежее. `privacy` — `everyone | contacts | close_friends | private`.

`seen_by_me` — состояние запрашивающего пользователя, как и `my_role` у чата.

## Экономика

### Кошелёк

```json
{
  "user_id": "u_7f2c",
  "balance": 1250,
  "frozen": 200,
  "earned_total": 4800,
  "spent_total": 3550,
  "updated_at": 1730000000000
}
```

`frozen` — сумма, заблокированная под активные сделки (лот выставлен, оффер
ожидает ответа). Доступно к трате `balance − frozen`. Сервер обязан
блокировать средства в момент выставления лота, а не в момент покупки: иначе
возможна продажа одного юзернейма дважды.

### Журнал операций

```json
{
  "id": "t_9",
  "amount": -500,
  "reason": "username_purchase",
  "reference": "l_3",
  "counterparty": "u_1",
  "balance_after": 1250,
  "created_at": 1730000000000,
  "reversible": false
}
```

`amount` — со знаком: положительный для начисления, отрицательный для
списания. `balance_after` обязателен: он позволяет клиенту обнаружить пропуск
операции, не запрашивая весь журнал.

Та же форма используется в трёх местах, и расхождение между ними недопустимо:

| Где | Как приходит |
|---|---|
| `GET /v1/wallet/ledger` | Список, курсорная пагинация по `before` — см. [02-rest-api.md](02-rest-api.md), раздел «Пагинация» |
| `POST /v1/wallet/transfer` | Одна запись — результат перевода |
| `wallet.ledger.append` | Одна запись по WebSocket — см. [04-events-catalog.md](04-events-catalog.md) |

Клиент хранит записи в Room и использует `id` как первичный ключ, поэтому
повторная доставка одной и той же строки безопасна: `upsert` перезапишет её
тем же значением. Это важно, потому что сервер вправе повторно прислать
запись после переподключения клиента.

`reversible` показывает, можно ли операцию вернуть через
`POST /v1/admin/transactions/{id}/refund`. Возврат создаёт **новую** запись с
противоположным `amount`, а не правит исходную: журнал неизменяем
([08-node-server.md](08-node-server.md), раздел «Экономика»).

`wallet.ledger.append` **не заменяет** `wallet.updated`. Запись журнала несёт
`balance_after`, но не несёт `frozen` и `spent_total`, поэтому баланс
остаётся зоной ответственности `wallet.updated`; сервер обязан посылать оба
события. Два источника, пишущих одно поле, устроили бы на клиенте гонку.

### Лот маркета

```json
{
  "id": "l_3",
  "username": "alina",
  "price_silver": 2500,
  "rarity": "rare",
  "categories": ["names", "short"],
  "status": "active",
  "seller_id": "u_1",
  "seller_name": "Игорь",
  "description": null,
  "views": 143,
  "offers_count": 2,
  "min_offer_silver": 1800,
  "history": [{ }],
  "created_at": 1729000000000,
  "expires_at": 1731000000000
}
```

`status` — `draft | active | reserved | sold | cancelled | blocked`.
`reserved` — состояние между принятием оффера и завершением перевода; без
него два покупателя могли бы пройти оплату одновременно.

`history` — точки изменения цены, нужны для графика в карточке лота.

### Серии дней

```json
{
  "streak_days": 7,
  "best_streak": 41,
  "frozen": 0,
  "last_claim_date": "2026-09-07",
  "next_reward_silver": 150,
  "milestones": [],
  "claimable": true
}
```

`last_claim_date` — единственное поле-строка во всём контракте: дата без
времени и часового пояса. Она сравнивается с серверной датой, поэтому
часовой пояс устройства не влияет на серию. `claimable` вычисляет сервер —
клиент не должен решать это сам, иначе пользователь в другом часовом поясе
получил бы награду дважды за сутки.

## Звонки

История звонка:

```json
{
  "call_id": "call_1",
  "chat_id": "c_9f2",
  "peer": { },
  "type": "video",
  "missed": true,
  "started_at": 1730000000000,
  "duration_ms": 0
}
```

ICE-серверы:

```json
{ "urls": ["turn:turn.silver.chat:3478"], "username": "1730000000:u_7", "credential": "…" }
```

Учётные данные TURN — короткоживущие (см.
[06-security.md](06-security.md), раздел «Звонки»).

## Где смотреть остальное

| Область | Файл DTO |
|---|---|
| Пользователь, аватар, баннер, бейджи, флаги, присутствие | `dto/UserDto.kt` |
| Чат, участники, приглашения, папки, права | `dto/ChatDto.kt` |
| Запросы (тела `POST`/`PATCH`) | `dto/Requests.kt` |
| Ответы, кошелёк, журнал, маркет-лента, серверные лимиты | `dto/Responses.kt` |
| Маркет: лоты, подарки, Premium-тарифы, свопы, офферы | `dto/MarketDto.kt` |
| Админ-панель: статистика, аудит, жалобы, рассылки | `dto/AdminDto.kt` |
| Полезные нагрузки событий WebSocket | `ws/SocketEventDecoder.kt` |
| Доменные модели клиента (не wire-формат) | `core/model/` |
