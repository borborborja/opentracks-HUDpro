package cat.rumb.app.data.recording

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import cat.rumb.app.data.opentracks.model.GeoPoint
import cat.rumb.app.data.opentracks.model.Segment
import cat.rumb.app.data.opentracks.model.TRACKPOINT_TYPE_TRACKPOINT
import cat.rumb.app.data.opentracks.model.Trackpoint
import java.time.Instant

/** An in-progress (or crashed) native recording; deleted once saved/discarded by the user. */
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** RECORDING | FINISHED. */
    val state: String = "RECORDING",
    /** Lap boundary marks (JSON of [LapMark]); null = no laps. Persisted for crash recovery. */
    val laps: String? = null,
)

@Entity(tableName = "recording_points")
data class RecordingPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    val seq: Long,
    /** Segment index; increases across pauses so segments can be rebuilt. */
    val segment: Int,
    val lat: Double,
    val lng: Double,
    val alt: Double?,
    @ColumnInfo(name = "time_ms") val timeMs: Long,
    val speed: Double,
    val bearing: Double?,
    val hr: Double?,
    val cad: Double?,
    val power: Double?,
) {
    fun toTrackpoint() = Trackpoint(
        trackId = 0L,
        id = seq,
        latLong = GeoPoint(lat, lng),
        type = TRACKPOINT_TYPE_TRACKPOINT,
        speed = speed,
        time = Instant.ofEpochMilli(timeMs),
        altitude = alt,
        heartRate = hr,
        cadence = cad,
        power = power,
        bearing = bearing,
    )

    companion object {
        fun from(recordingId: Long, segment: Int, p: Trackpoint) = RecordingPointEntity(
            recordingId = recordingId,
            seq = p.id,
            segment = segment,
            lat = p.latLong?.latitude ?: 0.0,
            lng = p.latLong?.longitude ?: 0.0,
            alt = p.altitude,
            timeMs = p.time.toEpochMilli(),
            speed = p.speed,
            bearing = p.bearing,
            hr = p.heartRate,
            cad = p.cadence,
            power = p.power,
        )
    }
}

@Dao
interface RecordingDao {
    @Insert
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Insert
    suspend fun insertPoints(points: List<RecordingPointEntity>)

    @Query("SELECT * FROM recordings WHERE state = 'RECORDING' ORDER BY started_at DESC LIMIT 1")
    suspend fun activeRecording(): RecordingEntity?

    /** A finished-but-unsaved recording that survived a process death before the user saved it. */
    @Query("SELECT * FROM recordings WHERE state = 'FINISHED' ORDER BY started_at DESC LIMIT 1")
    suspend fun finishedRecording(): RecordingEntity?

    @Query("SELECT * FROM recording_points WHERE recording_id = :recordingId ORDER BY seq ASC")
    suspend fun points(recordingId: Long): List<RecordingPointEntity>

    @Query("UPDATE recordings SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: String)

    @Query("UPDATE recordings SET laps = :laps WHERE id = :id")
    suspend fun setLaps(id: Long, laps: String?)

    @Query("DELETE FROM recording_points WHERE recording_id = :id")
    suspend fun deletePoints(id: Long)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecording(id: Long)

    /** Purges every finished recording (rows + points) once the user has saved or discarded it. */
    @Query("DELETE FROM recording_points WHERE recording_id IN (SELECT id FROM recordings WHERE state = 'FINISHED')")
    suspend fun deleteFinishedPoints()

    @Query("DELETE FROM recordings WHERE state = 'FINISHED'")
    suspend fun deleteFinishedRecordings()
}

/** Rebuilds pause-aware segments from persisted points (grouped by their segment index). */
fun List<RecordingPointEntity>.toSegments(): List<Segment> =
    groupBy { it.segment }.toSortedMap().values.map { pts -> pts.map { it.toTrackpoint() } }
