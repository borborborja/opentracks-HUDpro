package cat.hudpro.opentracks.viewer.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Transparent, non-interactive HUD drawn on top of the map. Widgets are grouped by [HudAnchor]
 * and rendered as translucent "pro" tiles. Values update live from [metrics].
 */
@Composable
fun HudOverlay(metrics: LiveMetrics, layout: HudLayout, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        AnchorGroup(HudAnchor.TOP_LEFT, layout, metrics, Alignment.TopStart)
        AnchorGroup(HudAnchor.TOP_CENTER, layout, metrics, Alignment.TopCenter)
        AnchorGroup(HudAnchor.TOP_RIGHT, layout, metrics, Alignment.TopEnd)
        AnchorGroup(HudAnchor.BOTTOM_LEFT, layout, metrics, Alignment.BottomStart)
        AnchorGroup(HudAnchor.BOTTOM_CENTER, layout, metrics, Alignment.BottomCenter)
        AnchorGroup(HudAnchor.BOTTOM_RIGHT, layout, metrics, Alignment.BottomEnd)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.AnchorGroup(
    anchor: HudAnchor,
    layout: HudLayout,
    metrics: LiveMetrics,
    alignment: Alignment,
) {
    val items = layout.byAnchor(anchor)
    if (items.isEmpty()) return
    val horizontal = anchor == HudAnchor.TOP_CENTER || anchor == HudAnchor.BOTTOM_CENTER
    val content: @Composable () -> Unit = {
        items.forEach { metric -> HudTile(metric, metrics, layout.scale) }
    }
    Box(Modifier.align(alignment)) {
        if (horizontal) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

@Composable
private fun HudTile(metric: HudMetric, metrics: LiveMetrics, scale: Float) {
    Column(
        modifier = Modifier
            .width((116 * scale).dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xB0000000))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        androidx.compose.material3.Text(
            text = metric.label.uppercase(),
            color = Color(0xFFB8C4CE),
            fontSize = (11 * scale).sp,
            fontWeight = FontWeight.Medium,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            androidx.compose.material3.Text(
                text = metric.value(metrics),
                color = Color.White,
                fontSize = (30 * scale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
            )
            if (metric.unit.isNotEmpty()) {
                androidx.compose.material3.Text(
                    text = " ${metric.unit}",
                    color = Color(0xFFB8C4CE),
                    fontSize = (13 * scale).sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}
