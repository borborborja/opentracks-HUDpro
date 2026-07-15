package cat.rumb.app.manager.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cat.rumb.app.R
import cat.rumb.app.RumbApplication
import cat.rumb.app.data.tracks.PersonalRecords
import cat.rumb.app.data.tracks.Record
import cat.rumb.app.data.tracks.RecordKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Personal records over the whole training library, one card per best, tap opens the training. */
@Composable
fun RecordsScreen(onBack: () -> Unit, onOpenTraining: (Long) -> Unit) {
    val context = LocalContext.current
    val app = remember { RumbApplication.from(context) }

    val prefs = remember { cat.rumb.app.data.prefs.ViewerPreferences.get(context) }
    val custom = remember(prefs.customActivityTypesJson) {
        cat.rumb.app.data.tracks.ActivityTypes.decodeCustom(prefs.customActivityTypesJson)
    }

    var records by remember { mutableStateOf<List<Record>?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    var summaries by remember { mutableStateOf<List<cat.rumb.app.data.tracks.FollowTrackEntity>>(emptyList()) }
    // Records are per family: a 5K set on a bike is not a running record.
    var family by remember { mutableStateOf<cat.rumb.app.data.tracks.ActivityFamily?>(null) }

    LaunchedEffect(Unit) {
        val all = app.trackRepository.observeSummaries().first()
        summaries = all
        // Default to whatever the user records most, so the screen opens on something meaningful.
        family = PersonalRecords.familiesIn(all, custom).firstOrNull()
    }
    LaunchedEffect(summaries, family) {
        if (summaries.isEmpty()) return@LaunchedEffect
        records = null
        records = withContext(Dispatchers.Default) {
            PersonalRecords.compute(
                tracks = summaries,
                loadPoints = { app.trackRepository.loadGpxRoute(it) },
                family = family,
                custom = custom,
                onProgress = { done, total -> progress = done to total },
            )
        }
    }

    DetailScaffold(title = stringResource(R.string.records_title), onBack = onBack) { modifier ->
      Column(modifier.fillMaxSize()) {
        val families = remember(summaries, custom) { PersonalRecords.familiesIn(summaries, custom) }
        if (families.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                families.forEach { f ->
                    FilterChip(
                        selected = family == f,
                        onClick = { family = f },
                        label = { Text(stringResource(activityFamilyLabel(f))) },
                    )
                }
            }
        }
        val recs = records
        when {
            recs == null -> {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.records_computing, progress.first, progress.second),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            recs.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.records_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(recs) { record ->
                        RecordCard(record) { onOpenTraining(record.trackId) }
                    }
                }
            }
        }
      }
    }
}

/** Label for an activity family (records filter, custom-type editor). */
@Composable
fun activityFamilyLabel(f: cat.rumb.app.data.tracks.ActivityFamily): Int = when (f) {
    cat.rumb.app.data.tracks.ActivityFamily.FOOT -> R.string.family_foot
    cat.rumb.app.data.tracks.ActivityFamily.WHEEL -> R.string.family_wheel
    cat.rumb.app.data.tracks.ActivityFamily.SNOW -> R.string.family_snow
    cat.rumb.app.data.tracks.ActivityFamily.SKATE -> R.string.family_skate
    cat.rumb.app.data.tracks.ActivityFamily.PADDLE -> R.string.family_paddle
    cat.rumb.app.data.tracks.ActivityFamily.SWIM -> R.string.family_swim
    cat.rumb.app.data.tracks.ActivityFamily.UNKNOWN -> R.string.family_unknown
}

@Composable
private fun RecordCard(record: Record, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = Color(0xFFF4A261),
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(stringResource(recordTitleRes(record.kind)), style = MaterialTheme.typography.labelLarge)
                Text(recordValueText(record), style = MaterialTheme.typography.headlineSmall)
                val date = remember(record.dateMs) {
                    DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(record.dateMs))
                }
                Text(
                    "${record.trackName} · $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun recordTitleRes(kind: RecordKind): Int = when (kind) {
    RecordKind.FASTEST_1K -> R.string.records_fastest_1k
    RecordKind.FASTEST_5K -> R.string.records_fastest_5k
    RecordKind.FASTEST_10K -> R.string.records_fastest_10k
    RecordKind.FASTEST_HALF -> R.string.records_fastest_half
    RecordKind.FASTEST_MARATHON -> R.string.records_fastest_marathon
    RecordKind.LONGEST_DISTANCE -> R.string.records_longest
    RecordKind.MAX_ASCENT -> R.string.records_max_ascent
    RecordKind.MAX_SPEED -> R.string.records_max_speed
    RecordKind.LONGEST_TIME -> R.string.records_longest_time
}

private fun recordValueText(record: Record): String = when (record.kind) {
    RecordKind.FASTEST_1K,
    RecordKind.FASTEST_5K,
    RecordKind.FASTEST_10K,
    RecordKind.FASTEST_HALF,
    RecordKind.FASTEST_MARATHON,
    -> formatHms(record.valueMs ?: 0L)
    RecordKind.LONGEST_DISTANCE -> String.format("%.1f km", (record.value ?: 0.0) / 1000.0)
    RecordKind.MAX_ASCENT -> "${(record.value ?: 0.0).toInt()} m"
    RecordKind.MAX_SPEED -> String.format("%.1f km/h", record.value ?: 0.0)
    RecordKind.LONGEST_TIME -> formatHms((record.value ?: 0.0).toLong())
}
