package cat.rumb.app.data.tracks

import cat.rumb.app.data.gpx.GpxPoint
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class FoundationV16Test {

    private val t0: Instant = Instant.parse("2026-07-13T10:00:00Z")
    private val zone: ZoneId = ZoneId.of("Europe/Madrid")

    // --- Calories ---

    @Test
    fun caloriesUseMetTimesWeightTimesHours() {
        assertThat(Calories.kcal(ActivityTypes.RUN, 70, Duration.ofHours(1))).isEqualTo(686) // 9.8×70×1
        assertThat(Calories.kcal(ActivityTypes.WALK, 80, Duration.ofMinutes(30))).isEqualTo(140) // 3.5×80×0.5
        assertThat(Calories.kcal("unknown_custom", 75, Duration.ofHours(1))).isEqualTo(450) // default 6.0
        assertThat(Calories.kcal(ActivityTypes.RUN, 70, null)).isEqualTo(0)
    }

    // --- ActivityTypeSuggester ---

    @Test
    fun suggesterCoversTheSpeedBands() {
        assertThat(ActivityTypeSuggester.suggest(4.5, 20.0, 5_000.0)).isEqualTo(ActivityTypes.WALK)
        assertThat(ActivityTypeSuggester.suggest(5.0, 300.0, 5_000.0)).isEqualTo(ActivityTypes.HIKE) // 60 m/km
        assertThat(ActivityTypeSuggester.suggest(10.0, 50.0, 10_000.0)).isEqualTo(ActivityTypes.RUN)
        assertThat(ActivityTypeSuggester.suggest(10.0, 500.0, 10_000.0)).isEqualTo(ActivityTypes.HIKE)
        assertThat(ActivityTypeSuggester.suggest(16.0, 400.0, 10_000.0)).isEqualTo(ActivityTypes.MTB) // 40 m/km
        assertThat(ActivityTypeSuggester.suggest(28.0, 200.0, 40_000.0)).isEqualTo(ActivityTypes.ROAD_BIKE)
        assertThat(ActivityTypeSuggester.suggest(null, 0.0, 0.0)).isEqualTo(ActivityTypes.WALK)
    }

    // --- ProgressStats ---

    private fun training(daysAgoFrom: Long, km: Double = 10.0, hours: Double = 1.0) = FollowTrackEntity(
        id = daysAgoFrom, name = "t", gpx = "", kind = TrackKind.TRAINING,
        distanceMeters = km * 1000, ascentM = 100.0,
        durationMs = (hours * 3_600_000).toLong(),
        createdAt = t0.toEpochMilli() - daysAgoFrom * 86_400_000L,
    )

    @Test
    fun weeklyBucketsAndTotals() {
        // t0 = Monday 2026-07-13. Same week (0 days ago) + last week (8 days ago) + gap.
        val all = listOf(training(0), training(7), training(30, km = 20.0))
        val weeks = ProgressStats.weekly(all, weeks = 6, nowMs = t0.toEpochMilli(), zone = zone)
        assertThat(weeks).hasSize(6)
        assertThat(weeks.last().km).isEqualTo(10.0, within(0.001)) // current week
        assertThat(weeks[weeks.size - 2].km).isEqualTo(10.0, within(0.001)) // previous week
        assertThat(weeks.sumOf { it.count }).isEqualTo(3)
        val totals = ProgressStats.totals(all)
        assertThat(totals.km).isEqualTo(40.0, within(0.001))
        assertThat(totals.count).isEqualTo(3)
    }

    @Test
    fun streakCountsBackAndSurvivesEmptyCurrentWeek() {
        // Trainings in the 2 previous weeks but none in the current one → streak of 2.
        val all = listOf(training(7), training(14))
        assertThat(ProgressStats.streakWeeks(all, nowMs = t0.toEpochMilli(), zone = zone)).isEqualTo(2)
        // Adding one this week extends it to 3.
        assertThat(ProgressStats.streakWeeks(all + training(0), nowMs = t0.toEpochMilli(), zone = zone)).isEqualTo(3)
        // A hole breaks it.
        assertThat(ProgressStats.streakWeeks(listOf(training(0), training(22)), nowMs = t0.toEpochMilli(), zone = zone)).isEqualTo(1)
        assertThat(ProgressStats.streakWeeks(emptyList(), nowMs = t0.toEpochMilli(), zone = zone)).isEqualTo(0)
    }

    // --- PersonalRecords ---

    /** Constant pace: 0.001° lat (~111.2 m) every 30 s → 1 km in ~270 s. */
    private fun steadyTrack(n: Int): List<GpxPoint> =
        (0 until n).map { GpxPoint(41.0 + it * 0.001, 2.0, time = t0.plusSeconds(it * 30L)) }

    @Test
    fun bestWindowFindsTheFastestStretch() {
        // 20 legs of 111.2 m / 30 s, then a faster finish: same legs at 15 s.
        val slow = steadyTrack(21)
        val fastStart = slow.last().time!!
        val fast = (1..10).map {
            GpxPoint(41.020 + it * 0.001, 2.0, time = fastStart.plusSeconds(it * 15L))
        }
        val best = PersonalRecords.bestWindowMs(slow + fast, 1_000.0)!!
        // 1 km at the fast pace: 9 legs × 15 s = 135 s (1000/111.2 ≈ 9 legs).
        assertThat(best).isLessThanOrEqualTo(140_000L)
        assertThat(best).isGreaterThan(120_000L)
        // Untimed or too-short tracks → null.
        assertThat(PersonalRecords.bestWindowMs(listOf(GpxPoint(41.0, 2.0), GpxPoint(41.001, 2.0)), 1_000.0)).isNull()
        assertThat(PersonalRecords.bestWindowMs(steadyTrack(5), 1_000.0)).isNull()
    }

    @Test
    fun computeAggregatesScalarAndWindowRecords() = runBlocking {
        val e1 = FollowTrackEntity(
            id = 1, name = "curta", gpx = "", kind = TrackKind.TRAINING,
            distanceMeters = 2_200.0, ascentM = 50.0, durationMs = 600_000, createdAt = 1,
        )
        val points = steadyTrack(21) // ~2.2 km timed
        val records = PersonalRecords.compute(listOf(e1), loadPoints = { points })
        val kinds = records.map { it.kind }
        assertThat(kinds).contains(RecordKind.FASTEST_1K, RecordKind.LONGEST_DISTANCE, RecordKind.MAX_ASCENT, RecordKind.LONGEST_TIME, RecordKind.MAX_SPEED)
        assertThat(kinds).doesNotContain(RecordKind.FASTEST_5K) // track too short
    }
}
