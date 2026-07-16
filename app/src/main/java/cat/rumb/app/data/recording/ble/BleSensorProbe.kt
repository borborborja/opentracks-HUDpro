package cat.rumb.app.data.recording.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import cat.rumb.app.data.debug.DebugLog
import kotlinx.coroutines.delay

/**
 * Answers "is this sensor switched on?" just before recording starts.
 *
 * A scan, not a connection: [BleSensorManager] isn't built until the recording service starts, so
 * there is no connection to inspect, and opening GATT links only to close them would be slower and
 * would fight the service for them a second later. Advertising is enough to tell a flat strap in a
 * drawer from one on someone's chest.
 */
object BleSensorProbe {

    /**
     * Which of [addresses] advertise within [timeoutMs]. Returns null when we cannot judge (no
     * adapter, Bluetooth off) — callers must treat that as "don't warn" rather than "all missing".
     * Stops early once every address is seen. Scans unfiltered by service: a sensor's advertisement
     * doesn't always carry its service UUID, and we already know the exact addresses we want.
     */
    @SuppressLint("MissingPermission") // callers check BLUETOOTH_SCAN first
    suspend fun advertising(context: Context, addresses: Set<String>, timeoutMs: Long): Set<String>? {
        if (addresses.isEmpty()) return emptySet()
        val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return null

        val seen = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val addr = result.device.address
                if (addr in addresses) seen.add(addr)
            }
            override fun onScanFailed(errorCode: Int) {
                DebugLog.e("Record", "sonda BLE fallida · codi $errorCode")
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return runCatching {
            scanner.startScan(emptyList(), settings, callback)
            val step = 200L
            var waited = 0L
            while (waited < timeoutMs && seen.size < addresses.size) {
                delay(step)
                waited += step
            }
            seen.toSet()
        }.onFailure { DebugLog.e("Record", "sonda BLE", it) }
            .also { runCatching { scanner.stopScan(callback) } }
            .getOrNull()
    }
}
