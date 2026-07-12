package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import cat.hudpro.opentracks.viewer.hud.HudCatalog
import cat.hudpro.opentracks.viewer.hud.HudData
import cat.hudpro.opentracks.viewer.hud.HudDesignerCanvas
import cat.hudpro.opentracks.viewer.hud.HudLayout
import cat.hudpro.opentracks.viewer.hud.HudLayoutStore
import kotlin.time.Duration.Companion.minutes

private val SAMPLE = HudData(
    metrics = cat.hudpro.opentracks.viewer.hud.LiveMetrics(
        speedKmh = 24.6, avgMovingSpeedKmh = 19.3, maxSpeedKmh = 41.2, distanceKm = 32.48,
        totalTime = 98.minutes, movingTime = 91.minutes, paceMinPerKm = 3.9, bearingDeg = 271.0,
        elevationGainM = 842.0, altitudeM = 1240.0, slopePercent = 6.4, remainingDistanceKm = 8.2,
        isRecording = true,
    ),
    speedSeries = listOf(12f, 15f, 18f, 22f, 20f, 24f, 27f, 25f, 23f, 26f, 28f, 24f),
    elevationProfile = listOf(800f, 840f, 910f, 1010f, 1180f, 1240f, 1200f, 1300f, 1360f),
    routeProgress = 0.55f,
)

@Composable
fun HudDesignerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ViewerPreferences.get(context) }
    var layout by remember { mutableStateOf(HudLayoutStore.load(prefs)) }
    var selected by remember { mutableStateOf<Int?>(null) }

    DetailScaffold(
        title = "Diseñar HUD",
        onBack = onBack,
        actions = {
            IconButton(onClick = { HudLayoutStore.save(prefs, layout) }) {
                Icon(Icons.Filled.Save, contentDescription = "Guardar")
            }
        },
    ) { modifier ->
        Column(modifier.fillMaxSize()) {
            // Live preview: real map + draggable HUD (fixed height so the scrollable controls below
            // can't starve it to zero height).
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                MapPreview(Modifier.fillMaxSize())
                HudDesignerCanvas(
                    layout = layout,
                    data = SAMPLE,
                    selectedIndex = selected,
                    onSelect = { selected = it },
                    onRemove = { i ->
                        layout = layout.copy(widgets = layout.widgets.toMutableList().also { it.removeAt(i) })
                        selected = null
                    },
                )
            }

            // Controls: presets, scale, palette. Scrollable so it never starves the preview.
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Presets:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    HudLayout.PRESETS.forEach { (name, preset) ->
                        AssistChip(onClick = { layout = preset; selected = null }, label = { Text(name) })
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Mida", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = layout.scale,
                        onValueChange = { layout = layout.copy(scale = it) },
                        valueRange = 0.7f..1.4f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                }
                // Zone picker for the selected widget (reliable placement, no drag).
                val sel = selected
                if (sel != null && sel in layout.widgets.indices) {
                    val current = layout.widgets[sel].zone
                    Text("Mou «${layout.widgets[sel].element?.label ?: ""}» a una zona:",
                        style = MaterialTheme.typography.labelLarge)
                    ZonePicker(current) { zone -> layout = layout.moveToZone(sel, zone) }
                } else {
                    Text("Toca un widget per seleccionar-lo i moure'l a una zona.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                Text("Afegir / treure widgets", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudCatalog.elements.forEach { element ->
                        val placed = layout.contains(element.id)
                        FilterChip(
                            selected = placed,
                            onClick = {
                                layout = if (placed) layout.remove(element.id) else layout.add(element.id)
                                selected = null
                            },
                            label = { Text(element.label) },
                        )
                    }
                }

                Text(
                    "L'aparença del track, la ruta a seguir, l'àudio i les unitats es configuren a Ajustos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** 3×3 grid of zone buttons (center-center is inactive) for placing the selected widget. */
@Composable
private fun ZonePicker(current: cat.hudpro.opentracks.viewer.hud.HudZone, onPick: (cat.hudpro.opentracks.viewer.hud.HudZone) -> Unit) {
    val grid = listOf(
        listOf(cat.hudpro.opentracks.viewer.hud.HudZone.TOP_LEFT, cat.hudpro.opentracks.viewer.hud.HudZone.TOP_CENTER, cat.hudpro.opentracks.viewer.hud.HudZone.TOP_RIGHT),
        listOf(cat.hudpro.opentracks.viewer.hud.HudZone.MIDDLE_LEFT, null, cat.hudpro.opentracks.viewer.hud.HudZone.MIDDLE_RIGHT),
        listOf(cat.hudpro.opentracks.viewer.hud.HudZone.BOTTOM_LEFT, cat.hudpro.opentracks.viewer.hud.HudZone.BOTTOM_CENTER, cat.hudpro.opentracks.viewer.hud.HudZone.BOTTOM_RIGHT),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        grid.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { zone ->
                    if (zone == null) {
                        Box(Modifier.size(56.dp))
                    } else {
                        val isSel = zone == current
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onPick(zone) },
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            Text(
                                zone.label,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

