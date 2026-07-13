package cat.rumb.app.data.endurain

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit

/** Builds a per-host [EndurainApi] with the API key attached to every request. */
object EndurainClient {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun create(host: String, apiKey: String): EndurainApi {
        val baseUrl = host.trimEnd('/') + "/"
        val client = OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor(apiKey))
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EndurainApi::class.java)
    }

    private fun apiKeyInterceptor(apiKey: String) = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("X-API-Key", apiKey)
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }
}
