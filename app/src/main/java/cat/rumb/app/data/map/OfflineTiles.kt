package cat.rumb.app.data.map

/**
 * Pure tile-set math for pruning an offline sector from a merged per-type MBTiles. When a sector is
 * deleted, only its EXCLUSIVE tiles must be removed — tiles also covered by a surviving sector are
 * kept, so overlaps don't create holes.
 */
object OfflineTiles {

    /**
     * XYZ tiles (z, x, y) that belong to [target] but to none of the [keep] sectors. A keep sector
     * only shields a tile at zoom `z` if its own zoom range includes `z`.
     */
    fun tilesToDelete(target: OfflineSector, keep: List<OfflineSector>): List<Triple<Int, Int, Int>> {
        val result = ArrayList<Triple<Int, Int, Int>>()
        for (z in target.minZoom..target.maxZoom) {
            val range = TileMath.tileRangeForBbox(target.bbox, z)
            val keepRanges = keep
                .filter { z in it.minZoom..it.maxZoom }
                .map { TileMath.tileRangeForBbox(it.bbox, z) }
            for (x in range.xMin..range.xMax) {
                for (y in range.yMin..range.yMax) {
                    if (keepRanges.none { x in it.xMin..it.xMax && y in it.yMin..it.yMax }) {
                        result.add(Triple(z, x, y))
                    }
                }
            }
        }
        return result
    }
}
