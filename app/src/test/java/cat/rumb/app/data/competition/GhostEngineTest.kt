package cat.rumb.app.data.competition

import cat.rumb.app.data.gpx.GpxPoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant

class GhostEngineTest {

    private val t0: Instant = Instant.parse("2026-07-13T10:00:00Z")

    /** North at ~11.1 m/s: 0.0001° lat per second. */
    private fun steady(n: Int): List<GpxPoint> =
        (0 until n).map { GpxPoint(41.0 + it * 0.0001, 2.0, time = t0.plusSeconds(it.toLong())) }

    @Test
    fun interpolatesDistanceAndPositionMidway() {
        val g = GhostEngine(steady(11)) // 10 s, ~111 m
        assertThat(g.totalDurationMs).isEqualTo(10_000)
        assertThat(g.totalMeters).isCloseTo(111.2, within(2.0))
        // Halfway between points 5 and 6.
        assertThat(g.distanceAt(5_500)).isCloseTo(g.totalMeters * 0.55, within(1.5))
        val pos = g.positionAt(5_500)
        assertThat(pos.latitude).isCloseTo(41.00055, within(1e-5))
        assertThat(pos.longitude).isEqualTo(2.0)
    }

    @Test
    fun clampsBeforeStartAndAfterEnd() {
        val g = GhostEngine(steady(11))
        assertThat(g.distanceAt(-5_000)).isEqualTo(0.0)
        assertThat(g.positionAt(0).latitude).isEqualTo(41.0)
        assertThat(g.distanceAt(999_999)).isEqualTo(g.totalMeters)
        assertThat(g.positionAt(999_999).latitude).isCloseTo(41.001, within(1e-9))
    }

    @Test
    fun pauseInReferenceKeepsGhostStill() {
        // Moves 5 s, stands still (same position) from t=5 to t=65, then moves again.
        val moving1 = (0..5).map { GpxPoint(41.0 + it * 0.0001, 2.0, time = t0.plusSeconds(it.toLong())) }
        val still = GpxPoint(41.0005, 2.0, time = t0.plusSeconds(65))
        val moving2 = (1..5).map { GpxPoint(41.0005 + it * 0.0001, 2.0, time = t0.plusSeconds(65 + it.toLong())) }
        val g = GhostEngine(moving1 + still + moving2)
        val dAtPauseStart = g.distanceAt(5_000)
        assertThat(g.distanceAt(30_000)).isCloseTo(dAtPauseStart, within(0.01))
        assertThat(g.distanceAt(64_000)).isCloseTo(dAtPauseStart, within(0.01))
        assertThat(g.distanceAt(70_000)).isGreaterThan(dAtPauseStart + 30)
    }

    @Test
    fun activeTimeRebasedGhostFinishesAtBestLapTime() {
        // A lap ghost re-based to active time (pause gap compressed out, as buildActiveTimeLapPoints
        // does): 10 s of movement whose timestamps already exclude a 60 s recording pause. The ghost's
        // total duration must equal the active lap time, so it "finishes" exactly at bestLapMs and the
        // chaser (driven by pause-excluding currentLapTimeMs) sees no lag.
        val g = GhostEngine(steady(11)) // active timestamps 0..10 s
        assertThat(g.totalDurationMs).isEqualTo(10_000) // == active lap time, NOT 10 s + pause
        // Distance advances continuously across the (removed) pause instant.
        assertThat(g.distanceAt(5_000)).isCloseTo(g.totalMeters * 0.5, within(1.5))
        assertThat(g.distanceAt(9_000)).isGreaterThan(g.distanceAt(5_000))
    }

    @Test
    fun isTimedRequiresTwoTimedPoints() {
        assertThat(GhostEngine.isTimed(steady(2))).isTrue()
        assertThat(GhostEngine.isTimed(listOf(GpxPoint(41.0, 2.0), GpxPoint(41.1, 2.0)))).isFalse()
        assertThat(GhostEngine.isTimed(listOf(GpxPoint(41.0, 2.0, time = t0), GpxPoint(41.1, 2.0)))).isFalse()
        assertThatThrownBy { GhostEngine(listOf(GpxPoint(41.0, 2.0, time = t0))) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun nonMonotonicTimestampsAreCoerced() {
        val pts = listOf(
            GpxPoint(41.0, 2.0, time = t0),
            GpxPoint(41.0001, 2.0, time = t0.plusSeconds(2)),
            GpxPoint(41.0002, 2.0, time = t0.plusSeconds(1)), // clock skew backwards
            GpxPoint(41.0003, 2.0, time = t0.plusSeconds(3)),
        )
        val g = GhostEngine(pts) // must not crash
        assertThat(g.totalDurationMs).isEqualTo(3_000)
        assertThat(g.distanceAt(3_000)).isEqualTo(g.totalMeters)
    }

    @Test
    fun untimedPointsMixedInAreSkipped() {
        val pts = listOf(
            GpxPoint(41.0, 2.0, time = t0),
            GpxPoint(41.00005, 2.0), // no time — skipped
            GpxPoint(41.0001, 2.0, time = t0.plusSeconds(1)),
        )
        val g = GhostEngine(pts)
        assertThat(g.totalDurationMs).isEqualTo(1_000)
        assertThat(g.totalMeters).isCloseTo(11.1, within(0.5))
    }
}
