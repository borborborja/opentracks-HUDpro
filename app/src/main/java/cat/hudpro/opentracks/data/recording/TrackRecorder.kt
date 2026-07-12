package cat.hudpro.opentracks.data.recording

import cat.hudpro.opentracks.data.opentracks.model.GeoPoint
import cat.hudpro.opentracks.data.opentracks.model.Segment
import cat.hudpro.opentracks.data.opentracks.model.TRACKPOINT_TYPE_TRACKPOINT
import cat.hudpro.opentracks.data.opentracks.model.TrackStatistics
import cat.hudpro.opentracks.data.opentracks.model.Trackpoint
import cat.hudpro.opentracks.viewer.hud.MetricsCalculator
import java.time.Instant
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

/** Tunables of the native recording engine. Defaults follow OpenTracks' recording behavior. */
data class RecorderConfig(
    /** Reject fixes with a reported accuracy worse than this (m). */
    val maxAccuracyM: Float = 25f,
    /** Minimum distance between recorded points (m); closer fixes are skipped (idle jitter). */
    val minDistanceM: Double = 3.0,
    /** Reject fixes implying a speed above this (m/s) — GPS jumps. */
    val maxImpliedSpeedMs: Double = 50.0,
    /** Below this speed (m/s) the athlete counts as idle (no moving-time accrual). */
    val idleSpeedMs: Double = 0.5,
    /** Smoothed-altitude change accumulated before it counts toward gain/loss (noise gate, m). */
    val elevationHysteresisM: Double = 2.0,
    /** EMA factor for altitude smoothing (0..1; higher follows GPS faster). */
    val altitudeSmoothing: Double = 0.3,
)

/** Immutable snapshot of an ongoing/finished native recording, consumed by the viewer pipeline. */
data class RecorderState(
    val segments: List<Segment> = emptyList(),
    val statistics: TrackStatistics = TrackStatistics(),
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
) {
    val isRecording: Boolean get() = !isFinished
    fun points(): List<Trackpoint> = segments.flatten()
}

/**
 * Pure (Android-free, JVM-testable) native recording engine. Feed it GPS fixes and sensor samples;
 * it filters noise, accumulates live [TrackStatistics] and builds pause-aware segments in the same
 * model the OpenTracks dashboard produces, so the whole viewer pipeline works unchanged.
 *
 * Filtering/statistics logic follows OpenTracks' TrackPointCreator/TrackStatisticsUpdater
 * (Apache-2.0, see NOTICE), with extra guards: accuracy gate, impossible-jump gate and a smoothed
 * altitude with hysteresis so GPS noise doesn't inflate elevation gain.
 */
class TrackRecorder(private val config: RecorderConfig = RecorderConfig()) {

    private val closedSegments = mutableListOf<Segment>()
    private val currentSegment = mutableListOf<Trackpoint>()
    private var seq = 0L

    private var startedAt: Instant? = null
    private var lastPointTime: Instant? = null
    private var lastLatLong: GeoPoint? = null

    // Active (non-paused) wall time bookkeeping.
    private var activeSince: Instant? = null
    private var accumulatedActive: Duration = Duration.ZERO
    private var paused = false
    private var finished = false

    // Statistics accumulators.
    private var distanceM = 0.0
    private var movingTime: Duration = Duration.ZERO
    private var maxSpeedMs = 0.0
    private var smoothedAlt: Double? = null
    private var pendingElevDelta = 0.0
    private var gainM = 0.0
    private var lossM = 0.0
    private var minElevM: Double? = null
    private var maxElevM: Double? = null

    // Latest sensor samples, attached to the next accepted point.
    private var heartRate: Double? = null
    private var cadence: Double? = null
    private var power: Double? = null

    fun start(time: Instant) {
        check(startedAt == null) { "already started" }
        startedAt = time
        activeSince = time
    }

    fun onHeartRate(bpm: Double?) { heartRate = bpm }
    fun onCadence(rpm: Double?) { cadence = rpm }
    fun onPower(watts: Double?) { power = watts }

