package cat.rumb.app.data.tracks

import cat.rumb.app.data.gpx.Gpx
import cat.rumb.app.data.gpx.GpxPoint
import cat.rumb.app.data.tracks.CompetitionRepository.AttemptOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * A recording only joins a leaderboard if it raced the same thing, the same way. Two gates:
 * the route must actually have been raced, and the sport must be of the same family — without the
 * latter, a bike lap on a running circuit is the fastest and steals the reference/ghost.
 */
class CompetitionRepositoryTest {

    private val t0: Instant = Instant.parse("2026-07-15T10:00:00Z")

    /** A straight south→north line of [n] points ~11 m apart, one second each. */
    private fun line(n: Int, startSec: Long = 0): List<GpxPoint> =
        (0 until n).map { i ->
            GpxPoint(41.0 + i * 0.0001, 2.0, 100.0, t0.plusSeconds(startSec + i))
        }

    private fun competition(refPts: List<GpxPoint>, sport: String? = null) = CompetitionEntity(
        id = 1L, name = "ruta", type = CompetitionType.ROUTE, createdAt = 0L,
        activityType = sport, referenceGpx = Gpx.write("ruta", refPts),
    )

    private fun track(sport: String?, source: TrackSource = TrackSource.RECORDED) = FollowTrackEntity(
        id = 9L, name = "intent", gpx = "", activityType = sport, source = source,
    )

    private fun repoWith(
        refPts: List<GpxPoint>,
        attemptPts: List<GpxPoint>,
        compSport: String? = null,
        trackSport: String? = null,
        custom: List<CustomActivityType> = emptyList(),
    ): Pair<CompetitionRepository, CompetitionDao> {
        val dao = mockk<CompetitionDao>(relaxed = true)
        val tracks = mockk<TrackRepository>()
        coEvery { dao.getCompetition(1L) } returns competition(refPts, compSport)
        coEvery { dao.attemptsForOnce(1L) } returns emptyList()
        coEvery { tracks.loadGpxRoute(9L) } returns attemptPts
        coEvery { tracks.get(9L) } returns track(trackSport)
        return CompetitionRepository(dao, tracks) { custom } to dao
    }

    // --- Route-raced gate ---

    @Test
    fun routeAttemptCoveringTheWholeRouteIsFiled() = runTest {
        val (repo, dao) = repoWith(line(100), line(100)) // same route, same finish
        val r = repo.addAttemptsFromTrack(1L, 9L, "intent", 0L)
        assertThat(r.outcome).isEqualTo(AttemptOutcome.FILED)
        assertThat(r.filed).isEqualTo(1)
        coVerify(exactly = 1) { dao.insertAttempt(any()) }
    }

