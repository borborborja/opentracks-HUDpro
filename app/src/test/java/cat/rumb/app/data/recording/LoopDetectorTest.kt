package cat.rumb.app.data.recording

import cat.rumb.app.data.opentracks.model.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The detector in isolation. It feeds synthetic fixes at ~11 m/step (0.0001° lat) and asserts on the
 * returned [LoopDetector.Match]. The star case is the out-and-back: a retrace must NOT be a loop.
 */
class LoopDetectorTest {

    private val M_PER_DEG_LAT = 111_320.0
    private fun cosLat(lat: Double) = Math.cos(Math.toRadians(lat))

    /** A GeoPoint [northM] north and [eastM] east of (41, 2). */
    private fun at(northM: Double, eastM: Double = 0.0) = GeoPoint(
        41.0 + northM / M_PER_DEG_LAT,
        2.0 + eastM / (M_PER_DEG_LAT * cosLat(41.0)),
    )

    /**
     * Drives a closed rectangle [side] m on a side, starting from the given odometer/seq, one fix
     * per ~11 m at 1 fix/s. Returns the first Match reported, plus the final odometer/seq/state.
     */
    private class Feed(val det: LoopDetector) {
        var dist = 0.0
        var seq = 0L
        var ms = 0L
        var match: LoopDetector.Match? = null
        fun go(to: GeoPoint, legM: Double) {
            dist += legM
            seq++
            ms += 1000
            val m = det.onFix(seq, to, dist, ms)
            if (m != null && match == null) match = m
        }
    }

    /** Walks a straight line from a to b in ~11 m steps, feeding each. */
    private fun Feed.line(from: GeoPoint, to: GeoPoint) {
        val dN = to.latitude - from.latitude
        val dE = to.longitude - from.longitude
        val distM = LoopMath.dist(from, to)
        val steps = maxOf(1, (distM / 11.0).toInt())
        for (i in 1..steps) {
            val p = GeoPoint(from.latitude + dN * i / steps, from.longitude + dE * i / steps)
            go(p, distM / steps)
        }
    }

    /** One clockwise lap of a square with the given corner reach (m). Start/end at the SW corner. */
    private fun Feed.square(side: Double) {
        val sw = at(0.0, 0.0); val nw = at(side, 0.0); val ne = at(side, side); val se = at(0.0, side)
        line(sw, nw); line(nw, ne); line(ne, se); line(se, sw)
    }

    @Test
    fun outAndBackIsNotALoop() {
        // 2 km north, 2 km back. At every return point you're on the outbound path, but heading the
        // opposite way — the whole reason the bearing test exists.
        val f = Feed(LoopDetector())
        f.line(at(0.0), at(2000.0))
        f.line(at(2000.0), at(0.0))
        assertThat(f.match).isNull()
    }

    @Test
    fun secondLapOfASquareIsDetected() {
        val f = Feed(LoopDetector())
        f.square(400.0) // lap 1
        f.square(400.0) // lap 2 → detected partway through
        assertThat(f.match).isNotNull()
        // The loop length is one square's perimeter (~1600 m), not two.
        assertThat(f.match!!.lapLengthM).isBetween(1400.0, 1800.0)
    }

    @Test
    fun theStartIsRetroactiveInLapOneNotAtTheConfirmPoint() {
        // Asserted on the odometer, not geography: P0 and the 60 m-into-lap-2 confirm point are only
        // ~60 m apart on the ground (both near the SW corner), so distance-on-the-ground can't tell
        // retroactive from not. The odometer can: the start is back in lap 1 (~0), the close a full
        // lap later (~1660). The 40 m trailing chord delays the corner, so allow the start a little
        // way up the first side.
        val f = Feed(LoopDetector())
        f.square(400.0)
        f.square(400.0)
        val m = f.match!!
        assertThat(m.startDistM).isLessThan(200.0)   // back in lap 1
        assertThat(m.closeDistM).isGreaterThan(1600.0) // a whole lap later
    }

    @Test
    fun aTinyLoopIsNeverReportedAtItsSubMinimumLength() {
        // A 50 m square is a 200 m loop, under LOOP_MIN_M = 300. It is NOT that it's ignored — ridden
        // enough it groups two physical laps into one 400 m circuit, which is defensible. What must
        // never happen is a reported length below the minimum, i.e. claiming the raw 200 m loop.
        val f = Feed(LoopDetector())
        repeat(4) { f.square(50.0) }
        f.match?.let { assertThat(it.lapLengthM).isGreaterThanOrEqualTo(300.0) }
    }

    @Test
    fun aSinglePassNeverDetects() {
        val f = Feed(LoopDetector())
        f.square(400.0)
        assertThat(f.match).isNull()
    }
}

/** Tiny haversine so the test doesn't reach into the viewer package for one call. */
private object LoopMath {
    fun dist(a: GeoPoint, b: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val la1 = Math.toRadians(a.latitude); val la2 = Math.toRadians(b.latitude)
        val h = Math.sin(dLat / 2).let { it * it } +
            Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(h)))
    }
}
