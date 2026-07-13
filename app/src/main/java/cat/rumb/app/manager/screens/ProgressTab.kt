package cat.rumb.app.manager.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cat.rumb.app.R
import cat.rumb.app.data.prefs.ViewerPreferences
import cat.rumb.app.data.tracks.FollowTrackEntity
import cat.rumb.app.data.tracks.ProgressStats
import cat.rumb.app.data.tracks.WeekBucket
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val DeltaGreen = Color(0xFF2ECC71)
private val DeltaRed = Color(0xFFE63946)
private val StreakOrange = Color(0xFFF4A261)

/** «Progrés» tab: training-volume dashboard over the track summaries. */
@Composable
fun ProgressTab(
    all: List<FollowTrackEntity>,
    onOpenRecords: () -> Unit,
    onOpenHeatmap: () -> Unit,
    onOpenTraining: (Long) -> Unit = {},
) {
    val prefs = ViewerPreferences.get(LocalContext.current)
    val typeOptions = rememberActivityTypeOptions(prefs)
    var typeFilter by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // a) Activity-type filter (single select, «Tots» = null).
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = typeFilter == null,
                onClick = { typeFilter = null },
                label = { Text(stringResource(R.string.progress_all)) },
            )
            typeOptions.forEach { option ->
                FilterChip(
                    selected = typeFilter == option.id,
                    onClick = { typeFilter = if (typeFilter == option.id) null else option.id },
                    label = { Text(option.label) },
                    leadingIcon = { Icon(option.icon, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }

        val lastTwo = ProgressStats.weekly(all, weeks = 2, typeFilter = typeFilter)
        ThisWeekCard(current = lastTwo.last(), previous = lastTwo.first())

        WeeksChartCard(ProgressStats.weekly(all, weeks = 12, typeFilter = typeFilter))

        StreakCard(ProgressStats.streakWeeks(all))

        TotalsCard(all, typeFilter)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EntryCard(
                icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = StreakOrange) },
                label = stringResource(R.string.records_entry),
                onClick = onOpenRecords,
                modifier = Modifier.weight(1f),
            )
            EntryCard(
                icon = { Icon(Icons.Filled.Whatshot, contentDescription = null, tint = DeltaRed) },
                label = stringResource(R.string.heatmap_entry),
                onClick = onOpenHeatmap,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// --- «Aquesta setmana» with deltas vs the previous week ---

@Composable
private fun ThisWeekCard(current: WeekBucket, previous: WeekBucket) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.progress_this_week), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.progress_vs_last_week), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            MetricRow(stringResource(R.string.progress_km), String.format(Locale.US, "%.1f", current.km), delta(current.km, previous.km))
            MetricRow(stringResource(R.string.progress_hours), hoursMinutes(current.hours), delta(current.hours, previous.hours))
            MetricRow(stringResource(R.string.progress_ascent), "${current.ascentM.roundToInt()} m", delta(current.ascentM, previous.ascentM))
            MetricRow(stringResource(R.string.progress_activities), "${current.count}", delta(current.count.toDouble(), previous.count.toDouble()))
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, delta: Pair<String, Color>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(delta.first, style = MaterialTheme.typography.bodySmall, color = delta.second)
    }
}

/** «+12%» green / «−8%» red vs previous; em-dash when there is no previous baseline. */
@Composable
private fun delta(current: Double, previous: Double): Pair<String, Color> {
    if (previous <= 0.0) return "—" to MaterialTheme.colorScheme.outline
    val pct = ((current - previous) / previous * 100).roundToInt()
    return when {
        pct > 0 -> "+$pct%" to DeltaGreen
        pct < 0 -> "−${abs(pct)}%" to DeltaRed
        else -> "0%" to MaterialTheme.colorScheme.outline
    }
}

private fun hoursMinutes(hours: Double): String {
    val totalMin = (hours * 60).roundToInt()
    return "${totalMin / 60}:${String.format(Locale.US, "%02d", totalMin % 60)} h"
}

// --- 12-week km bar chart ---

@Composable
private fun WeeksChartCard(weeks: List<WeekBucket>) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.progress_weeks_chart), fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                if (weeks.isEmpty()) return@Canvas
                val maxKm = weeks.maxOf { it.km }.takeIf { it > 0 } ?: 1.0
                val slot = size.width / weeks.size
                val barW = slot * 0.6f
                val minBar = 2.dp.toPx()
                weeks.forEachIndexed { i, w ->
                    val h = when {
                        w.km > 0 -> ((w.km / maxKm) * size.height).toFloat().coerceAtLeast(minBar)
                        w.count > 0 -> minBar
                        else -> 0f
                    }
                    if (h > 0f) {
                        drawRect(
                            color = if (i == weeks.lastIndex) primary else outline,
                            topLeft = Offset(i * slot + (slot - barW) / 2f, size.height - h),
                            size = Size(barW, h),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(monthLabel(weeks.first().startEpochDay), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text(monthLabel(weeks.last().startEpochDay), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun monthLabel(startEpochDay: Long): String =
    LocalDate.ofEpochDay(startEpochDay).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())

// --- Streak ---

@Composable
private fun StreakCard(streak: Int) {
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = StreakOrange)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.progress_streak_weeks, streak), fontWeight = FontWeight.Bold)
        }
    }
}

// --- All-time totals ---

@Composable
private fun TotalsCard(all: List<FollowTrackEntity>, typeFilter: String?) {
    val totals = ProgressStats.totals(all, typeFilter)
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.progress_totals), fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TotalCell(String.format(Locale.US, "%.1f", totals.km), stringResource(R.string.progress_km))
                TotalCell("${totals.hours.toInt()} h", stringResource(R.string.progress_hours))
                TotalCell("${totals.ascentM.roundToInt()} m", stringResource(R.string.progress_ascent))
                TotalCell("${totals.count}", stringResource(R.string.progress_activities))
            }
        }
    }
}

@Composable
private fun TotalCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

// --- Records / Heatmap entry points ---

@Composable
private fun EntryCard(icon: @Composable () -> Unit, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon()
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}
