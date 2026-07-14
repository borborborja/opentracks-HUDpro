package cat.rumb.app.data.tracks

import java.time.Duration

/**
 * Calorie estimate. When average heart rate + age + sex are known it uses the Keytel (2005)
 * HR-based formula (more accurate for a given effort); otherwise it falls back to the MET model
 * (kcal = MET × weight(kg) × active hours), which needs only the activity type + weight.
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

    /**
     * Estimated kcal for [movingTime]. Uses the Keytel HR formula when [avgHr] > 0 and both
     * [ageYears] > 0 and [sex] ("M"/"F") are known; otherwise the MET model. 0 when time is unknown.
     */
    fun kcal(
        activityType: String?,
        weightKg: Int,
        movingTime: Duration?,
        avgHr: Double? = null,
        ageYears: Int = 0,
        sex: String? = null,
    ): Int {
        val minutes = (movingTime?.toMillis() ?: 0L) / 60_000.0
        if (minutes <= 0.0) return 0
        if (avgHr != null && avgHr > 0.0 && ageYears > 0 && (sex == "M" || sex == "F")) {
            // Keytel et al. 2005: kcal/min from HR, weight, age, sex (÷4.184 kJ→kcal).
            val perMin = if (sex == "M") {
                (-55.0969 + 0.6309 * avgHr + 0.1988 * weightKg + 0.2017 * ageYears) / 4.184
            } else {
                (-20.4022 + 0.4472 * avgHr - 0.1263 * weightKg + 0.074 * ageYears) / 4.184
            }
            val kc = perMin * minutes
            if (kc > 0.0) return kc.toInt()
        }
        val hours = minutes / 60.0
        return (metFor(activityType) * weightKg * hours).toInt()
    }
}
