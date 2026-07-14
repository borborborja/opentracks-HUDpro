package cat.rumb.app.manager.screens

import android.app.Activity
import android.app.Application
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import cat.rumb.app.R
import cat.rumb.app.data.desktop.DesktopServer
import cat.rumb.app.data.desktop.LocalAddress
import cat.rumb.app.data.prefs.ViewerPreferences

/**
 * Owns the LAN server for the lifetime of the desktop-mode nav entry. Hosting it in a ViewModel
 * (not a DisposableEffect) means a configuration change like rotation no longer tears the server
 * down and restarts it — which would mint a fresh token and bounce every connected browser back to
 * the PIN screen. onCleared fires only when the entry actually leaves the back stack.
 */
class DesktopModeViewModel(app: Application) : AndroidViewModel(app) {
    val pin: String = (1000..9999).random().toString()
    val ip: String? = LocalAddress.wifiIpv4()
    private val server: DesktopServer? =
        DesktopServer.startOnFreePort(app, ViewerPreferences.get(app).desktopServerPort) { pin }
    val port: Int = server?.listeningPort ?: 0
    val serverStarted: Boolean = server != null

    override fun onCleared() {
        server?.stop()
    }
}

/**
 * Desktop mode: shows the URL + PIN to type on a computer's browser while the embedded LAN web
 * server (owned by [DesktopModeViewModel]) runs. The screen is kept awake meanwhile.
 */
@Composable
fun DesktopModeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: DesktopModeViewModel = viewModel()
    val pin = vm.pin
    val ip = vm.ip
    val port = vm.port

    // Keep the screen awake while desktop mode is on.
    val activity = context as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(FLAG_KEEP_SCREEN_ON) }
    }

    DetailScaffold(title = stringResource(R.string.desktop_title), onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            if (ip == null || !vm.serverStarted) {
                Card {
                    Text(
                        stringResource(if (ip == null) R.string.desktop_no_wifi else R.string.desktop_server_error),
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.desktop_url_label),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                SelectionContainer {
                    Text(
                        "http://$ip:$port",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    stringResource(R.string.desktop_pin_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    pin,
                    style = MaterialTheme.typography.displayLarge,
                    letterSpacing = 8.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.desktop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card {
                    Text(
                        stringResource(R.string.desktop_running),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.desktop_stop))
            }
        }
    }
}
