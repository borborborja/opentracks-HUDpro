package cat.rumb.app.data.recording.ble

/**
 * Pure parsers for the standard BLE fitness characteristics (JVM-testable). Byte layouts follow the
 * Bluetooth GATT specifications; logic ported from OpenTracks' sensor handlers (Apache-2.0).
 */
object BleParsers {

    /** Heart Rate Measurement (0x2A37): flags bit0 selects uint8 vs uint16 heart rate. */
    fun parseHeartRate(data: ByteArray): Double? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt() and 0xFF
        return if (flags and 0x01 == 0) {
            if (data.size < 2) null else (data[1].toInt() and 0xFF).toDouble()
        } else {
            if (data.size < 3) null else (u16(data, 1)).toDouble()
        }
    }

    /** Cycling Power Measurement (0x2A63): instantaneous power, sint16 LE at offset 2. */
    fun parseCyclingPower(data: ByteArray): Double? {
        if (data.size < 4) return null
        val raw = u16(data, 2)
        val signed = if (raw >= 0x8000) raw - 0x10000 else raw
        return signed.toDouble()
    }

    /** Rolling crank state for cadence derived from CSC Measurement (0x2A5B). */
    data class CrankState(val revolutions: Int, val eventTime1024: Int)

    /**
     * Parses a CSC Measurement and derives cadence (rpm) from the previous crank sample.
     * Returns (cadence or null, new state or null when no crank data present).
     */
    fun parseCadence(data: ByteArray, previous: CrankState?): Pair<Double?, CrankState?> {
        if (data.isEmpty()) return null to previous
        val flags = data[0].toInt() and 0xFF
        val hasWheel = flags and 0x01 != 0
        val hasCrank = flags and 0x02 != 0
        if (!hasCrank) return null to previous
        val offset = 1 + if (hasWheel) 6 else 0 // wheel block: uint32 revs + uint16 time
        if (data.size < offset + 4) return null to previous
        val revs = u16(data, offset)
        val time = u16(data, offset + 2)
        val state = CrankState(revs, time)
        val prev = previous ?: return null to state
        // uint16 rollover-aware deltas; event time in 1/1024 s.
        val dRevs = (revs - prev.revolutions + 0x10000) % 0x10000
        val dTime = (time - prev.eventTime1024 + 0x10000) % 0x10000
        if (dTime == 0 || dRevs == 0) return null to state
        val rpm = dRevs * 60.0 * 1024.0 / dTime
        // Guard absurd values from stale/rolled data.
        return (rpm.takeIf { it < 300 }) to state
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
}
