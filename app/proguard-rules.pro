# ===========================================================================
# SilverChat — правила R8, специфичные для :app.
#
# Общие правила (kotlinx-serialization, OkHttp, Retrofit, WebRTC, Room, Hilt,
# Compose, Coil, Media3, Tink, Timber) живут в config/proguard/silverchat-common.pro
# и подключаются convention-плагином ко ВСЕМ модулям. Здесь — только то, что
# имеет смысл исключительно в точке сборки.
# ===========================================================================

# --- Reflection из :core:notifications --------------------------------------
# NotificationIntentFactory резолвит Activity по имени:
#   Class.forName("com.silverchat.app.MainActivity")
# Это сделано, чтобы :core:notifications не зависел от :app (иначе в графе
# модулей возникает цикл). R8 переименовал бы класс, и каждый PendingIntent
# из уведомления стал бы падать в runCatching с тихим фолбэком на Intent() —
# тап по уведомлению перестал бы открывать приложение.
-keep class com.silverchat.app.MainActivity { *; }

# --- Компоненты, объявленные в манифесте ------------------------------------
# AGP сохраняет их автоматически, но ReplyReceiver создаётся по действию
# из RemoteInput, поэтому дублируем правило явно: цена — одна строка,
# цена ошибки — неработающий быстрый ответ из уведомления.
-keep class com.silverchat.app.notification.ReplyReceiver { *; }
-keep class com.silverchat.app.SilverChatApplication { *; }

# --- Порты, реализуемые в :app ----------------------------------------------
# MediaCaptureGateway и ProfileMediaPicker связываются Hilt по типу, но
# реализация ActivityCaptureGateway создаётся рефлективно через Dagger-фабрику;
#.keep на интерфейсе гарантирует, что сигнатуры методов переживут shrinking.
-keep interface com.silverchat.core.data.repository.MediaCaptureGateway { *; }

# --- FileProvider -----------------------------------------------------------
# androidx.core.content.FileProvider ищется по имени из манифеста.
-keep class androidx.core.content.FileProvider { *; }
