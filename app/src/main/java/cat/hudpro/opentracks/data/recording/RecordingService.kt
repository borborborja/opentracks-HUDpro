package cat.hudpro.opentracks.data.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import cat.hudpro.opentracks.data.debug.DebugLog
import java.time.Instant
import java.util.Locale

/**
 * Foreground service (type=location) hosting the native [TrackRecorder]: keeps GPS alive with the
 * screen off, shows a live-stats notification with pause/stop actions, and publishes every update to
 * [NativeRecording] for the viewer. Modeled on OpenTracks' TrackRecordingService (Apache-2.0).
 */
class RecordingService : Service() {

    private var recorder: TrackRecorder? = null
    private val gps = GpsSource(this)
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifiedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin()
            ACTION_PAUSE -> recorder?.let { it.pause(Instant.now()); publish(); updateNotification(force = true); DebugLog.i("Record", "pausa") }
            ACTION_RESUME -> recorder?.let { it.resume(Instant.now()); publish(); updateNotification(force = true); DebugLog.i("Record", "reprendre") }
            ACTION_STOP -> finish()
        }
        return START_STICKY
    }

    private fun begin() {
        if (recorder != null) return // already recording
        val r = TrackRecorder()
        r.start(Instant.now())
        recorder = r
        publish()
        startForegroundWithType()
        acquireWakeLock()
        val ok = gps.start { loc ->
            val rec = recorder ?: return@start
            rec.onLocation(
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = if (loc.hasAltitude()) loc.altitude else null,
                speedMs = if (loc.hasSpeed()) loc.speed.toDouble() else null,
                bearingDeg = if (loc.hasBearing()) loc.bearing.toDouble() else null,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE,
                time = Instant.ofEpochMilli(loc.time),
            )
            publish()
            updateNotification(force = false)
        }
        DebugLog.i("Record", "gravació nativa iniciada · gps=$ok")
    }

    private fun finish() {
        gps.stop()
        recorder?.let {
            it.stop(Instant.now())
            publish()
            DebugLog.i("Record", "gravació nativa aturada · ${it.snapshot(Instant.now()).points().size} punts")
        }
        recorder = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publish() {
        recorder?.let { NativeRecording.publish(it.snapshot(Instant.now())) }
    }

    override fun onDestroy() {
        gps.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    // --- Notification ---

    private fun startForegroundWithType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifiedAt < 3000) return // throttle
        lastNotifiedAt = now
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Gravació d'activitat", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val state = recorder?.snapshot(Instant.now())
        val stats = state?.statistics
        val km = (stats?.totalDistanceMeter ?: 0.0) / 1000.0
        val secs = stats?.totalTime?.inWholeSeconds ?: 0
        val text = String.format(
            Locale.US, "%.2f km · %d:%02d:%02d%s",
            km, secs / 3600, (secs % 3600) / 60, secs % 60,
            if (state?.isPaused == true) " · EN PAUSA" else "",
        )
        val paused = state?.isPaused == true

        fun action(action: String, code: Int): PendingIntent = PendingIntent.getService(
            this, code, Intent(this, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openViewer = PendingIntent.getActivity(
            this, 0,
            Intent().setClassName(this, "cat.hudpro.opentracks.viewer.MapViewerActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Gravant activitat")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openViewer)
            .addAction(
                0,
                if (paused) "Reprendre" else "Pausa",
                action(if (paused) ACTION_RESUME else ACTION_PAUSE, 1),
            )
            .addAction(0, "Aturar", action(ACTION_STOP, 2))
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HUDpro:recording").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) // 12h safety cap
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        private const val ACTION_START = "cat.hudpro.opentracks.recording.START"
        private const val ACTION_PAUSE = "cat.hudpro.opentracks.recording.PAUSE"
        private const val ACTION_RESUME = "cat.hudpro.opentracks.recording.RESUME"
        private const val ACTION_STOP = "cat.hudpro.opentracks.recording.STOP"
        private const val CHANNEL = "recording"
        private const val NOTIF_ID = 4243

        fun start(context: Context) = send(context, ACTION_START)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String) {
            val intent = Intent(context, RecordingService::class.java).setAction(action)
            if (action == ACTION_START) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
