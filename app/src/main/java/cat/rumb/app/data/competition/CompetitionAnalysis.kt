package cat.rumb.app.data.competition

import cat.rumb.app.data.gpx.GpxPoint
import cat.rumb.app.viewer.hud.MetricsCalculator

/** Gap of an attempt vs the best track at a given distance. Positive = attempt slower. */
data class GapSample(val distM: Double, val gapSeconds: Double)

/** Pure-JVM analysis helpers for the competition detail screen. */
object CompetitionAnalysis {

    /**
     * Samples the time gap between [attempt] and [best] at [buckets] evenly spaced distances in
     * (0, min(totalBest, totalAttempt)]. Time at each distance comes from a time-at-distance curve
     * built from the timed points of each track (linear interpolation on the distance axis).
     * Positive gap means the attempt is slower than the best at that distance.
     *
     * Returns an empty list if either track has fewer than 2 timed points or the common
     * distance is under 1 m.
     */
    fun gapOverDistance(best: List<GpxPoint>, attempt: List<GpxPoint>, buckets: Int = 300): List<GapSample> {
        val bestCurve = curveOf(best) ?: return emptyList()
        val attemptCurve = curveOf(attempt) ?: return emptyList()
        val total = minOf(bestCurve.totalDist, attemptCurve.totalDist)
        if (total < 1.0 || buckets <= 0) return emptyList()
        val out = ArrayList<GapSample>(buckets)
        for (i in 1..buckets) {
            val d = total * i / buckets
            out.add(GapSample(d, attemptCurve.timeAt(d) - bestCurve.timeAt(d)))
        }
        return out
    }

    /**
     * Milliseconds spent per heart-rate zone, as fractions of [maxHr]:
     * z0 <60%, z1 60–70%, z2 70–80%, z3 80–90%, z4 ≥90%.
     * Each segment between consecutive timed points is attributed to the zone of the later
     * point's heart rate; segments with a null HR or non-positive dt are skipped.
     */
    fun hrZones(points: List<GpxPoint>, maxHr: Int): LongArray {
        val zones = LongArray(5)
        if (maxHr <= 0) return zones
        val timed = points.filter { it.time != null }
        for (i in 1 until timed.size) {
            val dt = timed[i].time!!.toEpochMilli() - timed[i - 1].time!!.toEpochMilli()
            if (dt <= 0) continue
            val hr = timed[i].heartRate ?: continue
            val fraction = hr / maxHr
            val zone = when {
                fraction < 0.6 -> 0
                fraction < 0.7 -> 1
                fraction < 0.8 -> 2
                fraction < 0.9 -> 3
                else -> 4
            }
            zones[zone] += dt
        }
        return zones
    }

    /** Time-at-distance curve. Distances non-decreasing; times coerced monotonic. */
    private class Curve(private val dist: DoubleArray, private val time: DoubleArray) {
        val totalDist: Double get() = dist.last()

        /**
         * Relative seconds at distance [d], linearly interpolated on the distance axis.
         * Where the track pauses the distance repeats; the binary search returns the lower
         * bound (first index with dist >= d), i.e. the FIRST time at that distance.
         */
        fun timeAt(d: Double): Double {
            if (d <= dist.first()) return time.first()
            if (d >= dist.last()) return time.last()
            // Lower bound: first index i with dist[i] >= d.
            var lo = 0
            var hi = dist.size - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (dist[mid] >= d) hi = mid else lo = mid + 1
            }
            val d0 = dist[lo - 1]
            val d1 = dist[lo]
            if (d1 <= d0) return time[lo]
            val f = (d - d0) / (d1 - d0)
            return time[lo - 1] + (time[lo] - time[lo - 1]) * f
        }
    }

    /** Builds the curve from the timed points, or null with fewer than 2 of them. */
    private fun curveOf(points: List<GpxPoint>): Curve? {
        val timed = points.filter { it.time != null }
        if (timed.size < 2) return null
        val dist = DoubleArray(timed.size)
        val time = DoubleArray(timed.size)
        val t0 = timed.first().time!!.toEpochMilli()
        for (i in 1 until timed.size) {
            dist[i] = dist[i - 1] + MetricsCalculator.distanceMeters(timed[i - 1].toGeoPoint(), timed[i].toGeoPoint())
            val t = (timed[i].time!!.toEpochMilli() - t0) / 1000.0
            time[i] = maxOf(t, time[i - 1]) // coerce monotonic
        }
        return Curve(dist, time)
    }
}
