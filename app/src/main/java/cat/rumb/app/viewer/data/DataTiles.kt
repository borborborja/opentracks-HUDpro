package cat.rumb.app.viewer.data

import cat.rumb.app.R
import cat.rumb.app.viewer.hud.HudMetric

/**
 * Catalog of the non-metric Dades tiles and the tab taxonomy for the add-tile menu. All ids are
 * stable strings stored in [DataLayout.fields] (metric names, the [DataLayout.CLOCK] sentinel, and
 * the `chart:*` / `toggle:*` namespaces below). Unknown ids are ignored on load, so this can grow.
 */
enum class DataTab { GENERAL, COMPETITION, SETTINGS }

/** A graphical tile: a mini-chart of a live series drawn in the grid. */
enum class DataChart(val id: String, val labelRes: Int) {
    SPEED("chart:speed", R.string.data_chart_speed),
    ELEVATION("chart:elevation", R.string.data_chart_elevation),
    ;

    companion object {
        fun byId(id: String): DataChart? = entries.firstOrNull { it.id == id }
    }
}

/**
 * An interactive settings tile: a labelled switch that flips a [ViewerPreferences] boolean live.
 * [liveEffect] = the change applies immediately (the viewer re-reads it); when false the setting only
 * takes effect on the next recording (e.g. the barometer, read by the service at start).
 */
enum class DataToggle(val id: String, val labelRes: Int, val liveEffect: Boolean, val hintRes: Int? = null) {
    AUTO_PAUSE("toggle:recAutoPause", R.string.data_toggle_auto_pause, liveEffect = true),
    TURN_VOICE("toggle:turnVoice", R.string.data_toggle_turn_voice, liveEffect = true),
    ADAPTIVE_ZOOM("toggle:adaptiveZoom", R.string.data_toggle_adaptive_zoom, liveEffect = true),
    KEEP_SCREEN("toggle:keepScreenOn", R.string.data_toggle_keep_screen, liveEffect = true),
    BAROMETER("toggle:recBarometer", R.string.data_toggle_barometer, liveEffect = false, hintRes = R.string.data_toggle_barometer_hint),
    ;

    companion object {
        fun byId(id: String): DataToggle? = entries.firstOrNull { it.id == id }
    }
}

/** Metrics that are only meaningful mid-competition; addable always but rendered only when competing. */
val COMPETITION_FIELDS: Set<String> = setOf(HudMetric.GHOST_DELTA.name, HudMetric.GHOST_SECONDS.name)

/** Which add-menu tab a Dades field id belongs to. */
fun dataTabOf(id: String): DataTab = when {
    id in COMPETITION_FIELDS -> DataTab.COMPETITION
    DataToggle.byId(id) != null -> DataTab.SETTINGS
    else -> DataTab.GENERAL
}

/** Metrics that carry a recent history, so a per-tile mini-sparkline can be offered for them. */
val GRAPHABLE_FIELDS: Set<String> = setOf(
    HudMetric.SPEED.name, HudMetric.AVG_SPEED.name, HudMetric.MAX_SPEED.name,
    HudMetric.HEART_RATE.name, HudMetric.CADENCE.name, HudMetric.POWER.name,
    HudMetric.ALTITUDE.name, HudMetric.ELEV_GAIN.name,
)

fun fieldSupportsGraph(id: String): Boolean = id in GRAPHABLE_FIELDS

/** String resource labelling a Dades field id in menus/dialogs (0 if unknown). */
fun fieldLabelRes(id: String): Int = when {
    id == DataLayout.CLOCK -> R.string.hudel_clock
    DataChart.byId(id) != null -> DataChart.byId(id)!!.labelRes
    DataToggle.byId(id) != null -> DataToggle.byId(id)!!.labelRes
    else -> runCatching { HudMetric.valueOf(id).labelRes }.getOrDefault(0)
}

/** Every field id offerable in the add-tile menu, grouped by tab (order = menu order). */
object DataCatalog {
    val general: List<String> =
        (HudMetric.entries.map { it.name }.filterNot { it in COMPETITION_FIELDS }) +
            DataLayout.CLOCK +
            DataChart.entries.map { it.id }

    val competition: List<String> = COMPETITION_FIELDS.toList()

    val settings: List<String> = DataToggle.entries.map { it.id }

    fun forTab(tab: DataTab): List<String> = when (tab) {
        DataTab.GENERAL -> general
        DataTab.COMPETITION -> competition
        DataTab.SETTINGS -> settings
    }
}
