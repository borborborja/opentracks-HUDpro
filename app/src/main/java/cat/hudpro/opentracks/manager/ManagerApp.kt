package cat.hudpro.opentracks.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class ManagerEntry(val title: String, val subtitle: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerApp(onOpenViewer: () -> Unit) {
    val entries = listOf(
        ManagerEntry("Abrir visor", "Mapa + HUD en vivo", Icons.Filled.Map),
        ManagerEntry("Diseñar HUD", "Widgets, disposición y presets", Icons.Filled.Dashboard),
        ManagerEntry("Capas de mapa", "OSM · ICGC (online/offline)", Icons.Filled.Layers),
        ManagerEntry("Tracks a seguir", "Importar GPX y organizar", Icons.AutoMirrored.Filled.DirectionsRun),
        ManagerEntry("Endurain", "Subir grabados y sincronizar", Icons.Filled.CloudUpload),
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("HUD Pro") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries) { entry ->
                Card(onClick = { if (entry.title == "Abrir visor") onOpenViewer() }) {
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        supportingContent = { Text(entry.subtitle) },
                        leadingContent = { Icon(entry.icon, contentDescription = null) },
                    )
                }
            }
            item {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(
                        "Fork de OSMDashboard · Mapas ICGC · Endurain",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
