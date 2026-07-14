package cat.rumb.app.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * WebDAV upload settings (Nextcloud/ownCloud or any WebDAV server). GPX is PUT to
 * `<url>/<filename>.gpx` with HTTP Basic auth.
 */
class WebDavPreferences private constructor(private val prefs: SharedPreferences) {

    /** Collection URL to PUT into, e.g. https://cloud.example.com/remote.php/dav/files/user/Rumb */
    var url: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit().putString(KEY_URL, value?.trimEnd('/')).apply()

    var user: String?
        get() = prefs.getString(KEY_USER, null)
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    var pass: String?
        get() = prefs.getString(KEY_PASS, null)
        set(value) = prefs.edit().putString(KEY_PASS, value).apply()

    val isConfigured: Boolean
        get() = !url.isNullOrBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank()

    companion object {
        private const val KEY_URL = "webdav_url"
        private const val KEY_USER = "webdav_user"
        private const val KEY_PASS = "webdav_pass"

        fun get(context: Context): WebDavPreferences =
            WebDavPreferences(context.getSharedPreferences("webdav_prefs", Context.MODE_PRIVATE))
    }
}
