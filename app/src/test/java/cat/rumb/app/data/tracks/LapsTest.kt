package cat.rumb.app.data.tracks

import cat.rumb.app.data.recording.LapMark
import cat.rumb.app.data.recording.LapMarkType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LapsTest {

    @Test
    fun approachBeforeFirstStartIsNotALap() {
        // START at seq 10, SPLIT at 20, END at 30; saved points seq 0..39.
        val marks = listOf(
            LapMark(10, 100.0, 60_000, LapMarkType.START),
            LapMark(20, 200.0, 120_000, LapMarkType.SPLIT),
            LapMark(30, 300.0, 180_000, LapMarkType.END),
        )
        val seqs = (0L until 40L).toList()
        val ranges = Laps.fromMarks(marks, seqs)

        // approach [0,10), lap1 [10,20), lap2 [20,30), return [30,40)
        assertThat(ranges.map { it.kind }).containsExactly(
            LapKind.APPROACH, LapKind.LAP, LapKind.LAP, LapKind.RETURN,
        )
        val laps = ranges.filter { it.kind == LapKind.LAP }
        assertThat(laps).hasSize(2)
        assertThat(laps[0].startIdx to laps[0].endIdx).isEqualTo(10 to 20)
        assertThat(laps[1].startIdx to laps[1].endIdx).isEqualTo(20 to 30)
    }

    @Test
    fun noEndMeansLastLapRunsToTheTrackEnd() {
        val marks = listOf(
            LapMark(5, 50.0, 30_000, LapMarkType.START),
            LapMark(15, 150.0, 90_000, LapMarkType.SPLIT),
        )
        val seqs = (0L until 25L).toList()
        val ranges = Laps.fromMarks(marks, seqs)
        assertThat(ranges.filter { it.kind == LapKind.LAP }.last().endIdx).isEqualTo(25)
        assertThat(ranges.none { it.kind == LapKind.RETURN }).isTrue()
    }

    @Test
    fun jsonRoundTrip() {
        val ranges = listOf(LapRange(1, 10, 20, LapKind.LAP), LapRange(2, 20, 30, LapKind.LAP))
        assertThat(Laps.decode(Laps.encode(ranges))).isEqualTo(ranges)
        assertThat(Laps.decode(null)).isEmpty()
        assertThat(Laps.decode("garbage")).isEmpty()
    }

    @Test
    fun emptyMarksYieldNoLaps() {
        assertThat(Laps.fromMarks(emptyList(), listOf(0L, 1L, 2L))).isEmpty()
    }
}
