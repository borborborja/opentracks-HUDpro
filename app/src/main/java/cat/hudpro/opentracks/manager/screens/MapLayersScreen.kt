package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cat.hudpro.opentracks.data.map.MapSource
import cat.hudpro.opentracks.data.prefs.ViewerPreferences

@Composable
fun MapLayersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ViewerPreferences.get(context) }
    var selectedId by remember { mutableStateOf(MapSource.byId(prefs.baseMapId).id) }

    DetailScaffold(title = "Capas de mapa", onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Mapa base per al visor", style = MaterialTheme.typography.titleSmall) }
            items(MapSource.entries) { source ->
                Card {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedId == source.id,
                                onClick = {
                                    selectedId = source.id
                                    prefs.baseMapId = source.id
                                },
                            )
                            .padding(16.dp),
                    ) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedId == source.id, onClick = null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(source.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(source.attribution, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "L'offline (MBTiles OSM/ICGC) es gestiona a «Mapes offline» (properament).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
