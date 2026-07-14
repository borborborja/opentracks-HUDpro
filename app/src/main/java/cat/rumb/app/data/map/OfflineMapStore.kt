package cat.rumb.app.data.map

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** One downloaded area within a merged per-type offline map. */
@Serializable
data class OfflineSector(
    val id: String,
    /** [west, south, east, north]. */
    val bounds: List<Double>,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Long,
    val createdAt: Long,
) {
    val bbox: BoundingBox get() = BoundingBox(bounds[0], bounds[1], bounds[2], bounds[3])

    companion object {
        fun idOf(bbox: BoundingBox, minZoom: Int, maxZoom: Int): String =
            "${bbox.west},${bbox.south},${bbox.east},${bbox.north}@$minZoom-$maxZoom"
    }
}

@Serializable
data class OfflineMap(
    val name: String,
    val path: String,
    val attribution: String = "© ICGC / © OpenStreetMap contributors",
    /** Coverage bounds [west, south, east, north] to frame the viewer on; null if unknown. */
    val bounds: List<Double>? = null,
    /** Map source id (see MapSource) of the downloaded tiles; null for imported/legacy files. */
    val sourceId: String? = null,
    /** Individual downloaded areas merged into this archive. Empty for imported/legacy files. */
    val sectors: List<OfflineSector> = emptyList(),
) {
    /** Base-map id used in ViewerPreferences to select this offline map. */
    val selectionId: String get() = "$OFFLINE_PREFIX$path"

    companion object {
        const val OFFLINE_PREFIX = "offline:"
    }
}

/**
 * Manages imported MBTiles archives (offline OSM/ICGC tiles). Files are copied into the app's
 * private storage; MapLibre reads them via the `mbtiles://` scheme (see [MapStyleFactory]).
 * Get the official ICGC MBTiles from https://visors.icgc.cat/appdownloads/ (CC-BY © ICGC).
 */
class OfflineMapStore private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("offline_maps", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dir = File(context.filesDir, "mbtiles").apply { mkdirs() }

    private val serializer = ListSerializer(OfflineMap.serializer())

    fun list(): List<OfflineMap> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
            .filter { File(it.path).exists() }
    }

    fun bySelectionId(selectionId: String): OfflineMap? =
        list().firstOrNull { it.selectionId == selectionId }

    fun byPath(path: String): OfflineMap? = list().firstOrNull { it.path == path }

    /** The map already downloaded for [sourceId] (so new sectors accumulate into the same archive). */
    fun bySourceId(sourceId: String): OfflineMap? = list().firstOrNull { it.sourceId == sourceId }

    /**
     * Sectors of [map], synthesizing a single "legacy" sector for old records that predate per-sector
     * tracking (they only carried one overall bounds). Lets the sector viewer work on old downloads.
     */
    fun sectorsOf(map: OfflineMap): List<OfflineSector> {
        if (map.sectors.isNotEmpty()) return map.sectors
        val b = map.bounds ?: return emptyList()
        if (b.size != 4) return emptyList()
        val bbox = BoundingBox(b[0], b[1], b[2], b[3])
        return listOf(
            OfflineSector(
                id = OfflineSector.idOf(bbox, LEGACY_MIN_ZOOM, LEGACY_MAX_ZOOM),
                bounds = b,
                minZoom = LEGACY_MIN_ZOOM,
                maxZoom = LEGACY_MAX_ZOOM,
                tileCount = TileMath.tileCount(bbox, LEGACY_MIN_ZOOM, LEGACY_MAX_ZOOM),
                createdAt = 0L,
            ),
        )
    }

    /** Registers/merges a freshly-downloaded [sector] into the per-type archive at [path]. */
    fun addSector(sourceId: String, name: String, attribution: String, path: String, sector: OfflineSector) {
        val existing = byPath(path)
        val sectors = (existing?.sectors.orEmpty().filterNot { it.id == sector.id }) + sector
        val map = OfflineMap(
            name = name,
            path = path,
            attribution = attribution,
            bounds = unionBounds(sectors),
            sourceId = sourceId,
            sectors = sectors,
        )
        save(list().filterNot { it.path == path } + map)
        writeUnionMetadata(map)
    }

    /**
     * Rewrites the archive's mbtiles `metadata` to span the union of all its sectors. MapLibre reads
     * bounds/min-max zoom from that table to decide which tiles to request; since every sector of a
     * map type accumulates into one archive and each download only wrote its own window, previously
     * downloaded areas would fall outside the advertised bounds and render blank. This restores them.
     */
    private fun writeUnionMetadata(map: OfflineMap) {
        val sectors = map.sectors
        val bounds = unionBounds(sectors) ?: return
        val bbox = BoundingBox(bounds[0], bounds[1], bounds[2], bounds[3])
        val minZoom = sectors.minOf { it.minZoom }
        val maxZoom = sectors.maxOf { it.maxZoom }
        runCatching {
            val file = File(map.path)
            if (file.exists()) {
                MbtilesWriter(file).use { it.writeMetadata(map.name, bbox, minZoom, maxZoom, map.attribution) }
            }
        }
    }

    /** Removes [sector] from [map]: prunes its exclusive tiles and updates metadata (or deletes all). */
    fun deleteSector(map: OfflineMap, sector: OfflineSector) {
        val remaining = sectorsOf(map).filterNot { it.id == sector.id }
        if (remaining.isEmpty()) { delete(map); return }
        // Prune tiles that belong only to the removed sector (keep those shared with survivors).
        runCatching {
            val toDelete = OfflineTiles.tilesToDelete(sector, remaining)
            if (toDelete.isNotEmpty() && File(map.path).exists()) {
                MbtilesWriter(File(map.path)).use { w ->
                    w.batch { toDelete.forEach { (z, x, y) -> w.deleteTile(z, x, y) } }
                }
            }
        }
        val updated = map.copy(bounds = unionBounds(remaining), sectors = remaining)
        save(list().filterNot { it.path == map.path } + updated)
        writeUnionMetadata(updated)
    }

    fun unionBounds(sectors: List<OfflineSector>): List<Double>? {
        if (sectors.isEmpty()) return null
        val w = sectors.minOf { it.bounds[0] }
        val s = sectors.minOf { it.bounds[1] }
        val e = sectors.maxOf { it.bounds[2] }
        val n = sectors.maxOf { it.bounds[3] }
        return listOf(w, s, e, n)
    }

    /** Copies an MBTiles from a SAF [uri] into private storage and registers it. */
    fun import(resolver: ContentResolver, uri: Uri, name: String): OfflineMap {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "map" }
        val dest = File(dir, if (safe.endsWith(".mbtiles")) safe else "$safe.mbtiles")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open $uri" }
            dest.outputStream().use { input.copyTo(it) }
        }
        val map = OfflineMap(name = name.removeSuffix(".mbtiles"), path = dest.absolutePath)
        save(list().filterNot { it.path == map.path } + map)
        return map
    }

    /** Directory where generated/imported MBTiles live. */
    val mbtilesDir: File get() = dir

    /** Registers an already-written MBTiles (e.g. from an area download). */
    fun register(map: OfflineMap) {
        save(list().filterNot { it.path == map.path } + map)
    }

    fun delete(map: OfflineMap) {
        runCatching { File(map.path).delete() }
        save(list().filterNot { it.path == map.path })
    }

    private fun save(maps: List<OfflineMap>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, maps)).apply()
    }

    companion object {
        private const val KEY = "maps"
        private const val LEGACY_MIN_ZOOM = 9
        private const val LEGACY_MAX_ZOOM = 14
        fun get(context: Context) = OfflineMapStore(context.applicationContext)
    }
}
