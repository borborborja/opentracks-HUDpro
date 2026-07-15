package cat.rumb.app.data.tracks

import cat.rumb.app.data.competition.GhostEngine
import cat.rumb.app.data.debug.DebugLog
import cat.rumb.app.data.gpx.Gpx
import cat.rumb.app.data.gpx.GpxPoint
import cat.rumb.app.viewer.hud.MetricsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Unified competitions (leaderboards of attempts you try to beat). ROUTE = whole tracks; LAP = laps
 * of a fixed circuit. Reference and attempts keep GPX inline, so the analysis survives deletion or
 * editing of the source track.
 */
class CompetitionRepository(
    private val dao: CompetitionDao,
    private val trackRepository: TrackRepository,
) {
    fun observeCompetitions(): Flow<List<CompetitionEntity>> = dao.observeCompetitions()
    fun attemptsFor(competitionId: Long): Flow<List<CompetitionAttemptEntity>> = dao.attemptsFor(competitionId)
    suspend fun attemptsOnce(competitionId: Long): List<CompetitionAttemptEntity> = dao.attemptsForOnce(competitionId)
    fun observeSourceTrackIds(): Flow<List<Long>> = dao.observeSourceTrackIds()
    suspend fun getCompetition(id: Long): CompetitionEntity? = dao.getCompetition(id)
    suspend fun rename(id: Long, name: String) = dao.rename(id, name)
    suspend fun setArchived(id: Long, flag: Boolean) = dao.setArchived(id, flag)
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.deleteAttempts(id); dao.deleteCompetition(id) }

    /** A lap slice with its computed time, ready to become a LAP attempt or the reference. */
    private data class LapSlice(val lapIndex: Int, val points: List<GpxPoint>, val stats: TrackStats, val timeMs: Long)

    /**
     * Whether a ROUTE recording actually raced the route, so it may join the leaderboard. Without
     * this gate ANY timed recording became an attempt — and since [refreshReference] promotes the
     * FASTEST attempt, abandoning a route after two minutes would win and replace the ghost.
     * A valid attempt covers most of the reference distance AND ends at the reference finish.
     */
    private fun isValidRouteAttempt(refPts: List<GpxPoint>, pts: List<GpxPoint>, attemptDistM: Double): Boolean {
        if (pts.size < 2) return false
        // No usable reference (legacy/degenerate competition): can't judge, so don't block.
        if (refPts.size < 2) return true
        val refDistM = TrackStatsCalculator.compute(refPts).distanceM
        if (refDistM <= 0.0) return true
        if (attemptDistM < refDistM * MIN_ROUTE_COVERAGE) {
            DebugLog.i("Competi", "intent ROUTE descartat · ${fmtM(attemptDistM)}/${fmtM(refDistM)} m recorreguts")
            return false
        }
        val toFinishM = MetricsCalculator.distanceMeters(pts.last().toGeoPoint(), refPts.last().toGeoPoint())
        if (toFinishM > FINISH_RADIUS_M) {
            DebugLog.i("Competi", "intent ROUTE descartat · acaba a ${fmtM(toFinishM)} m de la meta")
            return false
        }
        return true
    }

    private fun fmtM(v: Double) = "%.0f".format(v)

    private fun sliceLaps(pts: List<GpxPoint>, laps: List<LapRange>): List<LapSlice> =
        laps.filter { it.kind == LapKind.LAP }.mapIndexedNotNull { i, lap ->
            val from = lap.startIdx.coerceIn(0, pts.size)
            val to = lap.endIdx.coerceIn(from, pts.size)
            if (to - from < 2) return@mapIndexedNotNull null
            val slice = pts.subList(from, to)
            val stats = TrackStatsCalculator.compute(slice)
            val secs = (stats.movingTime ?: stats.totalTime)?.seconds ?: 0L
            LapSlice(i, slice, stats, secs * 1000)
        }

    private suspend fun timedSlices(trackId: Long): List<LapSlice> {
        val pts = trackRepository.loadGpxRoute(trackId)
        val laps = Laps.decode(trackRepository.get(trackId)?.laps)
        return sliceLaps(pts, laps).filter { it.timeMs > 0 && GhostEngine.isTimed(it.points) }
    }

    /** Fastest positive attempt becomes the ghost/reference; the frozen line (LAP) never changes. */
    private suspend fun refreshReference(competitionId: Long, name: String) {
        val attempts = dao.attemptsForOnce(competitionId).filter { it.timeMs > 0 }
        if (attempts.isEmpty()) return
        // Defensive: only compare attempts that cover a similar distance, so a much shorter one
        // (e.g. an abandoned run filed by a build from before ROUTE attempts were validated) can't
        // win the ghost just for being "fastest". Same-length laps are unaffected.
        val longestM = attempts.maxOf { it.distanceM }
        val comparable = attempts.filter { it.distanceM >= longestM * MIN_ROUTE_COVERAGE }
        val best = (comparable.ifEmpty { attempts }).minByOrNull { it.timeMs } ?: return
        dao.updateReference(competitionId, best.gpx, best.id)
    }

    private suspend fun insertLapAttempts(competitionId: Long, trackId: Long, name: String, slices: List<LapSlice>, now: Long) {
        slices.forEach { s ->
            dao.insertAttempt(
                CompetitionAttemptEntity(
                    competitionId = competitionId, sourceTrackId = trackId, lapIndex = s.lapIndex,
                    timeMs = s.timeMs, distanceM = s.stats.distanceM, avgHr = s.stats.avgHr,
                    createdAt = now, gpx = Gpx.write(name, s.points),
                ),
            )
        }
        refreshReference(competitionId, name)
    }

    private suspend fun insertRouteAttempt(competitionId: Long, trackId: Long, name: String, pts: List<GpxPoint>, now: Long) {
        val stats = TrackStatsCalculator.compute(pts)
        dao.insertAttempt(
            CompetitionAttemptEntity(
                competitionId = competitionId, sourceTrackId = trackId, lapIndex = -1,
                timeMs = TrackRepository.durationMs(pts), distanceM = stats.distanceM, avgHr = stats.avgHr,
                createdAt = now, gpx = Gpx.write(name, pts),
            ),
        )
        refreshReference(competitionId, name)
    }

    /**
     * Creates a competition from [trackId]. LAP: the fastest lap is the reference (line + ghost) and
     * every lap becomes an attempt. ROUTE: the whole track is the reference and its first attempt.
     * Returns the new competition id, or null when the source can't act as a reference.
     */
    suspend fun createFromTrack(trackId: Long, name: String, activityType: String?, type: String, now: Long): Long? =
        withContext(Dispatchers.IO) {
            if (type == CompetitionType.LAP) {
                val slices = timedSlices(trackId)
                if (slices.isEmpty()) return@withContext null
                val parent = slices.minByOrNull { it.timeMs }!!
                val line = parent.points.first()
                val id = dao.insertCompetition(
                    CompetitionEntity(
                        name = name, type = CompetitionType.LAP, activityType = activityType, createdAt = now,
                        referenceGpx = Gpx.write(name, parent.points),
                        lineLat = line.latitude, lineLng = line.longitude,
                        radiusM = 25.0, minLapMs = 20_000, minLapM = 100.0,
                    ),
                )
                insertLapAttempts(id, trackId, name, slices, now)
                id
            } else {
                val pts = trackRepository.loadGpxRoute(trackId)
                if (!GhostEngine.isTimed(pts)) return@withContext null
                val id = dao.insertCompetition(
                    CompetitionEntity(
                        name = name, type = CompetitionType.ROUTE, activityType = activityType, createdAt = now,
                        referenceGpx = Gpx.write(name, pts),
                    ),
                )
                insertRouteAttempt(id, trackId, name, pts, now)
                id
            }
        }

    /**
     * Files a freshly-recorded [trackId] as attempt(s) of [competitionId] (branches on its type).
     * Returns how many attempts were filed: 0 when the recording doesn't qualify (no completed lap
     * for LAP; route not actually raced for ROUTE), so the caller can tell the user it didn't count.
     */
    suspend fun addAttemptsFromTrack(competitionId: Long, trackId: Long, name: String, now: Long): Int =
        withContext(Dispatchers.IO) {
            val comp = dao.getCompetition(competitionId) ?: return@withContext 0
            if (comp.type == CompetitionType.LAP) {
                val slices = timedSlices(trackId)
                if (slices.isEmpty()) return@withContext 0
                insertLapAttempts(competitionId, trackId, name, slices, now)
                slices.size
            } else {
                val pts = trackRepository.loadGpxRoute(trackId)
                if (!GhostEngine.isTimed(pts)) return@withContext 0
                val refPts = runCatching { Gpx.read(comp.referenceGpx.byteInputStream()).points }
                    .getOrDefault(emptyList())
                val distM = TrackStatsCalculator.compute(pts).distanceM
                if (!isValidRouteAttempt(refPts, pts, distM)) return@withContext 0
                insertRouteAttempt(competitionId, trackId, name, pts, now)
                1
            }
        }

    private companion object {
        /** Share of the reference distance an attempt must cover to count as having raced it. */
        const val MIN_ROUTE_COVERAGE = 0.9
        /** How close to the reference finish an attempt must end (m). */
        const val FINISH_RADIUS_M = 75.0
    }
}
