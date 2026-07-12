package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import cat.hudpro.opentracks.data.map.MapSource
import cat.hudpro.opentracks.data.map.MapStyleFactory
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cat.hudpro.opentracks.manager.Routes

private data class Tile(val title: String, val subtitle: String, val icon: ImageVector, val route: String)

/**
 * Full-width "Visor" button whose background is a live (non-interactive) map centered on the user's
 * current position — a pretty doorway into the viewer.
 */
@Composable
private fun ViewerMapButton(onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mapView = rememberMapViewWithLifecycle(textureMode = true)
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    // Use the user's online base map (fall back to ICGC if an offline one is active).
                    val baseId = ViewerPreferences.get(context).baseMapId
                        ?.takeUnless { it.startsWith(cat.hudpro.opentracks.data.map.OfflineMap.OFFLINE_PREFIX) }
                    map.setStyle(Style.Builder().fromJson(MapStyleFactory.rasterStyleJson(MapSource.byId(baseId))))
                    val here = lastKnownLatLng(context)
                    map.cameraPosition = CameraPosition.Builder()
                        .target(here ?: LatLng(41.65, 1.95))
                        .zoom(if (here != null) 14.0 else 7.0)
                        .build()
                    map.uiSettings.setAllGesturesEnabled(false)
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isAttributionEnabled = false
                }
                mapView
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Legibility scrim + label + click catcher (the MapView never sees the touch).
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x22000000), Color(0x99000000))))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Map, contentDescription = null, tint = Color.White)
                Text(
                    "  Visor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

/** Most recent OS location fix (any provider), or null without permission/fix. */
private fun lastKnownLatLng(context: android.content.Context): LatLng? {
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!granted) return null
    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE)
        as? android.location.LocationManager ?: return null
    for (provider in listOf("fused", android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)) {
        val loc = runCatching {
            @android.annotation.SuppressLint("MissingPermission")
            lm.getLastKnownLocation(provider)
        }.getOrNull()
        if (loc != null) return LatLng(loc.latitude, loc.longitude)
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenViewer: () -> Unit, onNavigate: (String) -> Unit, onOpenSettings: () -> Unit = {}) {
    val tiles = listOf(
        Tile("Capas de mapa", "Online i offline", Icons.Filled.Layers, Routes.LAYERS),
        Tile("Tracks a seguir", "GPX i col·leccions", Icons.AutoMirrored.Filled.DirectionsRun, Routes.TRACKS),
        Tile("Endurain", "Pujar i sincronitzar", Icons.Filled.CloudUpload, Routes.ENDURAIN),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HUD Pro") },
                actions = {
                    androidx.compose.material3.IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustos")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ViewerMapButton(onClick = onOpenViewer)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tiles) { tile ->
                    Card(onClick = { onNavigate(tile.route) }, modifier = Modifier.height(140.dp)) {
                        Box(Modifier.fillMaxSize().padding(16.dp)) {
                            Icon(tile.icon, contentDescription = null, modifier = Modifier.align(Alignment.TopStart))
                            Column(Modifier.align(Alignment.BottomStart)) {
                                Text(tile.title, fontWeight = FontWeight.Bold)
                                Text(tile.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
