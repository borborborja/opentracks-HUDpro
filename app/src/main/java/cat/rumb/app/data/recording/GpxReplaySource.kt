package cat.rumb.app.data.recording

import android.content.Context
import android.location.Location
import cat.rumb.app.RumbApplication
import cat.rumb.app.data.debug.DebugLog
import cat.rumb.app.viewer.hud.MetricsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Feeds the recorder from a saved track instead of the GPS, so laps, splits, competitions and the
 * ghost can be exercised from a chair. Drop-in for [GpsSource]: [RecordingService] swaps one for the
 * other, so the whole real path is tested (engine → auto-lap → competition → save), not a mock of it.
 *
 * **Timestamps carry the ORIGINAL elapsed time, not the accelerated one.** The recorder rejects any
 * fix implying more than [RecorderConfig.maxImpliedSpeedMs] (180 km/h) as a GPS jump, so stamping
 * points with the compressed clock would make a 5x bike replay look like 125 km/h and a 10x one
 * would be thrown away entirely — the simulator would record nothing. Replaying the real timeline
 * and merely *delivering* it faster keeps every derived number (speed, pace, splits, duration)
 * honest at any factor; only the viewer's live clock runs fast, which is cosmetic.
 */
class GpxReplaySource(
    private val context: Context,
    private val trackId: Long,
    private val factor: Float,
) : LocationSource {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    override fun start(intervalMs: Long, onLocation: (Location) -> Unit): Boolean {
        job = scope.launch {
            val points = RumbApplication.from(context).trackRepository.loadGpxRoute(trackId)
                .filter { it.time != null }
            if (points.size < 2) {
                DebugLog.w("Record", "simulació: el track $trackId no té punts amb hora")
                return@launch
            }
            val speedUp = factor.coerceAtLeast(0.1f)
            val srcStart = points.first().time!!.toEpochMilli()
            val wallStart = System.currentTimeMillis()
            DebugLog.i("Record", "simulació iniciada · track=$trackId · ${points.size} punts · ${speedUp}x")

            points.forEachIndexed { i, p ->
                val srcElapsed = p.time!!.toEpochMilli() - srcStart
                // Wait until this point is due on the ACCELERATED wall clock...
                val due = wallStart + (srcElapsed / speedUp).toLong()
                (due - System.currentTimeMillis()).takeIf { it > 0 }?.let { delay(it) }

                val prev = points.getOrNull(i - 1)
                val loc = Location(PROVIDER).apply {
                    latitude = p.latitude
                    longitude = p.longitude
                    p.elevation?.let { altitude = it }
                    // ...but stamp it with the REAL elapsed time (see the class note).
                    time = wallStart + srcElapsed
                    accuracy = SIM_ACCURACY_M
                    if (prev != null) {
                        val dtSec = (p.time!!.toEpochMilli() - prev.time!!.toEpochMilli()) / 1000.0
                        val meters = MetricsCalculator.distanceMeters(prev.toGeoPoint(), p.toGeoPoint())
                        if (dtSec > 0) speed = (meters / dtSec).toFloat()
                        bearing = MetricsCalculator.bearing(prev.toGeoPoint(), p.toGeoPoint()).toFloat()
                    }
                }
                withContext(Dispatchers.Main) { onLocation(loc) }
            }
            DebugLog.i("Record", "simulació acabada · ${points.size} punts reproduïts")
        }
        return true
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val PROVIDER = "gpx-replay"
        /** Well inside the recorder's accuracy gate, so replayed fixes are never dropped as noisy. */
        const val SIM_ACCURACY_M = 5f
    }
}
