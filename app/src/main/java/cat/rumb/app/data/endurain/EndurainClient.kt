package cat.rumb.app.data.endurain

import cat.rumb.app.data.prefs.EndurainPreferences
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit

/**
 * Builds the [EndurainApi]. Two flavours:
 * - [bare]: no auth headers — for the login/refresh calls that carry their own.
 * - [create]: auth headers per mode (X-API-Key, or Bearer + X-Client-Type via [EndurainAuth]),
 *   plus an [okhttp3.Authenticator] that on a 401 forces a fresh token and retries once.
 */
object EndurainClient {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun retrofit(host: String, client: OkHttpClient): EndurainApi =
        Retrofit.Builder()
            .baseUrl(host.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EndurainApi::class.java)

    /** No-auth client for [EndurainApi.login]/[EndurainApi.refresh]. */
    fun bare(host: String): EndurainApi = retrofit(host, OkHttpClient.Builder().build())

    /** Authenticated client for uploads/reads, wired to the given [prefs]'s mode and session. */
    fun create(prefs: EndurainPreferences, auth: EndurainAuth): EndurainApi {
        val host = prefs.host ?: return bare("http://localhost/") // never used: caller checks isConfigured
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor(prefs, auth))
            .authenticator(refreshAuthenticator(prefs, auth))
            .build()
        return retrofit(host, client)
    }

    private fun authInterceptor(prefs: EndurainPreferences, auth: EndurainAuth) = Interceptor { chain ->
        val b = chain.request().newBuilder().header("Accept", "application/json")
        when (prefs.authMode) {
            EndurainPreferences.AuthMode.API_KEY ->
                prefs.apiKey?.let { b.header("X-API-Key", it) }
            EndurainPreferences.AuthMode.CREDENTIALS -> {
                b.header("X-Client-Type", "mobile")
                // Interceptors run off the main thread, so blocking for the token is fine here.
                runCatching { runBlocking { auth.accessToken() } }.getOrNull()
                    ?.let { b.header("Authorization", "Bearer $it") }
            }
        }
        chain.proceed(b.build())
    }

    /**
     * On a 401 from an authenticated request (a token that expired between the interceptor and the
     * server), force a refresh and retry ONCE. Returning null on the second attempt stops the loop.
     */
    private fun refreshAuthenticator(prefs: EndurainPreferences, auth: EndurainAuth) =
        okhttp3.Authenticator { _: Route?, response: Response ->
            if (prefs.authMode != EndurainPreferences.AuthMode.CREDENTIALS) return@Authenticator null
            if (response.request.header("X-Rumb-Retry") != null) return@Authenticator null // already retried
            val fresh = runCatching { runBlocking { auth.forceRefresh() } }.getOrNull()
                ?: return@Authenticator null
            response.request.newBuilder()
                .header("Authorization", "Bearer $fresh")
                .header("X-Client-Type", "mobile")
                .header("X-Rumb-Retry", "1")
                .build()
        }
}
