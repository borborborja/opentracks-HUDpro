package cat.hudpro.opentracks

import android.app.Application
import org.maplibre.android.MapLibre

class HudProApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // MapLibre requires a one-time init before any MapView is created.
        // No API key needed for OSM / ICGC raster + ICGC vector styles.
        MapLibre.getInstance(this)
    }
}
