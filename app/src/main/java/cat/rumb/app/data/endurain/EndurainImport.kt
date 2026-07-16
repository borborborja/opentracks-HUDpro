package cat.rumb.app.data.endurain

import cat.rumb.app.data.gpx.GpxPoint
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Rebuilds a track from Endurain activity streams. Endurain stores no downloadable GPX/FIT, so the
 * geometry is reconstructed from the per-sample streams: the MAP stream (type 7) carries `lat`/`lon`
 * and defines the point sequence; elevation (type 4) and heart-rate (type 1) live in their own
 * streams and are joined back in by the shared `time` axis. Pure logic — unit-tested.
 */
object EndurainImport {

    /**
     * Turns the streams of one activity into ordered [GpxPoint]s. Returns an empty list when the
     * activity has no MAP (GPS) stream — there is no track to import in that case.
     */
    fun streamsToPoints(streams: List<EndurainStream>): List<GpxPoint> {
        val mapStream = streams.firstOrNull { it.streamType == EndurainStreamType.MAP }
            ?: return emptyList()

        // Index elevation/HR by their timestamp so each GPS sample can pull its matching value.
        val eleByTime: Map<String, Double> = streams
            .firstOrNull { it.streamType == EndurainStreamType.ELEVATION }
            ?.waypoints.orEmpty()
            .mapNotNull { w -> w.time?.let { t -> w.ele?.let { t to it } } }
            .toMap()
        val hrByTime: Map<String, Int> = streams
            .firstOrNull { it.streamType == EndurainStreamType.HEART_RATE }
            ?.waypoints.orEmpty()
            .mapNotNull { w -> w.time?.let { t -> w.hr?.let { t to it } } }
            .toMap()

        return mapStream.waypoints.mapNotNull { w ->
            val lat = w.lat ?: return@mapNotNull null
            val lon = w.lon ?: return@mapNotNull null
            val ele = w.ele ?: w.time?.let { eleByTime[it] }
            val hr = w.time?.let { hrByTime[it] }
            GpxPoint(
                latitude = lat,
                longitude = lon,
                elevation = ele,
                time = parseTime(w.time),
                heartRate = hr?.toDouble(),
            )
        }
    }

    /**
     * Parses an Endurain stream timestamp. Endurain emits a naive UTC datetime ("2026-03-28T15:19:19",
     * no zone) via its `format_utc`, but tolerate an explicit `Z`/offset too. Returns null on failure.
     */
    fun parseTime(raw: String?): Instant? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(s) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    /** Epoch millis of an activity's start_time, for preserving its real date on import. */
    fun startMillis(raw: String?): Long? = parseTime(raw)?.toEpochMilli()
}
