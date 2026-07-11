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

/** Screen anchor where a group of widgets is placed. */
@Serializable
enum class HudAnchor { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }

@Serializable
data class HudWidget(val metric: HudMetric, val anchor: HudAnchor)

/** The full HUD configuration: an ordered list of placed widgets + a size scale. */
@Serializable
data class HudLayout(
    val widgets: List<HudWidget> = emptyList(),
    val scale: Float = 1.0f,
    val showCompass: Boolean = true,
) {
    fun byAnchor(anchor: HudAnchor): List<HudMetric> =
        widgets.filter { it.anchor == anchor }.map { it.metric }

    companion object {
        val CYCLING = HudLayout(
            widgets = listOf(
                HudWidget(HudMetric.SPEED, HudAnchor.BOTTOM_LEFT),
                HudWidget(HudMetric.AVG_SPEED, HudAnchor.BOTTOM_LEFT),
                HudWidget(HudMetric.DISTANCE, HudAnchor.BOTTOM_RIGHT),
                HudWidget(HudMetric.DURATION, HudAnchor.BOTTOM_RIGHT),
                HudWidget(HudMetric.ELEV_GAIN, HudAnchor.TOP_RIGHT),
            ),
        )
        val TRAIL = HudLayout(
            widgets = listOf(
                HudWidget(HudMetric.PACE, HudAnchor.BOTTOM_LEFT),
                HudWidget(HudMetric.DISTANCE, HudAnchor.BOTTOM_LEFT),
                HudWidget(HudMetric.MOVING_TIME, HudAnchor.BOTTOM_RIGHT),
                HudWidget(HudMetric.ELEV_GAIN, HudAnchor.BOTTOM_RIGHT),
                HudWidget(HudMetric.REMAINING, HudAnchor.TOP_RIGHT),
            ),
        )
        val SKI = HudLayout(
            widgets = listOf(
                HudWidget(HudMetric.SPEED, HudAnchor.BOTTOM_LEFT),
                HudWidget(HudMetric.MAX_SPEED, HudAnchor.BOTTOM_LEFT),
                HudWidget(HudMetric.ALTITUDE, HudAnchor.BOTTOM_RIGHT),
                HudWidget(HudMetric.SLOPE, HudAnchor.BOTTOM_RIGHT),
            ),
        )
        val DEFAULT = CYCLING
        val PRESETS = mapOf("Ciclisme" to CYCLING, "Trail" to TRAIL, "Esquí" to SKI)
    }
}
