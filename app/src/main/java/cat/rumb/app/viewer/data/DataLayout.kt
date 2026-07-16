package cat.rumb.app.viewer.data

import cat.rumb.app.data.prefs.ViewerPreferences
import cat.rumb.app.viewer.hud.HudMetric
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Configuration of the full-screen "Dades" grid: which metrics show, in what order, how many columns,
 * and whether the live clock tile appears. [fields] holds [HudMetric] names (stable, serialized).
 */
@Serializable
data class DataLayout(
    val fields: List<String> = DEFAULT_FIELDS,
    val columns: Int = 2,
    val showClock: Boolean = true,
    /** Column span per metric name (1..columns); absent = 1. */
    val spans: Map<String, Int> = emptyMap(),
    /** Value-text color ("#RRGGBB") per metric name; absent = theme default. */
    val colors: Map<String, String> = emptyMap(),
    /** Per-tile font size multiplier; absent = 1 (mirror of HudWidget.scale). */
    val scales: Map<String, Float> = emptyMap(),
    /** Coloured outline ("#RRGGBB") per metric name; absent = no outline. */
    val borders: Map<String, String> = emptyMap(),
    /** Clock tile format: 24 h (default) or 12 h. */
    val clockH24: Boolean = true,
    /** Field ids showing a mini-sparkline under the value (series-backed metrics only). */
    val graphs: Set<String> = emptySet(),
) {
    fun colorOf(field: String): String? = colors[field]

    /** Effective font-size multiplier of [field], clamped to a sane range. */
    fun scaleOf(field: String): Float = (scales[field] ?: 1f).coerceIn(MIN_TILE_SCALE, MAX_TILE_SCALE)

    fun setScale(field: String, scale: Float): DataLayout =
        copy(scales = scales + (field to scale.coerceIn(MIN_TILE_SCALE, MAX_TILE_SCALE)))

    fun borderOf(field: String): String? = borders[field]

    /** Sets (or clears with null) the outline colour of [field]. */
    fun setBorder(field: String, hex: String?): DataLayout =
        copy(borders = if (hex == null) borders - field else borders + (field to hex))

    fun hasGraph(field: String): Boolean = field in graphs

    /** Toggles the per-tile mini-sparkline for [field]. */
    fun toggleGraph(field: String): DataLayout =
        copy(graphs = if (field in graphs) graphs - field else graphs + field)

    /** Sets (or clears with null) the value color of [field]. */
    fun setColor(field: String, hex: String?): DataLayout =
        copy(colors = if (hex == null) colors - field else colors + (field to hex))

    /** Effective span of [field], clamped to the current column count. */
    fun spanOf(field: String): Int = (spans[field] ?: 1).coerceIn(1, columns.coerceAtLeast(1))

    fun setSpan(field: String, span: Int): DataLayout = copy(spans = spans + (field to span))

    /** Cycles the tile width: 1 → 2 (→ 3 with three columns) → 1. */
    fun cycleSpan(field: String): DataLayout =
        setSpan(field, if (spanOf(field) >= columns) 1 else spanOf(field) + 1)

    /** Moves the field at [from] to position [to], preserving the rest of the order. */
    fun moveTo(from: Int, to: Int): DataLayout {
        if (from !in fields.indices || to !in fields.indices || from == to) return this
        val mutable = fields.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        return copy(fields = mutable)
    }

    /** Selected fields resolved to metrics, in order, skipping any unknown id. */
    fun metrics(): List<HudMetric> = fields.mapNotNull { runCatching { HudMetric.valueOf(it) }.getOrNull() }

    fun contains(field: String) = field in fields

    fun add(field: String): DataLayout = if (contains(field)) this else copy(fields = fields + field)

    fun remove(field: String): DataLayout = copy(fields = fields.filterNot { it == field })

    /** Moves the field at [index] by [delta] positions (clamped), preserving the rest of the order. */
    fun move(index: Int, delta: Int): DataLayout {
        if (index !in fields.indices) return this
        val target = (index + delta).coerceIn(0, fields.size - 1)
        if (target == index) return this
        val mutable = fields.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(target, item)
        return copy(fields = mutable)
    }

    /**
     * Migration: older layouts kept the clock OUT of [fields] (a trailing `showClock` tile). Now the
     * clock is an orderable field like any other; append it when the legacy flag asks for it.
     */
    fun normalized(): DataLayout =
        if (showClock && CLOCK !in fields) copy(fields = fields + CLOCK) else this

    companion object {
        /** Font-size multiplier bounds, matching the HUD widget range. */
        const val MIN_TILE_SCALE = 0.6f
        const val MAX_TILE_SCALE = 2.2f

        /** Field id of the clock tile (orderable/resizable like a metric). */
        const val CLOCK = "CLOCK"

        /** Every metric, matching the original fixed grid. */
        val DEFAULT_FIELDS: List<String> = HudMetric.entries.map { it.name }
    }
}

/**
 * Persists/loads the [DataLayout] as JSON in [ViewerPreferences] (mirror of HudLayoutStore). Per-sport
 * when a [sportId] is given: a sport's own layout → adopt the pre-sport global once → default. A null
 * [sportId] keeps the old single global behaviour (no sport chosen yet).
 */
object DataLayoutStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(raw: String?): DataLayout? = raw?.let {
        runCatching { json.decodeFromString(DataLayout.serializer(), it) }.getOrNull()?.normalized()
    }

    fun load(prefs: ViewerPreferences, sportId: String? = null): DataLayout {
        if (sportId == null) return decode(prefs.dataLayoutJson) ?: DataLayout().normalized()
        decode(prefs.dataLayoutJsonFor(sportId))?.let { return it }
        // First time this sport is shown: inherit the existing global layout once (don't lose it).
        val legacy = decode(prefs.dataLayoutJson)
        if (legacy != null && !prefs.dataLayoutAdopted) {
            prefs.dataLayoutAdopted = true
            save(prefs, legacy, sportId)
            return legacy
        }
        return DataLayout().normalized()
    }

    fun save(prefs: ViewerPreferences, layout: DataLayout, sportId: String? = null) {
        // Keep the legacy flag in sync so older readers behave.
        val synced = layout.copy(showClock = DataLayout.CLOCK in layout.fields)
        val raw = json.encodeToString(DataLayout.serializer(), synced)
        if (sportId == null) prefs.dataLayoutJson = raw else prefs.setDataLayoutJsonFor(sportId, raw)
    }
}
