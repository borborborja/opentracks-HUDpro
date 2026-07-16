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

/** A competition compares performances over time. ROUTE = whole tracks; LAP = laps of a fixed circuit. */
object CompetitionType {
    const val ROUTE = "ROUTE"
    const val LAP = "LAP"
}

/**
 * A unified competition: a leaderboard of attempts you try to beat, with a reference (ghost). ROUTE
 * competitions compare whole tracks; LAP competitions ("circuits") compare laps across a FIXED
 * start/finish line. Reference and attempts keep their GPX inline, so the leaderboard/gap/ghost are
 * self-contained and survive deletion or editing of the source track.
 */
@Entity(tableName = "competitions")
data class CompetitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = CompetitionType.ROUTE,
    @ColumnInfo(name = "activity_type") val activityType: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    val archived: Boolean = false,
    /** The ghost to chase: the whole reference track (ROUTE) or the best lap (LAP). */
    @ColumnInfo(name = "reference_gpx") val referenceGpx: String,
    @ColumnInfo(name = "best_attempt_id") val bestAttemptId: Long? = null,
    // LAP-only: the frozen start/finish line + auto-lap gate params (null for ROUTE).
    @ColumnInfo(name = "line_lat") val lineLat: Double? = null,
    @ColumnInfo(name = "line_lng") val lineLng: Double? = null,
    @ColumnInfo(name = "radius_m") val radiusM: Double? = null,
    @ColumnInfo(name = "min_lap_ms") val minLapMs: Long? = null,
    @ColumnInfo(name = "min_lap_m") val minLapM: Double? = null,
)

/** One attempt in a competition's leaderboard: a whole track (ROUTE) or a lap (LAP). GPX inline. */
@Entity(
    tableName = "competition_attempts",
    indices = [Index(value = ["competition_id", "source_track_id", "lap_index"], unique = true)],
)
data class CompetitionAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "competition_id") val competitionId: Long,
    @ColumnInfo(name = "source_track_id") val sourceTrackId: Long? = null,
    /** -1 for a whole-track (ROUTE) attempt; the lap index for a LAP attempt. */
    @ColumnInfo(name = "lap_index") val lapIndex: Int = -1,
    @ColumnInfo(name = "time_ms") val timeMs: Long,
    @ColumnInfo(name = "distance_m") val distanceM: Double = 0.0,
    @ColumnInfo(name = "avg_hr") val avgHr: Double? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    val gpx: String,
)

@Dao
interface CompetitionDao {
    @Query("SELECT * FROM competitions ORDER BY created_at DESC")
    fun observeCompetitions(): Flow<List<CompetitionEntity>>

    @Query("SELECT * FROM competitions WHERE id = :id")
    suspend fun getCompetition(id: Long): CompetitionEntity?

    @Query("SELECT * FROM competition_attempts WHERE competition_id = :competitionId ORDER BY time_ms ASC")
    fun attemptsFor(competitionId: Long): Flow<List<CompetitionAttemptEntity>>

    @Query("SELECT * FROM competition_attempts WHERE competition_id = :competitionId ORDER BY time_ms ASC")
    suspend fun attemptsForOnce(competitionId: Long): List<CompetitionAttemptEntity>

    /** Distinct source track ids that participate in any competition (for the library trophy badge). */
    @Query("SELECT DISTINCT source_track_id FROM competition_attempts WHERE source_track_id IS NOT NULL")
    fun observeSourceTrackIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompetition(competition: CompetitionEntity): Long

    /** IGNORE so a crash/restore that re-emits an attempt doesn't double-write (see the unique index). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttempt(attempt: CompetitionAttemptEntity): Long

    @Query("UPDATE competitions SET reference_gpx = :gpx, best_attempt_id = :bestAttemptId WHERE id = :id")
    suspend fun updateReference(id: Long, gpx: String, bestAttemptId: Long?)

    @Query("UPDATE competitions SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE competitions SET activity_type = :type WHERE id = :id")
    suspend fun setActivityType(id: Long, type: String?)

    @Query("UPDATE competitions SET archived = :flag WHERE id = :id")
    suspend fun setArchived(id: Long, flag: Boolean)

    @Query("DELETE FROM competitions WHERE id = :id")
    suspend fun deleteCompetition(id: Long)

    @Query("DELETE FROM competition_attempts WHERE competition_id = :competitionId")
    suspend fun deleteAttempts(competitionId: Long)

    @Query("DELETE FROM competition_attempts WHERE id = :id")
    suspend fun deleteAttempt(id: Long)
}
