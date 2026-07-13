package cat.hudpro.opentracks.manager.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NearestFieldTest {

    private val bounds = mapOf(
        "A" to Rect(0f, 0f, 100f, 100f),
        "B" to Rect(110f, 0f, 210f, 100f),
        "C" to Rect(0f, 110f, 100f, 210f),
    )

    @Test
    fun directHitWins() {
        assertThat(nearestField(bounds, Offset(50f, 50f), exclude = "B")).isEqualTo("A")
    }

    @Test
    fun excludedTileFallsBackToRowNeighbor() {
        // Pointer over A, but A excluded (it's the dragged tile): same-row neighbor B is the target.
        assertThat(nearestField(bounds, Offset(50f, 50f), exclude = "A")).isEqualTo("B")
    }

    @Test
    fun gapFallsBackToNearestInRow() {
        // Pointer in the horizontal gap between A and B (x=105): nearest by center X.
        assertThat(nearestField(bounds, Offset(105f, 50f), exclude = "C")).isEqualTo("A")
        assertThat(nearestField(bounds, Offset(109f, 50f), exclude = "A")).isEqualTo("B")
    }

    @Test
    fun noRowMatchReturnsNull() {
        assertThat(nearestField(bounds, Offset(50f, 500f), exclude = "A")).isNull()
    }
}
