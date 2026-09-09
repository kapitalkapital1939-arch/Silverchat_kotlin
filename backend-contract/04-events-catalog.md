# 04. Каталог операций WebSocket

Словарь операций на клиенте — `object SocketOps` в
`core/model/src/main/kotlin/com/silverchat/core/model/SocketEvent.kt`. Всего
**71 операция**: 44 принимаются клиентом, 24 отправляются им, 3
двусторонние, 19 зарезервированы.

Направления: **К → С** — клиент отправляет, **С → К** — сервер отправляет,
**К ↔ С** — оба.

## Зарезервированные операции

19 операций объявлены в словаре, но клиентом не отправляются. Это не
недоделка, а следствие архитектурного решения: **команды идут по REST,
события приходят по WebSocket.**

Принять участие в звонке можно и через `call.accept` по сокету, и через
`POST /v1/calls/{id}/accept`. Клиент выбирает REST, потому что команде нужен
детерминированный ответ «принято / отклонено» с кодом ошибки, а HTTP даёт его
бесплатно. Сокетная версия оставлена в словаре на случай, если задержка REST
станет критичной для звонков.

| Группа | Операции | Как делает клиент сейчас |
|---|---|---|
| Звонки | `call.initiate`, `call.accept`, `call.decline`, `call.hangup` | REST: `calls/start`, `calls/{id}/accept`, `calls/{id}/decline`, `calls/{id}/end` |
| Сообщения | `message.edit`, `message.delete`, `message.react`, `message.upload.ticket` | REST: `PATCH messages/{id}`, `DELETE messages/{id}`, `POST messages/{id}/reactions`, `POST media/upload-ticket` |
| Маркет | `market.purchase`, `market.sell`, `market.offer`, `market.gift.send` | REST: соответствующие эндпоинты `market/*` |
| Premium | `premium.purchase` | REST: `POST premium/purchase` |
| Экономика | `streak.claim` | REST: `POST wallet/streak/claim` |
| Сторис | `story.reply` | REST: `POST stories/{id}/reply` |
| Админ | `admin.grant`, `admin.credit` | REST: `POST admin/grant`, `POST admin/wallet/credit` |
| Сессия | `session.hello` | Клиент начинает диалог сам, см. [03-websocket.md](03-websocket.md) |
| Присутствие | `presence.typing` | Дублирует `message.typing`; используется второй |

### Почему `wallet.ledger.append` выбыл из этого списка

Операция была объявлена клиентской командой, и это было ошибкой классификации:
клиент не может дописать строку в собственный журнал кошелька — журнал
неизменяем и ведётся сервером двойной записью
([08-node-server.md](08-node-server.md), раздел «Экономика»). Единственный
смысл операции — уведомление о новой строке, то есть направление **С → К**.

Пока она числилась зарезервированной, это была единственная операция из
списка, потеря которой заметна пользователю: баланс после покупки приходил в
`wallet.updated`, а сама строка журнала — только при следующем
`GET /v1/wallet/ledger`. История операций обновлялась рывками.

Теперь операция декодируется в `SocketEvent.LedgerAppended` и пишется
диспетчером в Room напрямую, минуя шину: подписчик `observeLedger` и так
наблюдает за `LedgerDao`, а второй канал доставки привёл бы к дублированию.
`upsert` по первичному ключу делает повторную доставку безопасной — а она
возможна, потому что сервер вправе повторно прислать строку после
переподключения.

Рассылка `wallet.ledger.append` **не заменяет** `wallet.updated`: строка
журнала несёт `balance_after`, но не несёт `frozen` и `spent_total`. Баланс
остаётся зоной ответственности `wallet.updated`, и два источника, пишущих одно
поле, устроили бы гонку. Сервер обязан посылать оба события.

## Полезные нагрузки

Формы ниже нормативны: клиент декодирует именно их
(`core/network/src/main/kotlin/com/silverchat/core/network/ws/SocketEventDecoder.kt`).
Поля со значением по умолчанию могут отсутствовать в кадре.

