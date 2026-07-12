package cat.hudpro.opentracks.manager.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cat.hudpro.opentracks.data.map.MapSource
import cat.hudpro.opentracks.data.map.OfflineMap
import cat.hudpro.opentracks.data.map.OfflineMapStore
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import java.io.File
import java.util.Locale

@Composable
fun MapLayersScreen(
    onBack: () -> Unit,
    onDownloadArea: () -> Unit = {},
    onOpenSectors: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { ViewerPreferences.get(context) }
    val store = remember { OfflineMapStore.get(context) }
    var tab by remember { mutableIntStateOf(0) }
    var baseMapId by remember { mutableStateOf(prefs.baseMapId) }

    DetailScaffold(title = "Capas de mapa", onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Online") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Offline") })
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (tab == 0) {
                    OnlineTab(current = baseMapId) { id -> baseMapId = id; prefs.baseMapId = id }
                } else {
                    OfflineTab(
                        store = store,
                        current = baseMapId,
                        onUse = { id -> baseMapId = id; prefs.baseMapId = id },
                        onDownloadArea = onDownloadArea,
                        onOpenSectors = onOpenSectors,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineTab(current: String?, onSelect: (String) -> Unit) {
    Text("Mapa base per al visor", style = MaterialTheme.typography.titleSmall)
    MapSource.entries.forEach { source ->
        Card {
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = current == source.id, onClick = { onSelect(source.id) })
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == source.id, onClick = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(source.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(source.attribution, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OfflineTab(
    store: OfflineMapStore,
    current: String?,
    onUse: (String) -> Unit,
    onDownloadArea: () -> Unit,
    onOpenSectors: (String) -> Unit,
) {
    val context = LocalContext.current
    var maps by remember { mutableStateOf(store.list()) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "mapa"
            runCatching { store.import(context.contentResolver, uri, name) }
            maps = store.list()
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onDownloadArea, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Map, contentDescription = null)
            Text("  Descarregar àrea")
        }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Text("  Importar")
        }
    }

    if (maps.isEmpty()) {
        Text(
            "Encara no hi ha mapes offline. Descarrega una àrea o importa un fitxer .mbtiles " +
                "(p. ex. el topogràfic o l'ortofoto oficials de l'ICGC des de visors.icgc.cat/appdownloads).",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }

    maps.forEach { map ->
        OfflineRow(
            map = map,
            sectorCount = store.sectorsOf(map).size,
            isActive = current == map.selectionId,
            onUse = { onUse(map.selectionId) },
            onManage = { onOpenSectors(map.path) },
            onDelete = { store.delete(map); maps = store.list() },
        )
    }
}

@Composable
private fun OfflineRow(
    map: OfflineMap,
    sectorCount: Int,
    isActive: Boolean,
    onUse: () -> Unit,
    onManage: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isActive, onClick = onUse)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(map.name, style = MaterialTheme.typography.bodyLarge)
                    val mb = File(map.path).length() / (1024.0 * 1024.0)
                    Text(
                        String.format(Locale.US, "%.1f MB · %d sector%s", mb, sectorCount, if (sectorCount == 1) "" else "s"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Esborrar") }
            }
            OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Gestionar sectors")
            }
        }
    }
}
