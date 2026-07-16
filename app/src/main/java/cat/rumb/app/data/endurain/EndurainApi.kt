package cat.rumb.app.data.endurain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Endurain REST API (base path /api/v1). Auth headers are injected by an OkHttp interceptor (see
 * [EndurainClient]): `X-API-Key` in API-key mode, or `Authorization: Bearer …` + `X-Client-Type:
 * mobile` in credentials mode. [login]/[refresh] are the exception — they carry their own headers.
 *
 * Upload: POST /activities/create/upload (multipart, part "file"). API keys can ONLY use this one.
 */
interface EndurainApi {

    /** Mobile login: form-urlencoded username/password, no interceptor auth. Returns the token pair. */
    @FormUrlEncoded
    @POST("api/v1/auth/login")
    suspend fun login(
        @Header("X-Client-Type") clientType: String = "mobile",
        @Field("username") username: String,
        @Field("password") password: String,
    ): Response<EndurainTokens>

    /** Refresh with the REFRESH token in the Authorization header (rotation: new pair returned). */
    @POST("api/v1/auth/refresh")
    suspend fun refresh(
        @Header("X-Client-Type") clientType: String = "mobile",
        @Header("Authorization") bearerRefresh: String,
    ): Response<EndurainTokens>

    @Multipart
    @POST("api/v1/activities/create/upload")
    suspend fun uploadActivity(@Part file: MultipartBody.Part): Response<List<EndurainActivity>>

    @GET("api/v1/activities")
    suspend fun listActivities(
        @Query("page_number") page: Int = 1,
        @Query("num_records") pageSize: Int = 25,
    ): Response<List<EndurainActivity>>

    /** Authenticated count — validates a credentials session (JWT-only; an API key gets 401 here). */
    @GET("api/v1/activities/number")
    suspend fun activitiesCount(): Response<Int>
}

@Serializable
data class EndurainTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 900,
    @SerialName("refresh_token_expires_in") val refreshExpiresIn: Long = 604800,
)

@Serializable
data class EndurainActivity(
    val id: Long,
    val name: String? = null,
    @SerialName("activity_type") val activityType: Int? = null,
    val distance: Double? = null,
    @SerialName("start_time") val startTime: String? = null,
)
