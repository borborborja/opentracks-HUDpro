package cat.rumb.app.scale

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WeightResolverTest {

    private fun w(ts: Long, kg: Double) = WeighInEntity(0, ts, kg, null, 178, 40, "M")

    @Test
    fun moduleOffAlwaysReturnsManual() {
        // Even with weigh-ins present, disabled means the manual weight — the additive guarantee.
        assertThat(WeightResolver.resolve(listOf(w(100, 73.8)), null, 75, enabled = false)).isEqualTo(75)
    }

    @Test
    fun enabledButNoWeighInsReturnsManual() {
        assertThat(WeightResolver.resolve(emptyList(), null, 75, enabled = true)).isEqualTo(75)
    }

    @Test
    fun nullDateUsesLatestMeasuredWeightRounded() {
        // Out-of-order list; latest by timestamp is 73.8 kg → 74.
        val list = listOf(w(100, 76.1), w(300, 73.8), w(200, 75.0))
        assertThat(WeightResolver.resolve(list, null, 99, enabled = true)).isEqualTo(74)
    }

    @Test
    fun pastDateUsesTheWeighInOnOrBeforeThatDate() {
        val list = listOf(w(100, 76.0), w(200, 74.0), w(300, 73.0))
        // A calculation dated 250 uses your weight back then (the 200 weigh-in), not the latest.
        assertThat(WeightResolver.resolve(list, 250, 99, enabled = true)).isEqualTo(74)
    }

    @Test
    fun dateBeforeEveryWeighInFallsBackToEarliest() {
        val list = listOf(w(200, 74.0), w(300, 73.0))
        assertThat(WeightResolver.resolve(list, 100, 99, enabled = true)).isEqualTo(74)
    }

    @Test
    fun roundsToNearestKilogram() {
        assertThat(WeightResolver.resolve(listOf(w(1, 74.5)), null, 0, enabled = true)).isEqualTo(75)
        assertThat(WeightResolver.resolve(listOf(w(1, 74.4)), null, 0, enabled = true)).isEqualTo(74)
    }
}
