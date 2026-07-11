package cat.hudpro.opentracks.viewer.hud

import cat.hudpro.opentracks.data.opentracks.model.GeoPoint
import cat.hudpro.opentracks.data.opentracks.model.TRACKPOINT_TYPE_TRACKPOINT
import cat.hudpro.opentracks.data.opentracks.model.TrackStatistics
import cat.hudpro.opentracks.data.opentracks.model.Trackpoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

class MetricsCalculatorTest {

    private fun tp(lat: Double, lon: Double, speed: Double, id: Long) = Trackpoint(
        trackId = 1L, id = id, latLong = GeoPoint(lat, lon),
        type = TRACKPOINT_TYPE_TRACKPOINT, speed = speed, time = Instant.ofEpochMilli(id * 1000),
    )

    @Test
    fun convertsSpeedAndDerivesPace() {
        val segments = listOf(listOf(tp(41.0, 2.0, 5.0, 1), tp(41.001, 2.0, 5.0, 2)))
        val stats = TrackStatistics(
            totalDistanceMeter = 2500.0,
            movingTime = 10.minutes,
            avgMovingSpeedMeterPerSecond = 4.0,
            maxSpeedMeterPerSecond = 6.0,
            elevationGainMeter = 120.0,
        )
        val m = MetricsCalculator.compute(segments, stats, isRecording = true)

        assertThat(m.speedKmh).isEqualTo(18.0, within(0.001)) // 5 m/s
        assertThat(m.avgMovingSpeedKmh).isEqualTo(14.4, within(0.001))
        assertThat(m.maxSpeedKmh).isEqualTo(21.6, within(0.001))
        assertThat(m.distanceKm).isEqualTo(2.5, within(0.001))
        assertThat(m.elevationGainM).isEqualTo(120.0)
        // pace = 60/18 = 3.333 min/km
        assertThat(m.paceMinPerKm!!).isEqualTo(3.3333, within(0.001))
        assertThat(m.bearingDeg).isNotNull()
    }

    @Test
    fun bearingNorthIsZero() {
        val b = MetricsCalculator.bearing(GeoPoint(41.0, 2.0), GeoPoint(41.5, 2.0))
        assertThat(b).isEqualTo(0.0, within(0.5))
    }

    @Test
    fun distanceMatchesKnownValue() {
        // ~111.2 km per degree of latitude
        val d = MetricsCalculator.distanceMeters(GeoPoint(41.0, 2.0), GeoPoint(42.0, 2.0))
        assertThat(d).isEqualTo(111_195.0, within(500.0))
    }

    @Test
    fun formatsMissingValuesAsDash() {
        assertThat(HudMetric.SLOPE.value(LiveMetrics())).isEqualTo("—")
        assertThat(HudMetric.SPEED.value(LiveMetrics(speedKmh = 12.34))).isEqualTo("12.3")
        assertThat(HudMetric.PACE.value(LiveMetrics(paceMinPerKm = 4.5))).isEqualTo("4:30")
    }
}
