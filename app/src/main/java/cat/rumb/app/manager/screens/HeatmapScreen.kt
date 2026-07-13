package cat.rumb.app.manager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cat.rumb.app.R
import cat.rumb.app.RumbApplication
import cat.rumb.app.data.map.MapSource
import cat.rumb.app.data.map.MapStyleFactory
import cat.rumb.app.data.tracks.PolylineSimplifier
import cat.rumb.app.data.tracks.TrackKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Personal heatmap: every training (optionally routes too) drawn as a translucent line on a full
 * screen raster map — overlap accumulates visually into the "heat".
 */
@Composable
fun HeatmapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember { RumbApplication.from(context) }
    val mapView = rememberMapViewWithLifecycle()

    var controller by remember { mutableStateOf<HeatmapController?>(null) }
    var includeRoutes by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(controller, includeRoutes) {
        val c = controller ?: return@LaunchedEffect
        progress = 0 to 0
        val summaries = app.trackRepository.observeSummaries().first()
        val tracks = summaries.filter {
            it.kind == TrackKind.TRAINING || (includeRoutes && it.kind == TrackKind.ROUTE)
        }
        val features = mutableListOf<Feature>()
        val allPoints = mutableListOf<LatLng>()
        tracks.forEachIndexed { i, e ->
            val simplified = withContext(Dispatchers.Default) {
                val pts = app.trackRepository.loadRoute(e.id)
                PolylineSimplifier.simplify(pts, epsilonMeters = 50.0, maxPoints = 500)
            }
            if (simplified.size >= 2) {
                features.add(
                    Feature.fromGeometry(
                        LineString.fromLngLats(simplified.map { Point.fromLngLat(it.longitude, it.latitude) }),
                    ),
                )
                simplified.forEach { allPoints.add(LatLng(it.latitude, it.longitude)) }
            }
            progress = (i + 1) to tracks.size
            if ((i + 1) % 10 == 0) c.setFeatures(features)
        }
        c.setFeatures(features)
        c.frame(allPoints)
        progress = null
    }

    DetailScaffold(title = stringResource(R.string.heatmap_title), onBack = onBack) { modifier ->
        Box(modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    mapView.getMapAsync { map ->
                        val c = HeatmapController(map)
                        c.init { controller = c }
                    }
                    mapView
                },
                modifier = Modifier.fillMaxSize(),
            )
            FilterChip(
                selected = includeRoutes,
                onClick = { includeRoutes = !includeRoutes },
                label = { Text(stringResource(R.string.heatmap_include_routes)) },
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
            progress?.let { (done, total) ->
                Text(
                    stringResource(R.string.heatmap_loading, done, total),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Minimal map controller: raster base style + one translucent LineLayer for all the tracks. */
private class HeatmapController(private val map: MapLibreMap) {

    private var source: GeoJsonSource? = null

    fun init(onReady: () -> Unit) {
        map.setStyle(Style.Builder().fromJson(MapStyleFactory.rasterStyleJson(MapSource.ICGC_TOPO, desaturate = true))) { style ->
            val src = GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList()))
            style.addSource(src)
            // LineLayer only: SymbolLayers break GeoJSON rendering on raster styles (no glyphs).
            // Base map is greyscale (see desaturate above) so this red track stands out; overlap
            // of translucent lines still accumulates into the "heat".
            style.addLayer(
                LineLayer(LAYER_ID, SOURCE_ID).withProperties(
                    PropertyFactory.lineColor("#E63946"),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineOpacity(0.45f),
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineJoin("round"),
                ),
            )
            source = src
            // Center on Catalonia until the tracks arrive.
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(41.65, 1.95), 8.0))
            onReady()
        }
    }

    fun setFeatures(features: List<Feature>) {
        source?.setGeoJson(FeatureCollection.fromFeatures(features.toList()))
    }

    /** Frames the camera to the global bounds of the heatmap; no-op when there is nothing drawn. */
    fun frame(points: List<LatLng>) {
        if (points.size < 2) return
        runCatching {
            val bounds = LatLngBounds.Builder().includes(points).build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 60))
        }.onFailure {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 9.0))
        }
    }

    private companion object {
        const val SOURCE_ID = "heatmap-source"
        const val LAYER_ID = "heatmap-layer"
    }
}
