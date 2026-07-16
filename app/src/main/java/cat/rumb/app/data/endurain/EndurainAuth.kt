package cat.rumb.app.data.endurain

import cat.rumb.app.data.debug.DebugLog
import cat.rumb.app.data.prefs.EndurainPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A usable bearer token could not be obtained (bad credentials, expired refresh, network). */
class EndurainAuthException(message: String, val code: Int? = null) : Exception(message)

/**
 * Owns the JWT session for credentials mode: hands out a valid access token, refreshing or
 * re-logging in as needed, and persisting the rotated tokens. Everything runs under a single
 * [Mutex] — uploads fire on independent one-time workers, and two of them refreshing at once would
 * rotate the refresh token out from under each other.
 *
 * The login/refresh calls use a BARE api (no auth interceptor) so they never recurse through the
 * interceptor that calls this class.
 */
class EndurainAuth(private val prefs: EndurainPreferences) {

    private val mutex = Mutex()

    private fun bareApi(): EndurainApi? = prefs.host?.let { EndurainClient.bare(it) }

    /** Wall clock; a val so tests could substitute, but real time is fine here. */
    private fun now(): Long = System.currentTimeMillis()

    /**
     * A valid access token, obtaining one if the cached one is missing or expired. Throws
     * [EndurainAuthException] if a session can't be established.
     */
    suspend fun accessToken(): String = mutex.withLock {
        val cached = prefs.accessToken
        if (cached != null && now() < prefs.accessExpiresAtMs) return@withLock cached
        refreshOrLoginLocked()
    }

    /** Forces a new token even if the cached one hasn't expired — used after a 401 slips through. */
    suspend fun forceRefresh(): String = mutex.withLock { refreshOrLoginLocked(force = true) }

    private suspend fun refreshOrLoginLocked(force: Boolean = false): String {
        val api = bareApi() ?: throw EndurainAuthException("Sense servidor")
        // Try the refresh token first, unless it's expired.
        val refresh = prefs.refreshToken
        if (refresh != null && now() < prefs.refreshExpiresAtMs) {
            val r = runCatching { api.refresh(bearerRefresh = "Bearer $refresh") }.getOrNull()
            if (r != null && r.isSuccessful) {
                r.body()?.let { return storeTokens(it) }
            } else if (r != null && r.code() != 401) {
                // A non-auth failure (5xx/network-ish) — don't nuke the session, surface it.
                throw EndurainAuthException("Refresc HTTP ${r.code()}", r.code())
            }
            // 401 or null → the refresh token is dead; fall through to a full login.
            DebugLog.i("Endurain", "refresh rebutjat, login complet")
        }
        return login(api)
    }

    private suspend fun login(api: EndurainApi): String {
        val user = prefs.username
        val pass = prefs.password
        if (user.isNullOrBlank() || pass.isNullOrBlank()) {
            throw EndurainAuthException("Falten credencials")
        }
        val r = runCatching { api.login(username = user, password = pass) }
            .getOrElse { throw EndurainAuthException(it.message ?: "Error de xarxa") }
        if (!r.isSuccessful) throw EndurainAuthException("Login HTTP ${r.code()}", r.code())
        val body = r.body() ?: throw EndurainAuthException("Resposta de login buida")
        return storeTokens(body)
    }

    private fun storeTokens(t: EndurainTokens): String {
        prefs.accessToken = t.accessToken
        // Renew a safety margin before the real expiry so a token never dies mid-request.
        prefs.accessExpiresAtMs = now() + (t.expiresIn * 1000) - EXPIRY_MARGIN_MS
        t.refreshToken?.let {
            prefs.refreshToken = it
            prefs.refreshExpiresAtMs = now() + (t.refreshExpiresIn * 1000) - EXPIRY_MARGIN_MS
        }
        return t.accessToken
    }

    private companion object {
        const val EXPIRY_MARGIN_MS = 30_000L
    }
}
