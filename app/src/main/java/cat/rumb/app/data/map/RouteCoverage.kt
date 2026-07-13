package cat.rumb.app.data.map

import cat.rumb.app.data.opentracks.model.GeoPoint

enum class CoverageStatus { COVERED, PARTIAL, NONE }

/** Coverage of a followed route by the available offline maps. */
data class RouteCoverage(
    val status: CoverageStatus,
    val coveredFraction: Float,
    val coveringMaps: List<String>,
)

/**
 * Determines how well a route is covered by downloaded offline maps, testing each route point against
 * the union of the maps' bounding boxes. Pure/unit-testable.
 */
object RouteCoverageCalculator {

    private const val MARGIN_DEG = 0.01 // ~1 km padding around a route

    /** Tight bounding box of a route with a small margin, or null if empty. */
    fun boundingBox(points: List<GeoPoint>, marginDeg: Double = MARGIN_DEG): BoundingBox? {
        if (points.isEmpty()) return null
        var w = Double.MAX_VALUE; var e = -Double.MAX_VALUE
        var s = Double.MAX_VALUE; var n = -Double.MAX_VALUE
        for (p in points) {
            w = minOf(w, p.longitude); e = maxOf(e, p.longitude)
            s = minOf(s, p.latitude); n = maxOf(n, p.latitude)
        }
        return BoundingBox(west = w - marginDeg, south = s - marginDeg, east = e + marginDeg, north = n + marginDeg)
    }

    fun coverage(points: List<GeoPoint>, maps: List<OfflineMap>): RouteCoverage {
        if (points.isEmpty()) return RouteCoverage(CoverageStatus.NONE, 0f, emptyList())
        val boxed = maps.mapNotNull { m -> m.bounds?.takeIf { it.size == 4 }?.let { m.name to it } }
        if (boxed.isEmpty()) return RouteCoverage(CoverageStatus.NONE, 0f, emptyList())

        var covered = 0
        val used = LinkedHashSet<String>()
        for (p in points) {
            var pointCovered = false
            for ((name, b) in boxed) {
                if (contains(b, p)) {
                    pointCovered = true
                    used.add(name)
                }
            }
            if (pointCovered) covered++
        }
        val fraction = covered.toFloat() / points.size
        val status = when {
            covered == points.size -> CoverageStatus.COVERED
            covered > 0 -> CoverageStatus.PARTIAL
            else -> CoverageStatus.NONE
        }
        return RouteCoverage(status, fraction, used.toList())
    }

    /** bounds = [west, south, east, north]. */
    private fun contains(bounds: List<Double>, p: GeoPoint): Boolean =
        p.longitude in bounds[0]..bounds[2] && p.latitude in bounds[1]..bounds[3]
}
