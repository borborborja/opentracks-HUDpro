package cat.hudpro.opentracks.viewer.follow

import cat.hudpro.opentracks.data.opentracks.model.GeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class FollowRouteEngineTest {

    // A short north-going route along the 2.0 meridian.
    private val route = listOf(
        GeoPoint(41.000, 2.0),
        GeoPoint(41.010, 2.0),
        GeoPoint(41.020, 2.0),
        GeoPoint(41.030, 2.0),
    )

    @Test
    fun totalDistanceIsSumOfLegs() {
        val engine = FollowRouteEngine(route)
        // ~3 * 1113m per 0.01 deg latitude ≈ 3339 m
        assertThat(engine.totalMeters).isEqualTo(3339.0, within(50.0))
    }

    @Test
    fun remainingDecreasesAlongRoute() {
        val engine = FollowRouteEngine(route)
        val atStart = engine.update(GeoPoint(41.000, 2.0))!!
        val midway = engine.update(GeoPoint(41.020, 2.0))!!
        assertThat(atStart.remainingKm).isGreaterThan(midway.remainingKm)
        assertThat(midway.nearestIndex).isEqualTo(2)
    }

    @Test
    fun detectsOffRoute() {
        val engine = FollowRouteEngine(route)
        // ~0.01 deg lon east of the route ≈ 840 m off at this latitude
        val state = engine.update(GeoPoint(41.010, 2.01))!!
        assertThat(state.offRouteMeters).isGreaterThan(400.0)
        assertThat(state.isOffRoute()).isTrue()
    }

    @Test
    fun onRouteIsNotFlagged() {
        val engine = FollowRouteEngine(route)
        val state = engine.update(GeoPoint(41.0101, 2.0))!!
        assertThat(state.isOffRoute()).isFalse()
    }
}
