package cat.rumb.app.data.tracks

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One ISO week (Monday-based) of training volume. */
data class WeekBucket(
    val startEpochDay: Long,
    val km: Double,
    val hours: Double,
    val ascentM: Double,
    val count: Int,
)

/** All-time totals. */
data class ProgressTotals(val km: Double, val hours: Double, val ascentM: Double, val count: Int)

/**
 * Weekly training-volume aggregation over track summaries (no GPX parsing). Trainings only;
 * archived tracks included (they're still your history). Pure and clock-injected for tests.
 */
object ProgressStats {

    private fun weekStart(epochMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
            .with(DayOfWeek.MONDAY).toEpochDay()

    private fun trainings(all: List<FollowTrackEntity>, typeFilter: String?): List<FollowTrackEntity> =
        all.filter { it.kind == TrackKind.TRAINING && (typeFilter == null || it.activityType == typeFilter) }

    /** The last [weeks] ISO weeks ending in the week of [nowMs], oldest first (empty weeks included). */
    fun weekly(
        all: List<FollowTrackEntity>,
        weeks: Int = 12,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        typeFilter: String? = null,
    ): List<WeekBucket> {
        val byWeek = trainings(all, typeFilter).groupBy { weekStart(it.createdAt, zone) }
        val currentStart = weekStart(nowMs, zone)
        return (weeks - 1 downTo 0).map { back ->
            val start = LocalDate.ofEpochDay(currentStart).minusWeeks(back.toLong()).toEpochDay()
            val items = byWeek[start].orEmpty()
            WeekBucket(
                startEpochDay = start,
                km = items.sumOf { it.distanceMeters } / 1000.0,
                hours = items.sumOf { it.durationMs ?: 0L } / 3_600_000.0,
                ascentM = items.sumOf { it.ascentM },
                count = items.size,
            )
        }
    }

    /** Consecutive weeks with at least one training, counting back from the week of [nowMs]. */
    fun streakWeeks(
        all: List<FollowTrackEntity>,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val weeksWith = trainings(all, null).map { weekStart(it.createdAt, zone) }.toSet()
        if (weeksWith.isEmpty()) return 0
        var streak = 0
        var cursor = LocalDate.ofEpochDay(weekStart(nowMs, zone))
        // The current week may still be in progress: an empty current week doesn't break the streak.
        if (cursor.toEpochDay() !in weeksWith) cursor = cursor.minusWeeks(1)
        while (cursor.toEpochDay() in weeksWith) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }

    fun totals(all: List<FollowTrackEntity>, typeFilter: String? = null): ProgressTotals {
        val items = trainings(all, typeFilter)
        return ProgressTotals(
            km = items.sumOf { it.distanceMeters } / 1000.0,
            hours = items.sumOf { it.durationMs ?: 0L } / 3_600_000.0,
            ascentM = items.sumOf { it.ascentM },
            count = items.size,
        )
    }
}
