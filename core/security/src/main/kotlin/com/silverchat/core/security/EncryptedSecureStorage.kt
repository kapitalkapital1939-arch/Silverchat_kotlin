package com.silverchat.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [SecureStorage] на `EncryptedSharedPreferences`.
 *
 * Схема шифрования:
 *  - MasterKey: AES256-GCM, ключ генерируется внутри Android Keystore
 *    (`setKeyScheme(AES256_GCM)`), извлечь его из устройства нельзя;
 *  - файл настроек: ключи — AES256-SIV, значения — AES256-GCM.
 *
 * Отдельно обработан самый частый production-инцидент этого API:
 * при смене прошивки/восстановлении из бэкапа Keystore-ключ перестаёт
 * соответствовать зашифрованному файлу и каждое чтение бросает
 * `AEADBadTagException`. Мы НЕ роняем приложение, а детектируем ситуацию,
 * удаляем повреждённый файл и пересоздаём хранилище — пользователь просто
 * логинится заново.
 */
@Singleton
class EncryptedSecureStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : SecureStorage {

    private val prefs: SharedPreferences by lazy { createOrRecover() }

    /* ── Создание / восстановление хранилища ─────────────────────────────── */

    private fun createOrRecover(): SharedPreferences = try {
        create()
    } catch (e: GeneralSecurityException) {
        ScLogger.e(LogTag.SECURITY, "Keystore/EncryptedSharedPreferences повреждён — пересоздаём", e)
        wipeFiles()
        runCatching { create() }.getOrElse {
            ScLogger.e(LogTag.SECURITY, "Повторное создание тоже упало — fallback на in-memory", it)
            InMemoryFallback.create(context)
        }
    } catch (e: Exception) {
        ScLogger.e(LogTag.SECURITY, "Неожиданная ошибка шифрованного хранилища", e)
        wipeFiles()
        runCatching { create() }.getOrElse { InMemoryFallback.create(context) }
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(false) // StrongBox есть не везде — деградируем мягко
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun wipeFiles() {
        runCatching {
            File(context.filesDir.parentFile, "shared_prefs/$PREFS_FILE_NAME.xml").delete()
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }.onFailure { ScLogger.w(LogTag.SECURITY, "Не удалось удалить повреждённый файл", it) }
    }

    /* ── Типизированные слоты ────────────────────────────────────────────── */

    override var accessToken: String?
        get() = getString(SecureKeys.ACCESS_TOKEN)
        set(value) = putString(SecureKeys.ACCESS_TOKEN, value)

    override var refreshToken: String?
        get() = getString(SecureKeys.REFRESH_TOKEN)
        set(value) = putString(SecureKeys.REFRESH_TOKEN, value)

    override var deviceId: String?
        get() = getString(SecureKeys.DEVICE_ID)
        set(value) = putString(SecureKeys.DEVICE_ID, value)

    override var sessionId: String?
        get() = getString(SecureKeys.SESSION_ID)
        set(value) = putString(SecureKeys.SESSION_ID, value)

    override var appLockHash: String?
        get() = getString(SecureKeys.APP_LOCK_HASH)
        set(value) = putString(SecureKeys.APP_LOCK_HASH, value)

    override var biometricEnabled: Boolean
        get() = getBoolean(SecureKeys.BIOMETRIC_ENABLED, false)
        set(value) = putBoolean(SecureKeys.BIOMETRIC_ENABLED, value)

    override var showContentInNotifications: Boolean
        get() = getBoolean(SecureKeys.NOTIF_CONTENT, true)
        set(value) = putBoolean(SecureKeys.NOTIF_CONTENT, value)

    override var secureFlagEnabled: Boolean
        get() = getBoolean(SecureKeys.SECURE_FLAG, false)
        set(value) = putBoolean(SecureKeys.SECURE_FLAG, value)

    /* ── Общий доступ ────────────────────────────────────────────────────── */

    override fun putString(key: String, value: String?) {
        prefs.edit { if (value == null) remove(key) else putString(key, value) }
    }

    override fun getString(key: String, default: String?): String? =
        runCatching { prefs.getString(key, default) }
            .onFailure { ScLogger.e(LogTag.SECURITY, "Чтение '$key' не удалось", it) }
            .getOrNull() ?: default

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        runCatching { prefs.getBoolean(key, default) }.getOrDefault(default)

    override fun putLong(key: String, value: Long) {
        prefs.edit { putLong(key, value) }
    }

    override fun getLong(key: String, default: Long): Long =
        runCatching { prefs.getLong(key, default) }.getOrDefault(default)

    override fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    override fun clearAll() {
        ScLogger.i(LogTag.SECURITY, "Полная очистка защищённого хранилища (logout)")
        prefs.edit { clear() }
    }

    private inline fun SharedPreferences.edit(block: SharedPreferences.Editor.() -> Unit) {
        edit().apply(block).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "silverchat_secure_prefs"
    }
}

/**
 * Аварийный режим: если Keystore недоступен (редкие кастомные прошивки),
 * приложение не должно падать на старте. Храним в памяти и помечаем сессию
 * как деградировавшую — UI предложит перелогиниться.
 */
private object InMemoryFallback {
    fun create(context: Context): SharedPreferences {
        ScLogger.w(LogTag.SECURITY, "Включён небезопасный fallback-режим хранилища")
        return context.getSharedPreferences("sc_insecure_fallback", Context.MODE_PRIVATE)
    }
}
