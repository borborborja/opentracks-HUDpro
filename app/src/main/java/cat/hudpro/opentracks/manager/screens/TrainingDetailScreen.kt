package cat.hudpro.opentracks.manager.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cat.hudpro.opentracks.HudProApplication
import cat.hudpro.opentracks.R
import cat.hudpro.opentracks.data.gpx.GpxShare
import cat.hudpro.opentracks.data.opentracks.model.GeoPoint
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import cat.hudpro.opentracks.data.tracks.ActivityTypes
import cat.hudpro.opentracks.data.tracks.Difficulty
import cat.hudpro.opentracks.data.tracks.DifficultyCalculator
import cat.hudpro.opentracks.data.tracks.FollowTrackEntity
import cat.hudpro.opentracks.data.tracks.TrackSample
import cat.hudpro.opentracks.data.tracks.TrackStats
import cat.hudpro.opentracks.data.tracks.TrackStatsCalculator
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Statistics-focused detail screen for a saved training: map with scrubber, stats grid, stacked charts. */
@Composable
fun TrainingDetailScreen(trackId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember { HudProApplication.from(context) }
    val prefs = remember { ViewerPreferences.get(context) }
    val scope = rememberCoroutineScope()
    val mapView = rememberMapViewWithLifecycle()
    val custom = remember(prefs.customActivityTypesJson) { ActivityTypes.decodeCustom(prefs.customActivityTypesJson) }

    var entity by remember { mutableStateOf<FollowTrackEntity?>(null) }
    var stats by remember { mutableStateOf<TrackStats?>(null) }
    var samples by remember { mutableStateOf<List<TrackSample>>(emptyList()) }
    var controller by remember { mutableStateOf<RouteEditorController?>(null) }
    var reloadTick by remember { mutableStateOf(0) }

    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(trackId, reloadTick) {
        entity = app.trackRepository.get(trackId)
        val points = app.trackRepository.loadGpxRoute(trackId)
        val (s, smp) = withContext(Dispatchers.Default) {
            TrackStatsCalculator.compute(points) to TrackStatsCalculator.samples(points)
        }
        stats = s
        samples = smp
    }
    // Draw the track once both the map and the data are ready.
    LaunchedEffect(controller, samples) {
        val c = controller ?: return@LaunchedEffect
        if (samples.size >= 2) {
            val line = samples.map { cat.hudpro.opentracks.data.gpx.GpxPoint(it.lat, it.lon) }
            c.setRoute(line)
            c.frame(line)
        }
    }

    fun nearestSample(fraction: Float): TrackSample? {
        if (samples.isEmpty()) return null
        val target = fraction * samples.last().distM
        return samples.minByOrNull { kotlin.math.abs(it.distM - target) }
    }

    fun scrub(fraction: Float?) {
        highlight = fraction
        val sample = fraction?.let(::nearestSample)
        controller?.setHighlight(sample?.let { GeoPoint(it.lat, it.lon) })
    }

    DetailScaffold(
        title = entity?.name ?: stringResource(R.string.training_fallback_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                scope.launch { app.trackRepository.get(trackId)?.let { GpxShare.share(context, it.name, it.gpx) } }
            }) {
                Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.training_action_export))
            }
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.training_action_rename))
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.training_cd_more_actions))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.training_action_duplicate_route)) },
                    onClick = {
                        showMenu = false
                        scope.launch {
                            app.trackRepository.duplicateAsRoute(trackId)
                            Toast.makeText(context, context.getString(R.string.training_duplicated_toast), Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.training_action_type)) },
                    onClick = { showMenu = false; showTypePicker = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.training_action_move)) },
                    onClick = { showMenu = false; showMove = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.training_action_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; showDelete = true },
                )
            }
        },
    ) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AndroidView(
                factory = {
                    mapView.getMapAsync { map ->
                        val c = RouteEditorController(map)
                        controller = c
                        c.init { }
                    }
                    mapView
                },
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )

            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entity?.let { e -> TrainingHeader(e, custom) }

                stats?.let { s -> StatsCard(s) }

                if (samples.size >= 2) {
                    Card {
                        StackedTrackChart(
                            samples = samples,
                            highlightFraction = highlight,
                            onScrub = ::scrub,
                            modifier = Modifier.fillMaxWidth().height(240.dp).padding(vertical = 8.dp),
                        )
                    }
                }

                val h = highlight
                if (h != null) {
                    nearestSample(h)?.let { sample -> ScrubInfoCard(sample, samples.firstOrNull()?.time) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showRename) {
        TextDialog(
            title = stringResource(R.string.training_action_rename),
            initial = entity?.name ?: "",
            confirm = stringResource(R.string.training_save),
            onDismiss = { showRename = false },
        ) { name ->
            showRename = false
            scope.launch { app.trackRepository.rename(trackId, name); reloadTick++ }
        }
    }
    if (showTypePicker) {
        val options = rememberActivityTypeOptions(prefs)
        AlertDialog(
            onDismissRequest = { showTypePicker = false },
            title = { Text(stringResource(R.string.training_action_type)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showTypePicker = false
                                    scope.launch { app.trackRepository.setActivityType(trackId, option.id); reloadTick++ }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(option.icon, contentDescription = null)
                            Text(option.label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTypePicker = false }) { Text(stringResource(R.string.training_cancel)) }
            },
        )
    }
    if (showMove) {
        MoveToFolderDialog(
            folders = prefs.foldersTraining.toList().sorted(),
            current = entity?.collection ?: ROOT,
            onDismiss = { showMove = false },
            onMove = { folder ->
                showMove = false
                scope.launch {
                    app.trackRepository.setCollection(trackId, folder)
                    if (folder != ROOT) prefs.foldersTraining = prefs.foldersTraining + folder
                    reloadTick++
                }
            },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.training_action_delete)) },
            text = { Text(stringResource(R.string.training_delete_confirm, entity?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    scope.launch { app.trackRepository.delete(trackId); onBack() }
                }) { Text(stringResource(R.string.training_action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.training_cancel)) }
            },
        )
    }
}