    /** Feeds a GPS fix. Returns true if the point was accepted (passed all filters). */
    fun onLocation(
        latitude: Double,
        longitude: Double,
        altitude: Double?,
        speedMs: Double?,
        bearingDeg: Double?,
        accuracyM: Float,
        time: Instant,
    ): Boolean {
        if (paused || finished || startedAt == null) return false
        if (accuracyM > config.maxAccuracyM) return false

        val here = GeoPoint(latitude, longitude)
        val prevLatLong = lastLatLong
        val prevTime = lastPointTime
        var legDistance = 0.0
        var dt = Duration.ZERO
        if (prevLatLong != null && prevTime != null) {
            legDistance = MetricsCalculator.distanceMeters(prevLatLong, here)
            dt = java.time.Duration.between(prevTime, time).toKotlinDuration()
            val dtSec = dt.inWholeMilliseconds / 1000.0
            if (dtSec > 0 && legDistance / dtSec > config.maxImpliedSpeedMs) return false // GPS jump
            if (legDistance < config.minDistanceM) return false // idle jitter
        }

        val dtSec = dt.inWholeMilliseconds / 1000.0
        val speed = speedMs ?: if (dtSec > 0) legDistance / dtSec else 0.0

        // Altitude: EMA smoothing + hysteresis gate for gain/loss.
        val alt = altitude?.let { raw ->
            val prev = smoothedAlt
            val sm = if (prev == null) raw else prev + config.altitudeSmoothing * (raw - prev)
            if (prev != null) {
                pendingElevDelta += sm - prev
                if (abs(pendingElevDelta) >= config.elevationHysteresisM) {
                    if (pendingElevDelta > 0) gainM += pendingElevDelta else lossM += -pendingElevDelta
                    pendingElevDelta = 0.0
                }
            }
            smoothedAlt = sm
            minElevM = minOf(minElevM ?: sm, sm)
            maxElevM = maxOf(maxElevM ?: sm, sm)
            sm
        }

        distanceM += legDistance
        if (speed >= config.idleSpeedMs) movingTime += dt
        maxSpeedMs = maxOf(maxSpeedMs, speed)

        currentSegment.add(
            Trackpoint(
                trackId = 0L,
                id = seq++,
                latLong = here,
                type = TRACKPOINT_TYPE_TRACKPOINT,
                speed = speed,
                time = time,
                altitude = alt,
                heartRate = heartRate,
                cadence = cadence,
                power = power,
                bearing = bearingDeg,
            ),
        )
        lastLatLong = here
        lastPointTime = time
        return true
    }

    fun pause(time: Instant) {
        if (paused || finished) return
        accumulatedActive += activeDuration(time)
        activeSince = null
        paused = true
        closeSegment()
    }

    fun resume(time: Instant) {
        if (!paused || finished) return
        activeSince = time
        paused = false
        // New segment starts on the next accepted fix; reset the leg baseline so the gap
        // between pause and resume doesn't count as distance.
        lastLatLong = null
        lastPointTime = null
    }

    fun stop(time: Instant) {
        if (finished) return
        accumulatedActive += activeDuration(time)
        activeSince = null
        finished = true
        closeSegment()
    }

    fun snapshot(now: Instant): RecorderState {
        val total = accumulatedActive + activeDuration(now)
        val totalSec = total.inWholeMilliseconds / 1000.0
        val movingSec = movingTime.inWholeMilliseconds / 1000.0
        val stats = TrackStatistics(
            startTime = startedAt,
            stopTime = lastPointTime ?: startedAt,
            totalDistanceMeter = distanceM,
            totalTime = total,
            movingTime = movingTime,
            avgSpeedMeterPerSecond = if (totalSec > 0) distanceM / totalSec else null,
            avgMovingSpeedMeterPerSecond = if (movingSec > 0) distanceM / movingSec else null,
            maxSpeedMeterPerSecond = maxSpeedMs,
            minElevationMeter = minElevM ?: 0.0,
            maxElevationMeter = maxElevM ?: 0.0,
            elevationGainMeter = gainM,
            elevationLossMeter = lossM,
        )
        val segments = buildList {
            addAll(closedSegments)
            if (currentSegment.isNotEmpty()) add(currentSegment.toList())
        }
        return RecorderState(segments = segments, statistics = stats, isPaused = paused, isFinished = finished)
    }

    private fun activeDuration(now: Instant): Duration =
        activeSince?.let { java.time.Duration.between(it, now).toKotlinDuration() } ?: Duration.ZERO

    private fun closeSegment() {
        if (currentSegment.isNotEmpty()) {
            closedSegments.add(currentSegment.toList())
            currentSegment.clear()
        }
    }
}
