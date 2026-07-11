package cat.hudpro.opentracks.data.gpx

import cat.hudpro.opentracks.data.opentracks.model.GeoPoint
import org.w3c.dom.Element
import java.io.InputStream
import java.io.StringWriter
import java.time.Instant
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** A single GPX track point with optional elevation/time. */
data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val time: Instant? = null,
) {
    fun toGeoPoint() = GeoPoint(latitude, longitude)
}

/** An imported/exported route: an ordered list of points and a name. */
data class GpxRoute(val name: String?, val points: List<GpxPoint>)

/**
 * Minimal GPX 1.1 reader/writer. Uses DOM so it runs identically on Android and the plain JVM
 * (Android lacks StAX). Reads trkpt (and rtept/wpt as fallback) coordinates + ele + time.
 */
object Gpx {

    fun read(input: InputStream): GpxRoute {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val doc = factory.newDocumentBuilder().parse(input)
        doc.documentElement.normalize()

        val name = doc.getElementsByTagName("name").let { if (it.length > 0) it.item(0).textContent?.trim() else null }

        val points = mutableListOf<GpxPoint>()
        // Prefer trkpt, then rtept, then wpt.
        for (tag in listOf("trkpt", "rtept", "wpt")) {
            val nodes = doc.getElementsByTagName(tag)
            if (nodes.length == 0) continue
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
                val lon = el.getAttribute("lon").toDoubleOrNull() ?: continue
                val ele = childText(el, "ele")?.toDoubleOrNull()
                val time = childText(el, "time")?.let { runCatching { Instant.parse(it) }.getOrNull() }
                points.add(GpxPoint(lat, lon, ele, time))
            }
            if (points.isNotEmpty()) break
        }
        return GpxRoute(name, points)
    }

    private fun childText(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
    }

    /** Serializes points to a GPX 1.1 track document. */
    fun write(name: String, points: List<GpxPoint>): String {
        val sw = StringWriter()
        sw.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sw.append("""<gpx version="1.1" creator="OpenTracks HUD Pro" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        sw.append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <trkseg>\n")
        for (p in points) {
            sw.append(String.format(Locale.US, "      <trkpt lat=\"%.7f\" lon=\"%.7f\">", p.latitude, p.longitude))
            sw.append('\n')
            p.elevation?.let { sw.append(String.format(Locale.US, "        <ele>%.1f</ele>%n", it)) }
            p.time?.let { sw.append("        <time>").append(it.toString()).append("</time>\n") }
            sw.append("      </trkpt>\n")
        }
        sw.append("    </trkseg>\n  </trk>\n</gpx>\n")
        return sw.toString()
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
