package cat.hudpro.opentracks.data.routing

import android.content.Context
import android.util.Log
import cat.hudpro.opentracks.data.opentracks.model.GeoPoint

/**
 * Chooses a routing backend: the offline BRouter app when installed (works without network), else the
 * configurable BRouter HTTP server (default brouter.de; set a self-hosted URL for offline-on-LAN).
 */
class RoutingRepository(
    private val context: Context,
    private val httpBaseUrl: String = "https://brouter.de",
) {
    private val http by lazy { BRouterHttpProvider(httpBaseUrl) }

    suspend fun route(waypoints: List<GeoPoint>, profile: RoutingProfile): RoutedPath {
        if (BRouterServiceProvider.isInstalled(context)) {
            runCatching { BRouterServiceProvider(context).route(waypoints, profile) }
                .onSuccess { return it }
                .onFailure { Log.w("RoutingRepository", "Offline BRouter failed, falling back to HTTP", it) }
        }
        return http.route(waypoints, profile)
    }
}
