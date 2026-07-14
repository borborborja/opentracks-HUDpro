package cat.rumb.app.data.map

import android.content.Context
import cat.rumb.app.data.debug.DebugLog
import org.maplibre.android.offline.OfflineManager

/**
 * Configures MapLibre's built-in **ambient tile cache** — the LRU store that already caches every
 * online raster tile the map fetches while browsing. We only expose its size (a user budget) and a
 * clear action. This is separate from the explicit MBTiles offline maps ([OfflineMapStore]), which
 * are persistent and never evicted.
 */
object MapCache {

    /** Sets the ambient cache budget in MB (older tiles are evicted once it's exceeded). */
    fun applyAmbientSize(context: Context, sizeMb: Int) {
        val bytes = sizeMb.coerceAtLeast(20).toLong() * 1024L * 1024L
        runCatching {
            OfflineManager.getInstance(context.applicationContext)
                .setMaximumAmbientCacheSize(
                    bytes,
                    object : OfflineManager.FileSourceCallback {
                        override fun onSuccess() = DebugLog.i("MapCache", "ambient cache = ${sizeMb}MB")
                        override fun onError(message: String) = DebugLog.w("MapCache", "ambient size: $message")
                    },
                )
        }.onFailure { DebugLog.e("MapCache", "no s'ha pogut fixar la mida de la cache", it) }
    }

    /** Empties the ambient cache (does not touch downloaded offline maps). */
    fun clearAmbient(context: Context, onDone: () -> Unit = {}) {
        runCatching {
            OfflineManager.getInstance(context.applicationContext)
                .clearAmbientCache(object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() { DebugLog.i("MapCache", "ambient cache buidada"); onDone() }
                    override fun onError(message: String) { DebugLog.w("MapCache", "clear: $message"); onDone() }
                })
        }.onFailure { DebugLog.e("MapCache", "no s'ha pogut buidar la cache", it); onDone() }
    }
}
