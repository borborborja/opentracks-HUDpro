package cat.hudpro.opentracks

import android.app.Application
import android.content.Context
import androidx.room.Room
import cat.hudpro.opentracks.data.endurain.EndurainRepository
import cat.hudpro.opentracks.data.prefs.EndurainPreferences
import cat.hudpro.opentracks.data.tracks.HudProDatabase
import cat.hudpro.opentracks.data.tracks.TrackRepository
import org.maplibre.android.MapLibre

/**
 * Application + lightweight service locator. Avoids a DI framework to keep the build simple;
 * singletons are created lazily and shared across Activities.
 */
class HudProApplication : Application() {

    val database: HudProDatabase by lazy {
        Room.databaseBuilder(this, HudProDatabase::class.java, "hudpro.db").build()
    }
    val trackRepository: TrackRepository by lazy {
        TrackRepository(database.followTrackDao(), contentResolver)
    }
    val endurainRepository: EndurainRepository by lazy {
        EndurainRepository(EndurainPreferences.get(this))
    }
    val routingRepository: cat.hudpro.opentracks.data.routing.RoutingRepository by lazy {
        cat.hudpro.opentracks.data.routing.RoutingRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        // MapLibre requires a one-time init before any MapView is created.
        MapLibre.getInstance(this)
    }

    companion object {
        fun from(context: Context): HudProApplication =
            context.applicationContext as HudProApplication
    }
}
