package cat.rumb.app.data.tracks

import cat.rumb.app.data.gpx.GpxPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DurationMsTest {

    private val t0: Instant = Instant.parse("2026-07-13T10:00:00Z")

    @Test
    fun timedTrackReturnsElapsed() {
        val pts = listOf(
            GpxPoint(41.0, 2.0, time = t0),
            GpxPoint(41.1, 2.0), // untimed in the middle, ignored
            GpxPoint(41.2, 2.0, time = t0.plusSeconds(3725)),
        )
        assertThat(TrackRepository.durationMs(pts)).isEqualTo(3_725_000L)
    }

    @Test
    fun untimedTrackReturnsZeroSentinel() {
        assertThat(TrackRepository.durationMs(listOf(GpxPoint(41.0, 2.0), GpxPoint(41.1, 2.0)))).isEqualTo(0L)
        assertThat(TrackRepository.durationMs(emptyList())).isEqualTo(0L)
    }

    @Test
    fun singleTimedPointReturnsZero() {
        assertThat(TrackRepository.durationMs(listOf(GpxPoint(41.0, 2.0, time = t0)))).isEqualTo(0L)
    }
}
