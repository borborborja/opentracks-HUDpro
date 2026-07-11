package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import cat.hudpro.opentracks.viewer.hud.HudAnchor
import cat.hudpro.opentracks.viewer.hud.HudLayout
import cat.hudpro.opentracks.viewer.hud.HudLayoutStore
import cat.hudpro.opentracks.viewer.hud.HudMetric
import cat.hudpro.opentracks.viewer.hud.HudOverlay
import cat.hudpro.opentracks.viewer.hud.HudWidget
import cat.hudpro.opentracks.viewer.hud.LiveMetrics
import kotlin.time.Duration.Companion.minutes

private val SAMPLE = LiveMetrics(
    speedKmh = 24.6, avgMovingSpeedKmh = 19.3, maxSpeedKmh = 41.2, distanceKm = 32.48,
    totalTime = 98.minutes, movingTime = 91.minutes, paceMinPerKm = 3.9, bearingDeg = 271.0,
    elevationGainM = 842.0, altitudeM = 1240.0, slopePercent = 6.4, remainingDistanceKm = 8.2,
    isRecording = true,
)

@Composable
fun HudDesignerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ViewerPreferences.get(context) }
    var layout by remember { mutableStateOf(HudLayoutStore.load(prefs)) }

    DetailScaffold(
        title = "Diseñar HUD",
        onBack = onBack,
        actions = {
            IconButton(onClick = { HudLayoutStore.save(prefs, layout) }) {
                Icon(Icons.Filled.Save, contentDescription = "Guardar")
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // Live preview over a faux map background
                Card {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF37474F)),
                    ) {
                        HudOverlay(SAMPLE, layout)
                    }
                }
            }
            item {
                Text("Presets", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudLayout.PRESETS.forEach { (name, preset) ->
                        AssistChip(onClick = { layout = preset }, label = { Text(name) })
                    }
                }
            }
            item { Text("Mètriques i posició", style = MaterialTheme.typography.titleSmall) }
            items(HudMetric.entries) { metric ->
                MetricRow(
                    metric = metric,
                    layout = layout,
                    onChange = { layout = it },
                )
            }
        }
    }
}

@Composable
private fun MetricRow(metric: HudMetric, layout: HudLayout, onChange: (HudLayout) -> Unit) {
    val current = layout.widgets.firstOrNull { it.metric == metric }?.anchor
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("${metric.label}${if (metric.unit.isNotEmpty()) " (${metric.unit})" else ""}",
                style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = current == null,
                    onClick = { onChange(layout.copy(widgets = layout.widgets.filterNot { it.metric == metric })) },
                    label = { Text("Off") },
                )
                HudAnchor.entries.forEach { anchor ->
                    FilterChip(
                        selected = current == anchor,
                        onClick = {
                            val others = layout.widgets.filterNot { it.metric == metric }
                            onChange(layout.copy(widgets = others + HudWidget(metric, anchor)))
                        },
                        label = { Text(anchor.shortLabel()) },
                    )
                }
            }
        }
    }
}

private fun HudAnchor.shortLabel() = when (this) {
    HudAnchor.TOP_LEFT -> "↖"
    HudAnchor.TOP_CENTER -> "↑"
    HudAnchor.TOP_RIGHT -> "↗"
    HudAnchor.BOTTOM_LEFT -> "↙"
    HudAnchor.BOTTOM_CENTER -> "↓"
    HudAnchor.BOTTOM_RIGHT -> "↘"
}
