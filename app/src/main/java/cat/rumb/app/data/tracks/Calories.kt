package cat.rumb.app.data.tracks

import java.time.Duration

/**
 * MET-based calorie estimate: kcal = MET × weight(kg) × active hours. Coarse by design — good
 * enough for trends without needing age/sex; per-type MET values from the Compendium of PA.
 */
object Calories {

    private val MET: Map<String, Double> = mapOf(
        ActivityTypes.WALK to 3.5,
        ActivityTypes.RUN to 9.8,
        ActivityTypes.TRAIL_RUN to 9.0,
        ActivityTypes.ROAD_BIKE to 8.0,
        ActivityTypes.MTB to 8.5,
        ActivityTypes.HIKE to 6.0,
        ActivityTypes.SKI to 7.0,
        ActivityTypes.SKATE to 7.0,
        ActivityTypes.KAYAK to 5.0,
        ActivityTypes.SWIM to 8.0,
    )
    private const val DEFAULT_MET = 6.0

    fun metFor(activityType: String?): Double = MET[activityType] ?: DEFAULT_MET

    /** Estimated kcal for [movingTime] of [activityType] at [weightKg]. 0 when time is unknown. */
    fun kcal(activityType: String?, weightKg: Int, movingTime: Duration?): Int {
        val hours = (movingTime?.toMillis() ?: 0L) / 3_600_000.0
        return (metFor(activityType) * weightKg * hours).toInt()
    }
}
