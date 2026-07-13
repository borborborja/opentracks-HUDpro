package cat.rumb.app.viewer.follow

import cat.rumb.app.data.opentracks.model.GeoPoint
import cat.rumb.app.viewer.hud.MetricsCalculator

/** The next sharp turn ahead on the followed route. */
data class NextTurn(val index: Int, val distanceM: Double, val left: Boolean)

data class FollowState(
    val offRouteMeters: Double,
    val remainingKm: Double,
    val bearingToRouteDeg: Double?,
    val nearestIndex: Int,
    /** Distance (m) along the route to the next sharp turn ahead, or null if none. */
    val distanceToNextTurnM: Double? = null,
    /** Next turn ahead with its direction, or null when the route is straight to the end. */
    val nextTurn: NextTurn? = null,
    /** Distance (m) traveled along the route up to the nearest vertex (ghost-race progress). */
    val progressMeters: Double = 0.0,
) {
    fun isOffRoute(thresholdMeters: Double = 40.0) = offRouteMeters > thresholdMeters
}

/**
 * Breadcrumb navigation against a preloaded route. Vertex-based: finds the nearest route vertex to
 * the current position, then derives remaining distance (cumulative-to-end), lateral deviation and
 * the bearing to the next vertex ahead. Suitable for dense GPX; pure and unit-testable.
 */
class FollowRouteEngine(route: List<GeoPoint>, elevations: List<Double?> = emptyList()) {

    val points: List<GeoPoint> = route
    private val cumulative: DoubleArray = DoubleArray(route.size)
    val totalMeters: Double

    /** Elevation samples (m) aligned to [points], for the HUD elevation profile. Empty if unknown. */
    val elevationProfile: List<Float> =
        if (elevations.size == route.size && elevations.any { it != null }) {
            elevations.map { (it ?: 0.0).toFloat() }
        } else {
            emptyList()
        }

    /** Route vertices where the heading changes sharply (turns / junctions), ascending. */
    private val turnIndices: List<Int>

    /** Signed heading change per turn vertex (negative = left), aligned with [turnIndices]. */
    private val turnDeltas: List<Double>

    init {
        var acc = 0.0
        for (i in 1 until route.size) {
            acc += MetricsCalculator.distanceMeters(route[i - 1], route[i])
            cumulative[i] = acc
        }
        totalMeters = acc

        val turns = ArrayList<Int>()
        val deltas = ArrayList<Double>()
        for (i in 1 until route.size - 1) {
            val b1 = MetricsCalculator.bearing(route[i - 1], route[i])
            val b2 = MetricsCalculator.bearing(route[i], route[i + 1])
            // Signed change in (-180, 180]: negative = turn to the left.
            val signed = ((b2 - b1 + 540.0) % 360.0) - 180.0
            if (Math.abs(signed) > TURN_DEG) {
                turns.add(i)
                deltas.add(signed)
            }
        }
        turnIndices = turns
        turnDeltas = deltas
    }

    fun update(current: GeoPoint): FollowState? {
        if (points.isEmpty()) return null
        var nearest = 0
        var nearestDist = Double.MAX_VALUE
        for (i in points.indices) {
            val d = MetricsCalculator.distanceMeters(current, points[i])
            if (d < nearestDist) {
                nearestDist = d
                nearest = i
            }
        }
        val remaining = (totalMeters - cumulative[nearest]).coerceAtLeast(0.0)
        val target = points.getOrNull(nearest + 1) ?: points[nearest]
        val bearing = if (target != current) MetricsCalculator.bearing(current, target) else null
        val turnPos = turnIndices.indexOfFirst { it > nearest }
        val next = if (turnPos >= 0) {
            val idx = turnIndices[turnPos]
            NextTurn(
                index = idx,
                distanceM = (cumulative[idx] - cumulative[nearest]).coerceAtLeast(0.0),
                left = turnDeltas[turnPos] < 0,
            )
        } else {
            null
        }
        return FollowState(
            offRouteMeters = nearestDist,
            remainingKm = remaining / 1000.0,
            bearingToRouteDeg = bearing,
            nearestIndex = nearest,
            distanceToNextTurnM = next?.distanceM,
            nextTurn = next,
            progressMeters = cumulative[nearest],
        )
    }

    companion object {
        /** Heading change (deg) above which a route vertex counts as a turn/junction. */
        private const val TURN_DEG = 35.0
    }
}
