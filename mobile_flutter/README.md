# SilverChat Mobile — Flutter (Android)

Мобильное приложение SilverChat на **Flutter (Dart)**: вкладки **Чаты** (WebSocket, realtime)
и **Маркет** (Сильверы: баланс, покупка/продажа юзернеймов, админ-выдача для @silver).
Тёмная тема Fragment/Telegram: фон `#0E1621/#17212B`, акцент `#5288C1`, золото `#F5C242`.

## Почему это стабильнее, чем RN-версия
- **Нет Hermes, нет JS-бандла, нет нативных модулей** (кроме `shared_preferences` — самый
  стабильный из всех). Причина твоих крэшей на `MessageQueueThreadImpl` исключена архитектурно.
- Весь код — Dart, компилируется в нативный ARM (AOT) при сборке release.
- Глобальные обработчики ошибок: любые Dart-исключения логируются с меткой `[SilverChat]`
  и не роняют процесс.
- Все сетевые вызовы: try/catch + таймаут 15 с. WebSocket: reconnect с backoff.

## Сборка (пошагово)

### 1. Установи Flutter (если ещё нет)
- Windows: скачай архив с https://docs.flutter.dev/get-started/install/windows
- Распакуй (например, `C:\flutter`), добавь `C:\flutter\bin` в PATH.
- Проверь: `flutter doctor` — должны быть галочки у Dart и Android toolchain.

### 2. Сгенерируй каркас проекта вокруг этого кода
В папке `mobile_flutter` (там уже есть `pubspec.yaml` и `lib/main.dart`):

```bash
cd mobile_flutter
flutter create --org com.silver1488 --project-name silverchat_mobile --platforms android .
```

Команда создаст папку `android/` + тесты, **не трогая** мои `pubspec.yaml` и `lib/main.dart`.

### 3. INTERNET-разрешение (обязательно!)
Открой `android/app/src/main/AndroidManifest.xml` и добавь внутрь `<manifest>`,
**до** `<application>`:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

### 4. Зависимости и запуск
```bash
flutter pub get

# debug на подключённом телефоне (USB-отладка включена):
flutter run

# release APK:
flutter build apk --release
# готовый файл: build/app/outputs/flutter-apk/app-release.apk
```

## Подключение к серверу
Одна константа в `lib/main.dart` (строка ~25):

```dart
const String kApiBase = 'https://silverchat-production.up.railway.app';
```

Меняй на свой домен — WebSocket-адрес строится от неё автоматически.

## Что внутри (коротко)
- **Чаты**: список с онлайн-точками/непрочитанными, переписка с bubble-стилем Telegram,
  ✓/✓✓ (прочитано), «печатает…», LIVE/OFFLINE индикатор WS.
- **Маркет**: золотой баланс `💰 1,500 Silver`, витрина лотов с «Купить»,
  «Мои лоты» со снятием, форма «выставить тег за цену».
- **Админ** (только `is_admin`/`is_dev`, т.е. @silver): золотая карточка
  «👑 Админ: выдать Сильверы» — username + сумма.
- **Сессия**: токен хранится в `shared_preferences`, при старте проверяется
  через `/api/me`; «Выход» — в шапке вкладки Чаты.
- Бэкенд-эндпоинты: `/api/auth`, `/api/me`, `/api/my_chats`, `/api/history/:u`,
  `/api/messages`, `/api/read`, `/api/balance`, `/api/market*`,
  `/api/admin/grant_silvers`, WS `/ws?token=...`.

## Диагностика
- Логи телефона: `flutter run -v` (debug) или `adb logcat | findstr SilverChat` —
  все наши ошибки с меткой `[SilverChat]`.
- Если экран ошибки: текст виден в logcat по метке `[SilverChat]`
  (release не показывает текст на красном экране — только в логах).
