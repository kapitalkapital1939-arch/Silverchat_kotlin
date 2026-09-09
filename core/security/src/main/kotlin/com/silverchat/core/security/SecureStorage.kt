package com.silverchat.core.security

/**
 * Контракт защищённого хранилища.
 *
 * Реализация — [EncryptedSecureStorage] поверх `EncryptedSharedPreferences`
 * (ключи шифрования лежат в Android Keystore и не покидают устройство).
 *
 * Что здесь хранится:
 *  - access/refresh токены авторизации;
 *  - session id WebSocket-канала;
 *  - PIN/биометрический флаг блокировки приложения;
 *  - локальные черновики секретных чатов;
 *  - флаг «показывать содержимое уведомлений».
 *
 * Что здесь НЕ хранится: сообщения, медиа, username-сделки — это Room
 * (при желании шифруется SQLCipher на уровне :core:database).
 */
interface SecureStorage {

    /** Токен доступа (короткоживущий, 15 минут). */
    var accessToken: String?

    /** Refresh-токен (долгоживущий, одноразовый с ротацией). */
    var refreshToken: String?

    /** Идентификатор устройства для мультисессионности. */
    var deviceId: String?

    /** Идентификатор активной WebSocket-сессии. */
    var sessionId: String?

    /** Хэш PIN-кода блокировки приложения (сам PIN не храним никогда). */
    var appLockHash: String?

    /** Включена ли биометрическая разблокировка. */
    var biometricEnabled: Boolean

    /** Показывать ли текст сообщения в push-уведомлении на локскрине. */
    var showContentInNotifications: Boolean

    /** Скрывать ли приложения в списке недавних (скриншот-защита). */
    var secureFlagEnabled: Boolean

    /** Произвольный секрет по ключу (для фич, которым нужен свой слот). */
    fun putString(key: String, value: String?)

    fun getString(key: String, default: String? = null): String?

    fun putBoolean(key: String, value: Boolean)

    fun getBoolean(key: String, default: Boolean = false): Boolean

    fun putLong(key: String, value: Long)

    fun getLong(key: String, default: Long = 0L): Long

    fun remove(key: String)

    /** Полный wipe: выход из аккаунта, смена устройства, отзыв сессий. */
    fun clearAll()
}

/**
 * Маркер «чувствительных» ключей: они пишутся только через [SecureStorage]
 * и никогда не попадают в DataStore/Room. Детект нарушения — в lint-правиле.
 */
object SecureKeys {
    const val USER_ID = "sc_user_id"
    const val ACCESS_TOKEN = "sc_access_token"
    const val REFRESH_TOKEN = "sc_refresh_token"
    const val DEVICE_ID = "sc_device_id"
    const val SESSION_ID = "sc_session_id"
    const val APP_LOCK_HASH = "sc_app_lock_hash"
    const val BIOMETRIC_ENABLED = "sc_biometric_enabled"
    const val NOTIF_CONTENT = "sc_notif_content"
    const val SECURE_FLAG = "sc_secure_flag"
    const val TURN_CREDENTIAL_CACHE = "sc_turn_cred_cache"
    const val ADMIN_SESSION_PIN = "sc_admin_session_pin"
}
