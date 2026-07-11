package cat.hudpro.opentracks.data.tracks

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

enum class TrackSource { GPX_IMPORT, ENDURAIN }

class Converters {
    @TypeConverter fun sourceToString(s: TrackSource): String = s.name
    @TypeConverter fun stringToSource(s: String): TrackSource = TrackSource.valueOf(s)
}

/** A route the user can follow from the viewer. The GPX text is stored inline for simplicity. */
@Entity(tableName = "follow_tracks")
data class FollowTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val collection: String = "General",
    val source: TrackSource = TrackSource.GPX_IMPORT,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double = 0.0,
    @ColumnInfo(name = "point_count") val pointCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "gpx") val gpx: String,
    /** Remote id when [source] is ENDURAIN. */
    @ColumnInfo(name = "remote_id") val remoteId: Long? = null,
)

@Dao
interface FollowTrackDao {
    @Query("SELECT id, name, collection, source, distance_meters, point_count, created_at, remote_id, '' AS gpx FROM follow_tracks ORDER BY created_at DESC")
    fun observeSummaries(): Flow<List<FollowTrackEntity>>

    @Query("SELECT * FROM follow_tracks WHERE id = :id")
    suspend fun getById(id: Long): FollowTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: FollowTrackEntity): Long

    @Query("DELETE FROM follow_tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT remote_id FROM follow_tracks WHERE source = 'ENDURAIN'")
    suspend fun knownRemoteIds(): List<Long>
}

@Database(entities = [FollowTrackEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class HudProDatabase : RoomDatabase() {
    abstract fun followTrackDao(): FollowTrackDao
}
