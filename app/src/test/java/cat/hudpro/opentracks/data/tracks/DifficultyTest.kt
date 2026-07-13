package cat.hudpro.opentracks.data.tracks

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DifficultyTest {

    @Test
    fun kmEffortCombinesDistanceAndAscent() {
        assertThat(DifficultyCalculator.kmEffort(10_000.0, 0.0)).isEqualTo(10.0)
        assertThat(DifficultyCalculator.kmEffort(10_000.0, 500.0)).isEqualTo(15.0)
        assertThat(DifficultyCalculator.kmEffort(0.0, 1000.0)).isEqualTo(10.0)
    }

    @Test
    fun bandBoundaries() {
        assertThat(DifficultyCalculator.band(0.0)).isEqualTo(Difficulty.EASY)
        assertThat(DifficultyCalculator.band(7.99)).isEqualTo(Difficulty.EASY)
        assertThat(DifficultyCalculator.band(8.0)).isEqualTo(Difficulty.MODERATE)
        assertThat(DifficultyCalculator.band(17.99)).isEqualTo(Difficulty.MODERATE)
        assertThat(DifficultyCalculator.band(18.0)).isEqualTo(Difficulty.HARD)
        assertThat(DifficultyCalculator.band(31.99)).isEqualTo(Difficulty.HARD)
        assertThat(DifficultyCalculator.band(32.0)).isEqualTo(Difficulty.VERY_HARD)
    }

    @Test
    fun flatTrackIsRankedByDistanceOnly() {
        assertThat(DifficultyCalculator.bandOf(5_000.0, 0.0)).isEqualTo(Difficulty.EASY)
        assertThat(DifficultyCalculator.bandOf(40_000.0, 0.0)).isEqualTo(Difficulty.VERY_HARD)
    }
}
