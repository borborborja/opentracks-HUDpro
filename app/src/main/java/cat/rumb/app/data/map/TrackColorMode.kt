package cat.rumb.app.data.map

import cat.rumb.app.R
import cat.rumb.app.data.opentracks.model.Trackpoint

/** How the recorded track polyline is colored in the viewer. */
enum class TrackColorMode(val labelRes: Int) {
    SINGLE(R.string.trackcolor_single),
    SPEED(R.string.trackcolor_speed),
    ALTITUDE(R.string.trackcolor_altitude),
    HEART_RATE(R.string.trackcolor_heart_rate),
    ;

    /** The numeric value to color by for a trackpoint, or null if unavailable in this mode. */
    fun valueOf(tp: Trackpoint): Double? = when (this) {
        SINGLE -> null
        SPEED -> tp.speed.takeIf { it >= 0.0 }?.times(3.6) // km/h
        ALTITUDE -> tp.altitude
        HEART_RATE -> tp.heartRate
    }

    companion object {
        fun byName(name: String?): TrackColorMode = entries.firstOrNull { it.name == name } ?: SPEED
    }
}
