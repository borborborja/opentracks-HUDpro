package cat.hudpro.opentracks.viewer.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Editable HUD overlay for the designer. Renders exactly like the viewer (zone-stacked, no overlap),
 * but each widget is tappable to select it (yellow border + ✕ to remove). Moving a widget between
 * zones is done from the zone picker in the screen — reliable on any touchscreen (no fragile drag).
 */
@Composable
fun HudDesignerCanvas(
    layout: HudLayout,
    data: HudData,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .padding(12.dp)
            .noRippleClickable { onSelect(null) },
    ) {
        HudZone.entries.forEach { zone ->
            val items = layout.widgets.withIndex().filter { it.value.zone == zone }
            if (items.isEmpty()) return@forEach
            val content: @Composable () -> Unit = {
                items.forEach { (index, widget) ->
                    val element = widget.element ?: return@forEach
                    DesignerWidget(
                        selected = selectedIndex == index,
                        onSelect = { onSelect(index) },
                        onRemove = { onRemove(index) },
                    ) {
                        HudWidgetContent(element, data, HudControls.disabled, layout.scale * widget.scale)
                    }
                }
            }
            val zoneModifier = Modifier.align(zoneAlignment(zone))
                .then(if (zone.isTop) Modifier.padding(top = SWITCHER_BAND) else Modifier)
            Box(zoneModifier) {
                if (zone.isCenter) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
                }
            }
        }
    }
}

@Composable
private fun DesignerWidget(
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box {
        val deco = if (selected) {
            Modifier.clip(RoundedCornerShape(16.dp)).border(2.dp, Color(0xFFFFD166), RoundedCornerShape(16.dp))
        } else {
            Modifier
        }
        Box(deco.noRippleClickable(onSelect)) { content() }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE63946))
                    .noRippleClickable(onRemove),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, contentDescription = "Treure", tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
