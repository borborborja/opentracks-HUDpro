package cat.rumb.app.data.endurain

import cat.rumb.app.data.prefs.EndurainPreferences
import cat.rumb.app.data.tracks.ActivityTypes
import cat.rumb.app.data.tracks.TrackKind
import cat.rumb.app.data.tracks.TrackRepository
import cat.rumb.app.data.tracks.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface UploadResult {
    data class Success(val activityIds: List<Long>) : UploadResult
    data class Failure(val code: Int?, val message: String) : UploadResult
    data object NotConfigured : UploadResult
}

/** Thrown when a downloaded activity carries no GPS (MAP) stream, so there's no track to import. */
class EndurainNoGpsException : Exception("L'activitat no té dades GPS")

/** Outcome of a "test connection", shaped so the UI can show the right message. */
sealed interface ConnResult {
    /** Credentials mode validated; [activityCount] is the server's activity count when known. */
    data class Ok(val activityCount: Int?) : ConnResult
    data object BadKey : ConnResult
    data object MissingScope : ConnResult
    data object BadUrl : ConnResult
    data object NotConfigured : ConnResult
    data class Error(val message: String) : ConnResult
}

/** High-level Endurain operations built on top of [EndurainClient] and [EndurainPreferences]. */
class EndurainRepository(private val prefs: EndurainPreferences) {

    private val auth = EndurainAuth(prefs)

    /** The logged-in user's id, needed in the path of every list call. Resolved once, then cached. */
    @Volatile private var cachedUserId: Long? = null

    private fun api(): EndurainApi? {
        prefs.host ?: return null
        return EndurainClient.create(prefs, auth)
    }

    private suspend fun userId(api: EndurainApi): Long {
        cachedUserId?.let { return it }
        val response = api.profile()
        if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code()}")
        val id = response.body()?.id ?: throw IllegalStateException("Perfil sense id")
        cachedUserId = id
        return id
    }

    /**
     * Validates the connection.
     * - Credentials: log in and hit an authenticated read endpoint → the true count.
     * - API key: an API key grants only `activities:upload` and works on one endpoint, so there is
     *   no read endpoint to probe. Send a sentinel the upload endpoint can't parse and read the
     *   status: passing auth (any non-401/403/404) means the key is valid. See [interpretProbe].
     */
    suspend fun testConnection(): ConnResult = withContext(Dispatchers.IO) {
        if (!prefs.isConfigured) return@withContext ConnResult.NotConfigured
        val api = api() ?: return@withContext ConnResult.NotConfigured
        when (prefs.authMode) {
            EndurainPreferences.AuthMode.CREDENTIALS -> testCredentials(api)
            EndurainPreferences.AuthMode.API_KEY -> testApiKey(api)
        }
    }

    private suspend fun testCredentials(api: EndurainApi): ConnResult = runCatching {
        // The interceptor logs in for us; a bad password surfaces as the 401 below.
        val response = api.activitiesCount()
        when {
            response.isSuccessful -> ConnResult.Ok(response.body())
            response.code() == 401 -> ConnResult.BadKey // bad username/password
            response.code() == 404 -> ConnResult.BadUrl
            else -> ConnResult.Error("HTTP ${response.code()}")
        }
    }.getOrElse { e ->
        // The auth layer throws before any HTTP status when credentials/refresh fail outright.
        when ((e as? EndurainAuthException)?.code) {
            401 -> ConnResult.BadKey
            404 -> ConnResult.BadUrl
            else -> ConnResult.Error(e.message ?: "Error de xarxa")
        }
    }

    private suspend fun testApiKey(api: EndurainApi): ConnResult = runCatching {
        val body = "rumb-connection-test".toByteArray().toRequestBody("application/gpx+xml".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "rumb-connection-test.gpx", body)
        interpretProbe(api.uploadActivity(part).code())
    }.getOrElse { ConnResult.Error(it.message ?: "Error de xarxa") }

    /** Uploads a GPX/TCX document. [gpx] is the raw file text; [fileName]'s extension picks the MIME. */
    suspend fun uploadGpx(gpx: String, fileName: String): UploadResult = withContext(Dispatchers.IO) {
        val api = api() ?: return@withContext UploadResult.NotConfigured
        try {
            val body = gpx.toByteArray().toRequestBody(
                cat.rumb.app.data.gpx.mimeFor(cat.rumb.app.data.gpx.formatFor(fileName)).toMediaType(),
            )
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            val response = api.uploadActivity(part)
            if (response.isSuccessful) {
                UploadResult.Success(response.body()?.map { it.id } ?: emptyList())
            } else {
                UploadResult.Failure(response.code(), "Error del servidor: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            UploadResult.Failure(null, e.message ?: "Error de xarxa")
        }
    }

    /**
     * Lists the user's remote activities, newest first, paged (credentials/JWT mode only). Resolves
     * the user id from the profile first, since Endurain scopes the list endpoint to a user path.
     */
    suspend fun listActivities(page: Int = 1, pageSize: Int = 25): Result<List<EndurainActivity>> =
        withContext(Dispatchers.IO) {
            val api = api() ?: return@withContext Result.failure(IllegalStateException("No configurat"))
            runCatching {
                val response = api.listUserActivities(userId(api), page, pageSize)
                if (response.isSuccessful) response.body().orEmpty()
                else throw IllegalStateException("HTTP ${response.code()}")
            }
        }

    /**
     * Downloads one activity and imports it into the library as a training. The track is rebuilt from
     * the activity's streams (Endurain offers no GPX download); an activity without a GPS stream
     * fails with [EndurainNoGpsException]. Returns the new local track id. De-dupe against
     * [TrackRepository.knownEndurainIds] is the caller's responsibility.
     */
    suspend fun importActivity(id: Long, tracks: TrackRepository): Result<Long> =
        withContext(Dispatchers.IO) {
            val api = api() ?: return@withContext Result.failure(IllegalStateException("No configurat"))
            runCatching {
                val detail = api.activityDetail(id)
                val activity = when {
                    detail.isSuccessful -> detail.body() ?: EndurainActivity(id = id)
                    else -> throw IllegalStateException("HTTP ${detail.code()}")
                }
                val streamsResp = api.activityStreams(id)
                if (!streamsResp.isSuccessful) throw IllegalStateException("HTTP ${streamsResp.code()}")
                val points = EndurainImport.streamsToPoints(streamsResp.body().orEmpty())
                if (points.size < 2) throw EndurainNoGpsException()
                tracks.insertRoute(
                    name = activity.name?.takeIf { it.isNotBlank() } ?: "Endurain $id",
                    points = points,
                    source = TrackSource.ENDURAIN,
                    remoteId = id,
                    kind = TrackKind.TRAINING,
                    activityType = ActivityTypes.fromEndurain(activity.activityType),
                    createdAtMillis = EndurainImport.startMillis(activity.startTime),
                )
            }
        }

    companion object {
        /**
         * Maps the upload-probe's HTTP status to a result. The point: an upload-scoped API key can't
         * hit any read endpoint, so we send an unparseable sentinel to the ONE endpoint it can use.
         * - 401 → the key was rejected.
         * - 403 → the key is valid but lacks the `activities:upload` scope.
         * - 404 → the endpoint isn't there → wrong URL / not an Endurain server.
         * - anything else (2xx, 400, 413, 415, 422, 500…) → auth PASSED and only the sentinel body
         *   was rejected → the key works.
         */
        fun interpretProbe(code: Int): ConnResult = when (code) {
            401 -> ConnResult.BadKey
            403 -> ConnResult.MissingScope
            404 -> ConnResult.BadUrl
            else -> ConnResult.Ok(null)
        }
    }
}
