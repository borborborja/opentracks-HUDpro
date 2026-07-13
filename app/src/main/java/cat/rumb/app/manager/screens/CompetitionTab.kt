package cat.rumb.app.manager.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * Competition tab of Home: one card per ACTIVE reference track, plus a fixed "Archived" section.
 * The library (Recorded tab) always owns the tracks — everything here only flips flags/links.
 */
@Composable
fun CompetitionTab(
    viewMode: String = "DETAILED",
    all: List<FollowTrackEntity>,
    onOpen: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    onArchive: (Long, Boolean) -> Unit,
    onDeleteCompetition: (Long) -> Unit,
    onRemoveAttempt: (Long) -> Unit,
) {
    val refs = all.filter { it.isCompetition && !it.competitionArchived }
    val archivedRefs = all.filter { it.isCompetition && it.competitionArchived }
    val attempts = all.filter { it.competitionRefId != null }.groupBy { it.competitionRefId }
    var deleteFor by remember { mutableStateOf<FollowTrackEntity?>(null) }
    var archivedOpen by remember { mutableStateOf(false) }

    if (refs.isEmpty() && archivedRefs.isEmpty()) {
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
        when (viewMode) {
            // Tiles: two per row via chunking (no nested lazy grid inside the LazyColumn).
            "TILES" -> items(refs.chunked(2), key = { it.first().id }) { rowRefs ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowRefs.forEach { ref ->
                        CompetitionTile(
                            ref = ref,
                            attempts = attempts[ref.id].orEmpty(),
                            onOpen = { onOpen(ref.id) },
                            onPlay = { onPlay(ref.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowRefs.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            "LIST" -> items(refs, key = { it.id }) { ref ->
                CompetitionCompactRow(
                    ref = ref,
                    attempts = attempts[ref.id].orEmpty(),
                    onOpen = { onOpen(ref.id) },
                    onPlay = { onPlay(ref.id) },
                )
            }
            else -> items(refs, key = { it.id }) { ref ->
                CompetitionCard(
                    ref = ref,
                    attempts = attempts[ref.id].orEmpty(),
                    archived = false,
                    onOpen = { onOpen(ref.id) },
                    onPlay = { onPlay(ref.id) },
                    onArchive = { onArchive(ref.id, true) },
                    onUnarchive = {},
                    onDelete = { deleteFor = ref },
                    onRemoveAttempt = onRemoveAttempt,
                )
            }
        }
        // Fixed "Archived" section: competitions here keep their membership until unarchived.
        if (archivedRefs.isNotEmpty()) {
            item(key = "archived-header") {
                Card(onClick = { archivedOpen = !archivedOpen }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        Text(
                            "  " + stringResource(R.string.competition_archived_section),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${archivedRefs.size}  ", style = MaterialTheme.typography.bodySmall)
                        Icon(if (archivedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                }
            }
            if (archivedOpen) {
                items(archivedRefs, key = { "arch-${it.id}" }) { ref ->
                    CompetitionCard(
                        ref = ref,
                        attempts = attempts[ref.id].orEmpty(),
                        archived = true,
                        onOpen = { onOpen(ref.id) },
                        onPlay = {},
                        onArchive = {},
                        onUnarchive = { onArchive(ref.id, false) },
                        onDelete = { deleteFor = ref },
                        onRemoveAttempt = onRemoveAttempt,
                    )
                }
            }
        }
    }

    deleteFor?.let { ref ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text(stringResource(R.string.competition_delete)) },
            text = { Text(stringResource(R.string.competition_delete_msg)) },
            confirmButton = {
                TextButton(onClick = { onDeleteCompetition(ref.id); deleteFor = null }) {
                    Text(stringResource(R.string.home_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFor = null }) { Text(stringResource(R.string.home_cancel)) }
            },
        )
    }
}

@Composable
private fun CompetitionCard(
    ref: FollowTrackEntity,
    attempts: List<FollowTrackEntity>,
    archived: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onRemoveAttempt: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
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
                if (!archived) {
                    IconButton(onClick = onPlay) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.home_competition_play),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.competition_cd_menu))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (archived) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.competition_unarchive)) },
                                onClick = { menu = false; onUnarchive() },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.competition_archive)) },
                                onClick = { menu = false; onArchive() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.competition_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
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
                            // The track stays in the library; only the competition link is removed.
                            IconButton(onClick = { onRemoveAttempt(attempt.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.competition_remove_attempt),
                                    tint = MaterialTheme.colorScheme.outline,
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

/** Compact list row: type icon, name, best time and a play shortcut. */
@Composable
private fun CompetitionCompactRow(
    ref: FollowTrackEntity,
    attempts: List<FollowTrackEntity>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val best = (attempts + ref).mapNotNull { it.durationMs }.filter { it > 0 }.minOrNull()
    Card(onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                ActivityTypeCatalog.iconFor(ref.activityType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "  " + ref.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(best?.let { formatDuration(it) } ?: "—", style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.home_competition_play),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Grid tile: icon + name + best time + attempts, with a play shortcut. */
@Composable
private fun CompetitionTile(
    ref: FollowTrackEntity,
    attempts: List<FollowTrackEntity>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val best = (attempts + ref).mapNotNull { it.durationMs }.filter { it > 0 }.minOrNull()
    Card(onClick = onOpen, modifier = modifier.height(120.dp)) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    ActivityTypeCatalog.iconFor(ref.activityType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp).weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPlay, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.home_competition_play),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column {
                Text(ref.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    (best?.let { formatDuration(it) } ?: "—") + " · " +
                        stringResource(R.string.home_competition_attempts, attempts.size),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}
