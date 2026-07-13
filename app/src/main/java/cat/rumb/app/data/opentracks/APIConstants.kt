package cat.rumb.app.data.opentracks

import android.content.Intent
import android.net.Uri

/**
 * Contract with OpenTracks' Dashboard API. Preserved verbatim from OSMDashboard so that
 * OpenTracks (`de.dennisguse.opentracks`) keeps working as the data producer.
 *
 * Producer reference: OpenTracks `util/IntentDashboardUtils.java`.
 */
object APIConstants {
    const val LAT_LON_FACTOR = 1E6

    // NOTE: Must match the intent-filter action in AndroidManifest.xml!
    const val ACTION_DASHBOARD = "Intent.OpenTracks-Dashboard"
    const val ACTION_DASHBOARD_PAYLOAD = "$ACTION_DASHBOARD.Payload"

    fun getTracksUri(uris: List<Uri>): Uri = uris[0]
    fun getTrackpointsUri(uris: List<Uri>): Uri = uris[1]

    /** Waypoints/markers are only present in newer OpenTracks versions. */
    fun getWaypointsUri(uris: List<Uri>): Uri? = uris.getOrNull(2)
}

fun Intent.isDashboardAction(): Boolean = APIConstants.ACTION_DASHBOARD == action
