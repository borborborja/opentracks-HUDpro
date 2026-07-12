package cat.hudpro.opentracks.viewer.hud

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class UnitsTest {

    private val imperial = Units(DistanceUnit.MILE, ElevationUnit.FOOT, SpeedUnit.MPH)

    @Test
    fun metricIsUnchanged() {
        val m = LiveMetrics(speedKmh = 20.0, distanceKm = 10.0, elevationGainM = 100.0, paceMinPerKm = 5.0)
        assertThat(HudMetric.SPEED.value(m)).isEqualTo("20.0")
        assertThat(HudMetric.SPEED.unit()).isEqualTo("km/h")
        assertThat(HudMetric.DISTANCE.value(m)).isEqualTo("10.00")
        assertThat(HudMetric.ELEV_GAIN.value(m)).isEqualTo("100")
        assertThat(HudMetric.PACE.value(m)).isEqualTo("5:00")
        assertThat(HudMetric.PACE.unit()).isEqualTo("/km")
    }

    @Test
    fun imperialConvertsValuesAndLabels() {
        val m = LiveMetrics(speedKmh = 16.0934, distanceKm = 1.60934, elevationGainM = 100.0)
        // 16.0934 km/h ≈ 10.0 mph
        assertThat(HudMetric.SPEED.value(m, imperial)).isEqualTo("10.0")
        assertThat(HudMetric.SPEED.unit(imperial)).isEqualTo("mph")
        // 1.60934 km ≈ 1.00 mi
        assertThat(HudMetric.DISTANCE.value(m, imperial)).isEqualTo("1.00")
        assertThat(HudMetric.DISTANCE.unit(imperial)).isEqualTo("mi")
        // 100 m ≈ 328 ft
        assertThat(HudMetric.ELEV_GAIN.value(m, imperial)).isEqualTo("328")
        assertThat(HudMetric.ELEV_GAIN.unit(imperial)).isEqualTo("ft")
    }

    @Test
    fun paceConvertsPerMile() {
        // 5 min/km → 5 * 1.609344 ≈ 8:03 min/mi
        val m = LiveMetrics(paceMinPerKm = 5.0)
        assertThat(HudMetric.PACE.value(m, imperial)).isEqualTo("8:03")
        assertThat(HudMetric.PACE.unit(imperial)).isEqualTo("/mi")
    }

    @Test
    fun conversionHelpers() {
        assertThat(DistanceUnit.MILE.fromKm(1.609344)).isCloseTo(1.0, within(1e-6))
        assertThat(ElevationUnit.FOOT.fromMeters(1.0)).isCloseTo(3.280839895, within(1e-6))
        assertThat(SpeedUnit.MPH.fromKmh(1.609344)).isCloseTo(1.0, within(1e-4))
    }
}
