package cat.rumb.app.data.competition

import cat.rumb.app.data.gpx.GpxPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GhostSourceTest {

    private val t0: Instant = Instant.parse("2026-07-17T10:00:00Z")

    /** A ghost whose lap takes [seconds] — the only property the choice depends on. */
    private fun lap(seconds: Long): GhostEngine = GhostEngine(
        listOf(
            GpxPoint(41.0, 2.0, time = t0),
            GpxPoint(41.001, 2.0, time = t0.plusSeconds(seconds)),
        ),
    )

    // --- faster ---------------------------------------------------------------------------------

    @Test
    fun fasterPicksTheShorterLapWhicheverSideItIsOn() {
        val quick = lap(100)
        val slow = lap(120)
        assertThat(GhostSource.faster(quick, slow)).isSameAs(quick)
        assertThat(GhostSource.faster(slow, quick)).isSameAs(quick)
    }

    @Test
    fun fasterFallsBackToWhicheverExists() {
        val only = lap(100)
        assertThat(GhostSource.faster(null, only)).isSameAs(only)
        assertThat(GhostSource.faster(only, null)).isSameAs(only)
        assertThat(GhostSource.faster(null, null)).isNull()
    }

    @Test
    fun fasterKeepsTheIncumbentOnATie() {
        val first = lap(100)
        val second = lap(100)
        assertThat(GhostSource.faster(first, second)).isSameAs(first)
    }

    // --- pick: COMPETITION ----------------------------------------------------------------------

    @Test
    fun competitionChasesTheRecordOnTheFirstLap() {
        val record = lap(120)
        assertThat(GhostSource.pick(GhostSource.COMPETITION, record, null, true)).isSameAs(record)
    }

    @Test
    fun competitionPromotesTodaysLapAsSoonAsItBeatsTheRecord() {
        // The bug this feature exists to kill: beat the record on lap 1 and lap 2 must chase YOUR lap,
        // not the stored record from a past session.
        val record = lap(120)
        val today = lap(115)
        assertThat(GhostSource.pick(GhostSource.COMPETITION, record, today, true)).isSameAs(today)
    }

    @Test
    fun competitionKeepsTheRecordWhileYouHaveNotBeatenIt() {
        val record = lap(120)
        val today = lap(125)
        assertThat(GhostSource.pick(GhostSource.COMPETITION, record, today, true)).isSameAs(record)
    }

    @Test
    fun competitionOutsideACompetitionFallsBackToTodaysBest() {
        // No competition means no record, so the setting has nothing to add: your best lap is the
        // only lap there is. This is what keeps free laps behaving as they always have.
        val today = lap(115)
        assertThat(GhostSource.pick(GhostSource.COMPETITION, null, today, true)).isSameAs(today)
    }

    // --- pick: SESSION --------------------------------------------------------------------------

    @Test
    fun sessionIgnoresTheRecordEvenWhenItIsFaster() {
        val record = lap(100)
        val today = lap(130)
        assertThat(GhostSource.pick(GhostSource.SESSION, record, today, true)).isSameAs(today)
    }

    @Test
    fun sessionHasNoGhostOnTheFirstLap() {
        // Nothing to compare against yet — the warning under the chips says exactly this.
        assertThat(GhostSource.pick(GhostSource.SESSION, lap(100), null, true)).isNull()
    }

    // --- pick: disabled -------------------------------------------------------------------------

    @Test
    fun disabledChasesNothingInEitherMode() {
        val record = lap(100)
        val today = lap(110)
        assertThat(GhostSource.pick(GhostSource.COMPETITION, record, today, false)).isNull()
        assertThat(GhostSource.pick(GhostSource.SESSION, record, today, false)).isNull()
    }

    // --- byName ---------------------------------------------------------------------------------

    @Test
    fun byNameDefaultsToCompetitionSoExistingInstallsKeepAGhostFromLapOne() {
        assertThat(GhostSource.byName(null)).isEqualTo(GhostSource.COMPETITION)
        assertThat(GhostSource.byName("BASURA")).isEqualTo(GhostSource.COMPETITION)
        assertThat(GhostSource.byName("SESSION")).isEqualTo(GhostSource.SESSION)
        assertThat(GhostSource.byName("COMPETITION")).isEqualTo(GhostSource.COMPETITION)
    }
}
