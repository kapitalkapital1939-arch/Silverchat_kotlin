package com.silverchat.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.silverchat.core.common.log.LogTag
import com.silverchat.core.common.log.ScLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Биометрический/PIN замок приложения (код-замок в настройках приватности).
 *
 * Соль хэша PIN хранится в EncryptedSharedPreferences; сравнение — через
 * PBKDF2, поэтому даже при извлечении файла SharedPreferences PIN не
 * восстанавливается перебором за разумное время.
 */
@Singleton
class BiometricAuthenticator @Inject constructor(
    private val secureStorage: SecureStorage,
) {

    val isLockEnabled: Boolean
        get() = !secureStorage.appLockHash.isNullOrBlank()

    val isBiometricAvailable: Boolean
        get() = secureStorage.biometricEnabled

    fun canAuthenticate(activity: FragmentActivity): BiometricAvailability {
        val manager = BiometricManager.from(activity)
        return when (manager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.UPDATE_REQUIRED
            else -> BiometricAvailability.UNKNOWN
        }
    }

    fun enablePinLock(pin: String) {
        require(pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
            "PIN должен содержать $MIN_PIN_LENGTH..$MAX_PIN_LENGTH цифр"
        }
        val salt = generateSalt()
        secureStorage.putString(SALT_KEY, salt)
        secureStorage.appLockHash = hash(pin, salt)
        ScLogger.i(LogTag.SECURITY, "PIN-замок включён")
    }

    fun disablePinLock() {
        secureStorage.appLockHash = null
        secureStorage.remove(SALT_KEY)
        secureStorage.biometricEnabled = false
    }

    fun verifyPin(pin: String): Boolean {
        val stored = secureStorage.appLockHash ?: return false
        val salt = secureStorage.getString(SALT_KEY).orEmpty()
        return constantTimeEquals(stored, hash(pin, salt))
    }

    /**
     * Сравнение без early-return: иначе по времени ответа можно подобрать хэш.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun hash(pin: String, salt: String): String {
        val spec = java.security.spec.PBEKeySpec(
            pin.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            PBKDF2_ITERATIONS,
            256,
        )
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Показ системного биометрического диалога.
     * Вызывается из [FragmentActivity] — Compose-экран оборачивает Activity.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "SilverChat заблокирован",
        subtitle: String = "Подтвердите личность, чтобы открыть приложение",
        onResult: (Boolean, String?) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                ScLogger.i(LogTag.SECURITY, "Биометрия: успех")
                onResult(true, null)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                ScLogger.w(LogTag.SECURITY, "Биометрия: ошибка $errorCode — $errString")
                onResult(false, errString.toString())
            }

            override fun onAuthenticationFailed() {
                onResult(false, "Не распознано, попробуйте ещё раз")
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .setConfirmationRequired(false)
            .build()

        runCatching { prompt.authenticate(info) }
            .onFailure { onResult(false, it.message) }
    }

    private companion object {
        const val SALT_KEY = "sc_pin_salt"
        const val PBKDF2_ITERATIONS = 210_000
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
    }
}

enum class BiometricAvailability {
    AVAILABLE, NOT_ENROLLED, NO_HARDWARE, UNAVAILABLE, UPDATE_REQUIRED, UNKNOWN;

    val canUse: Boolean get() = this == AVAILABLE
}
