package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cat.hudpro.opentracks.BuildConfig
import cat.hudpro.opentracks.data.update.ApkInstaller
import cat.hudpro.opentracks.data.update.UpdateInfo
import cat.hudpro.opentracks.data.update.UpdateRepository
import kotlinx.coroutines.launch

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object Downloading : UpdateState
    data class Error(val message: String) : UpdateState
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { UpdateRepository() }

    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }

    DetailScaffold(title = "Ajustos", onBack = onBack) { modifier ->
        Column(
            modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Versió instal·lada", style = MaterialTheme.typography.labelMedium)
                    Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleMedium)
                }
            }

            Button(
                onClick = {
                    state = UpdateState.Checking
                    scope.launch {
                        state = try {
                            repo.checkForUpdate()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
                        } catch (e: Exception) {
                            UpdateState.Error(e.message ?: "Error de xarxa")
                        }
                    }
                },
                enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Buscar actualització") }

            when (val s = state) {
                is UpdateState.Checking -> Text("Comprovant…")
                is UpdateState.UpToDate -> Text("Estàs a l'última versió ✓")
                is UpdateState.Error -> Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                is UpdateState.Downloading ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Descarregant… ${(progress * 100).toInt()}%")
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                is UpdateState.Available -> Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nova versió v${s.info.version}", style = MaterialTheme.typography.titleMedium)
                        Text(s.info.changelog, style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = {
                                if (!ApkInstaller.canInstall(context)) {
                                    ApkInstaller.requestInstallPermission(context)
                                    return@Button
                                }
                                state = UpdateState.Downloading
                                progress = 0f
                                scope.launch {
                                    try {
                                        val file = ApkInstaller.download(context, s.info.apkUrl) { progress = it }
                                        ApkInstaller.install(context, file)
                                        state = UpdateState.Idle
                                    } catch (e: Exception) {
                                        state = UpdateState.Error(e.message ?: "Error descarregant")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Descarregar i instal·lar") }
                    }
                }
                UpdateState.Idle -> {}
            }

            // Debug: OpenTracks recording diagnostics.
            var diag by remember { mutableStateOf<String?>(null) }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Depuració · gravació OpenTracks", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(
                        onClick = { diag = cat.hudpro.opentracks.data.opentracks.OpenTracksRecording.diagnostics(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Diagnòstic de gravació") }
                    diag?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Quant a", style = MaterialTheme.typography.labelMedium)
                    Text("OpenTracks HUD Pro — fork d'OSMDashboard amb mapes ICGC, HUD i Endurain.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("github.com/borborborja/opentracks-HUDpro", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
