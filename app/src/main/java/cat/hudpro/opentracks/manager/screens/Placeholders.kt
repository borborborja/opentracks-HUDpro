package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Placeholders replaced by full implementations in later phases (tracks: Fase 4, Endurain: Fase 5).

@Composable
fun TrackLibraryScreen(onBack: () -> Unit) {
    DetailScaffold(title = "Tracks a seguir", onBack = onBack) { modifier ->
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Importa GPX i organitza col·leccions (properament).")
        }
    }
}

@Composable
fun EndurainScreen(onBack: () -> Unit) {
    DetailScaffold(title = "Endurain", onBack = onBack) { modifier ->
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Configura el servidor i puja els teus tracks (properament).")
        }
    }
}
