package cat.hudpro.opentracks.data.opentracks

object MapUtils {
    /** Valid WGS84 coordinate, excluding the OpenTracks pause sentinel (lat == 100.0). */
    fun isValid(latitude: Double, longitude: Double): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}
