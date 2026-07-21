package cat.rumb.app.manager.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
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
import cat.rumb.app.data.recording.ble.SavedSensor
import cat.rumb.app.data.recording.ble.SavedSensors

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

    var sensors by remember { mutableStateOf(SavedSensors.load(prefs)) }
    val paired = sensors.map { it.address }.toSet()
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
                // Many HR devices (Mi Band, Amazfit, watches) expose the heart-rate service only over
                // GATT and advertise a name, not the 0x180D service UUID — so we scan unfiltered and
                // keep any named device, plus the rare unnamed one that does advertise a fitness
                // service. This mirrors BleSensorProbe, which scans unfiltered for the same reason.
                val name = result.device.name ?: result.scanRecord?.deviceName
                val advertisesSensor = result.scanRecord?.serviceUuids
                    ?.any { it.uuid in BleSensorManager.SCAN_SERVICES } == true
                if (name == null && !advertisesSensor) return
                found[result.device.address] = name ?: context.getString(R.string.sensors_default_name)
            }
            override fun onScanFailed(errorCode: Int) {
                scanError = context.getString(R.string.sensors_scan_error, errorCode)
            }
        }
    }

    fun startScan() {
        val s = scanner ?: run { scanError = context.getString(R.string.sensors_bluetooth_off); return }
        // No ScanFilter: a service-UUID filter would drop devices that don't advertise 0x180D/0x1816/
        // 0x1818 (Mi Band, Amazfit, most watches). The callback filters to named/sensor devices.
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { s.startScan(emptyList(), settings, callback); scanning = true; scanError = null }
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
                val saved = sensors.firstOrNull { it.address == address }
                val fallback = stringResource(R.string.sensors_paired_name)
                SensorRow(
                    address = address,
                    // A live scan knows the real name; otherwise use the one stored when it was
                    // paired, and only then fall back to the generic label.
                    name = found[address] ?: saved?.name?.takeIf { it.isNotBlank() } ?: fallback,
                    paired = saved != null,
                    warnIfMissing = saved?.warnIfMissing == true,
                    onPaired = { checked ->
                        sensors = if (checked) {
                            sensors + SavedSensor(address, found[address].orEmpty())
                        } else {
                            sensors.filterNot { it.address == address }
                        }
                        SavedSensors.save(prefs, sensors)
                    },
                    onWarn = { checked ->
                        sensors = sensors.map {
                            if (it.address == address) it.copy(warnIfMissing = checked) else it
                        }
                        SavedSensors.save(prefs, sensors)
                    },
                )
            }
        }
    }
}

/**
 * One sensor: pair it, and — once paired — choose whether recording should warn you when it isn't
 * switched on. The warn option only exists for a paired sensor: there is nothing to miss otherwise.
 */
@Composable
private fun SensorRow(
    address: String,
    name: String,
    paired: Boolean,
    warnIfMissing: Boolean,
    onPaired: (Boolean) -> Unit,
    onWarn: (Boolean) -> Unit,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Checkbox(checked = paired, onCheckedChange = onPaired)
            }
            if (paired) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.sensors_warn_if_missing),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(checked = warnIfMissing, onCheckedChange = onWarn)
                }
            }
        }
    }
}
