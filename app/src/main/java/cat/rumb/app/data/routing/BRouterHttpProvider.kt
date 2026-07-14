package cat.rumb.app.data.routing

import cat.rumb.app.data.gpx.GpxPoint
import cat.rumb.app.data.opentracks.model.GeoPoint
import cat.rumb.app.viewer.hud.MetricsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/**
 * Snap-to-path routing via a BRouter HTTP server (default the public brouter.de; configurable so a
 * self-hosted / on-device BRouter server can be used offline). Returns geometry + per-point elevation.
 */
class BRouterHttpProvider(
    private val baseUrl: String = "https://brouter.de",
    private val client: OkHttpClient = OkHttpClient(),
) : RoutingProvider {

    override suspend fun route(waypoints: List<GeoPoint>, profile: RoutingProfile): RoutedPath =
        withContext(Dispatchers.IO) {
            require(waypoints.size >= 2) { "Calen almenys 2 punts" }
            val lonlats = waypoints.joinToString("|") {
                String.format(Locale.US, "%.6f,%.6f", it.longitude, it.latitude)
            }
            val url = "${baseUrl.trimEnd('/')}/brouter?lonlats=$lonlats&profile=${profile.brouter}" +
                "&alternativeidx=0&format=geojson"
            val request = Request.Builder().url(url).header("User-Agent", "Rumb").build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw RoutingException("BRouter HTTP ${response.code}: ${body.take(120)}")
                parse(body)
            }
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses a BRouter GeoJSON response into a [RoutedPath]. Robust to missing property keys. */
        fun parse(text: String): RoutedPath {
            val root = json.parseToJsonElement(text).jsonObject
            val features = root["features"]?.jsonArray
                ?: throw RoutingException("Resposta BRouter sense features")
            if (features.isEmpty()) throw RoutingException("Cap ruta trobada")
            val feature = features[0].jsonObject
            val coords = (feature["geometry"] as? kotlinx.serialization.json.JsonObject)?.get("coordinates")?.jsonArray
                ?: throw RoutingException("Resposta BRouter sense geometria")
            val points = coords.mapNotNull { element ->
                val c = element as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
                val lon = c.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val lat = c.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val ele = c.getOrNull(2)?.jsonPrimitive?.doubleOrNull
                GpxPoint(lat, lon, elevation = ele)
            }
            if (points.size < 2) throw RoutingException("Ruta BRouter buida o malformada")
            val props = feature["properties"]?.jsonObject
            val distance = props?.get("track-length")?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: computeDistance(points)
            val ascent = props?.get("filtered ascend")?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: computeAscent(points)
            return RoutedPath(points, distance, ascent)
        }

        private fun computeDistance(points: List<GpxPoint>): Double {
            var acc = 0.0
            for (i in 1 until points.size) {
                acc += MetricsCalculator.distanceMeters(
                    GeoPoint(points[i - 1].latitude, points[i - 1].longitude),
                    GeoPoint(points[i].latitude, points[i].longitude),
                )
            }
            return acc
        }

        private fun computeAscent(points: List<GpxPoint>): Double {
            var gain = 0.0
            for (i in 1 until points.size) {
                val a = points[i - 1].elevation ?: continue
                val b = points[i].elevation ?: continue
                if (b > a) gain += b - a
            }
            return gain
        }
    }
}
