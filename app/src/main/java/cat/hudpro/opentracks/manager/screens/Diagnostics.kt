package cat.hudpro.opentracks.manager.screens

import android.content.Context
import cat.hudpro.opentracks.BuildConfig
import cat.hudpro.opentracks.HudProApplication
import cat.hudpro.opentracks.data.map.OfflineMap
import cat.hudpro.opentracks.data.map.OfflineMapStore
import cat.hudpro.opentracks.data.opentracks.OpenTracksRecording
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import java.util.Locale

/**
 * Full diagnostic report for the debug button: app version, base map, the active follow route (loaded
 * point count + bounds), offline maps/sectors, viewer options, and OpenTracks recording detection.
 * Suspend because it reads the route GPX from the DB.
 */
suspend fun buildDiagnostics(context: Context): String {
    val prefs = ViewerPreferences.get(context)
    val app = HudProApplication.from(context)
    val store = OfflineMapStore.get(context)
    val sb = StringBuilder()

    sb.appendLine("App: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

    sb.appendLine()
    sb.appendLine("== Mapa base ==")
    val baseMapId = prefs.baseMapId
    sb.appendLine("baseMapId: ${baseMapId ?: "(default ICGC)"}")
    val offline = baseMapId?.takeIf { it.startsWith(OfflineMap.OFFLINE_PREFIX) }?.let { store.bySelectionId(it) }
    sb.appendLine("tipus: ${if (offline != null) "offline (${offline.name})" else "online"}")
    val maps = store.list()
    sb.appendLine("Mapes offline registrats: ${maps.size}")
    maps.forEach { m -> sb.appendLine("  • ${m.name} · ${store.sectorsOf(m).size} sectors · src=${m.sourceId ?: "?"}") }

    sb.appendLine()
    sb.appendLine("== Ruta a seguir ==")
    val id = prefs.activeFollowTrackId
    sb.appendLine("activeFollowTrackId: $id")
    if (id > 0) {
        val entity = runCatching { app.trackRepository.get(id) }.getOrNull()
        sb.appendLine("nom: ${entity?.name ?: "(NO trobada a la BD)"}")
        val gpx = runCatching { app.trackRepository.loadGpxRoute(id) }.getOrElse { emptyList() }
        sb.appendLine("punts carregats: ${gpx.size}")
        if (gpx.isNotEmpty()) {
            val lats = gpx.map { it.latitude }
            val lons = gpx.map { it.longitude }
            sb.appendLine(String.format(Locale.US, "bbox: %.5f,%.5f → %.5f,%.5f", lons.min(), lats.min(), lons.max(), lats.max()))
            sb.appendLine(String.format(Locale.US, "1r punt: %.5f, %.5f", gpx.first().latitude, gpx.first().longitude))
        }
    } else {
        sb.appendLine("(cap ruta activa)")
    }

    sb.appendLine()
    sb.appendLine("== Visor ==")
    sb.appendLine("orientació: ${prefs.mapOrientation}")
    sb.appendLine("pantalla encesa: ${prefs.keepScreenOn} · completa: ${prefs.fullscreen}")
    sb.appendLine("unitats: ${prefs.distanceUnit} / ${prefs.elevationUnit} / ${prefs.speedUnit}")
    sb.appendLine("color ruta: ${prefs.followColor} · gruix: ${prefs.followWidth}")

    sb.appendLine()
    sb.appendLine("== Gravació OpenTracks ==")
    sb.append(OpenTracksRecording.detectionReport(context))

    return sb.toString().trim()
}
