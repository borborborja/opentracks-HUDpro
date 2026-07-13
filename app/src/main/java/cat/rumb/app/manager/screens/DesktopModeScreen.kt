package cat.rumb.app.manager.screens

import android.app.Activity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.rumb.app.R
import cat.rumb.app.data.desktop.DesktopServer
import cat.rumb.app.data.desktop.LocalAddress
import cat.rumb.app.data.prefs.ViewerPreferences

/**
 * Desktop mode: starts the embedded LAN web server while visible and shows the URL + PIN to type
 * on a computer's browser. The server lives exactly for the lifetime of this screen (started in a
 * DisposableEffect, stopped on dispose) and the screen is kept awake meanwhile.
 */
@Composable
fun DesktopModeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ViewerPreferences.get(context) }
    val pin = rememberSaveable { (1000..9999).random().toString() }
    val ip = remember { LocalAddress.wifiIpv4() }

    var server by remember { mutableStateOf<DesktopServer?>(null) }
    var port by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val started = DesktopServer.startOnFreePort(context, prefs.desktopServerPort) { pin }
        server = started
        port = started?.listeningPort ?: 0
        onDispose { started?.stop() }
    }

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
            if (ip == null || server == null) {
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
