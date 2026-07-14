package cat.rumb.app.data.gpx

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportFormatsTest {

    private val kml = """<?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document><name>Ruta KML</name>
            <Placemark>
              <LineString><coordinates>
                2.0,41.0,100 2.001,41.001,110 2.002,41.002,120
              </coordinates></LineString>
            </Placemark>
          </Document>
        </kml>"""

    @Test
    fun kmlLineStringParses() {
        val route = Kml.read(kml.byteInputStream())
        assertThat(route.name).isEqualTo("Ruta KML")
        assertThat(route.points).hasSize(3)
        assertThat(route.points[0].latitude).isEqualTo(41.0)
        assertThat(route.points[0].longitude).isEqualTo(2.0)
        assertThat(route.points[2].elevation).isEqualTo(120.0)
    }

    @Test
    fun kmzExtractsInnerKml() {
        val zipped = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("doc.kml"))
                zip.write(kml.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val route = Kml.readKmz(zipped.inputStream())
        assertThat(route.points).hasSize(3)
        assertThat(route.name).isEqualTo("Ruta KML")
    }

    @Test
    fun tcxParsesPositionsAndHeartRate() {
        val tcx = """<?xml version="1.0"?>
            <TrainingCenterDatabase>
              <Activities><Activity Sport="Biking"><Lap><Track>
                <Trackpoint>
                  <Time>2026-07-13T10:00:00Z</Time>
                  <Position><LatitudeDegrees>41.0</LatitudeDegrees><LongitudeDegrees>2.0</LongitudeDegrees></Position>
                  <AltitudeMeters>100.0</AltitudeMeters>
                  <HeartRateBpm><Value>150</Value></HeartRateBpm>
                </Trackpoint>
                <Trackpoint>
                  <Time>2026-07-13T10:00:05Z</Time>
                  <Position><LatitudeDegrees>41.001</LatitudeDegrees><LongitudeDegrees>2.001</LongitudeDegrees></Position>
                  <AltitudeMeters>101.0</AltitudeMeters>
                  <HeartRateBpm><Value>152</Value></HeartRateBpm>
                </Trackpoint>
              </Track></Lap></Activity></Activities>
            </TrainingCenterDatabase>"""
        val route = Tcx.read(tcx.byteInputStream())
        assertThat(route.name).isEqualTo("Biking")
        assertThat(route.points).hasSize(2)
        assertThat(route.points[0].heartRate).isEqualTo(150.0)
        assertThat(route.points[1].elevation).isEqualTo(101.0)
        assertThat(route.points[0].time).isNotNull()
    }

    @Test
    fun kmlCoordinatesWithSpacesAfterCommasStillParse() {
        // Non-standard "lon, lat, ele" (space after comma) must not shred into separate tokens.
        val spaced = """<?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2"><Document>
              <Placemark><LineString><coordinates>
                2.0, 41.0, 100  2.001, 41.001, 110
              </coordinates></LineString></Placemark>
            </Document></kml>"""
        val route = Kml.read(spaced.byteInputStream())
        assertThat(route.points).hasSize(2)
        assertThat(route.points[0].latitude).isEqualTo(41.0)
        assertThat(route.points[1].longitude).isEqualTo(2.001)
    }

    @Test
    fun naiveTimestampsWithoutOffsetAreParsedNotDropped() {
        // A spec-valid xsd:dateTime with no zone (some exporters emit this) must still yield a time.
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1"><trk><trkseg>
              <trkpt lat="41.0" lon="2.0"><time>2024-06-01T08:00:00</time></trkpt>
              <trkpt lat="41.001" lon="2.001"><time>2024-06-01T08:00:05+02:00</time></trkpt>
            </trkseg></trk></gpx>"""
        val route = Gpx.read(gpx.byteInputStream())
        assertThat(route.points).hasSize(2)
        assertThat(route.points[0].time).isNotNull()
        assertThat(route.points[1].time).isNotNull()
    }

    @Test
    fun tcxNaiveTimestampParsed() {
        val tcx = """<?xml version="1.0"?>
            <TrainingCenterDatabase><Activities><Activity><Lap><Track>
              <Trackpoint>
                <Time>2026-07-13T10:00:00</Time>
                <Position><LatitudeDegrees>41.0</LatitudeDegrees><LongitudeDegrees>2.0</LongitudeDegrees></Position>
              </Trackpoint>
            </Track></Lap></Activity></Activities></TrainingCenterDatabase>"""
        val route = Tcx.read(tcx.byteInputStream())
        assertThat(route.points).hasSize(1)
        assertThat(route.points[0].time).isNotNull()
    }

    @Test
    fun formatDetectionByExtension() {
        assertThat(formatFor("sortida.gpx")).isEqualTo(TrackFormat.GPX)
        assertThat(formatFor("Ruta.KML")).isEqualTo(TrackFormat.KML)
        assertThat(formatFor("mapa.kmz")).isEqualTo(TrackFormat.KMZ)
        assertThat(formatFor("act.tcx")).isEqualTo(TrackFormat.TCX)
        assertThat(formatFor("cursa.fit")).isEqualTo(TrackFormat.UNSUPPORTED)
        assertThat(formatFor(null)).isEqualTo(TrackFormat.UNSUPPORTED)
        assertThat(formatFor("sense_extensio")).isEqualTo(TrackFormat.UNSUPPORTED)
    }
}
