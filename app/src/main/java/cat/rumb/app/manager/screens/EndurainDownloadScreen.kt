package cat.rumb.app.manager.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cat.rumb.app.R
import cat.rumb.app.RumbApplication
import cat.rumb.app.data.endurain.EndurainActivity
import cat.rumb.app.data.endurain.EndurainImport
import cat.rumb.app.data.endurain.EndurainNoGpsException
import cat.rumb.app.data.endurain.EndurainRepository
import cat.rumb.app.data.prefs.EndurainPreferences
import cat.rumb.app.data.tracks.TrackMetadataBackfillWorker
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 25

/**
 * Lists the user's Endurain activities (newest first, paged) and imports a chosen one into the
 * library, rebuilding its track from the server's streams. Reads need a JWT session, so this is only
 * available in credentials mode; already-imported activities are marked and can't be duplicated.
 */
@Composable
fun EndurainDownloadScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember { RumbApplication.from(context) }
    val endurainPrefs = remember { EndurainPreferences.get(context) }
    val repo = remember { EndurainRepository(endurainPrefs) }
    val scope = rememberCoroutineScope()

    val ready = endurainPrefs.isConfigured &&
        endurainPrefs.authMode == EndurainPreferences.AuthMode.CREDENTIALS

    var activities by remember { mutableStateOf<List<EndurainActivity>>(emptyList()) }
    var known by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var importingId by remember { mutableStateOf<Long?>(null) }

    fun loadNext() {
        if (loading || !canLoadMore) return
        loading = true
        error = null
        scope.launch {
            repo.listActivities(page, PAGE_SIZE)
                .onSuccess { batch ->
                    // distinctBy guards the LazyColumn key against an overlapping page from the server.
                    activities = (activities + batch).distinctBy { it.id }
                    if (batch.size < PAGE_SIZE) canLoadMore = false else page += 1
                }
                .onFailure { error = it.message ?: context.getString(R.string.endurain_dl_error) }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (ready) {
            known = app.trackRepository.knownEndurainIds()
            loadNext()
        }
    }

    DetailScaffold(title = stringResource(R.string.settings_sync_download), onBack = onBack) { modifier ->
        if (!ready) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.endurain_dl_need_credentials),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@DetailScaffold
        }
        LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(activities, key = { it.id }) { act ->
                ActivityRow(
                    act = act,
                    imported = act.id in known,
                    importing = importingId == act.id,
                    onImport = {
                        if (importingId != null) return@ActivityRow
                        importingId = act.id
                        scope.launch {
                            repo.importActivity(act.id, app.trackRepository)
                                .onSuccess {
                                    known = known + act.id
                                    TrackMetadataBackfillWorker.enqueue(context)
                                    toast(context, R.string.endurain_dl_ok)
                                }
                                .onFailure {
                                    toast(
                                        context,
                                        if (it is EndurainNoGpsException) R.string.endurain_dl_no_gps
                                        else R.string.endurain_dl_error,
                                    )
                                }
                            importingId = null
                        }
                    },
                )
            }
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator()
                        error != null -> Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        canLoadMore && activities.isNotEmpty() ->
                            OutlinedButton(onClick = { loadNext() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.endurain_dl_load_more))
                            }
                        activities.isEmpty() -> Text(
                            stringResource(R.string.endurain_dl_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    act: EndurainActivity,
    imported: Boolean,
    importing: Boolean,
    onImport: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    act.name?.takeIf { it.isNotBlank() } ?: "Endurain ${act.id}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    activitySubtitle(act),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                importing -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                imported -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        stringResource(R.string.endurain_dl_imported),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                else -> IconButton(onClick = onImport) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = stringResource(R.string.endurain_dl_import_cd),
                    )
                }
            }
        }
    }
}

/** "12/03/2026 · 8.4 km" — whichever parts are available. */
private fun activitySubtitle(act: EndurainActivity): String {
    val parts = mutableListOf<String>()
    EndurainImport.parseTime(act.startTime)?.let {
        parts += DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault())
            .format(it)
    }
    act.distance?.takeIf { it > 0 }?.let {
        parts += String.format(Locale.US, "%.1f km", it / 1000.0)
    }
    return parts.joinToString(" · ")
}

private fun toast(context: android.content.Context, res: Int) {
    android.widget.Toast.makeText(context, context.getString(res), android.widget.Toast.LENGTH_SHORT).show()
}
