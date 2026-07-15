package cat.rumb.app.data.tracks

import cat.rumb.app.data.gpx.Gpx
import cat.rumb.app.data.gpx.GpxPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * A ROUTE recording only joins the leaderboard if it actually raced the route. Without that gate any
 * timed track became an attempt, and since the FASTEST attempt becomes the reference/ghost, an
 * abandoned run would win and replace it.
 */
class CompetitionRepositoryTest {

    private val t0: Instant = Instant.parse("2026-07-15T10:00:00Z")

    /** A straight south→north line of [n] points ~11 m apart, one second each. */
    private fun line(n: Int, startSec: Long = 0): List<GpxPoint> =
        (0 until n).map { i ->
            GpxPoint(41.0 + i * 0.0001, 2.0, 100.0, t0.plusSeconds(startSec + i))
        }

    private fun competition(refPts: List<GpxPoint>) = CompetitionEntity(
        id = 1L, name = "ruta", type = CompetitionType.ROUTE, createdAt = 0L,
        referenceGpx = Gpx.write("ruta", refPts),
    )

    private fun repoWith(refPts: List<GpxPoint>, attemptPts: List<GpxPoint>): Pair<CompetitionRepository, CompetitionDao> {
        val dao = mockk<CompetitionDao>(relaxed = true)
        val tracks = mockk<TrackRepository>()
        coEvery { dao.getCompetition(1L) } returns competition(refPts)
        coEvery { dao.attemptsForOnce(1L) } returns emptyList()
        coEvery { tracks.loadGpxRoute(9L) } returns attemptPts
        return CompetitionRepository(dao, tracks) to dao
    }

    @Test
    fun routeAttemptCoveringTheWholeRouteIsFiled() = runTest {
        val ref = line(100)
        val (repo, dao) = repoWith(ref, line(100)) // same route, same finish
        val filed = repo.addAttemptsFromTrack(1L, 9L, "intent", 0L)
        assertThat(filed).isEqualTo(1)
        coVerify(exactly = 1) { dao.insertAttempt(any()) }
    }

    @Test
    fun abandonedRouteAttemptIsNotFiled() = runTest {
        // Gives up after ~10% of the route: short AND nowhere near the finish.
        val (repo, dao) = repoWith(line(100), line(10))
        val filed = repo.addAttemptsFromTrack(1L, 9L, "intent", 0L)
        assertThat(filed).isEqualTo(0)
        coVerify(exactly = 0) { dao.insertAttempt(any()) }
    }

    @Test
    fun routeAttemptStoppingShortOfTheFinishIsNotFiled() = runTest {
        // Covers ~80% of the distance — under the coverage bar and far from the finish.
        val (repo, dao) = repoWith(line(100), line(80))
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L)).isEqualTo(0)
        coVerify(exactly = 0) { dao.insertAttempt(any()) }
    }

    @Test
    fun untimedRouteAttemptIsNotFiled() = runTest {
        val untimed = (0 until 100).map { GpxPoint(41.0 + it * 0.0001, 2.0) }
        val (repo, dao) = repoWith(line(100), untimed)
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L)).isEqualTo(0)
        coVerify(exactly = 0) { dao.insertAttempt(any()) }
    }

    @Test
    fun lapCompetitionWithNoCompletedLapFilesNothing() = runTest {
        val dao = mockk<CompetitionDao>(relaxed = true)
        val tracks = mockk<TrackRepository>()
        coEvery { dao.getCompetition(1L) } returns competition(line(100)).copy(type = CompetitionType.LAP)
        coEvery { tracks.loadGpxRoute(9L) } returns line(100)
        coEvery { tracks.get(9L) } returns null // no saved lap ranges → no completed lap
        val repo = CompetitionRepository(dao, tracks)
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L)).isEqualTo(0)
        coVerify(exactly = 0) { dao.insertAttempt(any()) }
    }
}