### Общие

**`AckPayload`** — подтверждение команды:

```json
{ "op": "message.send", "ok": true }
```

**`ErrorPayload`** — отказ:

```json
{ "code": "chat_not_found", "message": "Чат не найден", "retryable": false }
```

### Присутствие и печать

**`TypingPayload`**:

```json
{ "chat_id": "c_1", "user_id": "u_7", "typing": true }
```

`typing = false` отправляется, когда пользователь очистил поле ввода или
перестал печатать. Сервер обязан гасить индикатор по таймауту (~5 с) даже без
`false`: клиент может быть убит, и «печатает…» зависнет навсегда.

**`PresencePayload`**:

```json
{ "user_id": "u_7", "kind": "online", "last_seen": 1730000000000 }
```

`kind` — строка, а не перечисление: клиент трактует неизвестное значение как
«офлайн», поэтому сервер может добавлять новые состояния (например, `away`)
без breaking change. `last_seen` обязателен при `kind = "offline"`.

### Сообщения

**`DeliveryPayload`** — доставка и прочтение:

```json
{ "chat_id": "c_1", "message_ids": ["m_1", "m_2"], "user_id": "u_7" }
```

Массив, а не одно сообщение: клиент подтверждает прочтение пакетами, иначе
каждая прочитанная строка порождала бы отдельный кадр.

**`MessageDeletedPayload`**:

```json
{ "chat_id": "c_1", "message_ids": ["m_3"], "for_everyone": true }
```

`for_everyone = false` означает «удалено только у меня»: сообщение исчезает из
локальной БД этого пользователя, но остаётся у остальных. Различать эти случаи
обязательно, иначе удаление у себя стирало бы переписку у собеседника.

**`DraftPayload`** — черновик с другого устройства:

```json
{ "chat_id": "c_1", "text": "привет", "reply_to": "m_9" }
```

### Чаты

**`ChatListSyncPayload`** — ответ на `session.subscribe` и на инкрементальную синхронизацию:

```json
{ "chats": [], "server_time": 1730000000000 }
```

`server_time` используется как метка следующей инкрементальной синхронизации
(параметр `since` в `GET /v1/chats`). Клиент намеренно не подставляет своё
локальное время: расхождение часов на устройстве привело бы к пропуску
обновлений.

**`MemberPayload`**:

```json
{ "chat_id": "c_1", "user_id": "u_7", "role": "admin" }
```

Одной формой покрыты три операции — `chat.member.joined`,
`chat.member.left`, `chat.member.role.changed`. Для первых двух `role`
может отсутствовать.

**`InvitePayload`**:

```json
{ "chat_id": "c_1", "token": "AbC123" }
```

### Сторис

**`StoryViewedPayload`**:

```json
{ "story_id": "s_5", "viewer_id": "u_7" }
```

**`StoryIdPayload`** — для `story.expired` и `story.deleted`:

```json
{ "story_id": "s_5" }
```

### Звонки

**`CallPayload`** — состояние звонка:

```json
{
  "call_id": "call_1",
  "chat_id": "c_1",
  "type": "video",
  "state": "ringing",
  "from_user_id": "u_7",
  "reason": null
}
```

`type` — `audio | video`. `state` — `ringing | active | ended`. `reason`
заполняется только при завершении (`missed`, `declined`, `busy`, `failed`,
`hung_up`).

**`CallSignalPayload`** — сигналинг WebRTC:

```json
{
  "call_id": "call_1",
  "kind": "offer",
  "sdp": "v=0\r\no=…",
  "candidate": null,
  "sdp_mid": null,
  "sdp_m_line_index": null
}
```

`kind` — `offer | answer | candidate`. Для `offer` и `answer` заполнено `sdp`;
для `candidate` — `candidate`, `sdp_mid` и `sdp_m_line_index`. Сервер
пересылает эти кадры **дословно и без задержки**: он не разбирает SDP и не
может менять порядок кадров, иначе ICE-сборка не сойдётся.

