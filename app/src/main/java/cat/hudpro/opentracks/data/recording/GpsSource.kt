package cat.hudpro.opentracks.data.recording

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import cat.hudpro.opentracks.data.debug.DebugLog

/**
 * GPS fixes for the native recorder via [LocationManager] (no Play Services dependency, like
 * OpenTracks). Prefers the system `fused` provider (API 31+) and falls back to raw GPS.
 */
class GpsSource(private val context: Context) {

    private var manager: LocationManager? = null
    private var listener: LocationListener? = null

    /** Starts 1 Hz updates; returns false if no provider is available or permission is missing. */
    @SuppressLint("MissingPermission")
    fun start(onLocation: (Location) -> Unit): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                lm.allProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            lm.allProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return false
        }
        val l = LocationListener { onLocation(it) }
        return runCatching {
            lm.requestLocationUpdates(provider, INTERVAL_MS, 0f, l, Looper.getMainLooper())
            manager = lm
            listener = l
            DebugLog.i("Record", "GPS actiu · provider=$provider")
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
