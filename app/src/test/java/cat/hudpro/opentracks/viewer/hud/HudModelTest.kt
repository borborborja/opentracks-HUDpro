package cat.hudpro.opentracks.viewer.hud

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HudModelTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun catalogResolvesEveryPlacedElementId() {
        val ids = (HudLayout.CYCLING.widgets + HudLayout.TRAIL.widgets + HudLayout.SKI.widgets)
            .map { it.elementId }.toSet()
        ids.forEach { id -> assertThat(HudCatalog.byId(id)).describedAs(id).isNotNull() }
    }

    @Test
    fun catalogHasMetricsChartsAndControls() {
        assertThat(HudCatalog.byId(HudCatalog.idOf(HudMetric.SPEED))?.category).isEqualTo(HudCategory.METRIC)
        assertThat(HudCatalog.byId(HudCatalog.CHART_SPEED)?.category).isEqualTo(HudCategory.CHART)
        assertThat(HudCatalog.byId(HudCatalog.CONTROL_RECENTER)?.category).isEqualTo(HudCategory.CONTROL)
        assertThat(HudCatalog.byId(HudCatalog.CONTROL_RECORD)?.category).isEqualTo(HudCategory.CONTROL)
    }

    @Test
    fun layoutSerializationRoundTripsWithZones() {
        val layout = HudLayout.CYCLING.copy(scale = 1.2f)
        val encoded = json.encodeToString(HudLayout.serializer(), layout)
        val decoded = json.decodeFromString(HudLayout.serializer(), encoded)
        assertThat(decoded).isEqualTo(layout)
        assertThat(decoded.widgets.first().zone).isEqualTo(layout.widgets.first().zone)
    }

    @Test
    fun addIsIdempotentAndRemoveWorks() {
        val base = HudLayout()
        val once = base.add(HudCatalog.CHART_SPEED, HudZone.TOP_LEFT)
        val twice = once.add(HudCatalog.CHART_SPEED, HudZone.BOTTOM_RIGHT)
        assertThat(twice.widgets).hasSize(1)
        assertThat(twice.widgets[0].zone).isEqualTo(HudZone.TOP_LEFT) // not re-added/moved
        assertThat(twice.remove(HudCatalog.CHART_SPEED).widgets).isEmpty()
    }

    @Test
    fun moveToZoneReassigns() {
        val layout = HudLayout().add(HudCatalog.CONTROL_ZOOM, HudZone.TOP_LEFT)
        val moved = layout.moveToZone(0, HudZone.BOTTOM_CENTER)
        assertThat(moved.widgets[0].zone).isEqualTo(HudZone.BOTTOM_CENTER)
    }

    @Test
    fun byZoneGroupsWidgets() {
        assertThat(HudLayout.CYCLING.byZone(HudZone.BOTTOM_LEFT).map { it.elementId })
            .containsExactly(HudCatalog.idOf(HudMetric.SPEED), HudCatalog.idOf(HudMetric.AVG_SPEED))
    }
}
