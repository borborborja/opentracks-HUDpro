package cat.hudpro.opentracks.viewer.follow

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OffRouteAlerterTest {

    @Test
    fun firesEnteredOnceAndExitedWithHysteresis() {
        val alerter = OffRouteAlerter(exitFactor = 0.7)
        val threshold = 40 // enter > 40, exit < 28

        assertThat(alerter.update(10.0, threshold)).isEqualTo(OffRouteAlerter.Event.NONE)
        assertThat(alerter.update(45.0, threshold)).isEqualTo(OffRouteAlerter.Event.ENTERED)
        // Still off-route → no repeat.
        assertThat(alerter.update(60.0, threshold)).isEqualTo(OffRouteAlerter.Event.NONE)
        assertThat(alerter.update(35.0, threshold)).isEqualTo(OffRouteAlerter.Event.NONE) // above exit band
        assertThat(alerter.update(20.0, threshold)).isEqualTo(OffRouteAlerter.Event.EXITED)
        assertThat(alerter.update(10.0, threshold)).isEqualTo(OffRouteAlerter.Event.NONE)
    }

    @Test
    fun nullDeviationIsIgnored() {
        val alerter = OffRouteAlerter()
        assertThat(alerter.update(null, 40)).isEqualTo(OffRouteAlerter.Event.NONE)
    }

    @Test
    fun reEntersAfterExit() {
        val alerter = OffRouteAlerter()
        assertThat(alerter.update(100.0, 40)).isEqualTo(OffRouteAlerter.Event.ENTERED)
        assertThat(alerter.update(5.0, 40)).isEqualTo(OffRouteAlerter.Event.EXITED)
        assertThat(alerter.update(100.0, 40)).isEqualTo(OffRouteAlerter.Event.ENTERED)
    }
}
