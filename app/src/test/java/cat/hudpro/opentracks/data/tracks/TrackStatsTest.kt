package cat.hudpro.opentracks.data.tracks

import cat.hudpro.opentracks.data.gpx.GpxPoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant

class TrackStatsTest {

    private val t0 = Instant.parse("2026-07-12T10:00:00Z")

    /** ~111 m per 0.001° of latitude; one point every 10 s → ~40 km/h. */
    private fun movingTrack(n: Int = 20): List<GpxPoint> = (0 until n).map { i ->
        GpxPoint(41.0 + i * 0.001, 2.0, 100.0 + i, t0.plusSeconds(i * 10L), heartRate = 140.0 + i)
    }

    @Test
    fun computesDistanceTimeAndSpeed() {
        val stats = TrackStatsCalculator.compute(movingTrack())
        assertThat(stats.distanceM).isCloseTo(19 * 111.2, within(30.0))
        assertThat(stats.totalTime!!.seconds).isEqualTo(190)
        assertThat(stats.movingTime!!.seconds).isEqualTo(190)
        assertThat(stats.avgSpeedKmh!!).isCloseTo(40.0, within(2.0))
        assertThat(stats.maxSpeedKmh!!).isCloseTo(40.0, within(2.0))
        assertThat(stats.ascentM).isCloseTo(19.0, within(0.01))
        assertThat(stats.descentM).isEqualTo(0.0)
    }

    @Test
    fun movingTimeExcludesStationarySegment() {
        // 5 moving points, then a 100 s stop at the same place, then 5 more moving points.
        val moving1 = (0 until 5).map { GpxPoint(41.0 + it * 0.001, 2.0, time = t0.plusSeconds(it * 10L)) }
        val stopped = GpxPoint(41.004, 2.0, time = t0.plusSeconds(140))
        val moving2 = (0 until 5).map { GpxPoint(41.004 + it * 0.001, 2.0, time = t0.plusSeconds(150 + it * 10L)) }
        val stats = TrackStatsCalculator.compute(moving1 + stopped + moving2)
        assertThat(stats.totalTime!!.seconds).isEqualTo(190)
        assertThat(stats.movingTime!!.seconds).isLessThan(160)
    }

    @Test
    fun heartRateAggregates() {
        val stats = TrackStatsCalculator.compute(movingTrack())
        assertThat(stats.avgHr!!).isCloseTo(149.5, within(0.01))
        assertThat(stats.maxHr).isEqualTo(159.0)
        assertThat(stats.avgCadence).isNull()
        assertThat(stats.avgPower).isNull()
    }

    @Test
    fun samplesDecimatesToMaxAndKeepsMonotonicDistance() {
        val big = (0 until 5000).map { i ->
            GpxPoint(41.0 + i * 0.0001, 2.0, 100.0, t0.plusSeconds(i.toLong()), heartRate = 120.0)
        }
        val samples = TrackStatsCalculator.samples(big, maxSamples = 600)
        assertThat(samples.size).isLessThanOrEqualTo(600)
        assertThat(samples.size).isGreaterThan(500)
        assertThat(samples.zipWithNext().all { (a, b) -> b.distM >= a.distM }).isTrue()
        assertThat(samples).allSatisfy { assertThat(it.hr).isNotNull() }
    }

    @Test
    fun shortTrackKeepsAllPoints() {
        val samples = TrackStatsCalculator.samples(movingTrack(10), maxSamples = 600)
        assertThat(samples).hasSize(10)
    }
}
