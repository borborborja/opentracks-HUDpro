package cat.rumb.app.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * "Save GPX to a folder" settings. [treeUri] is a persisted SAF document-tree Uri chosen by the user
 * via ACTION_OPEN_DOCUMENT_TREE; [enabled] gates the auto-save on recording.
 */
class FolderExportPreferences private constructor(private val prefs: SharedPreferences) {

    var treeUri: String?
        get() = prefs.getString(KEY_TREE_URI, null)
        set(value) = prefs.edit().putString(KEY_TREE_URI, value).apply()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** On when a folder has been chosen and auto-save is enabled. */
    val isEnabled: Boolean get() = enabled && !treeUri.isNullOrBlank()

    companion object {
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_ENABLED = "enabled"

        fun get(context: Context): FolderExportPreferences =
            FolderExportPreferences(context.getSharedPreferences("folder_export_prefs", Context.MODE_PRIVATE))
    }
}
