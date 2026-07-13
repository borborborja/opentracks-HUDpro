package cat.rumb.app.manager.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.NordicWalking
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.ElectricBike
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.IceSkating
import androidx.compose.material.icons.filled.Kayaking
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Paragliding
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.RollerSkating
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Snowboarding
import androidx.compose.material.icons.filled.Snowshoeing
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.Surfing
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import cat.rumb.app.R
import cat.rumb.app.data.prefs.ViewerPreferences
import cat.rumb.app.data.tracks.ActivityTypes
import cat.rumb.app.data.tracks.CustomActivityType

/** One selectable activity type (predefined or custom) resolved for UI use. */
data class ActivityTypeOption(val id: String, val label: String, val icon: ImageVector)

/** Icons for the predefined types plus the curated set offered when creating custom types. */
object ActivityTypeCatalog {

    private val ICONS: Map<String, ImageVector> = mapOf(
        ActivityTypes.WALK to Icons.AutoMirrored.Filled.DirectionsWalk,
        ActivityTypes.RUN to Icons.AutoMirrored.Filled.DirectionsRun,
        ActivityTypes.TRAIL_RUN to Icons.Filled.Terrain,
        ActivityTypes.ROAD_BIKE to Icons.AutoMirrored.Filled.DirectionsBike,
        ActivityTypes.MTB to Icons.Filled.PedalBike,
        ActivityTypes.HIKE to Icons.Filled.Hiking,
        ActivityTypes.SKI to Icons.Filled.DownhillSkiing,
        ActivityTypes.SKATE to Icons.Filled.RollerSkating,
        ActivityTypes.KAYAK to Icons.Filled.Kayaking,
        ActivityTypes.SWIM to Icons.Filled.Pool,
        // Extra curated icons for custom types.
        "sailing" to Icons.Filled.Sailing,
        "rowing" to Icons.Filled.Rowing,
        "ice_skating" to Icons.Filled.IceSkating,
        "nordic_walking" to Icons.Filled.NordicWalking,
        "snowboard" to Icons.Filled.Snowboarding,
        "snowshoes" to Icons.Filled.Snowshoeing,
        "gym" to Icons.Filled.SportsGymnastics,
        "fitness" to Icons.Filled.FitnessCenter,
        "paragliding" to Icons.Filled.Paragliding,
        "surf" to Icons.Filled.Surfing,
        "ebike" to Icons.Filled.ElectricBike,
        "mountain" to Icons.Filled.Landscape,
        "winter" to Icons.Filled.AcUnit,
    )

    /** Icon ids offered in the custom-type creation grid. */
    val CURATED_ICON_IDS: List<String> = ICONS.keys.toList()

    fun iconFor(idOrIconId: String?): ImageVector =
        ICONS[idOrIconId] ?: Icons.AutoMirrored.Filled.DirectionsWalk

    private val LABELS: Map<String, Int> = mapOf(
        ActivityTypes.WALK to R.string.activity_type_walk,
        ActivityTypes.RUN to R.string.activity_type_run,
        ActivityTypes.TRAIL_RUN to R.string.activity_type_trail_run,
        ActivityTypes.ROAD_BIKE to R.string.activity_type_road_bike,
        ActivityTypes.MTB to R.string.activity_type_mtb,
        ActivityTypes.HIKE to R.string.activity_type_hike,
        ActivityTypes.SKI to R.string.activity_type_ski,
        ActivityTypes.SKATE to R.string.activity_type_skate,
        ActivityTypes.KAYAK to R.string.activity_type_kayak,
        ActivityTypes.SWIM to R.string.activity_type_swim,
    )

    fun labelRes(predefinedId: String): Int? = LABELS[predefinedId]
}

/** Display label of any type id: predefined via resources, custom via its stored name. */
@Composable
fun activityTypeLabel(typeId: String?, custom: List<CustomActivityType>): String {
    if (typeId == null) return stringResource(R.string.activity_type_none)
    ActivityTypeCatalog.labelRes(typeId)?.let { return stringResource(it) }
    return custom.firstOrNull { it.id == typeId }?.name ?: stringResource(R.string.activity_type_none)
}

/** Icon of any type id: predefined directly, custom via its stored iconId. */
fun activityTypeIcon(typeId: String?, custom: List<CustomActivityType>): ImageVector {
    val customIcon = custom.firstOrNull { it.id == typeId }?.iconId
    return ActivityTypeCatalog.iconFor(customIcon ?: typeId)
}

/** String resource for a difficulty band. */
fun difficultyLabel(d: cat.rumb.app.data.tracks.Difficulty): Int = when (d) {
    cat.rumb.app.data.tracks.Difficulty.EASY -> R.string.difficulty_easy
    cat.rumb.app.data.tracks.Difficulty.MODERATE -> R.string.difficulty_moderate
    cat.rumb.app.data.tracks.Difficulty.HARD -> R.string.difficulty_hard
    cat.rumb.app.data.tracks.Difficulty.VERY_HARD -> R.string.difficulty_very_hard
}

/** All selectable options (predefined + custom) for pickers. */
@Composable
fun rememberActivityTypeOptions(prefs: ViewerPreferences): List<ActivityTypeOption> {
    val custom = remember(prefs.customActivityTypesJson) { ActivityTypes.decodeCustom(prefs.customActivityTypesJson) }
    val predefined = ActivityTypes.PREDEFINED.map { id ->
        ActivityTypeOption(id, activityTypeLabel(id, emptyList()), ActivityTypeCatalog.iconFor(id))
    }
    return predefined + custom.map { ActivityTypeOption(it.id, it.name, ActivityTypeCatalog.iconFor(it.iconId)) }
}
