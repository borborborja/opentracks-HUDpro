package cat.hudpro.opentracks.data.gpx

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Track file formats the app can import. */
enum class TrackFormat { GPX, KML, KMZ, TCX, UNSUPPORTED }

/** Detects the import format from a file/display name (extension-based). Pure. */
fun formatFor(fileName: String?): TrackFormat = when (fileName?.substringAfterLast('.', "")?.lowercase()) {
    "gpx" -> TrackFormat.GPX
    "kml" -> TrackFormat.KML
    "kmz" -> TrackFormat.KMZ
    "tcx" -> TrackFormat.TCX
    else -> TrackFormat.UNSUPPORTED
}

/** Shares a GPX document via the system chooser (save to Files/Drive, send to apps…). */
object GpxShare {
    fun share(context: Context, name: String, gpx: String) {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "track" }
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "$safe.gpx")
        file.writeText(gpx)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(cat.hudpro.opentracks.R.string.share_export_title, name)))
    }
}
