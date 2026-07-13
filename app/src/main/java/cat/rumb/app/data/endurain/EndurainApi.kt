package cat.rumb.app.data.endurain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Endurain REST API (base path /api/v1). Authentication is via the `X-API-Key` header injected by
 * an OkHttp interceptor (see [EndurainClient]).
 *
 * Upload: POST /activities/create/upload (multipart, part "file") -> 201 list[Activity].
 * Validate against your instance's /docs — Endurain is actively developed (v0.18.x in 2026).
 */
interface EndurainApi {

    @Multipart
    @POST("api/v1/activities/create/upload")
    suspend fun uploadActivity(@Part file: MultipartBody.Part): Response<List<EndurainActivity>>

    @GET("api/v1/activities")
    suspend fun listActivities(
        @Query("page_number") page: Int = 1,
        @Query("num_records") pageSize: Int = 25,
    ): Response<List<EndurainActivity>>

    /** Lightweight authenticated probe used to validate host + API key. */
    @GET("api/v1/activities/number")
    suspend fun activitiesCount(): Response<Int>
}

@Serializable
data class EndurainActivity(
    val id: Long,
    val name: String? = null,
    @SerialName("activity_type") val activityType: Int? = null,
    val distance: Double? = null,
    @SerialName("start_time") val startTime: String? = null,
)
