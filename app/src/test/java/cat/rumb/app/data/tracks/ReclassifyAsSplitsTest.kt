package cat.rumb.app.data.tracks

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReclassifyAsSplitsTest {

    private fun r(kind: LapKind, i: Int) = LapRange(index = i, startIdx = i * 10, endIdx = i * 10 + 5, kind = kind)

    @Test
    fun everyLapBecomesASplit() {
        val laps = listOf(r(LapKind.LAP, 1), r(LapKind.LAP, 2), r(LapKind.LAP, 3))
        assertThat(Laps.reclassifyAsSplits(laps).map { it.kind })
            .containsExactly(LapKind.SPLIT, LapKind.SPLIT, LapKind.SPLIT)
    }

    @Test
    fun approachReturnAbortedAreLeftAlone() {
        // A distance run yields pure LAP ranges, but be robust: only LAP is relabelled.
        val mixed = listOf(
            r(LapKind.APPROACH, 0), r(LapKind.LAP, 1), r(LapKind.RETURN, 2), r(LapKind.ABORTED, 3),
        )
        assertThat(Laps.reclassifyAsSplits(mixed).map { it.kind })
            .containsExactly(LapKind.APPROACH, LapKind.SPLIT, LapKind.RETURN, LapKind.ABORTED)
    }

    @Test
    fun theRangesThemselvesAreOtherwiseUnchanged() {
        val lap = r(LapKind.LAP, 4)
        val out = Laps.reclassifyAsSplits(listOf(lap)).single()
        assertThat(out).isEqualTo(lap.copy(kind = LapKind.SPLIT))
    }

    @Test
    fun emptyStaysEmpty() {
        assertThat(Laps.reclassifyAsSplits(emptyList())).isEmpty()
    }

    @Test
    fun theNewKindSurvivesEncodeDecode() {
        // The SPLIT enum value must round-trip through the JSON laps column.
        val laps = listOf(r(LapKind.SPLIT, 1), r(LapKind.LAP, 2))
        assertThat(Laps.decode(Laps.encode(laps))).isEqualTo(laps)
    }
}
