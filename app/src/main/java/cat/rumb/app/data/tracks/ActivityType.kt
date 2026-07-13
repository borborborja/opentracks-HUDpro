package cat.rumb.app.data.tracks

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A user-defined activity type. [id] is what's stored in follow_tracks.activity_type. */
@Serializable
data class CustomActivityType(val id: String, val name: String, val iconId: String)

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

    private val json = Json { ignoreUnknownKeys = true }

    fun decodeCustom(encoded: String?): List<CustomActivityType> =
        encoded?.let { runCatching { json.decodeFromString<List<CustomActivityType>>(it) }.getOrNull() }
            ?: emptyList()

    fun encodeCustom(list: List<CustomActivityType>): String = json.encodeToString(list)

    fun newCustomId(): String = "custom_" + java.util.UUID.randomUUID().toString()
}
