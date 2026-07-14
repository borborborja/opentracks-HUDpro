package cat.rumb.app.data.gpx

import cat.rumb.app.data.tracks.ActivityTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GpxTypeTest {

    private val pts = listOf(GpxPoint(41.0, 2.0), GpxPoint(41.001, 2.001))

    @Test
    fun writesTypeWhenProvided() {
        val gpx = Gpx.write("Ruta", pts, ActivityTypes.gpxType(ActivityTypes.MTB))
        assertThat(gpx).contains("<type>cycling</type>")
    }

    @Test
    fun omitsTypeForUnknownOrCustom() {
        assertThat(ActivityTypes.gpxType("custom_abc")).isNull()
        assertThat(ActivityTypes.gpxType(null)).isNull()
        assertThat(Gpx.write("Ruta", pts, null)).doesNotContain("<type>")
    }

    @Test
    fun typeRoundTripsThroughReader() {
        // The reader ignores <type> but must still parse points with it present.
        val gpx = Gpx.write("Ruta", pts, "running")
        val route = Gpx.read(gpx.byteInputStream())
        assertThat(route.points).hasSize(2)
    }
}
