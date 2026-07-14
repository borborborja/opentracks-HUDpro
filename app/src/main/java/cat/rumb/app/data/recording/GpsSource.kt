package cat.rumb.app.data.recording

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Looper
import cat.rumb.app.data.debug.DebugLog

/**
 * GPS fixes for the native recorder via [LocationManager] (no Play Services dependency, like
 * OpenTracks). Prefers the raw **GPS provider** at high accuracy — requesting GPS directly is what
 * powers the GPS chip into a hot, precise fix (the platform `fused` provider in low-power mode does
 * not, which stalled the warm-up). Falls back to `fused` only when there is no GPS provider.
 */
class GpsSource(private val context: Context) {

    private var manager: LocationManager? = null
    private var listener: LocationListener? = null

    /** Starts updates every [intervalMs]; returns false if no provider or permission is missing. */
    @SuppressLint("MissingPermission")
    fun start(intervalMs: Long = INTERVAL_MS, onLocation: (Location) -> Unit): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        // GPS first (forces a hot high-accuracy fix); fused only as a last resort.
        val provider = when {
            lm.allProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                lm.allProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            else -> return false
        }
        val l = LocationListener { onLocation(it) }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Modern request: explicitly ask for the highest accuracy the provider can give.
                val request = LocationRequest.Builder(intervalMs)
                    .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                    .setMinUpdateDistanceMeters(0f)
                    .build()
                lm.requestLocationUpdates(provider, request, context.mainExecutor, l)
            } else {
                // Legacy overload on GPS_PROVIDER already yields real high-accuracy GPS.
                lm.requestLocationUpdates(provider, intervalMs, 0f, l, Looper.getMainLooper())
            }
            manager = lm
            listener = l
            DebugLog.i("Record", "GPS actiu · provider=$provider · alta precisió")
            true
        }.getOrElse {
            DebugLog.e("Record", "GPS no disponible", it)
            false
        }
    }

    fun stop() {
        listener?.let { manager?.removeUpdates(it) }
        listener = null
        manager = null
    }

    private companion object {
        const val INTERVAL_MS = 1000L
    }
}
