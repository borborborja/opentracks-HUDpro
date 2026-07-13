package cat.rumb.app.manager.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cat.rumb.app.R
import cat.rumb.app.data.tracks.FollowTrackEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** h:mm:ss (durations are elapsed times, hours may exceed 9). */
private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d:%02d".format(totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60)
}

/**
 * Competition tab of Home: one card per reference track (a training «sent to competition»),
 * with the best time, the attempt count, a play button to race against it and an expandable
 * list of past attempts.
 */
@Composable
fun CompetitionTab(
    all: List<FollowTrackEntity>,
    onOpen: (Long) -> Unit,
    onPlay: (Long) -> Unit,
) {
    val refs = all.filter { it.isCompetition }
    val attempts = all.filter { it.competitionRefId != null }.groupBy { it.competitionRefId }

    if (refs.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.home_competition_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.home_competition_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(refs, key = { it.id }) { ref ->
            CompetitionCard(
                ref = ref,
                attempts = attempts[ref.id].orEmpty(),
                onOpen = { onOpen(ref.id) },
                onPlay = { onPlay(ref.id) },
            )
        }
    }
}

@Composable
private fun CompetitionCard(
    ref: FollowTrackEntity,
    attempts: List<FollowTrackEntity>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val timed = (attempts + ref).mapNotNull { it.durationMs }.filter { it > 0 }
    val best = timed.minOrNull()

    Card(onClick = onOpen) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    ActivityTypeCatalog.iconFor(ref.activityType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(ref.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(
                        (best?.let { formatDuration(it) } ?: "—") + " · " +
                            stringResource(R.string.home_competition_attempts, attempts.size),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onPlay) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.home_competition_play),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
                Column(Modifier.fillMaxWidth().padding(start = 30.dp, top = 4.dp, bottom = 4.dp)) {
                    attempts.sortedByDescending { it.createdAt }.forEach { attempt ->
                        val duration = attempt.durationMs?.takeIf { it > 0 }
                        val isBest = best != null && duration == best
                        val color = if (isBest) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                dateFormat.format(Date(attempt.createdAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = color,
                                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                duration?.let { formatDuration(it) } ?: "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = color,
                                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (isBest) {
                                Spacer(Modifier.size(4.dp))
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
