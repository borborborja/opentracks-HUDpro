package cat.rumb.app.data.tracks

import cat.rumb.app.data.competition.GhostEngine
import cat.rumb.app.data.gpx.Gpx
import cat.rumb.app.data.gpx.GpxPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Circuits (fixed start/finish line + lap-effort leaderboard) and their efforts. */
class CircuitRepository(
    private val dao: CircuitDao,
    private val trackRepository: TrackRepository,
) {
    fun observeCircuits(): Flow<List<CircuitEntity>> = dao.observeCircuits()
    fun effortsFor(circuitId: Long): Flow<List<CircuitEffortEntity>> = dao.effortsFor(circuitId)
    suspend fun getCircuit(id: Long): CircuitEntity? = dao.getCircuit(id)
    suspend fun rename(id: Long, name: String) = dao.rename(id, name)
    suspend fun setArchived(id: Long, flag: Boolean) = dao.setArchived(id, flag)
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.deleteEfforts(id); dao.deleteCircuit(id) }

    /** A lap slice with its computed time, ready to become an effort or the reference. */
    private data class LapSlice(val lapIndex: Int, val points: List<GpxPoint>, val stats: TrackStats, val timeMs: Long)

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

    /**
     * Seeds a circuit from [trackId]'s laps: the fastest LAP becomes the reference (line + ghost),
     * and every LAP is inserted as an initial effort. Returns the new circuit id, or null when the
     * source has no usable (timed, ≥2-point) laps.
     */
    suspend fun createCircuitFromTrack(trackId: Long, name: String, activityType: String?, now: Long): Long? =
        withContext(Dispatchers.IO) {
            val pts = trackRepository.loadGpxRoute(trackId)
            val laps = Laps.decode(trackRepository.get(trackId)?.laps)
            val slices = sliceLaps(pts, laps).filter { it.timeMs > 0 && GhostEngine.isTimed(it.points) }
            if (slices.isEmpty()) return@withContext null
            val parent = slices.minByOrNull { it.timeMs }!!
            val line = parent.points.first()
            val circuitId = dao.insertCircuit(
                CircuitEntity(
                    name = name,
                    activityType = activityType,
                    createdAt = now,
                    lineLat = line.latitude,
                    lineLng = line.longitude,
                    referenceGpx = Gpx.write(name, parent.points),
                ),
            )
            var bestEffortId: Long? = null
            var bestMs = Long.MAX_VALUE
            slices.forEach { s ->
                val effortId = dao.insertEffort(
                    CircuitEffortEntity(
                        circuitId = circuitId,
                        sourceTrackId = trackId,
                        lapIndex = s.lapIndex,
                        timeMs = s.timeMs,
                        distanceM = s.stats.distanceM,
                        avgHr = s.stats.avgHr,
                        createdAt = now,
                        gpx = Gpx.write(name, s.points),
                    ),
                )
                if (effortId > 0 && s.timeMs < bestMs) { bestMs = s.timeMs; bestEffortId = effortId }
            }
            dao.updateReference(circuitId, Gpx.write(name, parent.points), bestEffortId)
            circuitId
        }
}
