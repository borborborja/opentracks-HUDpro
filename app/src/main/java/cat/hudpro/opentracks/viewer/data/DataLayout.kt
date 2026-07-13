package cat.hudpro.opentracks.viewer.data

import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import cat.hudpro.opentracks.viewer.hud.HudMetric
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
    /** Clock tile format: 24 h (default) or 12 h. */
    val clockH24: Boolean = true,
) {
    fun colorOf(field: String): String? = colors[field]

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

    companion object {
        /** Every metric, matching the original fixed grid. */
        val DEFAULT_FIELDS: List<String> = HudMetric.entries.map { it.name }
    }
}

/** Persists/loads the [DataLayout] as JSON in [ViewerPreferences] (mirror of HudLayoutStore). */
object DataLayoutStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(prefs: ViewerPreferences): DataLayout {
        val raw = prefs.dataLayoutJson ?: return DataLayout()
        return runCatching { json.decodeFromString(DataLayout.serializer(), raw) }.getOrDefault(DataLayout())
    }

    fun save(prefs: ViewerPreferences, layout: DataLayout) {
        prefs.dataLayoutJson = json.encodeToString(DataLayout.serializer(), layout)
    }
}
