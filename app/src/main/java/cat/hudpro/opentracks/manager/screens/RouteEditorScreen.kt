package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cat.hudpro.opentracks.HudProApplication
import cat.hudpro.opentracks.data.gpx.GpxPoint
import cat.hudpro.opentracks.data.opentracks.model.GeoPoint
import cat.hudpro.opentracks.data.routing.RoutedPath
import cat.hudpro.opentracks.data.routing.RoutingProfile
import cat.hudpro.opentracks.data.tracks.TrackSource
import cat.hudpro.opentracks.viewer.hud.MetricsCalculator
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorScreen(trackId: Long? = null, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val app = remember { HudProApplication.from(context) }
    val scope = rememberCoroutineScope()
    val mapView = rememberMapViewWithLifecycle()
    val editing = trackId != null

    val waypoints = remember { mutableStateListOf<GeoPoint>() }
    var controller by remember { mutableStateOf<RouteEditorController?>(null) }
    var routed by remember { mutableStateOf<RoutedPath?>(null) }
    var snap by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf(RoutingProfile.HIKING) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Ruta nova") }
    // In edit mode the map is seeded from the stored route. `dirty` stays false until the user changes a
    // waypoint, so the original trace is preserved unless actually edited.
    var dirty by remember { mutableStateOf(false) }
    var originalPoints by remember { mutableStateOf<List<GpxPoint>>(emptyList()) }

    // Load the existing route once the map controller is ready (edit mode only).
    LaunchedEffect(controller, trackId) {
        val c = controller ?: return@LaunchedEffect
        val id = trackId ?: return@LaunchedEffect
        val gpx = app.trackRepository.loadGpxRoute(id)
        if (gpx.size < 2) return@LaunchedEffect
        originalPoints = gpx
        app.trackRepository.get(id)?.let { name = it.name }
        val simplified = cat.hudpro.opentracks.data.tracks.PolylineSimplifier
            .simplify(gpx.map { it.toGeoPoint() }, epsilonMeters = 20.0)
        waypoints.clear(); waypoints.addAll(simplified)
        routed = RoutedPath(
            gpx,
            cat.hudpro.opentracks.data.tracks.TrackRepository.routeDistance(gpx.map { it.toGeoPoint() }),
            cat.hudpro.opentracks.data.tracks.TrackRepository.ascent(gpx),
        )
        c.setWaypoints(simplified)
        c.setRoute(gpx)
        c.frame(gpx)
    }

    // Recompute the path whenever the waypoints/profile/mode change and the map is ready.
    LaunchedEffect(waypoints.size, snap, profile, controller) {
        val c = controller ?: return@LaunchedEffect
        // Don't overwrite the seeded original trace until the user actually edits it.
        if (editing && !dirty) return@LaunchedEffect
        c.setWaypoints(waypoints.toList())
        when {
            waypoints.size < 2 -> { routed = null; c.setRoute(emptyList()) }
            snap -> {
                loading = true; error = null
                routed = try {
                    app.routingRepository.route(waypoints.toList(), profile)
                } catch (e: Exception) {
                    error = e.message; straightPath(waypoints.toList())
                }
                loading = false
                c.setRoute(routed!!.points)
            }
            else -> { routed = straightPath(waypoints.toList()); c.setRoute(routed!!.points) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Editar ruta" else "Crear ruta") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Enrere") }
                },
                actions = {
                    IconButton(onClick = {
                        if (waypoints.size >= 2) { waypoints.removeAt(waypoints.lastIndex); dirty = true }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Desfer")
                    }
                    IconButton(onClick = { showSave = true }, enabled = (routed?.points?.size ?: 0) >= 2) {
                        Icon(Icons.Filled.Save, contentDescription = "Guardar")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = {
                    mapView.getMapAsync { map ->
                        val c = RouteEditorController(map)
                        controller = c
                        c.init { c.onMapClick { p -> waypoints.add(p); dirty = true } }
                    }
                    mapView
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(16.dp))
            }

            // Bottom control panel.
            Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Distància", String.format(Locale.US, "%.2f km", (routed?.distanceMeters ?: 0.0) / 1000.0))
                        Stat("Desnivell +", "${(routed?.ascentMeters ?: 0.0).toInt()} m")
                        Stat("Punts", "${waypoints.size}")
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RoutingProfile.entries.forEach { p ->
                            FilterChip(selected = profile == p, onClick = { profile = p }, label = { Text(p.label) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = snap, onCheckedChange = { snap = it })
                        Text("  Enganxar al camí", style = MaterialTheme.typography.bodyMedium)
                        error?.let {
                            Text("  · $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text("Toca el mapa per afegir punts.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text(if (editing) "Guardar canvis" else "Guardar ruta") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nom") })
            },
            confirmButton = {
                TextButton(onClick = {
                    // Edit + untouched → keep the original trace; otherwise use the (re)computed path.
                    val points = if (editing && !dirty) originalPoints
                    else routed?.points ?: straightPath(waypoints.toList()).points
                    scope.launch {
                        if (editing) {
                            app.trackRepository.updateRoute(trackId!!, name, points)
                        } else {
                            app.trackRepository.insertRoute(name, points, TrackSource.GPX_IMPORT, remoteId = null)
                        }
                        showSave = false
                        onSaved()
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel·lar") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Straight-line fallback path (no snapping): waypoints as-is, planar distance, no elevation. */
private fun straightPath(waypoints: List<GeoPoint>): RoutedPath {
    val points = waypoints.map { GpxPoint(it.latitude, it.longitude) }
    var distance = 0.0
    for (i in 1 until waypoints.size) distance += MetricsCalculator.distanceMeters(waypoints[i - 1], waypoints[i])
    return RoutedPath(points, distance, 0.0)
}
