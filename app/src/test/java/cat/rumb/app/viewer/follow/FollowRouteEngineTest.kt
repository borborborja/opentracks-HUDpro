package cat.rumb.app.viewer.follow

import cat.rumb.app.data.opentracks.model.GeoPoint
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

    @Test
    fun progressMetersMatchesCumulativeAtNearestVertex() {
        val engine = FollowRouteEngine(route)
        assertThat(engine.update(GeoPoint(41.000, 2.0))!!.progressMeters).isEqualTo(0.0)
        // 4 equidistant vertices: the second vertex sits at 1/3 of the total.
        val second = engine.update(GeoPoint(41.010, 2.0))!!
        assertThat(second.progressMeters).isEqualTo(engine.totalMeters / 3, within(engine.totalMeters * 0.02))
        assertThat(engine.update(GeoPoint(41.030, 2.0))!!.progressMeters)
            .isEqualTo(engine.totalMeters, within(0.01))
    }

    @Test
    fun straightRouteHasNoTurn() {
        val engine = FollowRouteEngine(route)
        assertThat(engine.update(GeoPoint(41.000, 2.0))!!.distanceToNextTurnM).isNull()
    }

    @Test
    fun detectsUpcomingTurnDistance() {
        // North for ~2 legs, then a 90° turn east at vertex 2.
        val bent = listOf(
            GeoPoint(41.000, 2.0),
            GeoPoint(41.010, 2.0),
            GeoPoint(41.020, 2.0), // turn here (heading N → E)
            GeoPoint(41.020, 2.01),
        )
        val engine = FollowRouteEngine(bent)
        val atStart = engine.update(GeoPoint(41.000, 2.0))!!
        // Turn is ~2 legs ahead (≈ 2226 m).
        assertThat(atStart.distanceToNextTurnM).isNotNull()
        assertThat(atStart.distanceToNextTurnM!!).isEqualTo(2226.0, within(80.0))
        // Once past the turn, there's no further turn.
        assertThat(engine.update(GeoPoint(41.020, 2.01))!!.distanceToNextTurnM).isNull()
    }
}
