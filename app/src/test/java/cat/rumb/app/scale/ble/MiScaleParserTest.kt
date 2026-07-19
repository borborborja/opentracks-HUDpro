package cat.rumb.app.scale.ble

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class MiScaleParserTest {

    /** Builds a 13-byte MIBCS2 frame from the fields the parser reads (LE). */
    private fun frame(
        unitFlags: Int = 0x00,
        statusFlags: Int,
        impedance: Int,
        weightRaw: Int,
    ): ByteArray {
        val b = ByteArray(13)
        b[0] = unitFlags.toByte()
        b[1] = statusFlags.toByte()
        // [2..8] timestamp — irrelevant to the parser, left zero.
        b[9] = (impedance and 0xFF).toByte()
        b[10] = ((impedance shr 8) and 0xFF).toByte()
        b[11] = (weightRaw and 0xFF).toByte()
        b[12] = ((weightRaw shr 8) and 0xFF).toByte()
        return b
    }

    @Test
    fun tooShortIsRejected() {
        assertThat(MiScaleParser.parseMibcs2(ByteArray(12))).isNull()
    }

    @Test
    fun stabilizedFrameWithImpedanceGivesWeightAndImpedance() {
        // 75.00 kg → raw 15000; impedance 500; stabilized (0x20) + impedance-present (0x02).
        val f = MiScaleParser.parseMibcs2(frame(statusFlags = 0x22, impedance = 500, weightRaw = 15000))!!
        assertThat(f.weightKg).isCloseTo(75.0, within(0.001))
        assertThat(f.impedanceOhm).isEqualTo(500)
        assertThat(f.stabilized).isTrue()
        assertThat(f.weightRemoved).isFalse()
    }

    @Test
    fun earlyFrameHasWeightButNoImpedanceAndIsNotStabilized() {
        // Just stepped on: weight is coming in, impedance bit not set yet.
        val f = MiScaleParser.parseMibcs2(frame(statusFlags = 0x00, impedance = 0, weightRaw = 14980))!!
        assertThat(f.weightKg).isCloseTo(74.9, within(0.01))
        assertThat(f.impedanceOhm).isNull()
        assertThat(f.stabilized).isFalse()
    }

    @Test
    fun impedanceBitSetButZeroOrMaxIsTreatedAsAbsent() {
        assertThat(MiScaleParser.parseMibcs2(frame(statusFlags = 0x02, impedance = 0, weightRaw = 15000))!!.impedanceOhm).isNull()
        assertThat(MiScaleParser.parseMibcs2(frame(statusFlags = 0x02, impedance = 0xFFFF, weightRaw = 15000))!!.impedanceOhm).isNull()
    }

    @Test
    fun weightRemovedBitIsReported() {
        val f = MiScaleParser.parseMibcs2(frame(statusFlags = 0x80, impedance = 0, weightRaw = 0))!!
        assertThat(f.weightRemoved).isTrue()
    }

    @Test
    fun poundUnitIsConvertedToKilograms() {
        // 100.0 lb (raw 10000, /100) → 45.36 kg.
        val f = MiScaleParser.parseMibcs2(frame(unitFlags = 0x01, statusFlags = 0x22, impedance = 500, weightRaw = 10000))!!
        assertThat(f.weightKg).isCloseTo(45.359, within(0.01))
    }

    // --- v1 / original scale (10-byte frame, control at [0], weight at [1..2], no impedance) ---

    private fun v1(ctrl: Int, weightRaw: Int): ByteArray {
        val b = ByteArray(10)
        b[0] = ctrl.toByte()
        b[1] = (weightRaw and 0xFF).toByte()
        b[2] = ((weightRaw shr 8) and 0xFF).toByte()
        return b
    }

    @Test
    fun v1FrameGivesStabilizedWeightWithoutImpedance() {
        // 75.00 kg → raw 15000; stabilized bit (0x20) in the control byte.
        val f = MiScaleParser.parseMiScaleV1(v1(ctrl = 0x20, weightRaw = 15000))!!
        assertThat(f.weightKg).isCloseTo(75.0, within(0.001))
        assertThat(f.impedanceOhm).isNull()
        assertThat(f.stabilized).isTrue()
    }

    /** Bytes as hex → ByteArray, for pasting real captured frames. */
    private fun hex(s: String): ByteArray =
        s.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun realCapturedFramesFromAn2016Scale() {
        // Actual frames logged from a Mi Body Composition Scale (XMTZC02HM). Weight settles first,
        // impedance lands a moment later — the reason completion must wait for the impedance frame.
        val settledNoImpedance = MiScaleParser.parseMibcs2(hex("02 24 b3 07 01 0d 0c 13 13 00 00 a8 39"))!!
        assertThat(settledNoImpedance.weightKg).isCloseTo(73.8, within(0.05))
        assertThat(settledNoImpedance.stabilized).isTrue()
        assertThat(settledNoImpedance.impedanceOhm).isNull()

        val withImpedance = MiScaleParser.parseMibcs2(hex("02 26 b3 07 01 0d 0c 13 13 dc 01 a8 39"))!!
        assertThat(withImpedance.weightKg).isCloseTo(73.8, within(0.05))
        assertThat(withImpedance.stabilized).isTrue()
        assertThat(withImpedance.impedanceOhm).isEqualTo(476)
    }

    @Test
    fun parseDispatchesByLength() {
        // A 13-byte frame → v2 (impedance possible); a 10-byte one → v1 (no impedance).
        assertThat(MiScaleParser.parse(frame(statusFlags = 0x22, impedance = 500, weightRaw = 15000))!!.impedanceOhm).isEqualTo(500)
        assertThat(MiScaleParser.parse(v1(ctrl = 0x20, weightRaw = 15000))!!.impedanceOhm).isNull()
    }
}
