package cat.hudpro.opentracks.manager.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cat.hudpro.opentracks.HudProApplication
import cat.hudpro.opentracks.data.gpx.GpxPoint
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import cat.hudpro.opentracks.data.tracks.FollowTrackEntity
import cat.hudpro.opentracks.data.tracks.TrackRepository
import cat.hudpro.opentracks.viewer.hud.ElevationProfile
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun RouteDetailScreen(
    trackId: Long,
    onBack: () -> Unit,
    onEditTrace: (Long) -> Unit,
    onDownloadMap: (cat.hudpro.opentracks.data.map.BoundingBox) -> Unit,
) {
    val context = LocalContext.current
    val app = remember { HudProApplication.from(context) }
    val prefs = remember { ViewerPreferences.get(context) }
    val scope = rememberCoroutineScope()
    val mapView = rememberMapViewWithLifecycle()

    var entity by remember { mutableStateOf<FollowTrackEntity?>(null) }
    var gpx by remember { mutableStateOf<List<GpxPoint>>(emptyList()) }
    var controller by remember { mutableStateOf<RouteEditorController?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    var showRename by remember { mutableStateOf(false) }
    var showCollection by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(trackId, reloadTick) {
        entity = app.trackRepository.get(trackId)
        gpx = app.trackRepository.loadGpxRoute(trackId)
    }
    // Draw the route once both the map and the data are ready.
    LaunchedEffect(controller, gpx) {
        val c = controller ?: return@LaunchedEffect
        if (gpx.size >= 2) { c.setRoute(gpx); c.frame(gpx) }
    }

    DetailScaffold(
        title = entity?.name ?: "Ruta",
        onBack = onBack,
        actions = {
            IconButton(onClick = { onEditTrace(trackId) }) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar traçat")
            }
        },
    ) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AndroidView(
                factory = {
                    mapView.getMapAsync { map ->
                        val c = RouteEditorController(map)
                        controller = c
                        c.init { }
                    }
                    mapView
                },
                modifier = Modifier.fillMaxWidth().height(260.dp),
            )

            val e = entity
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Distància", String.format(Locale.US, "%.2f km", (e?.distanceMeters ?: 0.0) / 1000.0))
                        Stat("Desnivell +", "${TrackRepository.ascent(gpx).toInt()} m")
                        Stat("Punts", "${e?.pointCount ?: gpx.size}")
                    }
                }
                Text(
                    "Font: ${e?.source?.name ?: "—"} · Col·lecció: ${e?.collection ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                )

                ElevationProfile(
                    profile = gpx.mapNotNull { it.elevation?.toFloat() },
                    progress = 1f,
                    scale = 1f,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )

                Button(onClick = {
                    prefs.activeFollowTrackId = trackId
                    Toast.makeText(context, "Ruta activa per seguir", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Seguir aquesta ruta") }

                OutlinedButton(onClick = { onEditTrace(trackId) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text("  Editar traçat")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showRename = true }, modifier = Modifier.weight(1f)) { Text("Renombrar") }
                    OutlinedButton(onClick = { showCollection = true }, modifier = Modifier.weight(1f)) { Text("Col·lecció") }
                }

                OutlinedButton(
                    onClick = {
                        scope.launch { app.trackRepository.routeBoundingBox(trackId)?.let(onDownloadMap) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Text("  Descarregar mapa de la ruta")
                }

                OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("  Esborrar", color = MaterialTheme.colorScheme.error)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showRename) {
        TextFieldDialog(
            title = "Renombrar ruta",
            initial = entity?.name ?: "",
            onConfirm = { newName ->
                scope.launch { app.trackRepository.rename(trackId, newName); reloadTick++ }
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }
    if (showCollection) {
        TextFieldDialog(
            title = "Col·lecció",
            initial = entity?.collection ?: "General",
            onConfirm = { c ->
                scope.launch { app.trackRepository.setCollection(trackId, c); reloadTick++ }
                showCollection = false
            },
            onDismiss = { showCollection = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Esborrar ruta") },
            text = { Text("Segur que vols esborrar «${entity?.name ?: ""}»?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.trackRepository.delete(trackId); onBack() }
                }) { Text("Esborrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel·lar") } },
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

@Composable
private fun TextFieldDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel·lar") } },
    )
}
