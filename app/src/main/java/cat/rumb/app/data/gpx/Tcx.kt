package cat.rumb.app.data.gpx

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal TCX (Training Center XML) reader: `Trackpoint` → Position (lat/lon) + AltitudeMeters +
 * Time + HeartRateBpm/Value. DOM-based so it runs identically on Android and the plain JVM.
 */
object Tcx {

    fun read(input: InputStream): GpxRoute {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val doc = factory.newDocumentBuilder().parse(input)
        doc.documentElement.normalize()

        // Activity Sport attribute or Notes as a poor-man's name; usually absent.
        val name = doc.getElementsByTagName("Activity")
            .let { if (it.length > 0) (it.item(0) as? Element)?.getAttribute("Sport")?.takeIf { s -> s.isNotBlank() } else null }

        val points = mutableListOf<GpxPoint>()
        val trackpoints = doc.getElementsByTagName("Trackpoint")
        for (i in 0 until trackpoints.length) {
            val tp = trackpoints.item(i) as? Element ?: continue
            val position = tp.getElementsByTagName("Position").let { if (it.length > 0) it.item(0) as? Element else null }
                ?: continue
            val lat = position.text("LatitudeDegrees")?.toDoubleOrNull() ?: continue
            val lon = position.text("LongitudeDegrees")?.toDoubleOrNull() ?: continue
            val ele = tp.text("AltitudeMeters")?.toDoubleOrNull()
            val time = tp.text("Time")?.let { parseTrackTime(it) }
            val hr = tp.getElementsByTagName("HeartRateBpm")
                .let { if (it.length > 0) (it.item(0) as? Element)?.text("Value")?.toDoubleOrNull() else null }
            points.add(GpxPoint(lat, lon, ele, time, heartRate = hr))
        }
        return GpxRoute(name, points)
    }

    private fun Element.text(tag: String): String? {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
    }
}