    @Test
    fun abandonedRouteAttemptIsNotFiled() = runTest {
        // Gives up after ~10% of the route: short AND nowhere near the finish.
        val (repo, dao) = repoWith(line(100), line(10))
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L).outcome)
            .isEqualTo(AttemptOutcome.ROUTE_NOT_RACED)
        coVerify(exactly = 0) { dao.insertAttempt(any()) }
    }

    @Test
    fun routeAttemptStoppingShortOfTheFinishIsNotFiled() = runTest {
        val (repo, dao) = repoWith(line(100), line(80))
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L).outcome)
            .isEqualTo(AttemptOutcome.ROUTE_NOT_RACED)
        coVerify(exactly = 0) { dao.insertAttempt(any()) }
    }

    @Test
    fun untimedRouteAttemptIsNotFiled() = runTest {
        val untimed = (0 until 100).map { GpxPoint(41.0 + it * 0.0001, 2.0) }
        val (repo, dao) = repoWith(line(100), untimed)
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L).outcome)
            .isEqualTo(AttemptOutcome.NOT_TIMED)
        coVerify(exactly = 0) { dao.insertAttempt(any()) }
    }

    // --- Sport-family gate: the whole point is that a bike can't win a running competition ---

    @Test
    fun bikeAttemptOnARunningCompetitionIsNotFiled() = runTest {
        val (repo, dao) = repoWith(
            line(100), line(100), compSport = ActivityTypes.RUN, trackSport = ActivityTypes.ROAD_BIKE,
        )
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L).outcome)
            .isEqualTo(AttemptOutcome.WRONG_SPORT)
        // Nothing written: the reference/ghost cannot be stolen.
        coVerify(exactly = 0) { dao.insertAttempt(any()) }
        coVerify(exactly = 0) { dao.updateReference(any(), any(), any()) }
    }

    @Test
    fun trailRunOnARunningCompetitionIsFiled() = runTest {
        // Same FOOT family — a legitimate attempt that must NOT be rejected.
        val (repo, dao) = repoWith(
            line(100), line(100), compSport = ActivityTypes.RUN, trackSport = ActivityTypes.TRAIL_RUN,
        )
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L).outcome).isEqualTo(AttemptOutcome.FILED)
        coVerify(exactly = 1) { dao.insertAttempt(any()) }
    }

    @Test
    fun competitionWithNoSportAcceptsAnything() = runTest {
        // Legacy rows can't be judged: UNKNOWN is permissive, so old data keeps working.
        val (repo, dao) = repoWith(
            line(100), line(100), compSport = null, trackSport = ActivityTypes.ROAD_BIKE,
        )
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L).outcome).isEqualTo(AttemptOutcome.FILED)
        coVerify(exactly = 1) { dao.insertAttempt(any()) }
    }

    @Test
    fun legacyCompetitionRecoversItsSportFromTheAttemptSourceTrack() = runTest {
        val dao = mockk<CompetitionDao>(relaxed = true)
        val tracks = mockk<TrackRepository>()
        coEvery { dao.getCompetition(1L) } returns competition(line(100), sport = null)
        coEvery { dao.attemptsForOnce(1L) } returns listOf(
            CompetitionAttemptEntity(id = 5L, competitionId = 1L, sourceTrackId = 7L, timeMs = 1000, gpx = ""),
        )
        coEvery { tracks.get(7L) } returns FollowTrackEntity(id = 7L, name = "src", gpx = "", activityType = ActivityTypes.RUN)
        coEvery { tracks.get(9L) } returns track(ActivityTypes.ROAD_BIKE)
        coEvery { tracks.loadGpxRoute(9L) } returns line(100)
        val repo = CompetitionRepository(dao, tracks) { emptyList() }

        // Sport recovered as RUN → the bike attempt is now correctly rejected...
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L).outcome).isEqualTo(AttemptOutcome.WRONG_SPORT)
        // ...and the recovery is persisted so it's paid once.
        coVerify(exactly = 1) { dao.setActivityType(1L, ActivityTypes.RUN) }
    }

    @Test
    fun customTypeFamilyIsHonoured() = runTest {
        val custom = listOf(
            CustomActivityType("custom_1", "Cursa d'orientació", "run", ActivityFamily.FOOT.name),
        )
        val (repo, dao) = repoWith(
            line(100), line(100), compSport = ActivityTypes.RUN, trackSport = "custom_1", custom = custom,
        )
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L).outcome).isEqualTo(AttemptOutcome.FILED)
        coVerify(exactly = 1) { dao.insertAttempt(any()) }
    }

    /** The debug simulator replays a track as fake GPS: at 5x it would be the best attempt ever. */
    @Test
    fun simulatedTrackIsNeverFiledAsAnAttempt() = runTest {
        val dao = mockk<CompetitionDao>(relaxed = true)
        val tracks = mockk<TrackRepository>()
        coEvery { dao.getCompetition(1L) } returns competition(line(100), ActivityTypes.RUN)
        coEvery { dao.attemptsForOnce(1L) } returns emptyList()
        coEvery { tracks.loadGpxRoute(9L) } returns line(100)
        // Same sport, a perfectly raced route — rejected purely for being simulated.
        coEvery { tracks.get(9L) } returns track(ActivityTypes.RUN, TrackSource.SIMULATED)
        val repo = CompetitionRepository(dao, tracks) { emptyList() }

        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L).outcome).isEqualTo(AttemptOutcome.SIMULATED)
        coVerify(exactly = 0) { dao.insertAttempt(any()) }
        coVerify(exactly = 0) { dao.updateReference(any(), any(), any()) }
    }

    @Test
    fun lapCompetitionWithNoCompletedLapFilesNothing() = runTest {
        val dao = mockk<CompetitionDao>(relaxed = true)
        val tracks = mockk<TrackRepository>()
        coEvery { dao.getCompetition(1L) } returns competition(line(100), ActivityTypes.RUN).copy(type = CompetitionType.LAP)
        coEvery { tracks.loadGpxRoute(9L) } returns line(100)
        coEvery { tracks.get(9L) } returns track(ActivityTypes.RUN) // no saved lap ranges → no completed lap
        val repo = CompetitionRepository(dao, tracks) { emptyList() }
        assertThat(repo.addAttemptsFromTrack(1L, 9L, "intent", 0L).outcome).isEqualTo(AttemptOutcome.NO_LAP)
        coVerify(exactly = 0) { dao.insertAttempt(any()) }
    }
}
