package cat.hudpro.opentracks.viewer.hud

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration

/** A single displayable metric. Each knows its label, unit and how to format a [LiveMetrics]. */
enum class HudMetric(val label: String, val unit: String, val format: (LiveMetrics) -> String) {
    SPEED("Velocitat", "km/h", { fmt1(it.speedKmh) }),
    AVG_SPEED("Vel. mitjana", "km/h", { fmt1(it.avgMovingSpeedKmh) }),
    MAX_SPEED("Vel. màx", "km/h", { fmt1(it.maxSpeedKmh) }),
    DISTANCE("Distància", "km", { fmt2(it.distanceKm) }),
    DURATION("Temps", "", { fmtDuration(it.totalTime) }),
    MOVING_TIME("Temps mov.", "", { fmtDuration(it.movingTime) }),
    PACE("Ritme", "/km", { fmtPace(it.paceMinPerKm) }),
    HEADING("Rumb", "°", { fmt0(it.bearingDeg) }),
    ELEV_GAIN("Desnivell +", "m", { fmt0(it.elevationGainM) }),
    ALTITUDE("Altitud", "m", { fmt0(it.altitudeM) }),
    SLOPE("Pendent", "%", { fmt1(it.slopePercent) }),
    VAM("VAM", "m/h", { fmt0(it.vamMeterPerHour) }),
    HEART_RATE("FC", "bpm", { fmt0(it.heartRateBpm) }),
    CADENCE("Cadència", "rpm", { fmt0(it.cadenceRpm) }),
    POWER("Potència", "W", { fmt0(it.powerW) }),
    REMAINING("Restant", "km", { fmt2(it.remainingDistanceKm) }),
    OFF_ROUTE("Desviació", "m", { fmt0(it.offRouteMeters) }),
    ;

    fun value(m: LiveMetrics): String = format(m)

    companion object {
        private fun fmt0(v: Double?) = v?.roundToInt()?.toString() ?: "—"
        private fun fmt1(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
        private fun fmt2(v: Double?) = v?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
        private fun fmtPace(v: Double?): String {
            if (v == null) return "—"
            val totalSec = (v * 60).roundToInt()
            return "%d:%02d".format(totalSec / 60, totalSec % 60)
        }
        private fun fmtDuration(d: Duration): String {
            val s = d.inWholeSeconds
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
        }
    }
}

/** Category of a placeable HUD element (drives palette grouping and rendering). */
enum class HudCategory { METRIC, CHART, CONTROL }

/** Descriptor of a placeable HUD element, resolvable from a stable [id]. */
data class HudElement(
    val id: String,
    val label: String,
    val category: HudCategory,
    val metric: HudMetric? = null,
)

/** Registry of every element that can be placed on the HUD. Ids are stable & serialized. */
object HudCatalog {
    const val CHART_SPEED = "chart:speed"
    const val CHART_ELEVATION = "chart:elevation"
    const val CONTROL_RECENTER = "control:recenter"
    const val CONTROL_COMPASS = "control:compass"
    const val CONTROL_ZOOM = "control:zoom"
    const val CONTROL_RECORD = "control:record"

    fun idOf(metric: HudMetric) = "metric:${metric.name}"

    val elements: List<HudElement> = buildList {
        HudMetric.entries.forEach { add(HudElement(idOf(it), it.label, HudCategory.METRIC, it)) }
        add(HudElement(CHART_SPEED, "Gràfic velocitat", HudCategory.CHART))
        add(HudElement(CHART_ELEVATION, "Perfil altitud", HudCategory.CHART))
        add(HudElement(CONTROL_RECENTER, "Centrar/seguir", HudCategory.CONTROL))
        add(HudElement(CONTROL_COMPASS, "Brúixola", HudCategory.CONTROL))
        add(HudElement(CONTROL_ZOOM, "Zoom", HudCategory.CONTROL))
        add(HudElement(CONTROL_RECORD, "Gravació", HudCategory.CONTROL))
    }

    private val byId = elements.associateBy { it.id }
    fun byId(id: String): HudElement? = byId[id]
}

/**
 * Screen zone where a widget is placed. Widgets in the same zone auto-stack (no overlap). No
 * center-center zone so the map middle stays clear.
 */
@Serializable
enum class HudZone(val label: String) {
    TOP_LEFT("↖"), TOP_CENTER("↑"), TOP_RIGHT("↗"),
    MIDDLE_LEFT("←"), MIDDLE_RIGHT("→"),
    BOTTOM_LEFT("↙"), BOTTOM_CENTER("↓"), BOTTOM_RIGHT("↘"),
    ;

