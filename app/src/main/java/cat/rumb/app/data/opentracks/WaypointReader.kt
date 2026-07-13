package cat.rumb.app.data.opentracks

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import cat.rumb.app.data.opentracks.model.GeoPoint
import cat.rumb.app.data.opentracks.model.Waypoint

/** Reads markers/waypoints from the OpenTracks content provider. Ported from OSMDashboard. */
object WaypointReader {
    const val ID = "_id"
    const val NAME = "name"
    const val DESCRIPTION = "description"
    const val CATEGORY = "category" // V2
    const val TYPE_LOCALIZED = "marker_type_localized" // V3
    const val TRACKID = "trackid"
    const val LONGITUDE = "longitude"
    const val LATITUDE = "latitude"
    const val PHOTOURL = "photoUrl"

    val PROJECTION = arrayOf(ID, NAME, DESCRIPTION, CATEGORY, TRACKID, LATITUDE, LONGITUDE, PHOTOURL)
    val PROJECTION_V3 = arrayOf(ID, NAME, DESCRIPTION, TYPE_LOCALIZED, TRACKID, LATITUDE, LONGITUDE, PHOTOURL)

    fun readWaypoints(
        resolver: ContentResolver,
        data: Uri,
        lastWaypointId: Long?,
        protocolVersion: Int,
    ): List<Waypoint> = buildList {
        val projection = if (protocolVersion < 3) PROJECTION else PROJECTION_V3
        val categoryColumn = if (protocolVersion < 3) CATEGORY else TYPE_LOCALIZED
        resolver.query(data, projection, null, null, null).use { cursor ->
            while (cursor!!.moveToNext()) {
                readWaypointFromCursor(cursor, lastWaypointId, categoryColumn)?.let(::add)
            }
        }
    }

    private fun readWaypointFromCursor(
        cursor: Cursor,
        lastWaypointId: Long?,
        categoryColumn: String,
    ): Waypoint? {
        val waypointId = cursor.getLong(cursor.getColumnIndexOrThrow(ID))
        if (lastWaypointId != null && lastWaypointId >= waypointId) return null // skip already-seen

        val name = cursor.getString(cursor.getColumnIndexOrThrow(NAME))
        val description = cursor.getString(cursor.getColumnIndexOrThrow(DESCRIPTION))
        val typeLocalized = cursor.getString(cursor.getColumnIndexOrThrow(categoryColumn))
        val trackId = cursor.getLong(cursor.getColumnIndexOrThrow(TRACKID))
        val latitude = cursor.getInt(cursor.getColumnIndexOrThrow(LATITUDE)) / APIConstants.LAT_LON_FACTOR
        val longitude = cursor.getInt(cursor.getColumnIndexOrThrow(LONGITUDE)) / APIConstants.LAT_LON_FACTOR
        val photoUrl = cursor.getString(cursor.getColumnIndexOrThrow(PHOTOURL))

        return if (MapUtils.isValid(latitude, longitude)) {
            Waypoint(waypointId, name, description, typeLocalized, trackId, GeoPoint(latitude, longitude), photoUrl)
        } else {
            null
        }
    }
}
