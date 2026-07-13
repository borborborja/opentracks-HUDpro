package cat.rumb.app.data.tracks

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TrackSortFilterTest {

    private fun track(
        id: Long,
        createdAt: Long = 0,
        distance: Double = 0.0,
        ascent: Double = 0.0,
        municipality: String? = null,
        type: String? = null,
    ) = FollowTrackEntity(
        id = id, name = "t$id", gpx = "", createdAt = createdAt, distanceMeters = distance,
        ascentM = ascent, municipality = municipality, activityType = type,
    )

    @Test
    fun dateSorts() {
        val list = listOf(track(1, createdAt = 10), track(2, createdAt = 30), track(3, createdAt = 20))
        assertThat(TrackSortFilter.apply(list, TrackSort.DATE_DESC, null).map { it.id }).containsExactly(2, 3, 1)
        assertThat(TrackSortFilter.apply(list, TrackSort.DATE_ASC, null).map { it.id }).containsExactly(1, 3, 2)
    }

    @Test
    fun distanceSorts() {
        val list = listOf(track(1, distance = 5000.0), track(2, distance = 20000.0), track(3, distance = 100.0))
        assertThat(TrackSortFilter.apply(list, TrackSort.DISTANCE_DESC, null).map { it.id }).containsExactly(2, 1, 3)
        assertThat(TrackSortFilter.apply(list, TrackSort.DISTANCE_ASC, null).map { it.id }).containsExactly(3, 1, 2)
    }

    @Test
    fun municipalitySortsCaseInsensitiveWithNullsLast() {
        val list = listOf(
            track(1, municipality = "vic"),
            track(2, municipality = "Berga"),
            track(3, municipality = null),
        )
        assertThat(TrackSortFilter.apply(list, TrackSort.MUNICIPALITY, null).map { it.id }).containsExactly(2, 1, 3)
    }

    @Test
    fun difficultySortsByKmEffortDescending() {
        val list = listOf(
            track(1, distance = 5000.0, ascent = 100.0), // 6.0
            track(2, distance = 10000.0, ascent = 1500.0), // 25.0
            track(3, distance = 12000.0, ascent = 0.0), // 12.0
        )
        assertThat(TrackSortFilter.apply(list, TrackSort.DIFFICULTY, null).map { it.id }).containsExactly(2, 3, 1)
    }

    @Test
    fun typeSortsWithNullsLastAndFilterWorks() {
        val list = listOf(track(1, type = "run"), track(2, type = null), track(3, type = "mtb"))
        assertThat(TrackSortFilter.apply(list, TrackSort.TYPE, null).map { it.id }).containsExactly(3, 1, 2)
        assertThat(TrackSortFilter.apply(list, TrackSort.DATE_DESC, "run").map { it.id }).containsExactly(1)
        assertThat(TrackSortFilter.apply(list, TrackSort.DATE_DESC, "swim")).isEmpty()
    }

    @Test
    fun equalDistanceFallsBackToNewestFirst() {
        val list = listOf(track(1, createdAt = 1, distance = 100.0), track(2, createdAt = 2, distance = 100.0))
        assertThat(TrackSortFilter.apply(list, TrackSort.DISTANCE_DESC, null).map { it.id }).containsExactly(2, 1)
    }
}
