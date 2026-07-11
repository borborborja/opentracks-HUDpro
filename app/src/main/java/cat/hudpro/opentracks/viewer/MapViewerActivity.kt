package cat.hudpro.opentracks.viewer

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import cat.hudpro.opentracks.HudProApplication
import cat.hudpro.opentracks.data.endurain.EndurainUploadWorker
import cat.hudpro.opentracks.data.gpx.Gpx
import cat.hudpro.opentracks.data.gpx.GpxPoint
import cat.hudpro.opentracks.data.map.MapSource
import cat.hudpro.opentracks.data.opentracks.DashboardReader
import cat.hudpro.opentracks.data.opentracks.isDashboardAction
import cat.hudpro.opentracks.data.opentracks.model.GeoPoint
import cat.hudpro.opentracks.data.opentracks.model.Segment
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import cat.hudpro.opentracks.viewer.follow.FollowRouteEngine
import cat.hudpro.opentracks.viewer.hud.HudLayout
import cat.hudpro.opentracks.viewer.hud.HudLayoutStore
import cat.hudpro.opentracks.viewer.hud.HudOverlay
import cat.hudpro.opentracks.viewer.hud.LiveMetrics
import cat.hudpro.opentracks.viewer.hud.MetricsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import org.maplibre.android.maps.MapView

/**
 * The map viewer launched by OpenTracks via its Dashboard API (action `Intent.OpenTracks-Dashboard`),
 * and also usable standalone. Renders the live track over an OSM/ICGC base map with the HUD overlay.
 */
class MapViewerActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private var controller: MapLibreController? = null
    private var reader: DashboardReader? = null

    private val metricsFlow = MutableStateFlow(LiveMetrics())
    private lateinit var hudLayout: HudLayout
    private var followEngine: FollowRouteEngine? = null
    private var wasRecording = false
    private var uploadedThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.isDashboardAction()) {
            reader = runCatching { DashboardReader(intent, contentResolver) }
                .onFailure { Log.e(TAG, "Failed to init DashboardReader", it) }
                .getOrNull()
        }
        applyWindowFlags()

        val prefs = ViewerPreferences.get(this)
        hudLayout = HudLayoutStore.load(prefs)

        mapView = MapView(this)
        val hud = ComposeView(this).apply {
            setContent {
                val metrics by metricsFlow.collectAsState()
                HudOverlay(metrics, hudLayout)
            }
        }
        setContentView(
            FrameLayout(this).apply {
                addView(mapView)
                addView(hud)
            },
        )
        mapView.onCreate(savedInstanceState)

        val source = MapSource.byId(prefs.baseMapId)
        wasRecording = reader?.isRecording == true
        mapView.getMapAsync { map ->
            val ctrl = MapLibreController(map)
            controller = ctrl
            ctrl.setBaseMap(source) {
                loadFollowRoute(prefs, ctrl)
                reader?.let { observe(it, ctrl) }
            }
        }

        reader?.start()
    }

    private fun loadFollowRoute(prefs: ViewerPreferences, ctrl: MapLibreController) {
        val id = prefs.activeFollowTrackId
        if (id <= 0) return
        lifecycleScope.launch {
            val route = HudProApplication.from(this@MapViewerActivity).trackRepository.loadRoute(id)
            if (route.isNotEmpty()) {
                followEngine = FollowRouteEngine(route)
                ctrl.setFollowRoute(route)
            }
        }
    }

    private fun observe(reader: DashboardReader, ctrl: MapLibreController) {
        lifecycleScope.launch {
            combine(reader.segments, reader.waypoints, reader.statistics) { segs, wps, stats ->
                Triple(segs, wps, stats)
            }.collect { (segs, wps, stats) ->
                ctrl.updateTrack(segs, frame = true)
                ctrl.updateWaypoints(wps)
                if (reader.isRecording) ctrl.follow(segs)

                var metrics = MetricsCalculator.compute(segs, stats, reader.isRecording)
                metrics = mergeFollow(metrics, segs)
                metricsFlow.value = metrics

                handleRecordingStopped(reader, segs)
            }
        }
    }

    private fun mergeFollow(metrics: LiveMetrics, segments: List<Segment>): LiveMetrics {
        val engine = followEngine ?: return metrics
        val current = segments.lastOrNull()?.lastOrNull { it.latLong != null }?.latLong ?: return metrics
        val state = engine.update(current) ?: return metrics
        return metrics.copy(
            remainingDistanceKm = state.remainingKm,
            offRouteMeters = state.offRouteMeters,
            bearingToRouteDeg = state.bearingToRouteDeg,
        )
    }

    /** When OpenTracks stops recording, auto-enqueue an Endurain upload of the reconstructed GPX. */
    private fun handleRecordingStopped(reader: DashboardReader, segments: List<Segment>) {
        val stoppedNow = wasRecording && !reader.isRecording
        wasRecording = reader.isRecording
        if (!stoppedNow || uploadedThisSession) return
        if (!cat.hudpro.opentracks.data.prefs.EndurainPreferences.get(this).isConfigured) return
        val gpx = buildGpx(segments) ?: return
        val stamp = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(java.time.Instant.now())
        EndurainUploadWorker.enqueue(this, gpx, "opentracks-$stamp.gpx")
        uploadedThisSession = true
    }

    private fun buildGpx(segments: List<Segment>): String? {
        val points = segments.flatten().filter { it.latLong != null && !it.isPause }.map {
            GpxPoint(it.latLong!!.latitude, it.latLong.longitude, elevation = null, time = it.time)
        }
        if (points.isEmpty()) return null
        return Gpx.write("OpenTracks HUD Pro", points)
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

    private companion object { const val TAG = "MapViewerActivity" }
}
