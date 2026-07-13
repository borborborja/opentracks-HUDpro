package cat.hudpro.opentracks.data.tracks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cat.hudpro.opentracks.BuildConfig
import cat.hudpro.opentracks.HudProApplication
import cat.hudpro.opentracks.data.geo.NominatimClient
import cat.hudpro.opentracks.data.gpx.Gpx
import kotlinx.coroutines.delay

/**
 * Fills in track metadata added in DB v4:
 * 1. ascent + start coordinates for tracks saved before the migration (parsed from the stored GPX);
 * 2. municipality via Nominatim reverse geocoding — serialized here with a >1 s delay per request,
 *    per the OSM usage policy (1 req/s max). Unique work guarantees a single queue.
 */
class TrackMetadataBackfillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = HudProApplication.from(applicationContext).database.followTrackDao()

        // Phase 1 (no network): extract ascent/start from the stored GPX blob.
        for (id in dao.idsNeedingMeta()) {
            val entity = dao.getById(id) ?: continue
            val points = runCatching { entity.gpx.byteInputStream().use { Gpx.read(it) }.points }
                .getOrDefault(emptyList())
            dao.setMeta(
                id,
                TrackRepository.ascent(points),
                points.firstOrNull()?.latitude,
                points.firstOrNull()?.longitude,
            )
        }

        // Phase 2 (network): resolve municipalities, strictly rate-limited.
        val pending = dao.needingMunicipality()
        if (pending.isEmpty()) return Result.success()
        val client = NominatimClient(
            "OpenTracksHUDpro/${BuildConfig.VERSION_NAME} (github.com/borborborja/opentracks-HUDpro)",
        )
        var failures = 0
        for (item in pending) {
            val name = client.municipality(item.startLat, item.startLon)
            if (name != null) dao.setMunicipality(item.id, name) else failures++
            delay(1100)
        }
        return if (failures > 0) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "track_metadata_backfill"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TrackMetadataBackfillWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
