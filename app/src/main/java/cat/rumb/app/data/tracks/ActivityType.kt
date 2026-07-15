package cat.rumb.app.data.tracks

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * How an activity moves. Only same-family efforts may be compared: a bike lap on a running circuit
 * would be the fastest and would steal the reference/ghost (refreshReference takes min(timeMs)).
 * [UNKNOWN] never blocks — it means "can't judge", not "reject".
 */
enum class ActivityFamily { FOOT, WHEEL, SNOW, SKATE, PADDLE, SWIM, UNKNOWN }

/**
 * A user-defined activity type. [id] is what's stored in follow_tracks.activity_type.
 * [family] defaults to UNKNOWN so types saved before families existed decode unchanged.
 */
@Serializable
data class CustomActivityType(
    val id: String,
    val name: String,
    val iconId: String,
    val family: String = ActivityFamily.UNKNOWN.name,
) {
    val familyEnum: ActivityFamily
        get() = runCatching { ActivityFamily.valueOf(family) }.getOrDefault(ActivityFamily.UNKNOWN)
}

/**
 * Activity-type registry. Predefined ids are stable strings stored in the DB; their display names
 * and icons live in the UI layer (ActivityTypeCatalog). Custom types are persisted as JSON in prefs.
 */
object ActivityTypes {
    const val WALK = "walk"
    const val RUN = "run"
    const val TRAIL_RUN = "trail_run"
    const val ROAD_BIKE = "road_bike"
    const val MTB = "mtb"
    const val HIKE = "hike"
    const val SKI = "ski"
    const val SKATE = "skate"
    const val KAYAK = "kayak"
    const val SWIM = "swim"

    val PREDEFINED = listOf(WALK, RUN, TRAIL_RUN, ROAD_BIKE, MTB, HIKE, SKI, SKATE, KAYAK, SWIM)

    /**
     * The family an activity belongs to, resolving custom types via [custom].
     * KAYAK and SWIM are deliberately NOT one "water" family: a kayak is far faster than a swimmer,
     * so sharing a family would let it steal a swimming record through the back door.
     * WALK/HIKE share FOOT with RUN on purpose — being slower they can never win a running record,
     * so grouping them costs nothing and avoids rejecting a legitimate trail run.
     */
    fun familyOf(id: String?, custom: List<CustomActivityType> = emptyList()): ActivityFamily = when (id) {
        WALK, RUN, TRAIL_RUN, HIKE -> ActivityFamily.FOOT
        ROAD_BIKE, MTB -> ActivityFamily.WHEEL
        SKI -> ActivityFamily.SNOW
        SKATE -> ActivityFamily.SKATE
        KAYAK -> ActivityFamily.PADDLE
        SWIM -> ActivityFamily.SWIM
        null -> ActivityFamily.UNKNOWN
        else -> custom.firstOrNull { it.id == id }?.familyEnum ?: ActivityFamily.UNKNOWN
    }

    /** Whether two efforts may be compared. UNKNOWN is permissive: legacy data must not break. */
    fun comparable(a: ActivityFamily, b: ActivityFamily): Boolean =
        a == ActivityFamily.UNKNOWN || b == ActivityFamily.UNKNOWN || a == b

    /** Convenience: are these two activity-type ids comparable? */
    fun comparableTypes(a: String?, b: String?, custom: List<CustomActivityType> = emptyList()): Boolean =
        comparable(familyOf(a, custom), familyOf(b, custom))

    /**
     * Maps a Rumb activity-type id to a GPX `<type>` string (Strava/Endurain vocabulary). Returns
     * null for unknown/custom types so the tag is omitted rather than guessed.
     */
    fun gpxType(id: String?): String? = when (id) {
        WALK -> "walking"
        RUN -> "running"
        TRAIL_RUN -> "running"
        ROAD_BIKE -> "cycling"
        MTB -> "cycling"
        HIKE -> "hiking"
        SKI -> "skiing"
        SKATE -> "inline skating"
        KAYAK -> "kayaking"
        SWIM -> "swimming"
        else -> null
    }

    /** TCX Sport attribute is limited to Running / Biking / Other. */
    fun tcxSport(id: String?): String = when (id) {
        RUN, TRAIL_RUN -> "Running"
        ROAD_BIKE, MTB -> "Biking"
        else -> "Other"
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun decodeCustom(encoded: String?): List<CustomActivityType> =
        encoded?.let { runCatching { json.decodeFromString<List<CustomActivityType>>(it) }.getOrNull() }
            ?: emptyList()

    fun encodeCustom(list: List<CustomActivityType>): String = json.encodeToString(list)

    fun newCustomId(): String = "custom_" + java.util.UUID.randomUUID().toString()
}
