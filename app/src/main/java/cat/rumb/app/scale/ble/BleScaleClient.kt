package cat.rumb.app.scale.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import cat.rumb.app.data.debug.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** What the scale screen observes while a weigh-in is happening. */
sealed interface ScaleState {
    object Idle : ScaleState
    object Connecting : ScaleState

    /** Weight is coming in but not settled yet — show it ticking. */
    data class Live(val weightKg: Double) : ScaleState

    /** Settled with impedance — the reading to keep. */
    data class Done(val frame: ScaleFrame) : ScaleState

    data class Error(val message: String) : ScaleState
}

/**
 * A one-shot GATT client for a Mi Body Composition Scale: connect, subscribe to the measurement
 * characteristic, parse frames via [MiScaleParser], and publish [ScaleState]. Mirrors the recording
 * sensor stack's GATT plumbing (`BleSensorManager`) — serialized CCC descriptor writes and all — but
 * lives ONLY for the duration of a weigh-in: [start] on entering the read, [stop] on leaving. No
 * background service, so a failure here can't touch recording or data.
 *
 * UNVERIFIED without the hardware. The candidate service/characteristic UUIDs below follow the
 * documented Body Composition / Weight Scale profiles; the exact ones (and whether a time/config
 * write is needed to make the scale emit impedance) must be pinned by sniffing on the device. If GATT
 * notify turns out unreliable on this unit, the fallback is parsing the advertisement service data
 * for 0x181B without connecting — a swap contained to this file.
 */
@SuppressLint("MissingPermission") // callers check BLUETOOTH_CONNECT via hasPermission()
class BleScaleClient(private val context: Context, private val address: String) {

    private val _state = MutableStateFlow<ScaleState>(ScaleState.Idle)
    val state: StateFlow<ScaleState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private val pendingWrites = ArrayDeque<BluetoothGattDescriptor>()

    fun start() {
        if (!hasPermission(context)) { _state.value = ScaleState.Error("no-permission"); return }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) { _state.value = ScaleState.Error("bluetooth-off"); return }
        _state.value = ScaleState.Connecting
        runCatching {
            gatt = adapter.getRemoteDevice(address).connectGatt(context, false, callback)
        }.onFailure {
            DebugLog.e("Scale", "BLE: error connectant $address", it)
            _state.value = ScaleState.Error(it.message ?: "connect-failed")
        }
    }

    fun stop() {
        runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null
        pendingWrites.clear()
        _state.value = ScaleState.Idle
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                DebugLog.i("Scale", "BLE: connectat ${g.device.address}")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                DebugLog.i("Scale", "BLE: desconnectat ${g.device.address}")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            pendingWrites.clear()
            // Diagnostic: log the whole GATT so an unknown/older scale's real UUIDs are captured.
            // Then subscribe to EVERY notify/indicate characteristic — not just the ones we guessed —
            // so whatever the scale streams reaches handle() and gets logged.
            var found = false
            for (service in g.services) {
                for (char in service.characteristics) {
                    val props = char.properties
                    val notifiable = props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    DebugLog.i("Scale", "BLE: char ${service.uuid}/${char.uuid} props=$props notify=$notifiable")
                    if (!notifiable) continue
                    val ccc = char.getDescriptor(CCC_DESCRIPTOR) ?: continue
                    g.setCharacteristicNotification(char, true)
                    pendingWrites.add(ccc)
                    found = true
                }
            }
            if (!found) {
                DebugLog.w("Scale", "BLE: cap característica notificable a ${g.device.address}")
                _state.value = ScaleState.Error("unknown-scale")
                return
            }
            writeNextDescriptor(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            writeNextDescriptor(g)
        }

        @Deprecated("pre-T callback")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handle(characteristic.uuid, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handle(characteristic.uuid, value)
        }
    }

    private fun writeNextDescriptor(g: BluetoothGatt) {
        val descriptor = pendingWrites.removeFirstOrNull() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    private fun handle(uuid: UUID, data: ByteArray) {
        // Diagnostic: the raw frame, so the exact protocol of an older scale can be pinned from a real
        // weigh-in. Read it in the in-app Debug Log or `adb logcat -s Rumb.Scale`.
        DebugLog.i("Scale", "BLE: trama $uuid len=${data.size} · ${data.joinToString(" ") { "%02x".format(it) }}")
        val frame = MiScaleParser.parse(data) ?: return
        _state.value = when {
            frame.weightRemoved -> ScaleState.Connecting // stepped off before finishing
            // Settled weight completes the reading; impedance rides along if the scale measures it.
            // A weight-only scale (e.g. the 2016 model) still finishes — you get weight + BMI.
            frame.stabilized -> ScaleState.Done(frame)
            else -> ScaleState.Live(frame.weightKg)
        }
    }

    companion object {
        // Standard Body Composition and Weight Scale profiles. The measurement notify characteristics.
        private val BODY_COMPOSITION_SERVICE: UUID = UUID.fromString("0000181b-0000-1000-8000-00805f9b34fb")
        private val BODY_COMPOSITION_MEASUREMENT: UUID = UUID.fromString("00002a9c-0000-1000-8000-00805f9b34fb")
        private val WEIGHT_SCALE_SERVICE: UUID = UUID.fromString("0000181d-0000-1000-8000-00805f9b34fb")
        private val WEIGHT_SCALE_MEASUREMENT: UUID = UUID.fromString("00002a9d-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val MEASUREMENTS = listOf(
            BODY_COMPOSITION_SERVICE to BODY_COMPOSITION_MEASUREMENT,
            WEIGHT_SCALE_SERVICE to WEIGHT_SCALE_MEASUREMENT,
        )

        /** The service a Mi Body Composition Scale advertises — the scan filter for pairing. */
        val SCAN_SERVICE: UUID = BODY_COMPOSITION_SERVICE

        fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    }
}
