package cat.rumb.app.data.competition

import cat.rumb.app.data.gpx.GpxPoint
import cat.rumb.app.data.opentracks.model.GeoPoint
import cat.rumb.app.viewer.hud.MetricsCalculator

/**
 * Replays a previously recorded track ("ghost") on its own timeline. Built from the reference
 * track's timed points; answers "where was the ghost, and how far along, at elapsed time t?".
 * Pure JVM. A pause in the reference (time advancing, position still) keeps the ghost still —
 * the honest race: the ghost repeats exactly what happened that day.
 */
class GhostEngine(points: List<GpxPoint>) {

    private val relTimeMs: LongArray
    private val cumMeters: DoubleArray
    private val lats: DoubleArray
    private val lons: DoubleArray

    val totalDurationMs: Long
    val totalMeters: Double

    init {
        val timed = points.filter { it.time != null }
        require(timed.size >= 2) { "ghost reference needs at least 2 timed points" }
        val start = timed.first().time!!.toEpochMilli()
        relTimeMs = LongArray(timed.size)
        cumMeters = DoubleArray(timed.size)
        lats = DoubleArray(timed.size)
        lons = DoubleArray(timed.size)
        var acc = 0.0
        var prevT = 0L
        for (i in timed.indices) {
            val p = timed[i]
            // Coerce monotonic time (guards GPS clock skew).
            val t = (p.time!!.toEpochMilli() - start).coerceAtLeast(prevT)
            relTimeMs[i] = t
            prevT = t
            if (i > 0) acc += MetricsCalculator.distanceMeters(timed[i - 1].toGeoPoint(), p.toGeoPoint())
            cumMeters[i] = acc
            lats[i] = p.latitude
            lons[i] = p.longitude
        }
        totalDurationMs = relTimeMs.last()
        totalMeters = acc
    }

    /** Ghost's distance along its own track (m) at [elapsedMs], clamped to [0, totalMeters]. */
    fun distanceAt(elapsedMs: Long): Double = interpolate(elapsedMs) { i, f ->
        cumMeters[i] + (cumMeters[i + 1] - cumMeters[i]) * f
    } ?: if (elapsedMs <= 0) 0.0 else totalMeters

    /** Ghost's position at [elapsedMs], clamped to the track's ends. */
    fun positionAt(elapsedMs: Long): GeoPoint {
        interpolate(elapsedMs) { i, f ->
            return GeoPoint(lats[i] + (lats[i + 1] - lats[i]) * f, lons[i] + (lons[i + 1] - lons[i]) * f)
        }
        return if (elapsedMs <= 0) GeoPoint(lats.first(), lons.first()) else GeoPoint(lats.last(), lons.last())
    }

    /** Runs [value] with the bracketing index and fraction for [elapsedMs], or null when clamped. */
    private inline fun <T> interpolate(elapsedMs: Long, value: (Int, Double) -> T): T? {
        if (elapsedMs <= 0 || elapsedMs >= totalDurationMs) return null
        var lo = 0
        var hi = relTimeMs.lastIndex
        while (lo + 1 < hi) {
            val mid = (lo + hi) / 2
            if (relTimeMs[mid] <= elapsedMs) lo = mid else hi = mid
        }
        val span = relTimeMs[lo + 1] - relTimeMs[lo]
        val f = if (span > 0) (elapsedMs - relTimeMs[lo]).toDouble() / span else 0.0
        return value(lo, f)
    }

    companion object {
        /** True when [points] can act as a ghost reference: ≥2 points carrying timestamps. */
        fun isTimed(points: List<GpxPoint>): Boolean = points.count { it.time != null } >= 2
    }
}
