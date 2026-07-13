package cat.rumb.app.data.recording

import cat.rumb.app.data.opentracks.model.GeoPoint
import cat.rumb.app.viewer.hud.MetricsCalculator
import java.time.Instant

/**
 * Pure auto-pause detector: pauses after [idleAfterSec] seconds below [idleSpeedMs], resumes once
 * the athlete moves [resumeDistanceM] away from where the pause happened. Only auto-resumes pauses
 * it created itself (a manual pause stays paused).
 */
class AutoPause(
    private val idleSpeedMs: Double = 0.4,
    private val idleAfterSec: Long = 10,
    private val resumeDistanceM: Double = 12.0,
) {
    enum class Command { NONE, PAUSE, RESUME }

    private var idleSince: Instant? = null
    private var pausedAt: GeoPoint? = null

    /** True while the current pause was triggered by this detector. */
    val isAutoPaused: Boolean get() = pausedAt != null

    /** Feed every GPS fix (even while paused). Returns the action the service should take. */
    fun onFix(position: GeoPoint, speedMs: Double, time: Instant, isPaused: Boolean): Command {
        if (isPaused) {
            val origin = pausedAt ?: return Command.NONE // manual pause: never auto-resume
            return if (MetricsCalculator.distanceMeters(origin, position) > resumeDistanceM) {
                pausedAt = null
                idleSince = null
                Command.RESUME
            } else {
                Command.NONE
            }
        }
        pausedAt = null
        if (speedMs < idleSpeedMs) {
            val since = idleSince ?: time.also { idleSince = it }
            if (java.time.Duration.between(since, time).seconds >= idleAfterSec) {
                pausedAt = position
                idleSince = null
                return Command.PAUSE
            }
        } else {
            idleSince = null
        }
        return Command.NONE
    }

    /** Call when the user pauses/resumes manually so the detector doesn't fight them. */
    fun onManualOverride() {
        pausedAt = null
        idleSince = null
    }
}
