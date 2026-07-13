package cat.rumb.app.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Endurain connection settings. An API key with scope `activities:upload` is recommended over
 * username/password JWT (no 15-min refresh, no X-Client-Type/CSRF handling).
 */
class EndurainPreferences private constructor(private val prefs: SharedPreferences) {

    /** Base host, e.g. https://endurain.example.com (no trailing /api/v1). */
    var host: String?
        get() = prefs.getString(KEY_HOST, null)
        set(value) = prefs.edit().putString(KEY_HOST, value?.trimEnd('/')).apply()

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    val isConfigured: Boolean get() = !host.isNullOrBlank() && !apiKey.isNullOrBlank()

    companion object {
        private const val KEY_HOST = "endurain_host"
        private const val KEY_API_KEY = "endurain_api_key"

        fun get(context: Context): EndurainPreferences =
            EndurainPreferences(context.getSharedPreferences("endurain_prefs", Context.MODE_PRIVATE))
    }
}