**`IceServersPayload`**:

```json
{ "call_id": "call_1", "servers": [{ "urls": ["stun:…"], "username": null, "credential": null }] }
```

Учётные данные TURN выдаются короткоживущими (до часа): постоянные позволили бы
любому использовать ретранслятор как открытый прокси.

### Экономика

**`PurchasePayload`** — результат покупки:

```json
{ "listing": {}, "wallet": {}, "transaction_id": "t_9" }
```

`transaction_id` обязателен: по нему клиент идемпотентно обрабатывает повтор
события, а поддержка находит операцию в журнале.

**`PremiumGrantedPayload`**:

```json
{ "user_id": "u_7", "days": 30, "granted_by": "u_silver" }
```

`granted_by` нужен для журнала аудита: выдача Premium администратором должна
быть атрибутирована.

### Администрирование

**`BroadcastPayload`** — рассылка от `@silver`:

```json
{ "text": "Технические работы 12:00–13:00", "audience": "all" }
```

Клиент показывает её системным уведомлением
(`app/notification/AdminBroadcastNotifier.kt`), идентификатор уведомления —
хеш текста, поэтому повторная рассылка с тем же текстом заменяет предыдущую, а
не дублирует её.

## Каталог операций

Группы соответствуют комментариям в `SocketOps`.
### session

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `session.hello` | К → С | **зарезервировано** |
| `session.auth` | К → С | клиент отправляет: первый кадр при подключении |
| `session.auth.ok` | С → К | подтверждение авторизации (транспортный слой) |
| `session.auth.fail` | С → К | отказ авторизации (транспортный слой) |
| `session.ping` | К → С | клиент отправляет: heartbeat каждые 25 с; эфемерный кадр — сбрасывается из очереди офлайн старше 5 минут |
| `session.pong` | С → К | ответ на heartbeat (транспортный слой) |
| `session.kick` | С → К | сервер отозвал сессию (транспортный слой) |
| `session.subscribe` | К → С | клиент отправляет: подписка на открытые чаты |

### presence

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `presence.update` | К ↔ С | декодируется в `SocketEvent.PresenceChanged`; клиент отправляет: вход/выход из приложения; эфемерный кадр — сбрасывается из очереди офлайн старше 5 минут |
| `presence.typing` | К → С | **зарезервировано** |
| `presence.read` | С → К | декодируется в `SocketEvent.ReadUpdated` |

### chat

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `chat.list.sync` | С → К | декодируется в `SocketEvent.ChatListSync` |
| `chat.updated` | С → К | декодируется в `SocketEvent.ChatUpdated` |
| `chat.created` | С → К | декодируется в `SocketEvent.ChatCreated` |
| `chat.member.joined` | С → К | декодируется в `SocketEvent.MemberChanged` |
| `chat.member.left` | С → К | декодируется в `SocketEvent.MemberChanged` |
| `chat.member.role.changed` | С → К | декодируется в `SocketEvent.MemberChanged` |
| `chat.invite.created` | С → К | декодируется в `SocketEvent.InviteUpdated` |
| `chat.invite.revoked` | С → К | декодируется в `SocketEvent.InviteUpdated` |
| `chat.draft.updated` | С → К | декодируется в `SocketEvent.DraftUpdated` |
| `chat.pinned.updated` | С → К | декодируется в `SocketEvent.PinnedUpdated` |

