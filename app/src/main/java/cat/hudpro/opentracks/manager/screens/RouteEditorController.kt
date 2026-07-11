package cat.hudpro.opentracks.manager.screens

import cat.hudpro.opentracks.data.gpx.GpxPoint
import cat.hudpro.opentracks.data.map.MapSource
import cat.hudpro.opentracks.data.map.MapStyleFactory
import cat.hudpro.opentracks.data.opentracks.model.GeoPoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Draws the in-progress route (snapped line) and its waypoints on an editable MapLibre map. */
class RouteEditorController(private val map: MapLibreMap) {

    private var routeSource: GeoJsonSource? = null
    private var waypointSource: GeoJsonSource? = null

    fun init(onReady: () -> Unit) {
        map.setStyle(Style.Builder().fromJson(MapStyleFactory.rasterStyleJson(MapSource.ICGC_TOPO))) { style ->
            val route = GeoJsonSource(ROUTE_SOURCE, FeatureCollection.fromFeatures(emptyList()))
            style.addSource(route)
            style.addLayer(
                LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                    PropertyFactory.lineColor("#3A86FF"),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineJoin("round"),
                ),
            )
            routeSource = route

            val wp = GeoJsonSource(WP_SOURCE, FeatureCollection.fromFeatures(emptyList()))
            style.addSource(wp)
            style.addLayer(
                CircleLayer(WP_LAYER, WP_SOURCE).withProperties(
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleColor("#E63946"),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(2f),
                ),
            )
            waypointSource = wp

            // Center on Catalonia by default.
            map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(41.65, 1.95), 9.0))
            onReady()
        }
    }

    fun onMapClick(listener: (GeoPoint) -> Unit) {
        map.addOnMapClickListener { latLng ->
            listener(GeoPoint(latLng.latitude, latLng.longitude))
            true
        }
    }

    fun setWaypoints(points: List<GeoPoint>) {
        val features = points.map { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
        waypointSource?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun setRoute(points: List<GpxPoint>) {
        val feature = if (points.size >= 2) {
            listOf(Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })))
        } else {
            emptyList()
        }
        routeSource?.setGeoJson(FeatureCollection.fromFeatures(feature))
    }

    private companion object {
        const val ROUTE_SOURCE = "editor-route-source"
        const val ROUTE_LAYER = "editor-route-layer"
        const val WP_SOURCE = "editor-wp-source"
        const val WP_LAYER = "editor-wp-layer"
    }
}
