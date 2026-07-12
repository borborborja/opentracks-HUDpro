package cat.hudpro.opentracks.data.gpx

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GpxExtensionsTest {

    @Test
    fun writesSensorExtensionsWhenPresent() {
        val gpx = Gpx.write(
            "Sortida",
            listOf(
                GpxPoint(41.0, 2.0, 100.0, Instant.parse("2026-07-12T10:00:00Z"), heartRate = 150.0, cadence = 85.0, power = 210.0),
                GpxPoint(41.001, 2.0, 101.0, Instant.parse("2026-07-12T10:00:05Z")),
            ),
        )
        assertThat(gpx).contains("xmlns:gpxtpx=")
        assertThat(gpx).contains("<gpxtpx:hr>150</gpxtpx:hr>")
        assertThat(gpx).contains("<gpxtpx:cad>85</gpxtpx:cad>")
        assertThat(gpx).contains("<power>210</power>")
        // The extension-less point has no extensions block after it (only one occurrence).
        assertThat(gpx.split("<extensions>").size - 1).isEqualTo(1)
    }

    @Test
    fun plainPointsProduceNoExtensionNamespace() {
        val gpx = Gpx.write("Ruta", listOf(GpxPoint(41.0, 2.0), GpxPoint(41.001, 2.0)))
        assertThat(gpx).doesNotContain("gpxtpx")
        assertThat(gpx).doesNotContain("<extensions>")
    }

    @Test
    fun extendedGpxStillRoundTrips() {
        val gpx = Gpx.write(
            "Sortida",
            listOf(
                GpxPoint(41.0, 2.0, 100.0, Instant.parse("2026-07-12T10:00:00Z"), heartRate = 150.0),
                GpxPoint(41.001, 2.0, 101.0, Instant.parse("2026-07-12T10:00:05Z"), heartRate = 151.0),
            ),
        )
        val read = Gpx.read(gpx.byteInputStream())
        assertThat(read.name).isEqualTo("Sortida")
        assertThat(read.points).hasSize(2)
        assertThat(read.points[0].latitude).isEqualTo(41.0)
        assertThat(read.points[0].elevation).isEqualTo(100.0)
    }
}
