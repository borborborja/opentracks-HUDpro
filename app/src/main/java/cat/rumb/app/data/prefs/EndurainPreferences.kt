package cat.rumb.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Endurain connection settings. Two auth modes:
 *
 * - CREDENTIALS (username + password → JWT): full access — validation, uploads, and reading
 *   activities back. Tokens are cached and refreshed silently; the password is stored so the
 *   session can always be renewed, even after weeks idle.
 * - API_KEY: upload-only. An Endurain API key grants a single scope (`activities:upload`) and works
 *   on exactly one endpoint, so it can upload but can't validate against a read endpoint or list.
 *
 * The username/password and the session tokens are secrets, so they live in an
 * [EncryptedSharedPreferences] store, separate from the plaintext host/api-key/mode file.
 */
class EndurainPreferences private constructor(
    private val prefs: SharedPreferences,
    private val secure: SharedPreferences,
) {

    enum class AuthMode { API_KEY, CREDENTIALS }

    /** Base host, e.g. https://endurain.example.com (no trailing /api/v1). */
    var host: String?
        get() = prefs.getString(KEY_HOST, null)
        set(value) = prefs.edit().putString(KEY_HOST, value?.trimEnd('/')).apply()

    var authMode: AuthMode
        get() = runCatching { AuthMode.valueOf(prefs.getString(KEY_AUTH_MODE, null) ?: "") }
            .getOrDefault(AuthMode.CREDENTIALS)
        set(value) = prefs.edit().putString(KEY_AUTH_MODE, value.name).apply()

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    // --- Credentials (encrypted) ---

    var username: String?
        get() = secure.getString(KEY_USERNAME, null)
        set(value) = secure.edit().putString(KEY_USERNAME, value).apply()

    var password: String?
        get() = secure.getString(KEY_PASSWORD, null)
        set(value) = secure.edit().putString(KEY_PASSWORD, value).apply()

    // --- Cached JWT session (encrypted). Cleared on logout / auth failure. ---

    var accessToken: String?
        get() = secure.getString(KEY_ACCESS, null)
        set(value) = secure.edit().putString(KEY_ACCESS, value).apply()

    var refreshToken: String?
        get() = secure.getString(KEY_REFRESH, null)
        set(value) = secure.edit().putString(KEY_REFRESH, value).apply()

    /** Epoch-ms when the access token stops being usable (with the safety margin already applied). */
    var accessExpiresAtMs: Long
        get() = secure.getLong(KEY_ACCESS_EXP, 0L)
        set(value) = secure.edit().putLong(KEY_ACCESS_EXP, value).apply()

    var refreshExpiresAtMs: Long
        get() = secure.getLong(KEY_REFRESH_EXP, 0L)
        set(value) = secure.edit().putLong(KEY_REFRESH_EXP, value).apply()

    /** Forgets the cached tokens (not the credentials), forcing a fresh login next time. */
    fun clearSession() = secure.edit()
        .remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_ACCESS_EXP).remove(KEY_REFRESH_EXP)
        .apply()

    val isConfigured: Boolean
        get() = !host.isNullOrBlank() && when (authMode) {
            AuthMode.API_KEY -> !apiKey.isNullOrBlank()
            AuthMode.CREDENTIALS -> !username.isNullOrBlank() && !password.isNullOrBlank()
        }

    companion object {
        private const val KEY_HOST = "endurain_host"
        private const val KEY_API_KEY = "endurain_api_key"
        private const val KEY_AUTH_MODE = "endurain_auth_mode"
        private const val KEY_USERNAME = "endurain_username"
        private const val KEY_PASSWORD = "endurain_password"
        private const val KEY_ACCESS = "endurain_access_token"
        private const val KEY_REFRESH = "endurain_refresh_token"
        private const val KEY_ACCESS_EXP = "endurain_access_exp"
        private const val KEY_REFRESH_EXP = "endurain_refresh_exp"

        fun get(context: Context): EndurainPreferences {
            val plain = context.getSharedPreferences("endurain_prefs", Context.MODE_PRIVATE)
            return EndurainPreferences(plain, secureStore(context, plain))
        }

        /**
         * The encrypted store for secrets. If the keystore-backed store can't be opened (a known
         * failure on some devices after a backup restore), fall back to the plaintext file so the
         * app keeps working rather than crashing — degraded, but never dead.
         */
        private fun secureStore(context: Context, fallback: SharedPreferences): SharedPreferences =
            runCatching {
                val key = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "endurain_secure",
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse {
                // Never crash, but this is a security downgrade — the password lands in plaintext.
                cat.rumb.app.data.debug.DebugLog.e("Endurain", "magatzem xifrat no disponible · credencials en clar", it)
                fallback
            }
    }
}
