package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import cat.hudpro.opentracks.viewer.data.DataLayout
import cat.hudpro.opentracks.viewer.data.DataLayoutStore
import cat.hudpro.opentracks.viewer.hud.HudMetric
import cat.hudpro.opentracks.viewer.hud.LiveMetrics
import cat.hudpro.opentracks.viewer.hud.Units
import cat.hudpro.opentracks.viewer.hud.UnitsStore
import kotlin.time.Duration.Companion.minutes

private val SAMPLE_METRICS = LiveMetrics(
    speedKmh = 24.6, avgMovingSpeedKmh = 19.3, maxSpeedKmh = 41.2, distanceKm = 32.48,
    totalTime = 98.minutes, movingTime = 91.minutes, paceMinPerKm = 3.9, bearingDeg = 271.0,
    elevationGainM = 842.0, altitudeM = 1240.0, slopePercent = 6.4, vamMeterPerHour = 780.0,
    heartRateBpm = 148.0, cadenceRpm = 86.0, powerW = 213.0, remainingDistanceKm = 8.2,
    isRecording = true,
)

/**
 * Interactive editor for the "Dades" grid, Android-tiles style: the grid IS the editor —
 * long-press & drag a tile to reorder, tap to select, then change its width (1x/2x/3x) or remove it.
 */
@Composable
fun DataDesignerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ViewerPreferences.get(context) }
    val units = remember { UnitsStore.load(prefs) }
    var layout by remember { mutableStateOf(DataLayoutStore.load(prefs)) }
    var selected by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf<String?>(null) }

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
            Text(
                "Mantén premut i arrossega per reordenar · toca per seleccionar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            TileGrid(
                layout = layout,
                units = units,
                selected = selected,
                dragging = dragging,
                onSelect = { selected = if (selected == it) null else it },
                onDragState = { dragging = it },
                onReorder = { from, to -> layout = layout.moveTo(from, to) },
            )

            // Selected-tile actions: width + remove.
            val sel = selected?.takeIf { layout.contains(it) }
            if (sel != null) {
                val metric = runCatching { HudMetric.valueOf(sel) }.getOrNull()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("«${metric?.label ?: sel}»", style = MaterialTheme.typography.labelLarge)
                    Text("Amplada:", style = MaterialTheme.typography.bodySmall)
                    (1..layout.columns).forEach { n ->
                        FilterChip(
                            selected = layout.spanOf(sel) == n,
                            onClick = { layout = layout.setSpan(sel, n) },
                            label = { Text("${n}x") },
                        )
                    }
                    IconButton(onClick = { layout = layout.remove(sel); selected = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Treure", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Text(
                    "Toca una casella per canviar-ne l'amplada o treure-la.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

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
                Spacer(Modifier.weight(1f))
                Text("Rellotge", style = MaterialTheme.typography.labelLarge)
                Switch(checked = layout.showClock, onCheckedChange = { layout = layout.copy(showClock = it) })
            }

            // Available metrics to add.
            val available = HudMetric.entries.filter { !layout.contains(it.name) }
            if (available.isNotEmpty()) {
                Text("Afegir mètriques", style = MaterialTheme.typography.labelLarge)
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

/**
 * The editable grid. Rows are packed by span (like the real Dades view). Every tile reports its
 * bounds; a long-press drag live-reorders by dropping the dragged tile onto whichever tile the
 * finger is over (launcher-style).
 */
@Composable
private fun TileGrid(
    layout: DataLayout,
    units: Units,
    selected: String?,
    dragging: String?,
    onSelect: (String) -> Unit,
    onDragState: (String?) -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    val currentLayout by rememberUpdatedState(layout)
    bounds.keys.retainAll(layout.fields.toSet())

    // Pack fields into rows of `columns` capacity using each tile's span.
    val rows = remember(layout) {
        val out = mutableListOf<MutableList<String>>()
        var used = 0
        for (f in layout.fields) {
            val span = layout.spanOf(f)
            if (out.isEmpty() || used + span > layout.columns) {
                out.add(mutableListOf(f)); used = span
            } else {
                out.last().add(f); used += span
            }
        }
        out
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { field ->
                    key(field) {
                        EditableTile(
                            field = field,
                            units = units,
                            isSelected = selected == field,
                            isDragging = dragging == field,
                            weight = layout.spanOf(field).toFloat(),
                            onSelect = { onSelect(field) },
                            onBounds = { bounds[field] = it },
                            onDragStart = { onDragState(field) },
                            onDragEnd = { onDragState(null) },
                            onDragTo = { pointer ->
                                val target = bounds.entries
                                    .firstOrNull { it.key != field && it.value.contains(pointer) }?.key
                                if (target != null) {
                                    val from = currentLayout.fields.indexOf(field)
                                    val to = currentLayout.fields.indexOf(target)
                                    if (from >= 0 && to >= 0) onReorder(from, to)
                                }
                            },
                            modifier = Modifier.weight(layout.spanOf(field).toFloat()),
                        )
                    }
                }
                // Fill the leftover columns so tiles keep their true width.
                val usedSpan = row.sumOf { layout.spanOf(it) }
                if (usedSpan < layout.columns) Spacer(Modifier.weight((layout.columns - usedSpan).toFloat()))
            }
        }
        if (layout.fields.isEmpty()) {
            Text("Cap mètrica. Afegeix-ne a sota.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EditableTile(
    field: String,
    units: Units,
    isSelected: Boolean,
    isDragging: Boolean,
    weight: Float,
    onSelect: () -> Unit,
    onBounds: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragTo: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metric = runCatching { HudMetric.valueOf(field) }.getOrNull()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft; onBounds(it.boundsInRoot()) }
            .alpha(if (isDragging) 0.45f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, _ ->
                        change.consume()
                        onDragTo(origin + change.position)
                    },
                )
            }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (metric?.label ?: field).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Icon(
                    Icons.Filled.UnfoldMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.height(14.dp),
                )
            }
        }
        Text(
            "${metric?.value(SAMPLE_METRICS, units) ?: "—"} ${metric?.unit(units) ?: ""}".trim(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
