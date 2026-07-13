package cat.rumb.app.viewer.hud

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HudDataTest {

    private fun withDelta(d: Double?) = HudData(competing = true, metrics = LiveMetrics(ghostDeltaMeters = d))

    @Test
    fun ghostStateBands() {
        assertThat(withDelta(12.0).ghostState).isEqualTo(GhostState.AHEAD)
        assertThat(withDelta(5.01).ghostState).isEqualTo(GhostState.AHEAD)
        assertThat(withDelta(5.0).ghostState).isEqualTo(GhostState.EVEN)
        assertThat(withDelta(0.0).ghostState).isEqualTo(GhostState.EVEN)
        assertThat(withDelta(-5.0).ghostState).isEqualTo(GhostState.EVEN)
        assertThat(withDelta(-5.01).ghostState).isEqualTo(GhostState.BEHIND)
        assertThat(withDelta(-40.0).ghostState).isEqualTo(GhostState.BEHIND)
    }

    @Test
    fun ghostStateNullWithoutDelta() {
        assertThat(withDelta(null).ghostState).isNull()
    }
}