@Composable
private fun TrainingHeader(e: FollowTrackEntity, custom: List<cat.hudpro.opentracks.data.tracks.CustomActivityType>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(activityTypeIcon(e.activityType, custom), contentDescription = activityTypeLabel(e.activityType, custom), modifier = Modifier.size(32.dp))
        Column {
            Text(e.name, style = MaterialTheme.typography.titleMedium)
            val date = remember(e.createdAt) {
                DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(e.createdAt))
            }
            val difficulty = DifficultyCalculator.bandOf(e.distanceMeters, e.ascentM)
            val parts = mutableListOf<String>()
            e.municipality?.takeIf { it.isNotBlank() }?.let(parts::add)
            parts.add(date)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    parts.joinToString(" · ") + " · ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(difficultyLabelRes(difficulty)),
                    style = MaterialTheme.typography.bodySmall,
                    color = difficultyColor(difficulty),
                )
            }
        }
    }
}

private fun difficultyLabelRes(d: Difficulty): Int = when (d) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MODERATE -> R.string.difficulty_moderate
    Difficulty.HARD -> R.string.difficulty_hard
    Difficulty.VERY_HARD -> R.string.difficulty_very_hard
}

private fun difficultyColor(d: Difficulty): Color = when (d) {
    Difficulty.EASY -> Color(0xFF2A9D8F)
    Difficulty.MODERATE -> Color(0xFFF4A261)
    Difficulty.HARD -> Color(0xFFE76F51)
    Difficulty.VERY_HARD -> Color(0xFFE63946)
}

@Composable
private fun StatsCard(s: TrackStats) {
    val cells = buildList {
        add(stringResource(R.string.training_stat_distance) to String.format("%.1f km", s.distanceM / 1000.0))
        add(stringResource(R.string.training_stat_total_time) to (s.totalTime?.let(::formatDuration) ?: "—"))
        add(stringResource(R.string.training_stat_moving_time) to (s.movingTime?.let(::formatDuration) ?: "—"))
        add(stringResource(R.string.training_stat_avg_speed) to (s.avgSpeedKmh?.let { String.format("%.1f km/h", it) } ?: "—"))
        add(stringResource(R.string.training_stat_max_speed) to (s.maxSpeedKmh?.let { String.format("%.1f km/h", it) } ?: "—"))
        add(stringResource(R.string.training_stat_ascent) to "${s.ascentM.toInt()} m")
        add(stringResource(R.string.training_stat_descent) to "${s.descentM.toInt()} m")
        s.avgHr?.let { add(stringResource(R.string.training_stat_avg_hr) to "${it.toInt()} bpm") }
        s.maxHr?.let { add(stringResource(R.string.training_stat_max_hr) to "${it.toInt()} bpm") }
        s.avgCadence?.let { add(stringResource(R.string.training_stat_avg_cadence) to "${it.toInt()} rpm") }
        s.avgPower?.let { add(stringResource(R.string.training_stat_avg_power) to "${it.toInt()} W") }
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cells.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                            Text(value, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ScrubInfoCard(sample: TrackSample, startTime: Instant?) {
    val cells = buildList {
        if (sample.time != null && startTime != null) {
            add(stringResource(R.string.training_scrub_time) to formatDuration(Duration.between(startTime, sample.time)))
        }
        sample.elevation?.let { add(stringResource(R.string.training_scrub_altitude) to "${it.toInt()} m") }
        sample.speedKmh?.let { add(stringResource(R.string.training_scrub_speed) to String.format("%.1f km/h", it)) }
        sample.hr?.let { add(stringResource(R.string.training_scrub_hr) to "${it.toInt()} bpm") }
    }
    if (cells.isEmpty()) return
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            cells.forEach { (label, value) ->
                Column {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    Text(value, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun formatDuration(d: Duration): String {
    val totalSeconds = d.seconds.coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format("%d:%02d:%02d", h, m, s)
}
