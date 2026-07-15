package cat.rumb.app.data.tracks

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A "circuit": a FIXED start/finish line plus a leaderboard of lap efforts across days — the
 * lap-slice analogue of a (whole-track) competition. The line is frozen from the parent lap's start
 * point; the fastest effort is the ghost to chase. The reference lap's GPX is stored inline so the
 * ghost and gap charts survive deletion/editing of the source track.
 */
@Entity(tableName = "circuits")
data class CircuitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "activity_type") val activityType: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    val archived: Boolean = false,
    // Frozen start/finish line (from the parent lap's first point).
    @ColumnInfo(name = "line_lat") val lineLat: Double,
    @ColumnInfo(name = "line_lng") val lineLng: Double,
    // Auto-lap gate params frozen at creation (mirror RecorderConfig).
    @ColumnInfo(name = "radius_m") val radiusM: Double = 25.0,
    @ColumnInfo(name = "min_lap_ms") val minLapMs: Long = 20_000,
    @ColumnInfo(name = "min_lap_m") val minLapM: Double = 100.0,
    // Best lap so far, serialized as GPX; fed straight into GhostEngine.
    @ColumnInfo(name = "reference_gpx") val referenceGpx: String,
    @ColumnInfo(name = "best_effort_id") val bestEffortId: Long? = null,
)

/** One lap effort in a circuit's leaderboard. The lap slice is stored inline (provenance kept apart). */
@Entity(
    tableName = "circuit_efforts",
    indices = [Index(value = ["circuit_id", "source_track_id", "lap_index"], unique = true)],
)
data class CircuitEffortEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "circuit_id") val circuitId: Long,
    @ColumnInfo(name = "source_track_id") val sourceTrackId: Long? = null,
    @ColumnInfo(name = "lap_index") val lapIndex: Int = 0,
    @ColumnInfo(name = "time_ms") val timeMs: Long,
    @ColumnInfo(name = "distance_m") val distanceM: Double = 0.0,
    @ColumnInfo(name = "avg_hr") val avgHr: Double? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    val gpx: String,
)

@Dao
interface CircuitDao {
    @Query("SELECT * FROM circuits ORDER BY created_at DESC")
    fun observeCircuits(): Flow<List<CircuitEntity>>

    @Query("SELECT * FROM circuits WHERE id = :id")
    suspend fun getCircuit(id: Long): CircuitEntity?

    @Query("SELECT * FROM circuit_efforts WHERE circuit_id = :circuitId ORDER BY time_ms ASC")
    fun effortsFor(circuitId: Long): Flow<List<CircuitEffortEntity>>

    @Query("SELECT * FROM circuit_efforts WHERE circuit_id = :circuitId ORDER BY time_ms ASC")
    suspend fun effortsForOnce(circuitId: Long): List<CircuitEffortEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCircuit(circuit: CircuitEntity): Long

    /** IGNORE so a crash/restore that re-emits a lap doesn't double-write (see the unique index). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEffort(effort: CircuitEffortEntity): Long

    @Query("UPDATE circuits SET reference_gpx = :gpx, best_effort_id = :bestEffortId WHERE id = :id")
    suspend fun updateReference(id: Long, gpx: String, bestEffortId: Long?)

    @Query("UPDATE circuits SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE circuits SET archived = :flag WHERE id = :id")
    suspend fun setArchived(id: Long, flag: Boolean)

    @Query("DELETE FROM circuits WHERE id = :id")
    suspend fun deleteCircuit(id: Long)

    @Query("DELETE FROM circuit_efforts WHERE circuit_id = :circuitId")
    suspend fun deleteEfforts(circuitId: Long)
}
