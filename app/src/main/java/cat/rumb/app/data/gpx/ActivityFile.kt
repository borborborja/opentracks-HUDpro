package cat.rumb.app.data.gpx

import cat.rumb.app.data.tracks.ActivityTypes
import cat.rumb.app.data.tracks.LapRange

/** Chooses the activity-file format for upload/export: TCX when the track has usable laps, else GPX. */
object ActivityFile {

    data class Built(val fileName: String, val content: String)

    /**
     * Builds the best file for [points]. Uses TCX (real `<Lap>` elements, with per-lap calories from
     * [weightKg]/[ageYears]/[sex]) when [laps] is non-empty and the points carry timestamps (TCX needs
     * them); otherwise GPX. [baseName] must NOT include the extension. [activityType] is a Rumb type id.
     */
    fun build(
        baseName: String,
        points: List<GpxPoint>,
        laps: List<LapRange>,
        activityType: String?,
        weightKg: Int = 0,
        ageYears: Int = 0,
        sex: String? = null,
    ): Built {
        val useTcx = laps.isNotEmpty() && points.firstOrNull()?.time != null
        return if (useTcx) {
            Built("$baseName.tcx", Tcx.write(baseName, points, laps, activityType, weightKg, ageYears, sex))
        } else {
            Built("$baseName.gpx", Gpx.write(baseName, points, ActivityTypes.gpxType(activityType)))
        }
    }
}
