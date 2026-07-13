package cat.rumb.app.manager.screens

import androidx.compose.foundation.clickable
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

    var records by remember { mutableStateOf<List<Record>?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }

    LaunchedEffect(Unit) {
        val all = app.trackRepository.observeSummaries().first()
        records = withContext(Dispatchers.Default) {
            PersonalRecords.compute(
                tracks = all,
                loadPoints = { app.trackRepository.loadGpxRoute(it) },
                onProgress = { done, total -> progress = done to total },
            )
        }
    }

    DetailScaffold(title = stringResource(R.string.records_title), onBack = onBack) { modifier ->
        val recs = records
        when {
            recs == null -> {
                Column(
                    modifier.fillMaxSize(),
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
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    modifier.fillMaxSize(),
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
    -> formatHms(record.valueMs ?: 0L)
    RecordKind.LONGEST_DISTANCE -> String.format("%.1f km", (record.value ?: 0.0) / 1000.0)
    RecordKind.MAX_ASCENT -> "${(record.value ?: 0.0).toInt()} m"
    RecordKind.MAX_SPEED -> String.format("%.1f km/h", record.value ?: 0.0)
    RecordKind.LONGEST_TIME -> formatHms((record.value ?: 0.0).toLong())
}

private fun formatHms(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format("%d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
}
