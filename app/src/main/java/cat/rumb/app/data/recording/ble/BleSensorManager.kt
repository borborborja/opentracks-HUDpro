package cat.rumb.app.data.recording.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import cat.rumb.app.data.debug.DebugLog
import java.util.UUID

/**
 * Connects to paired BLE fitness sensors (heart rate, cadence, power) and streams parsed samples.
 * GATT plumbing ported from OpenTracks' BluetoothConnectionManager (Apache-2.0). Uses autoConnect so
 * sensors that drop out (or are turned on later) reconnect on their own.
 */
@SuppressLint("MissingPermission") // callers check BLUETOOTH_CONNECT via hasPermission()
class BleSensorManager(
    private val context: Context,
    private val onHeartRate: (Double) -> Unit,
    private val onCadence: (Double) -> Unit,
    private val onPower: (Double) -> Unit,
) {
    private val gatts = mutableListOf<BluetoothGatt>()
    private var crank: BleParsers.CrankState? = null
    // Android GATT allows one outstanding operation per connection: enabling notifications for a
    // multi-characteristic sensor (e.g. power + cadence) must write CCC descriptors one at a time,
    // each from the previous onDescriptorWrite — otherwise all but the first write is dropped.
    private val pendingWrites = java.util.concurrent.ConcurrentHashMap<BluetoothGatt, ArrayDeque<android.bluetooth.BluetoothGattDescriptor>>()

    fun start(addresses: Set<String>) {
        if (!hasPermission(context)) {
            DebugLog.w("Record", "BLE: sense permís BLUETOOTH_CONNECT")
            return
        }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            DebugLog.w("Record", "BLE: Bluetooth desactivat")
            return
        }
        for (address in addresses) {
            runCatching {
                val device = adapter.getRemoteDevice(address)
                gatts.add(device.connectGatt(context, true, callback))
                DebugLog.i("Record", "BLE: connectant a $address")
            }.onFailure { DebugLog.e("Record", "BLE: error connectant $address", it) }
        }
    }

    fun stop() {
        gatts.forEach { runCatching { it.disconnect(); it.close() } }
        gatts.clear()
        pendingWrites.clear()
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                DebugLog.i("Record", "BLE: connectat ${gatt.device.address}")
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // Collect every CCC descriptor first, then write them one at a time (serialized).
            val queue = ArrayDeque<android.bluetooth.BluetoothGattDescriptor>()
            for ((service, characteristic) in MEASUREMENTS) {
                val char = gatt.getService(service)?.getCharacteristic(characteristic) ?: continue
                gatt.setCharacteristicNotification(char, true)
                char.getDescriptor(CCC_DESCRIPTOR)?.let { queue.add(it) }
            }
            pendingWrites[gatt] = queue
            writeNextDescriptor(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: android.bluetooth.BluetoothGattDescriptor, status: Int) {
            writeNextDescriptor(gatt)
        }

        @Deprecated("pre-T callback")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handle(characteristic.uuid, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handle(characteristic.uuid, value)
        }
    }

    /** Writes the next queued CCC descriptor for [gatt]; called after each onDescriptorWrite. */
    private fun writeNextDescriptor(gatt: BluetoothGatt) {
        val descriptor = pendingWrites[gatt]?.removeFirstOrNull() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
        DebugLog.i("Record", "BLE: notificacions actives · ${gatt.device.address} · ${descriptor.characteristic.uuid}")
    }

    private fun handle(uuid: UUID, data: ByteArray) {
        when (uuid) {
            HR_MEASUREMENT -> BleParsers.parseHeartRate(data)?.let(onHeartRate)
            CSC_MEASUREMENT -> {
                val (rpm, next) = BleParsers.parseCadence(data, crank)
                crank = next
                rpm?.let(onCadence)
            }
            POWER_MEASUREMENT -> BleParsers.parseCyclingPower(data)?.let(onPower)
        }
    }

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CSC_SERVICE: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        val CSC_MEASUREMENT: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
        val POWER_SERVICE: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
        val POWER_MEASUREMENT: UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val MEASUREMENTS = listOf(
            HR_SERVICE to HR_MEASUREMENT,
            CSC_SERVICE to CSC_MEASUREMENT,
            POWER_SERVICE to POWER_MEASUREMENT,
        )

        /** All BLE services we can scan/pair (used by the sensors screen's scan filter). */
        val SCAN_SERVICES = listOf(HR_SERVICE, CSC_SERVICE, POWER_SERVICE)

        fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    }
}
