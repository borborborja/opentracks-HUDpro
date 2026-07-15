package cat.rumb.app.viewer.hud

import cat.rumb.app.data.prefs.ViewerPreferences
import cat.rumb.app.data.tracks.ActivityFamily
import cat.rumb.app.data.tracks.ActivityTypes
import cat.rumb.app.data.tracks.CustomActivityType
import kotlinx.serialization.json.Json

/**
 * Persists/loads the [HudLayout] **per sport**: a runner wants pace where a cyclist wants speed, and
 * a single global layout is what made the app feel cycling-centric.
 *
 * Resolution for a sport: its own saved layout → the factory preset for its family → DEFAULT.
 *
 * Compatibility matters more than tidiness here. Everyone already has ONE global `hudLayoutJson`,
 * possibly hand-tuned over months. It is adopted as the layout of the FIRST sport that asks for one,
 * so nobody opens the app to find their HUD silently replaced by a preset.
 */
object HudLayoutStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The factory layout for a sport, by family — the starting point before the user edits it. */
    fun presetFor(sportId: String?, custom: List<CustomActivityType> = emptyList()): HudLayout =
        when (ActivityTypes.familyOf(sportId, custom)) {
            ActivityFamily.FOOT ->
                if (sportId == ActivityTypes.TRAIL_RUN || sportId == ActivityTypes.HIKE) HudLayout.TRAIL
                else HudLayout.RUNNING
            ActivityFamily.WHEEL -> HudLayout.CYCLING
            ActivityFamily.SNOW -> HudLayout.SKI
            else -> HudLayout.DEFAULT
        }

    /** [sportId] null keeps the old global behaviour (no sport chosen yet). */
    fun load(prefs: ViewerPreferences, sportId: String? = null): HudLayout {
        if (sportId == null) return decode(prefs.hudLayoutJson) ?: HudLayout.DEFAULT
        decode(prefs.hudLayoutJsonFor(sportId))?.let { return it }
        val legacy = decode(prefs.hudLayoutJson)
        if (legacy != null && !prefs.hudLayoutAdopted) {
            prefs.hudLayoutAdopted = true
            save(prefs, legacy, sportId)
            return legacy
        }
        return presetFor(sportId)
    }

    fun save(prefs: ViewerPreferences, layout: HudLayout, sportId: String? = null) {
        val raw = json.encodeToString(HudLayout.serializer(), layout)
        if (sportId == null) prefs.hudLayoutJson = raw else prefs.setHudLayoutJsonFor(sportId, raw)
    }

    private fun decode(raw: String?): HudLayout? =
        raw?.let { runCatching { json.decodeFromString(HudLayout.serializer(), it) }.getOrNull() }
}
