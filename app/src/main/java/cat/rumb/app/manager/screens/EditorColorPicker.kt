package cat.rumb.app.manager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Swatch palette shared by the widget/tile value-colour and outline pickers; null = default/none. */
val EDITOR_COLOR_PALETTE: List<String?> =
    listOf(null, "#FFD166", "#E63946", "#2A9D8F", "#3A86FF", "#F4A261", "#9B5DE5")

/**
 * A row of colour swatches for the HUD/Dades editors. [current] is the selected hex; null selects the
 * default/"none" swatch, drawn with [noneSwatch]. Tapping a swatch calls [onPick] with its hex (null
 * for the default). Extracted so the value-colour and the new outline-colour pickers share one look.
 */
@Composable
fun ColorPickerRow(current: String?, noneSwatch: Color, onPick: (String?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EDITOR_COLOR_PALETTE.forEach { hex ->
            val selected = current == hex || (hex == null && current == null)
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (hex == null) noneSwatch else Color(android.graphics.Color.parseColor(hex)))
                    .border(
                        if (selected) 3.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                        CircleShape,
                    )
                    .clickable { onPick(hex) },
            )
        }
    }
}
