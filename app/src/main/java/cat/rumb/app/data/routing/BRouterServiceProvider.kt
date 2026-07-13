package cat.rumb.app.data.routing

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import btools.routingapp.IBRouterService
import cat.rumb.app.data.gpx.Gpx
import cat.rumb.app.data.opentracks.model.GeoPoint
import cat.rumb.app.viewer.hud.MetricsCalculator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Offline snap-to-path routing via the installed BRouter app (`btools.routingapp`) and its bound
 * AIDL service. Requires the user to have BRouter installed with Catalonia segment (.rd5) data.
 * Falls back is handled by [RoutingRepository].
 */
class BRouterServiceProvider(private val context: Context) : RoutingProvider {

    override suspend fun route(waypoints: List<GeoPoint>, profile: RoutingProfile): RoutedPath {
        require(waypoints.size >= 2) { "Calen almenys 2 punts" }
        val gpx = callService(waypoints, profile)
            ?: throw RoutingException("BRouter offline no ha retornat cap ruta")
        if (!gpx.trimStart().startsWith("<")) {
            // BRouter returns a plain error string (e.g. missing segments) instead of GPX.
            throw RoutingException("BRouter offline: ${gpx.take(120)}")
        }
        val route = gpx.byteInputStream().use { Gpx.read(it) }
        if (route.points.isEmpty()) throw RoutingException("Ruta offline buida")
        var distance = 0.0
        var ascent = 0.0
        for (i in 1 until route.points.size) {
            val prev = route.points[i - 1]
            val cur = route.points[i]
            distance += MetricsCalculator.distanceMeters(
                GeoPoint(prev.latitude, prev.longitude), GeoPoint(cur.latitude, cur.longitude),
            )
            val a = prev.elevation
            val b = cur.elevation
            if (a != null && b != null && b > a) ascent += b - a
        }
        return RoutedPath(route.points, distance, ascent)
    }

    private suspend fun callService(waypoints: List<GeoPoint>, profile: RoutingProfile): String? =
        suspendCancellableCoroutine { cont ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    try {
                        val service = IBRouterService.Stub.asInterface(binder)
                        val params = Bundle().apply {
                            putString("trackFormat", "gpx")
                            putDoubleArray("lats", waypoints.map { it.latitude }.toDoubleArray())
                            putDoubleArray("lons", waypoints.map { it.longitude }.toDoubleArray())
                            putString("fast", "1")
                            putString("v", profile.brouter)
                        }
                        val result = service.getTrackFromParams(params)
                        cont.resume(result)
                    } catch (e: Exception) {
                        cont.resumeWithException(RoutingException(e.message ?: "Error BRouter"))
                    } finally {
                        runCatching { context.unbindService(this) }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
            val intent = Intent("btools.routingapp.BRouterService").apply {
                setClassName(BROUTER_PACKAGE, "btools.routingapp.BRouterService")
            }
            val bound = runCatching {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
            if (!bound) {
                runCatching { context.unbindService(connection) }
                cont.resumeWithException(RoutingException("No s'ha pogut connectar amb BRouter"))
            }
            cont.invokeOnCancellation { runCatching { context.unbindService(connection) } }
        }

    companion object {
        const val BROUTER_PACKAGE = "btools.routingapp"

        fun isInstalled(context: Context): Boolean = runCatching {
            context.packageManager.getPackageInfo(BROUTER_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }
}
