package cat.hudpro.opentracks.data.tracks

import cat.hudpro.opentracks.data.opentracks.model.GeoPoint
import kotlin.math.cos

/**
 * Ramer–Douglas–Peucker simplification of a lat/lng polyline, used to turn a dense stored route into a
 * handful of editable waypoints. Distances are measured in meters via a local equirectangular
 * projection (accurate enough at the scale of a single route).
 */
object PolylineSimplifier {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Simplifies [points] keeping the shape within [epsilonMeters]. Endpoints are always preserved. If
     * the result still exceeds [maxPoints], epsilon is doubled and it retries so editing stays manageable.
     */
    fun simplify(points: List<GeoPoint>, epsilonMeters: Double, maxPoints: Int = 60): List<GeoPoint> {
        if (points.size <= 2) return points
        var epsilon = epsilonMeters.coerceAtLeast(1.0)
        var result = rdp(points, epsilon)
        var guard = 0
        while (result.size > maxPoints && guard < 20) {
            epsilon *= 2
            result = rdp(points, epsilon)
            guard++
        }
        return result
    }

    private fun rdp(points: List<GeoPoint>, epsilon: Double): List<GeoPoint> {
        if (points.size < 3) return points
        val lat0 = points.first().latitude
        var maxDist = 0.0
        var index = 0
        for (i in 1 until points.lastIndex) {
            val d = perpendicularDistance(points[i], points.first(), points.last(), lat0)
            if (d > maxDist) { maxDist = d; index = i }
        }
        return if (maxDist > epsilon) {
            val left = rdp(points.subList(0, index + 1), epsilon)
            val right = rdp(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(points.first(), points.last())
        }
    }

    /** Distance (m) from [p] to the segment [a]-[b] using a local flat projection around [lat0]. */
    private fun perpendicularDistance(p: GeoPoint, a: GeoPoint, b: GeoPoint, lat0: Double): Double {
        val kx = EARTH_RADIUS_M * Math.PI / 180.0 * cos(Math.toRadians(lat0))
        val ky = EARTH_RADIUS_M * Math.PI / 180.0
        val px = p.longitude * kx; val py = p.latitude * ky
        val ax = a.longitude * kx; val ay = a.latitude * ky
        val bx = b.longitude * kx; val by = b.latitude * ky
        val dx = bx - ax; val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return Math.hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        val projX = ax + t * dx; val projY = ay + t * dy
        return Math.hypot(px - projX, py - projY)
    }
}
