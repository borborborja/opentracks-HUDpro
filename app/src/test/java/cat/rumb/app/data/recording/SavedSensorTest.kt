package cat.rumb.app.data.recording

import cat.rumb.app.data.recording.ble.SavedSensor
import cat.rumb.app.data.recording.ble.SavedSensors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SavedSensorTest {

    @Test
    fun roundTripsThroughJson() {
        val sensors = listOf(
            SavedSensor("AA:BB:CC:DD:EE:FF", "Polar H10", warnIfMissing = true),
            SavedSensor("11:22:33:44:55:66", "Wahoo CADENCE"),
        )
        assertThat(SavedSensors.decode(SavedSensors.encode(sensors))).isEqualTo(sensors)
    }

    @Test
    fun nothingStoredOrUnreadableIsNullSoTheCallerCanMigrate() {
        // Null, not empty: "no JSON yet" must be told apart from "you have no sensors", or the
        // address-only pairings of an older install would be silently dropped instead of migrated.
        assertThat(SavedSensors.decode(null)).isNull()
        assertThat(SavedSensors.decode("garbage")).isNull()
    }

    @Test
    fun sensorsWrittenByAnOlderBuildStillLoad() {
        // No warnIfMissing (predates the setting) and an unknown key from a hypothetical newer one.
        val legacy = """[{"address":"AA:BB:CC:DD:EE:FF","name":"Polar H10","future":"x"}]"""
        val decoded = SavedSensors.decode(legacy)
        assertThat(decoded).containsExactly(SavedSensor("AA:BB:CC:DD:EE:FF", "Polar H10"))
        assertThat(decoded!!.single().warnIfMissing).isFalse()
    }
}
