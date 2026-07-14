package cat.rumb.app.data.gpx

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Parses a GPX/TCX `<time>` value into an [Instant], or null if unparseable.
 *
 * [Instant.parse] only accepts a value with a zone/offset (`…Z` or `…+02:00`). Some exporters and
 * hand-edited files emit spec-valid `xsd:dateTime` with **no** offset (e.g. `2024-06-01T08:00:00`);
 * those must not be silently dropped — a whole track would import as untimed, killing every derived
 * metric (duration, speed, PRs, gap charts). Fall back to an offset date-time, then to a naive local
 * date-time interpreted as UTC.
 */
fun parseTrackTime(raw: String): Instant? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    runCatching { return Instant.parse(s) }
    runCatching { return OffsetDateTime.parse(s).toInstant() }
    runCatching { return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC) }
    return null
}
