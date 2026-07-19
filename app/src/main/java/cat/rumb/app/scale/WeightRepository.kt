package cat.rumb.app.scale

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Thin store over [WeighInDao]. The self-contained data seam of the weight module. */
class WeightRepository(private val dao: WeighInDao) {

    fun observeAll(): Flow<List<WeighInEntity>> = dao.observeAll()

    /** Persists a weigh-in with the profile snapshot; only ever called for YOUR own readings. */
    suspend fun add(timestamp: Long, weightKg: Double, impedanceOhm: Int?, heightCm: Int, ageYears: Int, sex: String): Long =
        withContext(Dispatchers.IO) {
            dao.insert(WeighInEntity(0, timestamp, weightKg, impedanceOhm, heightCm, ageYears, sex))
        }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }

    /**
     * The body weight (kg) to use for a calculation dated [atTs] (null = now/latest). Returns the
     * MEASURED weight only when the module is [enabled] and a weigh-in exists; otherwise [manualKg].
     * See [WeightResolver] — additive by design, so callers are unchanged without the scale.
     */
    suspend fun weightKgFor(atTs: Long?, manualKg: Int, enabled: Boolean): Int = withContext(Dispatchers.IO) {
        if (!enabled) manualKg else WeightResolver.resolve(dao.allOnce(), atTs, manualKg, enabled)
    }
}
