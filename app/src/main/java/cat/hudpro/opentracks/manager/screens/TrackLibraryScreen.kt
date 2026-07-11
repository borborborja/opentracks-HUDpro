package cat.hudpro.opentracks.manager.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun TrackLibraryScreen(onBack: () -> Unit, onCreateRoute: () -> Unit = {}) {
    val context = LocalContext.current
    val app = remember { HudProApplication.from(context) }
    val prefs = remember { ViewerPreferences.get(context) }
    val scope = rememberCoroutineScope()

    val summariesFlow = remember { app.trackRepository.observeSummaries() }
    val tracks by summariesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var activeId by remember { mutableLongStateOf(prefs.activeFollowTrackId) }

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
                            onFollow = { activeId = track.id; prefs.activeFollowTrackId = track.id },
                            onDelete = { scope.launch { app.trackRepository.delete(track.id) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: FollowTrackEntity, isActive: Boolean, onFollow: () -> Unit, onDelete: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = isActive, onClick = onFollow)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(track.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    String.format(Locale.US, "%.1f km · %d punts · %s", track.distanceMeters / 1000.0, track.pointCount, track.source.name),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Esborrar") }
        }
    }
}
