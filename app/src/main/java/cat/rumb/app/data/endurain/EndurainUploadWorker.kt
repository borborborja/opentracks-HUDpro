package cat.rumb.app.data.endurain

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cat.rumb.app.RumbApplication
import java.io.File

/**
 * Uploads a GPX file to Endurain with retry. The GPX is written to the app's cache first (WorkManager
 * Data has a ~10KB limit, too small for a track), and its path is passed via inputData.
 */
class EndurainUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_PATH) ?: return Result.failure()
        val fileName = inputData.getString(KEY_NAME) ?: "track.gpx"
        val file = File(path)
        if (!file.exists()) return Result.failure()

        val repo = RumbApplication.from(applicationContext).endurainRepository
        return when (val result = repo.uploadGpx(file.readText(), fileName)) {
            is UploadResult.Success -> {
                file.delete()
                Result.success(workDataOf(KEY_RESULT to "ok:${result.activityIds.joinToString(",")}"))
            }
            is UploadResult.NotConfigured -> Result.failure()
            is UploadResult.Failure -> {
                // Retry on transient/network errors (no code); give up on 4xx client errors.
                if (result.code == null || result.code in 500..599) Result.retry()
                else Result.failure(workDataOf(KEY_RESULT to "http:${result.code}"))
            }
        }
    }

    companion object {
        const val KEY_PATH = "gpx_path"
        const val KEY_NAME = "gpx_name"
        const val KEY_RESULT = "result"

        /** Writes [gpx] to cache and enqueues an upload. Returns the queued file for reference. */
        fun enqueue(context: Context, gpx: String, fileName: String): File {
            val dir = File(context.cacheDir, "endurain_queue").apply { mkdirs() }
            // The cache filename must be unique per enqueue: two activities that sanitize to the same
            // name (same-day auto-upload, both-blank manual saves) would otherwise clobber each other's
            // queued bytes before the workers drain — losing one upload and duplicating the other.
            val unique = "${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}"
            val file = File(dir, "$unique.gpx")
            file.writeText(gpx)
            val data: Data = workDataOf(KEY_PATH to file.absolutePath, KEY_NAME to fileName)
            val request = OneTimeWorkRequestBuilder<EndurainUploadWorker>()
                .setInputData(data)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return file
        }
    }
}
