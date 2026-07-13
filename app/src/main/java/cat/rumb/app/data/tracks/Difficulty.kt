package cat.rumb.app.data.tracks

/** Difficulty band of a track, derived from distance + ascent (km-effort). */
enum class Difficulty { EASY, MODERATE, HARD, VERY_HARD }

/**
 * Automatic difficulty score. Uses the classic "km-effort" heuristic (flat km + 1 point per 100 m of
 * ascent) so every track gets a comparable score regardless of sensor data availability.
 */
object DifficultyCalculator {

    fun kmEffort(distanceMeters: Double, ascentMeters: Double): Double =
        distanceMeters / 1000.0 + ascentMeters / 100.0

    fun band(kmEffort: Double): Difficulty = when {
        kmEffort < 8.0 -> Difficulty.EASY
        kmEffort < 18.0 -> Difficulty.MODERATE
        kmEffort < 32.0 -> Difficulty.HARD
        else -> Difficulty.VERY_HARD
    }

    fun bandOf(distanceMeters: Double, ascentMeters: Double): Difficulty =
        band(kmEffort(distanceMeters, ascentMeters))
}
