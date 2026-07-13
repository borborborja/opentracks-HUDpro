package cat.rumb.app.data.tracks

/**
 * Guesses the activity type of a finished recording from its aggregate numbers, to preselect the
 * type in the save dialog (always overridable by the user).
 */
object ActivityTypeSuggester {

    fun suggest(avgMovingSpeedKmh: Double?, ascentM: Double, distanceM: Double): String {
        val speed = avgMovingSpeedKmh ?: return ActivityTypes.WALK
        val ascentPerKm = if (distanceM > 100) ascentM / (distanceM / 1000.0) else 0.0
        return when {
            speed < 6.5 -> if (ascentPerKm > 40) ActivityTypes.HIKE else ActivityTypes.WALK
            speed < 13.0 -> if (ascentPerKm > 40) ActivityTypes.HIKE else ActivityTypes.RUN
            speed < 18.0 && ascentPerKm > 25 -> ActivityTypes.MTB
            else -> ActivityTypes.ROAD_BIKE
        }
    }
}
