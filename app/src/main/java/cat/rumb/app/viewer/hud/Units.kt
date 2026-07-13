package cat.rumb.app.viewer.hud

/**
 * User-selectable display units. OpenTracks sends raw data (m, m/s, microdegrees) over the Dashboard
 * API and its own unit setting does NOT travel with it, so Rumb owns unit formatting. Units are
 * chosen per magnitude (distance / elevation / speed); pace derives from the distance unit and VAM /
 * off-route deviation from the elevation unit.
 */
enum class DistanceUnit(val label: String, val kmPerUnit: Double) {
    KM("km", 1.0),
    MILE("mi", 1.609344),
    ;
    /** Converts a value in km to this unit. */
    fun fromKm(km: Double): Double = km / kmPerUnit
}

enum class ElevationUnit(val label: String, val perMeter: Double) {
    METER("m", 1.0),
    FOOT("ft", 3.280839895),
    ;
    fun fromMeters(m: Double): Double = m * perMeter
}

enum class SpeedUnit(val label: String, val perKmh: Double) {
    KMH("km/h", 1.0),
    MPH("mph", 0.621371192),
    ;
    fun fromKmh(kmh: Double): Double = kmh * perKmh
}

data class Units(
    val distance: DistanceUnit = DistanceUnit.KM,
    val elevation: ElevationUnit = ElevationUnit.METER,
    val speed: SpeedUnit = SpeedUnit.KMH,
)

/** Builds/reads [Units] from persisted preferences (kept out of the prefs class to avoid coupling). */
object UnitsStore {
    fun load(prefs: cat.rumb.app.data.prefs.ViewerPreferences): Units = Units(
        distance = runCatching { DistanceUnit.valueOf(prefs.distanceUnit) }.getOrDefault(DistanceUnit.KM),
        elevation = runCatching { ElevationUnit.valueOf(prefs.elevationUnit) }.getOrDefault(ElevationUnit.METER),
        speed = runCatching { SpeedUnit.valueOf(prefs.speedUnit) }.getOrDefault(SpeedUnit.KMH),
    )
}
