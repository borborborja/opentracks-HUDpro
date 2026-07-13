package cat.rumb.app.manager.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.os.ParcelUuid
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cat.rumb.app.R
import cat.rumb.app.data.debug.DebugLog
import cat.rumb.app.data.prefs.ViewerPreferences
import cat.rumb.app.data.recording.ble.BleSensorManager

/**
 * Scan & pair BLE fitness sensors (heart rate 0x180D, cadence/CSC 0x1816, power 0x1818). Paired
 * addresses are stored in [ViewerPreferences.bleSensorAddrs]; the recording service connects to
 * them automatically on the next recording.
 */
@SuppressLint("MissingPermission") // scanning only starts after the runtime permission is granted
@Composable
fun SensorsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ViewerPreferences.get(context) }

    var paired by remember { mutableStateOf(prefs.bleSensorAddrs) }
    val found = remember { mutableStateMapOf<String, String>() } // address → name
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    val scanner = remember {
        (context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
    }
    val callback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName
                    ?: context.getString(R.string.sensors_default_name)
                found[result.device.address] = name
            }
            override fun onScanFailed(errorCode: Int) {
                scanError = context.getString(R.string.sensors_scan_error, errorCode)
            }
        }
    }

    fun startScan() {
        val s = scanner ?: run { scanError = context.getString(R.string.sensors_bluetooth_off); return }
        val filters = BleSensorManager.SCAN_SERVICES.map {
            ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { s.startScan(filters, settings, callback); scanning = true; scanError = null }
            .onFailure { scanError = it.message; DebugLog.e("Record", "BLE scan", it) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> if (granted.values.all { it }) startScan() }

    fun scanWithPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permLauncher.launch(
                arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT),
            )
        } else {
            startScan()
        }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { scanner?.stopScan(callback) } }
    }

    DetailScaffold(title = stringResource(R.string.sensors_title), onBack = onBack) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.sensors_intro),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { if (scanning) { runCatching { scanner?.stopScan(callback) }; scanning = false } else scanWithPermissions() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (scanning) R.string.sensors_stop_scan else R.string.sensors_scan)) }
            scanError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            // Paired sensors not currently in range still show (so they can be unpaired).
            val all = (found.keys + paired).distinct().sorted()
            if (all.isEmpty()) {
                Text(
                    stringResource(if (scanning) R.string.sensors_scanning_hint else R.string.sensors_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            all.forEach { address ->
                val name = found[address] ?: stringResource(R.string.sensors_paired_name)
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Checkbox(
                            checked = address in paired,
                            onCheckedChange = { checked ->
                                paired = if (checked) paired + address else paired - address
                                prefs.bleSensorAddrs = paired
                            },
                        )
                    }
                }
            }
        }
    }
}
