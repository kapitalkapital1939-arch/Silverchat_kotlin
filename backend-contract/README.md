# Контракт с бэкендом SilverChat

Этот каталог — **единственный источник правды** о том, как Android-клиент
разговаривает с сервером. Всё, что реализовано в `:core:network` и `:core:data`,
обязано соответствовать документам здесь; любое расхождение — баг одного из двух.

## Почему это документация, а не код

Сервер SilverChat — Node.js + WebSocket. В этом репозитории **нет и не будет**
серверного кода: клиент и сервер разводятся по разным репозиториям, а общим
у них остаётся только контракт. Поэтому здесь описаны формы кадров, списки
операций, коды ошибок и модели данных — на естественном языке и в примерах
JSON, но без единого исполняемого файла.

Правило простое: **JSON-примеры в этих документах нормативны.** Если клиент
декодирует поле `unread_count`, а сервер шлёт `unreadCount` — не прав тот,
кто отступил от примера здесь.

## Состав

| Файл | О чём |
|---|---|
| [01-overview.md](01-overview.md) | Адреса, транспорты, версионирование, соглашения JSON |
| [02-rest-api.md](02-rest-api.md) | Конвенции REST и полный каталог эндпоинтов |
| [03-websocket.md](03-websocket.md) | Протокол реального времени: кадр, рукопожатие, heartbeat, переподключение |
| [04-events-catalog.md](04-events-catalog.md) | Все операции протокола с направлением и полезной нагрузкой |
| [05-data-models.md](05-data-models.md) | Модели данных, общие для клиента и сервера |
| [06-security.md](06-security.md) | TLS/WSS, жизненный цикл токенов, хранение на устройстве, права `@silver` |
| [07-errors.md](07-errors.md) | Конверт ошибки, коды, retryable, отображение в клиентский `ScError` |
| [08-node-server.md](08-node-server.md) | Как устроен сервер: процессы, очереди, хранилища, сигналинг звонков |

## Ключевые числа

| Параметр | Значение | Где задан |
|---|---|---|
| Версия протокола | `1` | `PROTOCOL_VERSION`, поле `v` в каждом кадре |
| REST base URL | `https://api.silver.chat/v1/` | `BACKEND_URL` |
| WebSocket URL | `wss://api.silver.chat/v1/realtime` | `WS_URL` |
| CDN | `https://cdn.silver.chat/` | `CDN_URL` |
| Heartbeat | `25` с | `WS_HEARTBEAT_SEC` |
| Порог пропущенных pong | `2` | `MISSED_PONG_LIMIT` в `OkHttpRealtimeSocket` |
| Начальный backoff | `250` мс | `INITIAL_BACKOFF_MS` |
| Максимальный backoff | `30` с | `WS_MAX_BACKOFF_SEC` |

Все значения собраны в `build-logic/convention/src/main/kotlin/AndroidConfig.kt`
и попадают в `BuildConfig` каждого модуля. Менять их нужно там — не в коде.

## Как вносить изменения в контракт

1. Правка начинается с этого каталога, а не с кода.
2. Изменение, ломающее старых клиентов, обязано поднимать `PROTOCOL_VERSION`
   и описывать переходный период (см. раздел «Версионирование» в
   [01-overview.md](01-overview.md)).
3. Новое поле в существующей модели — **не** breaking change: клиент собран
   с `ignoreUnknownKeys = true` и просто его проигнорирует.
4. Новая операция `op` — не breaking change: неизвестные кадры попадают в
   `SocketEvent.Unknown` и не роняют декодер.

## Соответствие в коде клиента

| Раздел контракта | Реализация |
|---|---|
| REST-эндпоинты | `core/network/api/SilverChatApi.kt` |
| Запросы/ответы | `core/network/dto/Requests.kt`, `Responses.kt`, `ChatDto.kt`, `UserDto.kt`, `MarketDto.kt`, `AdminDto.kt` |
| Словарь операций | `core/model/SocketEvent.kt` → `object SocketOps` |
| Конверт кадра | `core/model/SocketEvent.kt` → `SocketFrame` |
| Декодирование событий | `core/network/ws/SocketEventDecoder.kt` |
| Транспорт WebSocket | `core/network/ws/OkHttpRealtimeSocket.kt` |
| Очередь офлайн-отправки | `core/network/ws/OutgoingQueue.kt` |
| Авторизация HTTP | `core/network/interceptor/AuthInterceptor.kt`, `TokenAuthenticator.kt` |
| Отображение ошибок | `core/common/result/ScResult.kt` |
