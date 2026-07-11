package cat.hudpro.opentracks.viewer

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import cat.hudpro.opentracks.data.map.MapSource
import cat.hudpro.opentracks.data.opentracks.DashboardReader
import cat.hudpro.opentracks.data.opentracks.isDashboardAction
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapView

/**
 * The map viewer launched by OpenTracks via its Dashboard API (action `Intent.OpenTracks-Dashboard`),
 * and also usable standalone. Renders the live track over an OSM/ICGC base map with the HUD overlay.
 */
class MapViewerActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private var controller: MapLibreController? = null
    private var reader: DashboardReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.isDashboardAction()) {
            reader = runCatching { DashboardReader(intent, contentResolver) }
                .onFailure { Log.e("MapViewerActivity", "Failed to init DashboardReader", it) }
                .getOrNull()
        }
        applyWindowFlags()

        mapView = MapView(this)
        setContentView(mapView)
        mapView.onCreate(savedInstanceState)

        val source = MapSource.byId(ViewerPreferences.get(this).baseMapId)
        mapView.getMapAsync { map ->
            val ctrl = MapLibreController(map)
            controller = ctrl
            ctrl.setBaseMap(source) {
                reader?.let { observe(it, ctrl) }
            }
        }

        reader?.start()
    }

    private fun observe(reader: DashboardReader, ctrl: MapLibreController) {
        lifecycleScope.launch {
            reader.segments.combine(reader.waypoints) { segs, wps -> segs to wps }
                .collect { (segs, wps) ->
                    ctrl.updateTrack(segs, frame = true)
                    ctrl.updateWaypoints(wps)
                    if (reader.isRecording) ctrl.follow(segs)
                }
        }
    }

    private fun applyWindowFlags() {
        val r = reader ?: return
        if (r.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (r.showOnLockScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        if (r.showFullscreen) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    override fun onDestroy() {
        reader?.stop()
        mapView.onDestroy()
        super.onDestroy()
    }
}
