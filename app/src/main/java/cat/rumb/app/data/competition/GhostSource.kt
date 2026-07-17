package cat.rumb.app.data.competition

import cat.rumb.app.R

/**
 * Which lap the ghost replays. Both modes rebuild the ghost at EVERY closed lap — the only
 * difference is whether the stored record takes part in the comparison.
 */
enum class GhostSource(val labelRes: Int, val helpRes: Int) {
    COMPETITION(R.string.viewer_qs_ghost_competition, R.string.viewer_qs_ghost_competition_help),
    SESSION(R.string.viewer_qs_ghost_session, R.string.viewer_qs_ghost_session_help),
    ;

    companion object {
        fun byName(name: String?): GhostSource = entries.firstOrNull { it.name == name } ?: COMPETITION

        /** The faster of two ghosts: the one whose lap takes less time. */
        fun faster(a: GhostEngine?, b: GhostEngine?): GhostEngine? = when {
            a == null -> b
            b == null -> a
            else -> if (b.totalDurationMs < a.totalDurationMs) b else a
        }

        /**
         * The ghost to chase. [COMPETITION] weighs the stored [record] against your best lap of today,
         * so beating the record promotes YOUR lap on the very next one — today's laps are attempts too,
         * the leaderboard just doesn't know until you save. [SESSION] ignores the record entirely, which
         * is why its first lap has nothing to chase. Outside a competition there is no record, so both
         * pick [session] — the only lap there is.
         */
        fun pick(
            source: GhostSource,
            record: GhostEngine?,
            session: GhostEngine?,
            enabled: Boolean,
        ): GhostEngine? = if (!enabled) {
            null
        } else {
            when (source) {
                SESSION -> session
                COMPETITION -> faster(record, session)
            }
        }
    }
}
