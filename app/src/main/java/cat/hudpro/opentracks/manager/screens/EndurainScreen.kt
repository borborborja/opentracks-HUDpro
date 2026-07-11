package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cat.hudpro.opentracks.data.endurain.EndurainRepository
import cat.hudpro.opentracks.data.prefs.EndurainPreferences
import kotlinx.coroutines.launch

@Composable
fun EndurainScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { EndurainPreferences.get(context) }
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf(prefs.host ?: "") }
    var apiKey by remember { mutableStateOf(prefs.apiKey ?: "") }
    var status by remember { mutableStateOf<String?>(null) }

    DetailScaffold(title = "Endurain", onBack = onBack) { modifier ->
        Column(
            modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connexió", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Servidor (https://…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key (scope activities:upload)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    prefs.host = host
                    prefs.apiKey = apiKey
                    status = "Desat. Provant connexió…"
                    scope.launch {
                        val repo = EndurainRepository(prefs)
                        status = repo.testConnection().fold(
                            onSuccess = { "Connectat ✓ ($it activitats al servidor)" },
                            onFailure = { "Error: ${it.message}" },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar i provar connexió") }

            status?.let {
                Card { Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
            }

            Text("Sincronització", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val repo = EndurainRepository(prefs)
                        status = repo.listActivities().fold(
                            onSuccess = { list -> "Rebudes ${list.size} activitats. La descàrrega de GPX per seguir arribarà aviat." },
                            onFailure = { "Error: ${it.message}" },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Llistar activitats") }

            Text(
                "Els tracks gravats amb OpenTracks es pugen automàticament a Endurain quan s'atura la " +
                    "gravació (si la connexió està configurada). La cua reintenta si no hi ha xarxa.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
