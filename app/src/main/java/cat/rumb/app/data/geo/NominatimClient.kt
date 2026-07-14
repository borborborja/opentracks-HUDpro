package cat.rumb.app.data.geo

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

    /**
     * Reverse-geocode outcome: [Ok] (name may be null = geocoded but no place); [Failed] = transient
     * (retry worthwhile); [PermanentFail] = a 4xx the request will never pass (don't retry — the
     * caller should mark the row as checked so it isn't re-queried forever).
     */
    sealed interface Reverse {
        data class Ok(val name: String?) : Reverse
        data object Failed : Reverse
        data object PermanentFail : Reverse
    }

    /**
     * Reverse-geocodes the coordinates. Distinguishes a successful lookup with no place name
     * ([Reverse.Ok] with null) from a transient failure ([Reverse.Failed]) and a permanent one
     * ([Reverse.PermanentFail]) — the caller must not retry either non-transient outcome forever.
     * Never throws.
     */
    suspend fun reverse(lat: Double, lon: Double): Reverse = withContext(Dispatchers.IO) {
        runCatching {
            val lang = java.util.Locale.getDefault().language.ifBlank { "en" }
            val url = "https://nominatim.openstreetmap.org/reverse" +
                "?format=jsonv2&lat=$lat&lon=$lon&zoom=10&accept-language=$lang"
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 4xx (except 408 Request Timeout / 429 Too Many Requests, which are worth
                    // retrying) won't ever succeed for this request — don't loop forever re-hitting
                    // Nominatim; a bad User-Agent/policy 403 is the realistic case.
                    val code = response.code
                    return@runCatching if (code in 400..499 && code != 408 && code != 429) {
                        Reverse.PermanentFail
                    } else {
                        Reverse.Failed
                    }
                }
                Reverse.Ok(parseMunicipality(response.body?.string() ?: return@runCatching Reverse.Failed))
            }
        }.getOrDefault(Reverse.Failed)
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
