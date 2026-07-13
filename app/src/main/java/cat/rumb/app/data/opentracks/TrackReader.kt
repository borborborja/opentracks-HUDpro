package cat.rumb.app.data.opentracks

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import cat.rumb.app.data.opentracks.model.Track
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reads Track metadata from the OpenTracks content provider. Supports protocol V2 and V3
 * column layouts. Ported from OSMDashboard.
 */
object TrackReader {
    private const val TAG = "TrackReader"

    const val ID = "_id"
    const val NAME = "name"
    const val DESCRIPTION = "description"
    const val CATEGORY = "category" // V2
    const val ACTIVITY_TYPE = "activity_type" // V3
    const val ACTIVITY_TYPE_LOCALIZED = "activity_type_localized" // V3
    const val STARTTIME = "starttime" // V2
    const val TIME_START = "time_start" // V3
    const val STOPTIME = "stoptime" // V2
    const val TIME_STOP = "time_stop" // V3
    const val TOTALDISTANCE = "totaldistance" // V2
    const val DISTANCE = "distance" // V3
    const val TOTALTIME = "totaltime" // V2
    const val DURATION_TOTAL = "duration_total" // V3
    const val MOVINGTIME = "movingtime" // V2
    const val DURATION_MOVING = "duration_moving" // V3
    const val AVGSPEED = "avgspeed" // V2
    const val AVGMOVINGSPEED = "avgmovingspeed" // V2
    const val MAXSPEED = "maxspeed" // V2
    const val SPEED_MAX = "speed_max" // V3
    const val MINELEVATION = "minelevation" // V2
    const val MIN_ALTITUDE = "altitude_min" // V3
    const val MAXELEVATION = "maxelevation" // V2
    const val MAX_ALTITUDE = "altitude_max" // V3
    const val ELEVATIONGAIN = "elevationgain" // V2
    const val ALTITUDE_GAIN = "altitude_gain" // V3
    const val ALTITUDE_LOSS = "altitude_loss" // V3

    val PROJECTION = arrayOf(
        ID, NAME, DESCRIPTION, CATEGORY, STARTTIME, STOPTIME, TOTALDISTANCE,
        TOTALTIME, MOVINGTIME, AVGSPEED, AVGMOVINGSPEED, MAXSPEED,
        MINELEVATION, MAXELEVATION, ELEVATIONGAIN,
    )

    val PROJECTION_V3 = arrayOf(
        ID, NAME, DESCRIPTION, ACTIVITY_TYPE, ACTIVITY_TYPE_LOCALIZED, TIME_START, TIME_STOP,
        DISTANCE, DURATION_TOTAL, DURATION_MOVING, SPEED_MAX,
        MIN_ALTITUDE, MAX_ALTITUDE, ALTITUDE_GAIN, ALTITUDE_LOSS,
    )

    fun readTracks(resolver: ContentResolver, data: Uri, protocolVersion: Int): List<Track> =
        buildList {
            try {
                if (protocolVersion < 3) {
                    resolver.query(data, PROJECTION, null, null, null).use { cursor ->
                        while (cursor!!.moveToNext()) {
                            add(readV2(cursor))
                        }
                    }
                } else {
                    resolver.query(data, PROJECTION_V3, null, null, null).use { cursor ->
                        while (cursor!!.moveToNext()) {
                            add(readV3(cursor))
                        }
                    }
                }
            } catch (_: SecurityException) {
                Log.w(TAG, "No permission to read track")
            } catch (e: Exception) {
                Log.e(TAG, "Reading track failed", e)
            }
        }

    private fun readV2(cursor: android.database.Cursor) = Track(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(ID)),
        name = cursor.getString(cursor.getColumnIndexOrThrow(NAME)),
        description = cursor.getString(cursor.getColumnIndexOrThrow(DESCRIPTION)),
        activityType = cursor.getString(cursor.getColumnIndexOrThrow(CATEGORY)),
        timeStart = Instant.ofEpochMilli(cursor.getLong(cursor.getColumnIndexOrThrow(STARTTIME))),
        timeStop = Instant.ofEpochMilli(cursor.getLong(cursor.getColumnIndexOrThrow(STOPTIME))),
        distance = cursor.getDouble(cursor.getColumnIndexOrThrow(TOTALDISTANCE)),
        durationTotal = cursor.getLong(cursor.getColumnIndexOrThrow(TOTALTIME)).milliseconds,
        durationMoving = cursor.getLong(cursor.getColumnIndexOrThrow(MOVINGTIME)).milliseconds,
        avgSpeedMeterPerSecond = cursor.getDouble(cursor.getColumnIndexOrThrow(AVGSPEED)).takeIf { it > 0.0 },
        avgMovingSpeedMeterPerSecond = cursor.getDouble(cursor.getColumnIndexOrThrow(AVGMOVINGSPEED)).takeIf { it > 0.0 },
        maxSpeedMeterPerSecond = cursor.getDouble(cursor.getColumnIndexOrThrow(MAXSPEED)),
        minAltitudeMeter = cursor.getDouble(cursor.getColumnIndexOrThrow(MINELEVATION)),
        maxAltitudeMeter = cursor.getDouble(cursor.getColumnIndexOrThrow(MAXELEVATION)),
        altitudeGainMeter = cursor.getDouble(cursor.getColumnIndexOrThrow(ELEVATIONGAIN)),
    )

    private fun readV3(cursor: android.database.Cursor) = Track(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(ID)),
        name = cursor.getString(cursor.getColumnIndexOrThrow(NAME)),
        description = cursor.getString(cursor.getColumnIndexOrThrow(DESCRIPTION)),
        activityType = cursor.getString(cursor.getColumnIndexOrThrow(ACTIVITY_TYPE)),
        activityTypeLocalized = cursor.getString(cursor.getColumnIndexOrThrow(ACTIVITY_TYPE_LOCALIZED)),
        timeStart = Instant.ofEpochMilli(cursor.getLong(cursor.getColumnIndexOrThrow(TIME_START))),
        timeStop = Instant.ofEpochMilli(cursor.getLong(cursor.getColumnIndexOrThrow(TIME_STOP))),
        distance = cursor.getDouble(cursor.getColumnIndexOrThrow(DISTANCE)),
        durationTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DURATION_TOTAL)).milliseconds,
        durationMoving = cursor.getLong(cursor.getColumnIndexOrThrow(DURATION_MOVING)).milliseconds,
        maxSpeedMeterPerSecond = cursor.getDouble(cursor.getColumnIndexOrThrow(SPEED_MAX)),
        minAltitudeMeter = cursor.getDouble(cursor.getColumnIndexOrThrow(MIN_ALTITUDE)),
        maxAltitudeMeter = cursor.getDouble(cursor.getColumnIndexOrThrow(MAX_ALTITUDE)),
        altitudeGainMeter = cursor.getDouble(cursor.getColumnIndexOrThrow(ALTITUDE_GAIN)),
        altitudeLossMeter = cursor.getDouble(cursor.getColumnIndexOrThrow(ALTITUDE_LOSS)),
    )
}
