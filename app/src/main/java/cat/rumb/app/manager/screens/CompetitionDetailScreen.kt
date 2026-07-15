package cat.rumb.app.manager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.rumb.app.R
import cat.rumb.app.RumbApplication
import cat.rumb.app.data.competition.CompetitionAnalysis
import cat.rumb.app.data.competition.GapSample
import cat.rumb.app.data.gpx.Gpx
import cat.rumb.app.data.gpx.GpxPoint
import cat.rumb.app.data.prefs.ViewerPreferences
import cat.rumb.app.data.tracks.CompetitionAttemptEntity
import cat.rumb.app.data.tracks.CompetitionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ZoneColors = listOf(
    Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFF44336),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionDetailScreen(competitionId: Long, onBack: () -> Unit, onStartCompetition: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val app = remember { RumbApplication.from(context) }
    val scope = rememberCoroutineScope()
    val prefs = remember { ViewerPreferences.get(context) }
    val maxHr = remember { prefs.userMaxHr }
    val competitions by remember { app.competitionRepository.observeCompetitions() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val attempts by remember(competitionId) { app.competitionRepository.attemptsFor(competitionId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val competition = competitions.firstOrNull { it.id == competitionId }
    val name = competition?.name ?: ""
    val isLap = competition?.type == CompetitionType.LAP

    var pointsById by remember { mutableStateOf<Map<Long, List<GpxPoint>>>(emptyMap()) }
    LaunchedEffect(attempts) {
        val m = withContext(Dispatchers.Default) {
            attempts.associate { it.id to runCatching { Gpx.read(it.gpx.byteInputStream()).points }.getOrDefault(emptyList()) }
        }
        pointsById = m
    }

    var menuOpen by remember { mutableStateOf(false) }
    var renameTo by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name.ifBlank { stringResource(R.string.home_tab_competition) })
                        TypeBadge(isLap)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onStartCompetition(competitionId) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.circuit_start))
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_rename)) },
                            onClick = { menuOpen = false; renameTo = name },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_delete)) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (attempts.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.circuit_empty_efforts))
                }
            } else {
                val best = attempts.minByOrNull { it.timeMs }
                val bestMs = best?.timeMs
                Text(
                    stringResource(if (isLap) R.string.circuit_efforts_count else R.string.competition_attempts_count, attempts.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                attempts.forEachIndexed { rank, a -> AttemptRow(rank + 1, a, bestMs) }

                if (best != null && attempts.size >= 2) {
                    GapCard(best, attempts.filter { it.id != best.id }, pointsById)
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.comp_evolution_title), style = MaterialTheme.typography.titleSmall)
                            val byDate = attempts.sortedBy { it.createdAt }
                            EvolutionBars(byDate.map { it.timeMs.toFloat() }, lowerBetter = true, Modifier.fillMaxWidth().height(120.dp))
                        }
                    }
                }
                HrZonesCard(attempts, pointsById, maxHr)
            }
        }
    }

    renameTo?.let { current ->
        var text by remember(current) { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { renameTo = null },
            title = { Text(stringResource(R.string.home_rename)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = text.trim()
                    if (n.isNotEmpty()) scope.launch { app.competitionRepository.rename(competitionId, n) }
                    renameTo = null
                }) { Text(stringResource(R.string.home_save)) }
            },
            dismissButton = { TextButton(onClick = { renameTo = null }) { Text(stringResource(R.string.home_cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.home_delete)) },
            text = { Text(stringResource(R.string.home_delete_confirm, name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { app.competitionRepository.delete(competitionId); onBack() }
                }) { Text(stringResource(R.string.home_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.home_cancel)) } },
        )
    }
}

@Composable
private fun TypeBadge(isLap: Boolean) {
    Text(
        stringResource(if (isLap) R.string.competition_type_lap else R.string.competition_type_route),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun GapCard(best: CompetitionAttemptEntity, others: List<CompetitionAttemptEntity>, pointsById: Map<Long, List<GpxPoint>>) {
    if (others.isEmpty()) return
    var selectedId by remember(others) { mutableStateOf(others.minByOrNull { it.timeMs }?.id ?: others.first().id) }
    var series by remember { mutableStateOf<List<GapSample>>(emptyList()) }
    LaunchedEffect(selectedId, pointsById) {
        val bestPts = pointsById[best.id].orEmpty()
        val selPts = pointsById[selectedId].orEmpty()
        series = withContext(Dispatchers.Default) { CompetitionAnalysis.gapOverDistance(bestPts, selPts) }
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.competition_gap_chart_title), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { a ->
                    FilterChip(selected = selectedId == a.id, onClick = { selectedId = a.id }, label = { Text(fmtTime(a.timeMs)) })
                }
            }
            GapChart(series, Modifier.fillMaxWidth().height(160.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(GapGreenSolid, stringResource(R.string.competition_gap_ahead))
                LegendDot(GapRedSolid, stringResource(R.string.competition_gap_behind))
            }
        }
    }
}

@Composable
private fun HrZonesCard(attempts: List<CompetitionAttemptEntity>, pointsById: Map<Long, List<GpxPoint>>, maxHr: Int) {
    val zonesById = remember(pointsById, maxHr) {
        attempts.associate { it.id to CompetitionAnalysis.hrZones(pointsById[it.id].orEmpty(), maxHr) }
    }
    if (zonesById.values.none { it.sum() > 0L }) return
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.competition_hr_zones_title), style = MaterialTheme.typography.titleSmall)
            attempts.forEach { a ->
                val zones = zonesById[a.id]
                if (zones != null && zones.sum() > 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(fmtTime(a.timeMs), style = MaterialTheme.typography.labelSmall, modifier = Modifier.size(width = 56.dp, height = 16.dp))
                        Row(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(4.dp))) {
                            zones.forEachIndexed { i, ms ->
                                if (ms > 0L) Box(Modifier.weight(ms.toFloat()).fillMaxSize().background(ZoneColors[i]))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttemptRow(rank: Int, a: CompetitionAttemptEntity, bestMs: Long?) {
    val isBest = bestMs != null && a.timeMs == bestMs
    Card {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isBest) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            } else {
                Text("$rank", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    fmtTime(a.timeMs),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                    color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    fmtDate(a.createdAt) + " · " + "%.2f km".format(a.distanceM / 1000.0) +
                        (a.avgHr?.let { " · %d bpm".format(it.toInt()) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val gap = if (bestMs != null) a.timeMs - bestMs else 0L
            Text(
                if (isBest) stringResource(R.string.home_laps_best) else "+" + fmtTime(gap),
                style = MaterialTheme.typography.labelMedium,
                color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun fmtTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun fmtDate(ms: Long): String =
    if (ms <= 0) "" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateFmt)
