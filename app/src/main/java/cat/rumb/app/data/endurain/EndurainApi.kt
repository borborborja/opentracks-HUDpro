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
import retrofit2.http.Path

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

    /** The logged-in user's profile — [EndurainProfile.id] is needed to page their activities. */
    @GET("api/v1/profile")
    suspend fun profile(): Response<EndurainProfile>

    /**
     * The user's activities, newest first, paged. Endurain pages by PATH segments (not query
     * params), and the list is scoped to a user id — the query-param form doesn't exist server-side.
     */
    @GET("api/v1/activities/user/{userId}/page_number/{page}/num_records/{pageSize}")
    suspend fun listUserActivities(
        @Path("userId") userId: Long,
        @Path("page") page: Int,
        @Path("pageSize") pageSize: Int,
    ): Response<List<EndurainActivity>>

    /** A single activity's metadata (name, type, start time…). Streams come from [activityStreams]. */
    @GET("api/v1/activities/{id}")
    suspend fun activityDetail(@Path("id") id: Long): Response<EndurainActivity>

    /**
     * All recorded streams for an activity. Endurain stores no GPX/FIT to download — the track is
     * rebuilt from these: type 7 (MAP) carries `lat`/`lon`, type 4 elevation, type 1 heart rate,
     * every waypoint stamped with the same `time` axis. See [EndurainStream].
     */
    @GET("api/v1/activities_streams/activity_id/{id}/all")
    suspend fun activityStreams(@Path("id") id: Long): Response<List<EndurainStream>>

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
    @SerialName("elevation_gain") val elevationGain: Double? = null,
)

/** Minimal view of `GET /profile` (`UsersMe`) — we only need the id to page the user's activities. */
@Serializable
data class EndurainProfile(
    val id: Long,
    val username: String? = null,
    val name: String? = null,
)

/**
 * One recorded stream of an activity. [streamType] is Endurain's fixed code (1 heart-rate, 3
 * cadence, 4 elevation, 5 speed, 6 pace, 7 MAP=lat/lon, 8 temperature); [waypoints] holds the
 * per-sample values, all sharing the activity's `time` axis so streams can be joined by timestamp.
 */
@Serializable
data class EndurainStream(
    @SerialName("stream_type") val streamType: Int,
    @SerialName("stream_waypoints") val waypoints: List<EndurainWaypoint> = emptyList(),
)

/**
 * A single stream sample. Only the fields relevant to the parent [EndurainStream.streamType] are
 * populated (the rest arrive absent and stay null); unknown keys are ignored by the JSON config.
 */
@Serializable
data class EndurainWaypoint(
    val time: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val ele: Double? = null,
    val hr: Int? = null,
)

/** Endurain activity-stream type codes (see `activity_streams/constants.py`). */
object EndurainStreamType {
    const val HEART_RATE = 1
    const val ELEVATION = 4
    const val MAP = 7 // lat/lon
}
