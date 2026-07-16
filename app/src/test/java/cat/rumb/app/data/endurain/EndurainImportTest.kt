package cat.rumb.app.data.endurain

import cat.rumb.app.data.tracks.ActivityTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Reconstruction of a track from Endurain streams. Endurain has no GPX to download, so the geometry
 * is rebuilt from the MAP stream and joined with elevation/HR by the shared `time` axis. HTTP I/O is
 * device-verified; this covers the pure join, timestamp parsing, and the sport-code mapping.
 */
class EndurainImportTest {

    private val t0 = "2026-03-28T15:19:19"
    private val t1 = "2026-03-28T15:19:29"

    private fun sampleStreams() = listOf(
        EndurainStream(
            streamType = EndurainStreamType.MAP,
            waypoints = listOf(
                EndurainWaypoint(time = t0, lat = 41.0, lon = 2.0),
                EndurainWaypoint(time = t1, lat = 41.001, lon = 2.001),
            ),
        ),
        EndurainStream(
            streamType = EndurainStreamType.ELEVATION,
            waypoints = listOf(
                EndurainWaypoint(time = t0, ele = 100.0),
                EndurainWaypoint(time = t1, ele = 110.0),
            ),
        ),
        EndurainStream(
            streamType = EndurainStreamType.HEART_RATE,
            waypoints = listOf(
                EndurainWaypoint(time = t0, hr = 120),
                EndurainWaypoint(time = t1, hr = 130),
            ),
        ),
    )

    @Test
    fun mapStreamBecomesOrderedPoints() {
        val points = EndurainImport.streamsToPoints(sampleStreams())
        assertThat(points).hasSize(2)
        assertThat(points[0].latitude).isEqualTo(41.0)
        assertThat(points[0].longitude).isEqualTo(2.0)
        assertThat(points[1].latitude).isEqualTo(41.001)
    }

    @Test
    fun elevationAndHeartRateAreJoinedByTimestamp() {
        val points = EndurainImport.streamsToPoints(sampleStreams())
        // If the time-join were broken these would be null, not the other stream's values.
        assertThat(points[0].elevation).isEqualTo(100.0)
        assertThat(points[0].heartRate).isEqualTo(120.0)
        assertThat(points[1].elevation).isEqualTo(110.0)
        assertThat(points[1].heartRate).isEqualTo(130.0)
    }

    @Test
    fun naiveUtcTimestampParsesAsUtcInstant() {
        val points = EndurainImport.streamsToPoints(sampleStreams())
        assertThat(points[0].time).isEqualTo(Instant.parse("${t0}Z"))
    }

    @Test
    fun noMapStreamMeansNoTrack() {
        val onlyElevation = listOf(
            EndurainStream(
                streamType = EndurainStreamType.ELEVATION,
                waypoints = listOf(EndurainWaypoint(time = t0, ele = 100.0)),
            ),
        )
        assertThat(EndurainImport.streamsToPoints(onlyElevation)).isEmpty()
    }

    @Test
    fun waypointsMissingCoordinatesAreDropped() {
        val streams = listOf(
            EndurainStream(
                streamType = EndurainStreamType.MAP,
                waypoints = listOf(
                    EndurainWaypoint(time = t0, lat = 41.0, lon = 2.0),
                    EndurainWaypoint(time = t1, lat = null, lon = 2.001), // no lat → skip
                ),
            ),
        )
        assertThat(EndurainImport.streamsToPoints(streams)).hasSize(1)
    }

    @Test
    fun parseTimeAcceptsBothNaiveAndZonedForms() {
        assertThat(EndurainImport.parseTime("2026-03-28T15:19:19"))
            .isEqualTo(Instant.parse("2026-03-28T15:19:19Z"))
        assertThat(EndurainImport.parseTime("2026-03-28T15:19:19Z"))
            .isEqualTo(Instant.parse("2026-03-28T15:19:19Z"))
        assertThat(EndurainImport.parseTime(null)).isNull()
        assertThat(EndurainImport.parseTime("")).isNull()
    }

    @Test
    fun startMillisIsEpochOfStartTime() {
        assertThat(EndurainImport.startMillis("2026-03-28T15:19:19"))
            .isEqualTo(Instant.parse("2026-03-28T15:19:19Z").toEpochMilli())
    }

    @Test
    fun sportCodesMapToTheRightLocalType() {
        // Disciplines the app distinguishes: not every run is RUN, not every ride is ROAD_BIKE.
        assertThat(ActivityTypes.fromEndurain(1)).isEqualTo(ActivityTypes.RUN)
        assertThat(ActivityTypes.fromEndurain(2)).isEqualTo(ActivityTypes.TRAIL_RUN)
        assertThat(ActivityTypes.fromEndurain(4)).isEqualTo(ActivityTypes.ROAD_BIKE)
        assertThat(ActivityTypes.fromEndurain(6)).isEqualTo(ActivityTypes.MTB)
        assertThat(ActivityTypes.fromEndurain(8)).isEqualTo(ActivityTypes.SWIM)
        assertThat(ActivityTypes.fromEndurain(11)).isEqualTo(ActivityTypes.WALK)
        assertThat(ActivityTypes.fromEndurain(12)).isEqualTo(ActivityTypes.HIKE)
    }

    @Test
    fun unknownOrNonGeoSportCodesMapToNull() {
        assertThat(ActivityTypes.fromEndurain(10)).isNull() // workout
        assertThat(ActivityTypes.fromEndurain(999)).isNull()
        assertThat(ActivityTypes.fromEndurain(null)).isNull()
    }
}
