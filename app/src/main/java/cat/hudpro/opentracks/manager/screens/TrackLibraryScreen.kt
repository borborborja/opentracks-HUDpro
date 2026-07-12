package cat.hudpro.opentracks.manager.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.hudpro.opentracks.HudProApplication
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import cat.hudpro.opentracks.data.tracks.FollowTrackEntity
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackLibraryScreen(
    onBack: () -> Unit,
    onCreateRoute: () -> Unit = {},
    onDownloadRouteMap: (cat.hudpro.opentracks.data.map.BoundingBox) -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember { HudProApplication.from(context) }
    val prefs = remember { ViewerPreferences.get(context) }
    val store = remember { cat.hudpro.opentracks.data.map.OfflineMapStore.get(context) }
    val scope = rememberCoroutineScope()

    val summariesFlow = remember { app.trackRepository.observeSummaries() }
    val tracks by summariesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var activeId by remember { mutableLongStateOf(prefs.activeFollowTrackId) }

    // Compute offline-map coverage per route (points-in-bbox against the downloaded maps).
    var coverage by remember { mutableStateOf<Map<Long, cat.hudpro.opentracks.data.map.RouteCoverage>>(emptyMap()) }
    androidx.compose.runtime.LaunchedEffect(tracks) {
        val maps = store.list()
        coverage = tracks.associate { t ->
            t.id to cat.hudpro.opentracks.data.map.RouteCoverageCalculator.coverage(
                app.trackRepository.loadRoute(t.id), maps,
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                app.trackRepository.importGpx(uri, fallbackName = "Ruta importada")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracks a seguir") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Enrere") }
                },
            )
        },
        floatingActionButton = {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.SmallFloatingActionButton(onClick = onCreateRoute) {
                    Icon(Icons.Filled.Edit, contentDescription = "Crear ruta")
                }
                ExtendedFloatingActionButton(
                    onClick = { importLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*")) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Importar GPX") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (tracks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Encara no hi ha rutes. Importa un GPX o sincronitza amb Endurain.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isActive = activeId == track.id,
                            cov = coverage[track.id],
                            onFollow = { activeId = track.id; prefs.activeFollowTrackId = track.id },
                            onDelete = { scope.launch { app.trackRepository.delete(track.id) } },
                            onDownloadMap = {
                                scope.launch {
                                    app.trackRepository.routeBoundingBox(track.id)?.let { onDownloadRouteMap(it) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: FollowTrackEntity,
    isActive: Boolean,
    cov: cat.hudpro.opentracks.data.map.RouteCoverage?,
    onFollow: () -> Unit,
    onDelete: () -> Unit,
    onDownloadMap: () -> Unit,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isActive, onClick = onFollow)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(track.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        String.format(Locale.US, "%.1f km · %d punts · %s", track.distanceMeters / 1000.0, track.pointCount, track.source.name),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onDownloadMap) {
                    Icon(Icons.Filled.Download, contentDescription = "Descarregar mapa de la ruta")
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Esborrar") }
            }
            CoverageBadge(cov)
        }
    }
}

@Composable
private fun CoverageBadge(cov: cat.hudpro.opentracks.data.map.RouteCoverage?) {
    val status = cov?.status ?: cat.hudpro.opentracks.data.map.CoverageStatus.NONE
    val (color, label) = when (status) {
        cat.hudpro.opentracks.data.map.CoverageStatus.COVERED -> Color(0xFF2A9D8F) to "Coberta offline"
        cat.hudpro.opentracks.data.map.CoverageStatus.PARTIAL -> Color(0xFFF4A261) to
            "Parcial (${((cov?.coveredFraction ?: 0f) * 100).toInt()}%)"
        cat.hudpro.opentracks.data.map.CoverageStatus.NONE -> Color(0xFF9AA5AD) to "Sense mapa offline"
    }
    Row(
        Modifier.padding(start = 48.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
        Text(
            "  $label" + (cov?.coveringMaps?.takeIf { it.isNotEmpty() }?.let { " · " + it.joinToString(", ") } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
