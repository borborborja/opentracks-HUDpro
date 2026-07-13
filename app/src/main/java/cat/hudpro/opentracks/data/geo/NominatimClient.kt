package cat.hudpro.opentracks.data.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reverse geocoder backed by OSM's Nominatim. Usage policy: max 1 request/second and a valid
 * identifying User-Agent — callers (the backfill worker) must serialize and rate-limit requests.
 * Results are cached permanently in the DB, so each track is geocoded at most once.
 */
class NominatimClient(
    private val userAgent: String,
    private val client: OkHttpClient = OkHttpClient(),
) {

    /** Municipality (city/town/village) at the coordinates, or null on any failure. Never throws. */
    suspend fun municipality(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            val lang = java.util.Locale.getDefault().language.ifBlank { "en" }
            val url = "https://nominatim.openstreetmap.org/reverse" +
                "?format=jsonv2&lat=$lat&lon=$lon&zoom=10&accept-language=$lang"
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                parseMunicipality(response.body?.string() ?: return@runCatching null)
            }
        }.getOrNull()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class NominatimAddress(
            val city: String? = null,
            val town: String? = null,
            val village: String? = null,
            val municipality: String? = null,
        )

        @Serializable
        private data class NominatimResponse(val address: NominatimAddress? = null)

        /** Pure parse of a Nominatim jsonv2 reverse response; null if no usable place name. */
        fun parseMunicipality(body: String): String? = runCatching {
            val a = json.decodeFromString<NominatimResponse>(body).address ?: return@runCatching null
            a.city ?: a.town ?: a.village ?: a.municipality
        }.getOrNull()
    }
}
