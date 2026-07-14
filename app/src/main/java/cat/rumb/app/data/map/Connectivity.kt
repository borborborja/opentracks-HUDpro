package cat.rumb.app.data.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Cheap point-in-time network check (no callback registration). */
object Connectivity {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
