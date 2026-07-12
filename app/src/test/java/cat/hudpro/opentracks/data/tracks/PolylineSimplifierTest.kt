package cat.hudpro.opentracks.data.tracks

import cat.hudpro.opentracks.data.opentracks.model.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PolylineSimplifierTest {

    @Test
    fun keepsEndpointsAndDropsNearlyCollinearPoints() {
        // A near-straight west→east line with tiny north wiggles well under epsilon.
        val points = listOf(
            GeoPoint(41.0000, 1.0000),
            GeoPoint(41.00001, 1.0010),
            GeoPoint(41.0000, 1.0020),
            GeoPoint(41.00001, 1.0030),
            GeoPoint(41.0000, 1.0040),
        )
        val simplified = PolylineSimplifier.simplify(points, epsilonMeters = 50.0)
        assertThat(simplified.first()).isEqualTo(points.first())
        assertThat(simplified.last()).isEqualTo(points.last())
        assertThat(simplified.size).isLessThan(points.size)
    }

    @Test
    fun keepsSharpCorner() {
        // An L shape: the corner must survive simplification.
        val corner = GeoPoint(41.0100, 1.0000)
        val points = listOf(GeoPoint(41.0000, 1.0000), corner, GeoPoint(41.0100, 1.0100))
        val simplified = PolylineSimplifier.simplify(points, epsilonMeters = 20.0)
        assertThat(simplified).contains(corner)
        assertThat(simplified.size).isEqualTo(3)
    }

    @Test
    fun respectsMaxPointsByRaisingEpsilon() {
        // A zigzag of many points with large deviations; cap forces fewer output points.
        val points = (0..200).map { i ->
            GeoPoint(41.0 + (if (i % 2 == 0) 0.0 else 0.001), 1.0 + i * 0.001)
        }
        val simplified = PolylineSimplifier.simplify(points, epsilonMeters = 1.0, maxPoints = 20)
        assertThat(simplified.size).isLessThanOrEqualTo(20)
        assertThat(simplified.first()).isEqualTo(points.first())
        assertThat(simplified.last()).isEqualTo(points.last())
    }

    @Test
    fun shortLinesReturnedUnchanged() {
        val two = listOf(GeoPoint(41.0, 1.0), GeoPoint(41.1, 1.1))
        assertThat(PolylineSimplifier.simplify(two, epsilonMeters = 10.0)).isEqualTo(two)
    }
}
