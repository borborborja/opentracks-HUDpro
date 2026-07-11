package cat.hudpro.opentracks.data.routing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class BRouterParseTest {

    @Test
    fun parsesGeoJsonWithElevationAndProperties() {
        val json = """
            {"type":"FeatureCollection","features":[{"type":"Feature",
              "properties":{"track-length":"1500","filtered ascend":"120"},
              "geometry":{"type":"LineString","coordinates":[
                [2.10,41.30,100.0],[2.11,41.31,160.0],[2.12,41.32,150.0]]}}]}
        """.trimIndent()

        val path = BRouterHttpProvider.parse(json)

        assertThat(path.points).hasSize(3)
        assertThat(path.points[0].latitude).isEqualTo(41.30, within(1e-6))
        assertThat(path.points[0].longitude).isEqualTo(2.10, within(1e-6))
        assertThat(path.points[1].elevation).isEqualTo(160.0)
        assertThat(path.distanceMeters).isEqualTo(1500.0)
        assertThat(path.ascentMeters).isEqualTo(120.0)
    }

    @Test
    fun computesDistanceAndAscentWhenPropertiesMissing() {
        val json = """
            {"features":[{"geometry":{"type":"LineString","coordinates":[
              [2.0,41.0,100.0],[2.0,41.01,200.0]]},"properties":{}}]}
        """.trimIndent()

        val path = BRouterHttpProvider.parse(json)

        assertThat(path.ascentMeters).isEqualTo(100.0)
        assertThat(path.distanceMeters).isGreaterThan(1000.0) // ~1.11 km per 0.01° lat
    }
}
