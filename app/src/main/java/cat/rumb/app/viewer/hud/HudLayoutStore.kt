package cat.rumb.app.viewer.hud

import cat.rumb.app.data.prefs.ViewerPreferences
import kotlinx.serialization.json.Json

/** Persists/loads the [HudLayout] as JSON in [ViewerPreferences]. */
object HudLayoutStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(prefs: ViewerPreferences): HudLayout {
        val raw = prefs.hudLayoutJson ?: return HudLayout.DEFAULT
        return runCatching { json.decodeFromString(HudLayout.serializer(), raw) }.getOrDefault(HudLayout.DEFAULT)
    }

    fun save(prefs: ViewerPreferences, layout: HudLayout) {
        prefs.hudLayoutJson = json.encodeToString(HudLayout.serializer(), layout)
    }
}
