package cat.rumb.app.data.map

import cat.rumb.app.data.opentracks.model.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RouteCoverageCorridorTest {

    @Test
    fun shortRouteYieldsOneBox() {
        val pts = listOf(GeoPoint(41.0, 2.0), GeoPoint(41.001, 2.001))
        val boxes = RouteCoverageCalculator.corridorBoxes(pts, chunkKm = 8.0)
        assertThat(boxes).hasSize(1)
        assertThat(boxes[0].isValid).isTrue()
    }

    @Test
    fun longLinearRouteSplitsIntoMultipleBoxes() {
        // ~30 km east-west line at ~1 km spacing → several ~8 km chunks, not one giant box.
        val pts = (0..30).map { GeoPoint(41.0, 2.0 + it * 0.012) } // ~1 km per 0.012° lon at this lat
        val boxes = RouteCoverageCalculator.corridorBoxes(pts, chunkKm = 8.0)
        assertThat(boxes.size).isGreaterThanOrEqualTo(3)
        boxes.forEach { assertThat(it.isValid).isTrue() }
        // Each chunk box is narrower than the whole-route box.
        val whole = RouteCoverageCalculator.boundingBox(pts)!!
        val wholeWidth = whole.east - whole.west
        assertThat(boxes.maxOf { it.east - it.west }).isLessThan(wholeWidth)
    }

    @Test
    fun emptyRouteYieldsNoBoxes() {
        assertThat(RouteCoverageCalculator.corridorBoxes(emptyList())).isEmpty()
    }
}
