package cat.rumb.app.data.recording

import kotlinx.serialization.Serializable

/** Where a lap boundary was placed during recording. Laps are orthogonal to pause-segments. */
enum class LapMarkType { START, SPLIT, END }

/**
 * A lap boundary captured live: the point [seq] where the boundary lands, plus the running
 * distance/elapsed at that instant (so lap stats and crash-recovery need no recomputation).
 * START opens lap 1 (everything before is approach), SPLIT closes a lap and opens the next, END
 * closes the lap block (what follows is the return, not a lap).
 */
@Serializable
data class LapMark(
    val seq: Long,
    val distanceM: Double,
    val totalMs: Long,
    val type: LapMarkType,
)
