package cat.rumb.app.data.tracks

/**
 * Pure transforms behind the graphical lap editor. A lap set is held while editing as two parallel
 * lists: [cuts] (boundary point-indices, size N+1, sorted) and per-segment [kinds] (size N). These
 * functions add/remove a boundary and rebuild [LapRange]s, keeping the two lists aligned and never
 * touching the approach/return boundaries (add/remove only apply within the LAP block). Kept out of
 * the Compose screen so the index bookkeeping is unit-testable.
 */
object LapEdits {

    /** The segment (index into kinds) strictly containing point [idx], or -1 if on/outside a cut. */
    fun segmentOf(cuts: List<Int>, kinds: List<LapKind>, idx: Int): Int =
        kinds.indices.firstOrNull { idx > cuts[it] && idx < cuts[it + 1] } ?: -1

    /** True when a split can be inserted at point [idx]: strictly inside a LAP segment, off any cut. */
    fun canAdd(cuts: List<Int>, kinds: List<LapKind>, idx: Int): Boolean =
        cuts.size >= 2 && idx > cuts.first() && idx < cuts.last() && cuts.none { it == idx } &&
            segmentOf(cuts, kinds, idx).let { it >= 0 && kinds[it] == LapKind.LAP }

    /** Inserts a split at [idx], turning one LAP segment into two. Returns null if [idx] is invalid. */
    fun addSplit(cuts: List<Int>, kinds: List<LapKind>, idx: Int): Pair<List<Int>, List<LapKind>>? {
        if (!canAdd(cuts, kinds, idx)) return null
        val seg = segmentOf(cuts, kinds, idx)
        val newCuts = (cuts + idx).sorted()
        val newKinds = kinds.toMutableList().also { it.add(seg, LapKind.LAP) }
        return newCuts to newKinds
    }

    /** True when internal cut [i] sits between two LAP segments (so removing it merges two laps). */
    fun canRemove(cuts: List<Int>, kinds: List<LapKind>, i: Int): Boolean =
        i in 1 until cuts.size - 1 && kinds[i - 1] == LapKind.LAP && kinds[i] == LapKind.LAP

    /** Removes cut [i], merging the two LAP segments around it. Returns null if [i] can't be removed. */
    fun removeCut(cuts: List<Int>, kinds: List<LapKind>, i: Int): Pair<List<Int>, List<LapKind>>? {
        if (!canRemove(cuts, kinds, i)) return null
        val newCuts = cuts.toMutableList().also { it.removeAt(i) }
        val newKinds = kinds.toMutableList().also { it[i - 1] = LapKind.LAP; it.removeAt(i) }
        return newCuts to newKinds
    }

    /** Rebuilds ranges from [cuts]/[kinds], re-numbering the laps in order; drops empty ranges. */
    fun rebuild(cuts: List<Int>, kinds: List<LapKind>): List<LapRange> {
        var lapNo = 0
        return kinds.mapIndexed { k, kind ->
            val index = if (kind == LapKind.LAP) ++lapNo else k
            LapRange(index, cuts[k], cuts[k + 1], kind)
        }.filter { it.endIdx > it.startIdx }
    }
}
