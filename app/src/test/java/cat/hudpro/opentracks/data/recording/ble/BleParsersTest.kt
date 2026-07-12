package cat.hudpro.opentracks.data.recording.ble

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class BleParsersTest {

    @Test
    fun heartRateUint8() {
        // flags=0x00 (uint8), hr=150
        assertThat(BleParsers.parseHeartRate(byteArrayOf(0x00, 150.toByte()))).isEqualTo(150.0)
    }

    @Test
    fun heartRateUint16() {
        // flags=0x01 (uint16 LE), hr=300 = 0x012C
        assertThat(BleParsers.parseHeartRate(byteArrayOf(0x01, 0x2C, 0x01))).isEqualTo(300.0)
    }

    @Test
    fun heartRateMalformedIsNull() {
        assertThat(BleParsers.parseHeartRate(byteArrayOf())).isNull()
        assertThat(BleParsers.parseHeartRate(byteArrayOf(0x01, 0x2C))).isNull() // uint16 flag but 1 byte
    }

    @Test
    fun cyclingPowerInstantaneous() {
        // flags=0x0000, power=250 = 0xFA 0x00 (sint16 LE)
        assertThat(BleParsers.parseCyclingPower(byteArrayOf(0x00, 0x00, 0xFA.toByte(), 0x00))).isEqualTo(250.0)
    }

    @Test
    fun cyclingPowerNegative() {
        // -10 W = 0xFFF6 LE
        assertThat(BleParsers.parseCyclingPower(byteArrayOf(0x00, 0x00, 0xF6.toByte(), 0xFF.toByte()))).isEqualTo(-10.0)
    }

    @Test
    fun cadenceFromTwoCrankSamples() {
        // flags=0x02 (crank only): revs uint16 LE, time uint16 LE (1/1024 s)
        fun csc(revs: Int, time: Int) = byteArrayOf(
            0x02,
            (revs and 0xFF).toByte(), (revs shr 8).toByte(),
            (time and 0xFF).toByte(), (time shr 8).toByte(),
        )
        val (rpm0, s0) = BleParsers.parseCadence(csc(100, 0), null)
        assertThat(rpm0).isNull() // first sample only primes the state
        // +2 revs in 1.5 s (1536/1024) → 80 rpm
        val (rpm1, _) = BleParsers.parseCadence(csc(102, 1536), s0)
        assertThat(rpm1!!).isEqualTo(80.0, within(0.1))
    }

    @Test
    fun cadenceHandlesUint16Rollover() {
        fun csc(revs: Int, time: Int) = byteArrayOf(
            0x02,
            (revs and 0xFF).toByte(), (revs shr 8).toByte(),
            (time and 0xFF).toByte(), (time shr 8).toByte(),
        )
        val (_, s0) = BleParsers.parseCadence(csc(0xFFFF, 0xFF00), null)
        // Roll over both counters: +2 revs, +1536 ticks.
        val (rpm, _) = BleParsers.parseCadence(csc(0x0001, (0xFF00 + 1536) and 0xFFFF), s0)
        assertThat(rpm!!).isEqualTo(80.0, within(0.1))
    }

    @Test
    fun cadenceIgnoresWheelOnlyPacket() {
        // flags=0x01 (wheel only) → no cadence.
        val (rpm, state) = BleParsers.parseCadence(byteArrayOf(0x01, 0, 0, 0, 0, 0, 0), null)
        assertThat(rpm).isNull()
        assertThat(state).isNull()
    }
}
