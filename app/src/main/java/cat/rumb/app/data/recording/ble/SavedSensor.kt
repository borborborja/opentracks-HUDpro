package cat.rumb.app.data.recording.ble

import cat.rumb.app.data.prefs.ViewerPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A BLE sensor the user paired. Until now pairing stored a bare MAC address, so a sensor out of
 * range showed as a generic "paired sensor" and a warning about it could only quote a MAC — which
 * tells nobody which strap is flat. [name] is captured from the scan that paired it.
 *
 * [warnIfMissing]: warn me before recording if this one isn't switched on.
 */
@Serializable
data class SavedSensor(
    val address: String,
    val name: String = "",
    val warnIfMissing: Boolean = false,
)

object SavedSensors {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val serializer = kotlinx.serialization.builtins.ListSerializer(SavedSensor.serializer())

    fun encode(sensors: List<SavedSensor>): String = json.encodeToString(serializer, sensors)

    /** Null (not empty) when there is nothing stored or it can't be parsed — the caller migrates. */
    fun decode(raw: String?): List<SavedSensor>? =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    /**
     * The paired sensors, migrating the old address-only set on first read. The legacy key is left
     * alone: [BleSensorManager] still reads addresses, and an older build sharing the install would
     * otherwise lose its pairings.
     */
    fun load(prefs: ViewerPreferences): List<SavedSensor> {
        decode(prefs.bleSensorsJson)?.let { return it }
        val migrated = prefs.bleSensorAddrs.map { SavedSensor(address = it) }
        if (migrated.isNotEmpty()) save(prefs, migrated)
        return migrated
    }

    /** Persists [sensors], keeping the address set in sync so the recording service still connects. */
    fun save(prefs: ViewerPreferences, sensors: List<SavedSensor>) {
        prefs.bleSensorsJson = encode(sensors)
        prefs.bleSensorAddrs = sensors.map { it.address }.toSet()
    }
}
