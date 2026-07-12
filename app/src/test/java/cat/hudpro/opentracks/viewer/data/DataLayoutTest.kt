package cat.hudpro.opentracks.viewer.data

import cat.hudpro.opentracks.viewer.hud.HudMetric
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DataLayoutTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun defaultFieldsAllResolveToMetrics() {
        val layout = DataLayout()
        assertThat(layout.fields).isNotEmpty()
        assertThat(layout.metrics()).hasSize(layout.fields.size)
        assertThat(layout.metrics()).containsExactlyElementsOf(HudMetric.entries)
    }

    @Test
    fun roundTripsThroughJson() {
        val layout = DataLayout(fields = listOf(HudMetric.SPEED.name, HudMetric.DISTANCE.name), columns = 3, showClock = false)
        val restored = json.decodeFromString(DataLayout.serializer(), json.encodeToString(DataLayout.serializer(), layout))
        assertThat(restored).isEqualTo(layout)
    }

    @Test
    fun addRemoveAreIdempotentAndOrderPreserving() {
        val base = DataLayout(fields = listOf(HudMetric.SPEED.name))
        assertThat(base.add(HudMetric.SPEED.name)).isEqualTo(base) // no duplicate
        val added = base.add(HudMetric.PACE.name)
        assertThat(added.fields).containsExactly(HudMetric.SPEED.name, HudMetric.PACE.name)
        assertThat(added.remove(HudMetric.SPEED.name).fields).containsExactly(HudMetric.PACE.name)
    }

    @Test
    fun moveReordersWithinBounds() {
        val layout = DataLayout(fields = listOf("A", "B", "C"))
        assertThat(layout.move(0, +1).fields).containsExactly("B", "A", "C")
        assertThat(layout.move(2, -1).fields).containsExactly("A", "C", "B")
        // Clamped at edges — no-op.
        assertThat(layout.move(0, -1).fields).containsExactly("A", "B", "C")
        assertThat(layout.move(2, +5).fields).containsExactly("A", "B", "C")
    }

    @Test
    fun unknownFieldsAreSkippedByMetrics() {
        val layout = DataLayout(fields = listOf(HudMetric.SPEED.name, "NOT_A_METRIC"))
        assertThat(layout.metrics()).containsExactly(HudMetric.SPEED)
    }

    @Test
    fun spansDefaultToOneAndClampToColumns() {
        val layout = DataLayout(fields = listOf("SPEED"), columns = 2)
        assertThat(layout.spanOf("SPEED")).isEqualTo(1)
        val wide = layout.setSpan("SPEED", 3) // beyond columns → clamped at read time
        assertThat(wide.spanOf("SPEED")).isEqualTo(2)
    }

    @Test
    fun cycleSpanWraps() {
        val l2 = DataLayout(fields = listOf("SPEED"), columns = 2)
        assertThat(l2.cycleSpan("SPEED").spanOf("SPEED")).isEqualTo(2)
        assertThat(l2.cycleSpan("SPEED").cycleSpan("SPEED").spanOf("SPEED")).isEqualTo(1)
    }

    @Test
    fun moveToRepositions() {
        val layout = DataLayout(fields = listOf("A", "B", "C", "D"))
        assertThat(layout.moveTo(0, 2).fields).containsExactly("B", "C", "A", "D")
        assertThat(layout.moveTo(3, 0).fields).containsExactly("D", "A", "B", "C")
        assertThat(layout.moveTo(1, 1)).isEqualTo(layout)
    }

    @Test
    fun legacyJsonWithoutSpansDecodes() {
        val legacy = """{"fields":["SPEED","DISTANCE"],"columns":2,"showClock":true}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(DataLayout.serializer(), legacy)
        assertThat(decoded.spans).isEmpty()
        assertThat(decoded.spanOf("SPEED")).isEqualTo(1)
    }
}
