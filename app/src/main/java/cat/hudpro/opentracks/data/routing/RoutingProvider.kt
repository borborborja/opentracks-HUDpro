package cat.hudpro.opentracks.data.routing

import cat.hudpro.opentracks.R
import cat.hudpro.opentracks.data.gpx.GpxPoint
import cat.hudpro.opentracks.data.opentracks.model.GeoPoint

/** Routing profile (maps to a BRouter profile name). */
enum class RoutingProfile(val brouter: String, val labelRes: Int) {
    HIKING("hiking-mountain", R.string.routing_hiking),
    TREKKING("trekking", R.string.routing_trekking),
    MTB("mtb", R.string.routing_mtb),
    SHORTEST("shortest", R.string.routing_shortest),
}

/** A routed path snapped to trails/roads, with elevation. */
data class RoutedPath(
    val points: List<GpxPoint>,
    val distanceMeters: Double,
    val ascentMeters: Double,
) {
    val isEmpty get() = points.isEmpty()
}

/** Computes a path that snaps the given waypoints to the underlying trail/road network. */
interface RoutingProvider {
    suspend fun route(waypoints: List<GeoPoint>, profile: RoutingProfile): RoutedPath
}

class RoutingException(message: String) : Exception(message)
