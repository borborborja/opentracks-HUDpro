package cat.rumb.app.data.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OfflineTilesTest {

    private fun sector(w: Double, s: Double, e: Double, n: Double, min: Int, max: Int) = OfflineSector(
        id = OfflineSector.idOf(BoundingBox(w, s, e, n), min, max),
        bounds = listOf(w, s, e, n),
        minZoom = min,
        maxZoom = max,
        tileCount = 0,
        createdAt = 0,
    )

    @Test
    fun deletesAllTilesWhenNoOtherSector() {
        val target = sector(1.0, 41.0, 1.5, 41.5, 10, 12)
        val toDelete = OfflineTiles.tilesToDelete(target, emptyList())
        var expected = 0L
        for (z in 10..12) expected += TileMath.tileRangeForBbox(target.bbox, z).count
        assertThat(toDelete.size.toLong()).isEqualTo(expected)
        // Every deleted tile lies within the target's per-zoom range.
        toDelete.forEach { (z, x, y) ->
            val r = TileMath.tileRangeForBbox(target.bbox, z)
            assertThat(x in r.xMin..r.xMax && y in r.yMin..r.yMax).isTrue()
        }
    }

    @Test
    fun keepsTilesSharedWithOverlappingSurvivor() {
        val target = sector(1.0, 41.0, 1.5, 41.5, 12, 12)
        // A survivor that overlaps the western half.
        val survivor = sector(1.0, 41.0, 1.25, 41.5, 12, 12)
        val toDelete = OfflineTiles.tilesToDelete(target, listOf(survivor))
        val keepRange = TileMath.tileRangeForBbox(survivor.bbox, 12)
        // No deleted tile falls inside the survivor's range.
        toDelete.forEach { (_, x, y) ->
            assertThat(x in keepRange.xMin..keepRange.xMax && y in keepRange.yMin..keepRange.yMax).isFalse()
        }
        // And something is still deleted (the eastern-only part).
        assertThat(toDelete).isNotEmpty()
    }

    @Test
    fun survivorAtDifferentZoomDoesNotShieldOtherZooms() {
        val target = sector(1.0, 41.0, 1.5, 41.5, 10, 11)
        val survivor = sector(1.0, 41.0, 1.5, 41.5, 11, 11) // only shields z=11
        val toDelete = OfflineTiles.tilesToDelete(target, listOf(survivor))
        // z=10 fully deleted; z=11 fully shielded.
        assertThat(toDelete.map { it.first }.toSet()).containsExactly(10)
    }

    @Test
    fun fullyCoveredSurvivorDeletesNothing() {
        val target = sector(1.0, 41.0, 1.2, 41.2, 12, 13)
        val survivor = sector(0.5, 40.5, 2.0, 42.0, 12, 13) // encloses target
        assertThat(OfflineTiles.tilesToDelete(target, listOf(survivor))).isEmpty()
    }
}
