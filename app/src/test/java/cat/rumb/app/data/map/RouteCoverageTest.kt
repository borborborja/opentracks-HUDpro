package cat.rumb.app.data.map

import cat.rumb.app.data.opentracks.model.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class RouteCoverageTest {

    private fun map(name: String, w: Double, s: Double, e: Double, n: Double) =
        OfflineMap(name = name, path = "/x/$name.mbtiles", bounds = listOf(w, s, e, n))

    private val route = listOf(
        GeoPoint(41.40, 2.10), GeoPoint(41.45, 2.15), GeoPoint(41.50, 2.20),
    )

    @Test
    fun fullyCoveredBySingleMap() {
        val maps = listOf(map("A", 2.0, 41.3, 2.3, 41.6))
        val c = RouteCoverageCalculator.coverage(route, maps)
        assertThat(c.status).isEqualTo(CoverageStatus.COVERED)
        assertThat(c.coveredFraction).isEqualTo(1f)
        assertThat(c.coveringMaps).containsExactly("A")
    }

    @Test
    fun coveredByUnionOfTwoMaps() {
        val maps = listOf(
            map("West", 2.0, 41.3, 2.14, 41.6), // covers first point
            map("East", 2.14, 41.3, 2.3, 41.6), // covers the rest
        )
        val c = RouteCoverageCalculator.coverage(route, maps)
        assertThat(c.status).isEqualTo(CoverageStatus.COVERED)
        assertThat(c.coveringMaps).containsExactlyInAnyOrder("West", "East")
    }

    @Test
    fun partialCoverage() {
        val maps = listOf(map("A", 2.0, 41.3, 2.16, 41.6)) // misses the last point (2.20)
        val c = RouteCoverageCalculator.coverage(route, maps)
        assertThat(c.status).isEqualTo(CoverageStatus.PARTIAL)
        assertThat(c.coveredFraction).isEqualTo(2f / 3f, within(0.001f))
    }

    @Test
    fun noCoverage() {
        val maps = listOf(map("Far", 0.0, 40.0, 0.5, 40.5))
        assertThat(RouteCoverageCalculator.coverage(route, maps).status).isEqualTo(CoverageStatus.NONE)
        assertThat(RouteCoverageCalculator.coverage(route, emptyList()).status).isEqualTo(CoverageStatus.NONE)
    }

    @Test
    fun boundingBoxAddsMargin() {
        val bbox = RouteCoverageCalculator.boundingBox(route, marginDeg = 0.01)!!
        assertThat(bbox.west).isEqualTo(2.09, within(1e-9))
        assertThat(bbox.east).isEqualTo(2.21, within(1e-9))
        assertThat(bbox.south).isEqualTo(41.39, within(1e-9))
        assertThat(bbox.north).isEqualTo(41.51, within(1e-9))
    }
}