### message

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `message.send` | К → С | клиент отправляет: отправка сообщения |
| `message.sent.ack` | С → К | декодируется в `SocketEvent.MessageAck` |
| `message.new` | С → К | декодируется в `SocketEvent.MessageNew` |
| `message.edit` | К → С | **зарезервировано** |
| `message.edited` | С → К | декодируется в `SocketEvent.MessageEdited` |
| `message.delete` | К → С | **зарезервировано** |
| `message.deleted` | С → К | декодируется в `SocketEvent.MessageDeleted` |
| `message.react` | К → С | **зарезервировано** |
| `message.reaction.updated` | С → К | декодируется в `SocketEvent.ReactionUpdated` |
| `message.read` | С → К | декодируется в `SocketEvent.ReadUpdated` |
| `message.delivered` | С → К | декодируется в `SocketEvent.DeliveryUpdated` |
| `message.typing` | К ↔ С | декодируется в `SocketEvent.Typing`; клиент отправляет: «печатает…»; эфемерный кадр — сбрасывается из очереди офлайн старше 5 минут |
| `message.upload.ticket` | К → С | **зарезервировано** |

### story

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `story.new` | С → К | декодируется в `SocketEvent.StoryNew` |
| `story.view` | К → С | клиент отправляет: просмотр сторис; эфемерный кадр — сбрасывается из очереди офлайн старше 5 минут |
| `story.viewed` | С → К | декодируется в `SocketEvent.StoryViewed` |
| `story.reply` | К → С | **зарезервировано** |
| `story.expired` | С → К | декодируется в `SocketEvent.StoryExpired` |
| `story.deleted` | С → К | декодируется в `SocketEvent.StoryDeleted` |

### call

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `call.initiate` | К → С | **зарезервировано** |
| `call.incoming` | С → К | декодируется в `SocketEvent.CallIncoming` |
| `call.accept` | К → С | **зарезервировано** |
| `call.decline` | К → С | **зарезервировано** |
| `call.hangup` | К → С | **зарезервировано** |
| `call.signal` | К ↔ С | декодируется в `SocketEvent.CallSignalReceived`; клиент отправляет: SDP и ICE-кандидаты WebRTC |
| `call.state` | С → К | декодируется в `SocketEvent.CallStateChanged` |
| `call.ice.servers` | С → К | декодируется в `SocketEvent.CallIceServers` |

### market

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `market.listing.new` | С → К | декодируется в `SocketEvent.ListingNew` |
| `market.listing.updated` | С → К | декодируется в `SocketEvent.ListingUpdated` |
| `market.purchase` | К → С | **зарезервировано** |
| `market.purchase.result` | С → К | декодируется в `SocketEvent.PurchaseCompleted` |
| `market.offer` | К → С | **зарезервировано** |
| `market.sell` | К → С | **зарезервировано** |
| `market.gift.send` | К → С | **зарезервировано** |

### wallet / streak

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `wallet.updated` | С → К | декодируется в `SocketEvent.WalletUpdated` |
| `wallet.ledger.append` | С → К | декодируется в `SocketEvent.LedgerAppended` |
| `streak.claim` | К → С | **зарезервировано** |
| `streak.updated` | С → К | декодируется в `SocketEvent.StreakUpdated` |

### premium

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `premium.purchase` | К → С | **зарезервировано** |
| `premium.updated` | С → К | декодируется в `SocketEvent.PremiumUpdated` |
| `premium.granted` | С → К | декодируется в `SocketEvent.PremiumGranted` |

### profile

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `profile.updated` | С → К | декодируется в `SocketEvent.ProfileUpdated` |
| `profile.avatar.updated` | С → К | декодируется в `SocketEvent.AvatarUpdated` |
| `profile.banner.updated` | С → К | декодируется в `SocketEvent.BannerUpdated` |

### admin

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `admin.grant` | К → С | **зарезервировано** |
| `admin.credit` | К → С | **зарезервировано** |
| `admin.log.append` | С → К | декодируется в `SocketEvent.AdminLogAppended` |
| `admin.broadcast` | С → К | декодируется в `SocketEvent.AdminBroadcast` |

### system

| Операция | Направление | Реализация на клиенте |
|---|---|---|
| `error` | С → К | декодируется в `SocketEvent.ServerError` |
| `ack` | С → К | декодируется в `SocketEvent.Ack` |
