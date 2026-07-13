package cat.rumb.app.data.tracks

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

enum class TrackSource { GPX_IMPORT, ENDURAIN, RECORDED }

/** What a saved track IS: a route to follow, or a recorded/imported training. */
object TrackKind {
    const val ROUTE = "ROUTE"
    const val TRAINING = "TRAINING"
}

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
    /** [TrackKind.ROUTE] (to follow) or [TrackKind.TRAINING] (recorded/imported activity). */
    val kind: String = TrackKind.ROUTE,
    /** Activity type id (predefined like "run"/"mtb" or "custom_<uuid>"); null = unassigned. */
    @ColumnInfo(name = "activity_type") val activityType: String? = null,
    /** Municipality of the start point, reverse-geocoded once via Nominatim. */
    val municipality: String? = null,
    /** Total ascent in meters, persisted so lists can sort by difficulty without parsing GPX. */
    @ColumnInfo(name = "ascent_m") val ascentM: Double = 0.0,
    @ColumnInfo(name = "start_lat") val startLat: Double? = null,
    @ColumnInfo(name = "start_lon") val startLon: Double? = null,
    /** True once ascent/start have been extracted (municipality may still be pending). */
    @ColumnInfo(name = "meta_done") val metaDone: Boolean = false,
)

/** Projection for the municipality backfill queue. */
data class IdLatLon(
    val id: Long,
    @ColumnInfo(name = "start_lat") val startLat: Double,
    @ColumnInfo(name = "start_lon") val startLon: Double,
)

@Dao
interface FollowTrackDao {
    @Query(
        "SELECT id, name, collection, source, distance_meters, point_count, created_at, remote_id, kind, " +
            "activity_type, municipality, ascent_m, start_lat, start_lon, meta_done, '' AS gpx " +
            "FROM follow_tracks ORDER BY created_at DESC",
    )
    fun observeSummaries(): Flow<List<FollowTrackEntity>>

    @Query("UPDATE follow_tracks SET collection = :newName WHERE collection = :oldName AND kind = :kind")
    suspend fun renameCollection(oldName: String, newName: String, kind: String)

    @Query("SELECT * FROM follow_tracks WHERE id = :id")
    suspend fun getById(id: Long): FollowTrackEntity?

    @Query("UPDATE follow_tracks SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE follow_tracks SET collection = :collection WHERE id = :id")
    suspend fun setCollection(id: Long, collection: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: FollowTrackEntity): Long

    @Query("DELETE FROM follow_tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT remote_id FROM follow_tracks WHERE source = 'ENDURAIN'")
    suspend fun knownRemoteIds(): List<Long>

    @Query("UPDATE follow_tracks SET activity_type = :type WHERE id = :id")
    suspend fun setActivityType(id: Long, type: String?)

    @Query("UPDATE follow_tracks SET ascent_m = :ascent, start_lat = :lat, start_lon = :lon, meta_done = 1 WHERE id = :id")
    suspend fun setMeta(id: Long, ascent: Double, lat: Double?, lon: Double?)

    @Query("UPDATE follow_tracks SET municipality = :municipality WHERE id = :id")
    suspend fun setMunicipality(id: Long, municipality: String)

    @Query("SELECT id FROM follow_tracks WHERE meta_done = 0")
    suspend fun idsNeedingMeta(): List<Long>

    @Query("SELECT id, start_lat, start_lon FROM follow_tracks WHERE municipality IS NULL AND start_lat IS NOT NULL")
    suspend fun needingMunicipality(): List<IdLatLon>

    @Query("SELECT DISTINCT collection FROM follow_tracks WHERE kind = :kind")
    suspend fun collections(kind: String): List<String>
}

@Database(
    entities = [
        FollowTrackEntity::class,
        cat.rumb.app.data.recording.RecordingEntity::class,
        cat.rumb.app.data.recording.RecordingPointEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class RumbDatabase : RoomDatabase() {
    abstract fun followTrackDao(): FollowTrackDao
    abstract fun recordingDao(): cat.rumb.app.data.recording.RecordingDao

    companion object {
        /** v4: activity type, municipality and sortable metadata on follow_tracks. */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE follow_tracks ADD COLUMN activity_type TEXT")
                db.execSQL("ALTER TABLE follow_tracks ADD COLUMN municipality TEXT")
                db.execSQL("ALTER TABLE follow_tracks ADD COLUMN ascent_m REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE follow_tracks ADD COLUMN start_lat REAL")
                db.execSQL("ALTER TABLE follow_tracks ADD COLUMN start_lon REAL")
                db.execSQL("ALTER TABLE follow_tracks ADD COLUMN meta_done INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: route/training kind on follow_tracks (recorded tracks become trainings). */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE follow_tracks ADD COLUMN kind TEXT NOT NULL DEFAULT 'ROUTE'")
                db.execSQL("UPDATE follow_tracks SET kind = 'TRAINING' WHERE source = 'RECORDED'")
            }
        }

        /** v2: crash-safe native recording tables. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recordings` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`started_at` INTEGER NOT NULL, `state` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_points` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`recording_id` INTEGER NOT NULL, `seq` INTEGER NOT NULL, " +
                        "`segment` INTEGER NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, " +
                        "`alt` REAL, `time_ms` INTEGER NOT NULL, `speed` REAL NOT NULL, " +
                        "`bearing` REAL, `hr` REAL, `cad` REAL, `power` REAL)",
                )
            }
        }
    }
}