    val isCenter: Boolean get() = this == TOP_CENTER || this == BOTTOM_CENTER
}

/** A placed HUD element assigned to a [zone]. Stacking order follows the list order. */
@Serializable
data class HudWidget(
    val elementId: String,
    val zone: HudZone,
    val scale: Float = 1f,
) {
    val element: HudElement? get() = HudCatalog.byId(elementId)
}

/** The full HUD configuration: zone-placed widgets + a global size scale. */
@Serializable
data class HudLayout(
    val widgets: List<HudWidget> = emptyList(),
    val scale: Float = 1.0f,
) {
    fun contains(elementId: String) = widgets.any { it.elementId == elementId }

    fun byZone(zone: HudZone): List<HudWidget> = widgets.filter { it.zone == zone }

    fun add(elementId: String, zone: HudZone = HudZone.TOP_LEFT): HudLayout =
        if (contains(elementId)) this else copy(widgets = widgets + HudWidget(elementId, zone))

    fun remove(elementId: String): HudLayout =
        copy(widgets = widgets.filterNot { it.elementId == elementId })

    fun moveToZone(index: Int, zone: HudZone): HudLayout {
        if (index !in widgets.indices) return this
        val w = widgets[index].copy(zone = zone)
        return copy(widgets = widgets.toMutableList().also { it[index] = w })
    }

    companion object {
        private fun m(metric: HudMetric, zone: HudZone) = HudWidget(HudCatalog.idOf(metric), zone)

        val CYCLING = HudLayout(
            widgets = listOf(
                m(HudMetric.SPEED, HudZone.BOTTOM_LEFT),
                m(HudMetric.AVG_SPEED, HudZone.BOTTOM_LEFT),
                m(HudMetric.DISTANCE, HudZone.BOTTOM_RIGHT),
                m(HudMetric.DURATION, HudZone.BOTTOM_RIGHT),
                m(HudMetric.ELEV_GAIN, HudZone.TOP_RIGHT),
                HudWidget(HudCatalog.CHART_SPEED, HudZone.TOP_LEFT),
                HudWidget(HudCatalog.CONTROL_RECENTER, HudZone.MIDDLE_RIGHT),
                HudWidget(HudCatalog.CONTROL_RECORD, HudZone.MIDDLE_RIGHT),
            ),
        )
        val TRAIL = HudLayout(
            widgets = listOf(
                m(HudMetric.PACE, HudZone.BOTTOM_LEFT),
                m(HudMetric.DISTANCE, HudZone.BOTTOM_LEFT),
                m(HudMetric.MOVING_TIME, HudZone.BOTTOM_RIGHT),
                m(HudMetric.ELEV_GAIN, HudZone.BOTTOM_RIGHT),
                m(HudMetric.REMAINING, HudZone.TOP_RIGHT),
                HudWidget(HudCatalog.CHART_ELEVATION, HudZone.TOP_LEFT),
                HudWidget(HudCatalog.CONTROL_RECENTER, HudZone.MIDDLE_RIGHT),
            ),
        )
        val SKI = HudLayout(
            widgets = listOf(
                m(HudMetric.SPEED, HudZone.BOTTOM_LEFT),
                m(HudMetric.MAX_SPEED, HudZone.BOTTOM_LEFT),
                m(HudMetric.ALTITUDE, HudZone.BOTTOM_RIGHT),
                m(HudMetric.SLOPE, HudZone.BOTTOM_RIGHT),
                HudWidget(HudCatalog.CONTROL_COMPASS, HudZone.MIDDLE_RIGHT),
                HudWidget(HudCatalog.CONTROL_ZOOM, HudZone.MIDDLE_RIGHT),
                HudWidget(HudCatalog.CONTROL_RECENTER, HudZone.MIDDLE_RIGHT),
            ),
        )
        val DEFAULT = CYCLING
        val PRESETS = mapOf("Ciclisme" to CYCLING, "Trail" to TRAIL, "Esquí" to SKI)
    }
}
