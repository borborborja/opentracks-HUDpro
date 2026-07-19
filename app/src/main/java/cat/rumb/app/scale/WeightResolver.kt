package cat.rumb.app.scale

import kotlin.math.roundToInt

/**
 * Picks the body weight (kg) to feed a calculation — calories, power-to-weight, etc. — from the
 * measured weigh-ins, falling back to the manually-entered profile weight.
 *
 * The rule is deliberately ADDITIVE: it returns the measured weight only when the weight-control
 * module is enabled AND at least one weigh-in exists. With the module off, or no weigh-ins, it always
 * returns [manualKg], so every caller behaves exactly as it did before the scale existed — the scale
 * data can only ever refine a number, never remove or replace the manual weight.
 *
 * Pure and JVM-testable; the DB read lives in [WeightRepository.weightKgFor].
 */
object WeightResolver {

    /**
     * @param atTs the moment the calculation is about (a past activity's start), or null for "now".
     *   For a past date the weigh-in on or before it is used (your weight back then); if the activity
     *   predates every weigh-in, the earliest one is used; for null, the latest weigh-in.
     */
    fun resolve(weighIns: List<WeighInEntity>, atTs: Long?, manualKg: Int, enabled: Boolean): Int {
        if (!enabled || weighIns.isEmpty()) return manualKg
        val sorted = weighIns.sortedBy { it.timestamp }
        val chosen = when {
            atTs == null -> sorted.last()
            else -> sorted.lastOrNull { it.timestamp <= atTs } ?: sorted.first()
        }
        return chosen.weightKg.roundToInt()
    }
}
