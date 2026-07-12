package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import cat.hudpro.opentracks.viewer.data.DataLayout
import cat.hudpro.opentracks.viewer.data.DataLayoutStore
import cat.hudpro.opentracks.viewer.hud.HudMetric
import cat.hudpro.opentracks.viewer.hud.LiveMetrics
import kotlin.time.Duration.Companion.minutes

private val SAMPLE_METRICS = LiveMetrics(
    speedKmh = 24.6, avgMovingSpeedKmh = 19.3, maxSpeedKmh = 41.2, distanceKm = 32.48,
    totalTime = 98.minutes, movingTime = 91.minutes, paceMinPerKm = 3.9, bearingDeg = 271.0,
    elevationGainM = 842.0, altitudeM = 1240.0, slopePercent = 6.4, vamMeterPerHour = 780.0,
    heartRateBpm = 148.0, cadenceRpm = 86.0, powerW = 213.0, remainingDistanceKm = 8.2,
    isRecording = true,
)

/**
 * Editor for the full-screen "Dades" grid: pick which metrics show, reorder them (↑/↓), choose the
 * column count and whether the live clock appears. Simple list interaction (no drag), consistent with
 * the HUD designer. Live preview at the top reflects every change.
 */
@Composable
fun DataDesignerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ViewerPreferences.get(context) }
    val units = remember { cat.hudpro.opentracks.viewer.hud.UnitsStore.load(prefs) }
    var layout by remember { mutableStateOf(DataLayoutStore.load(prefs)) }

    DetailScaffold(
        title = "Editar Dades",
        onBack = onBack,
        actions = {
            IconButton(onClick = { DataLayoutStore.save(prefs, layout) }) {
                Icon(Icons.Filled.Save, contentDescription = "Guardar")
            }
        },
    ) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Live preview of the resulting grid.
            Text("Vista prèvia", style = MaterialTheme.typography.labelLarge)
            PreviewGrid(layout, units)

            // Columns + clock.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Columnes", style = MaterialTheme.typography.labelLarge)
                listOf(2, 3).forEach { n ->
                    FilterChip(
                        selected = layout.columns == n,
                        onClick = { layout = layout.copy(columns = n) },
                        label = { Text("$n") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mostrar rellotge", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(checked = layout.showClock, onCheckedChange = { layout = layout.copy(showClock = it) })
            }

            // Selected metrics, in order.
            Text("Mètriques visibles (${layout.metrics().size})", style = MaterialTheme.typography.labelLarge)
            if (layout.metrics().isEmpty()) {
                Text("Cap. Afegeix-ne alguna a sota.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            layout.metrics().forEachIndexed { index, metric ->
                SelectedRow(
                    metric = metric,
                    units = units,
                    canUp = index > 0,
                    canDown = index < layout.metrics().size - 1,
                    onUp = { layout = layout.move(index, -1) },
                    onDown = { layout = layout.move(index, +1) },
                    onRemove = { layout = layout.remove(metric.name) },
                )
            }

            // Available metrics to add.
            val available = HudMetric.entries.filter { !layout.contains(it.name) }
            if (available.isNotEmpty()) {
                Text("Afegir mètriques", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    available.forEach { metric ->
                        FilterChip(
                            selected = false,
                            onClick = { layout = layout.add(metric.name) },
                            label = { Text("+ ${metric.label}") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedRow(
    metric: HudMetric,
    units: cat.hudpro.opentracks.viewer.hud.Units,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(metric.label, fontWeight = FontWeight.Bold)
                Text(
                    "${metric.value(SAMPLE_METRICS, units)} ${metric.unit(units)}".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onUp, enabled = canUp) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Pujar")
            }
            IconButton(onClick = onDown, enabled = canDown) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "Baixar")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Treure", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PreviewGrid(layout: DataLayout, units: cat.hudpro.opentracks.viewer.hud.Units) {
    val cells = layout.metrics().map { Triple(it.label, it.value(SAMPLE_METRICS, units), it.unit(units)) }.toMutableList()
    if (layout.showClock) cells.add(Triple("Rellotge", "12:34:56", ""))
    LazyVerticalGrid(
        columns = GridCells.Fixed(layout.columns.coerceIn(1, 3)),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(cells) { (label, value, unit) ->
            Card {
                Column(Modifier.padding(10.dp)) {
                    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "$value ${unit}".trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
