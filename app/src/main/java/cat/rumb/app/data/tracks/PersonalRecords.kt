package cat.rumb.app.data.tracks

import cat.rumb.app.data.gpx.GpxPoint
import cat.rumb.app.viewer.hud.MetricsCalculator

/** A personal best. Time records carry ms in [valueMs]; scalar records use [value]. */
data class Record(
    val kind: RecordKind,
    val valueMs: Long? = null,
    val value: Double? = null,
    val trackId: Long,
    val trackName: String,
    val dateMs: Long,
)

enum class RecordKind(val distanceM: Double?) {
    FASTEST_1K(1_000.0),
    FASTEST_5K(5_000.0),
    FASTEST_10K(10_000.0),
    FASTEST_HALF(21_097.5),
    FASTEST_MARATHON(42_195.0),
    LONGEST_DISTANCE(null),
    MAX_ASCENT(null),
    MAX_SPEED(null),
    LONGEST_TIME(null),
}

/**
 * Personal records over the whole library. Rolling-window bests use a two-pointer sweep over the
 * cumulative distance/time arrays of each timed track. Pure JVM.
 */
object PersonalRecords {

    /** Best rolling time (ms) to cover [distanceM] within [points], or null if the track is shorter/untimed. */
    fun bestWindowMs(points: List<GpxPoint>, distanceM: Double): Long? {
        val timed = points.filter { it.time != null }
        if (timed.size < 2) return null
        val cum = DoubleArray(timed.size)
        val t = LongArray(timed.size)
        val start = timed.first().time!!.toEpochMilli()
        var prevT = 0L
        for (i in timed.indices) {
            if (i > 0) cum[i] = cum[i - 1] + MetricsCalculator.distanceMeters(timed[i - 1].toGeoPoint(), timed[i].toGeoPoint())
            t[i] = (timed[i].time!!.toEpochMilli() - start).coerceAtLeast(prevT)
            prevT = t[i]
        }
        if (cum.last() < distanceM) return null
        var best = Long.MAX_VALUE
        var lo = 0
        for (hi in timed.indices) {
            while (cum[hi] - cum[lo] >= distanceM) {
                best = minOf(best, t[hi] - t[lo])
                lo++
            }
        }
        return best.takeIf { it != Long.MAX_VALUE }
    }

    /** Families present in [tracks], most-recorded first — what the records screen offers to filter by. */
    fun familiesIn(
        tracks: List<FollowTrackEntity>,
        custom: List<CustomActivityType> = emptyList(),
    ): List<ActivityFamily> =
        tracks.filter { it.kind == TrackKind.TRAINING }
            .groupingBy { ActivityTypes.familyOf(it.activityType, custom) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

    /**
     * Computes all records over [tracks] (summaries) using [loadPoints] for the per-point sweeps.
     * Scalar records come straight from the summaries; window records only parse timed tracks.
     *
     * [family] restricts to one activity family: a "fastest 5K" set on a bike is not a running
     * record, and without this the library's fastest sport silently owns every distance record.
     * Null keeps the old whole-library behaviour.
     */
    suspend fun compute(
        tracks: List<FollowTrackEntity>,
        loadPoints: suspend (Long) -> List<GpxPoint>,
        family: ActivityFamily? = null,
        custom: List<CustomActivityType> = emptyList(),
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Record> {
        val trainings = tracks.filter { it.kind == TrackKind.TRAINING }
            .filter { family == null || ActivityTypes.familyOf(it.activityType, custom) == family }
        val records = mutableListOf<Record>()

        fun scalar(kind: RecordKind, selector: (FollowTrackEntity) -> Double?) {
            trainings.mapNotNull { e -> selector(e)?.let { v -> e to v } }
                .maxByOrNull { it.second }
                ?.let { (e, v) -> records.add(Record(kind, value = v, trackId = e.id, trackName = e.name, dateMs = e.createdAt)) }
        }
        scalar(RecordKind.LONGEST_DISTANCE) { it.distanceMeters.takeIf { d -> d > 0 } }
        scalar(RecordKind.MAX_ASCENT) { it.ascentM.takeIf { a -> a > 0 } }
        scalar(RecordKind.LONGEST_TIME) { it.durationMs?.takeIf { d -> d > 0 }?.toDouble() }

        val windowKinds = listOf(
            RecordKind.FASTEST_1K, RecordKind.FASTEST_5K, RecordKind.FASTEST_10K,
            RecordKind.FASTEST_HALF, RecordKind.FASTEST_MARATHON,
        )
        val bests = HashMap<RecordKind, Record>()
        var maxSpeed: Record? = null
        val timedTracks = trainings.filter { (it.durationMs ?: 0L) > 0L }
        timedTracks.forEachIndexed { i, e ->
            val pts = loadPoints(e.id)
            for (kind in windowKinds) {
                val ms = bestWindowMs(pts, kind.distanceM!!) ?: continue
                if (bests[kind]?.valueMs?.let { ms < it } != false) {
                    bests[kind] = Record(kind, valueMs = ms, trackId = e.id, trackName = e.name, dateMs = e.createdAt)
                }
            }
            val stats = TrackStatsCalculator.compute(pts)
            stats.maxSpeedKmh?.let { v ->
                if ((maxSpeed?.value ?: 0.0) < v) {
                    maxSpeed = Record(RecordKind.MAX_SPEED, value = v, trackId = e.id, trackName = e.name, dateMs = e.createdAt)
                }
            }
            onProgress(i + 1, timedTracks.size)
        }
        records.addAll(bests.values)
        maxSpeed?.let { records.add(it) }
        return records.sortedBy { it.kind.ordinal }
    }
}
