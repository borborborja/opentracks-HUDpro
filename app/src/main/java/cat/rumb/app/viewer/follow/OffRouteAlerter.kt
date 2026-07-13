package cat.rumb.app.viewer.follow

/**
 * Stateful off-route detector with hysteresis: fires [Event.ENTERED] once when the deviation crosses
 * above the threshold, and [Event.EXITED] once when it drops back below `threshold * EXIT_FACTOR`.
 * While the user stays off-route it returns [Event.NONE] so the alert isn't repeated. Pure/testable.
 */
class OffRouteAlerter(private val exitFactor: Double = 0.7) {

    enum class Event { NONE, ENTERED, EXITED }

    private var offRoute = false

    /** Feeds the latest deviation (m) and threshold (m); returns the transition event, if any. */
    fun update(offRouteMeters: Double?, thresholdMeters: Int): Event {
        if (offRouteMeters == null) return Event.NONE
        val enter = thresholdMeters.toDouble()
        val exit = enter * exitFactor
        return when {
            !offRoute && offRouteMeters > enter -> { offRoute = true; Event.ENTERED }
            offRoute && offRouteMeters < exit -> { offRoute = false; Event.EXITED }
            else -> Event.NONE
        }
    }

    fun reset() { offRoute = false }
}
