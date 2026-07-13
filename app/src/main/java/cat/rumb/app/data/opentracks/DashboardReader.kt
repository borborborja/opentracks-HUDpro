package cat.rumb.app.data.opentracks

import android.content.ContentResolver
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import cat.rumb.app.data.opentracks.model.Segment
import cat.rumb.app.data.opentracks.model.Track
import cat.rumb.app.data.opentracks.model.TrackStatistics
import cat.rumb.app.data.opentracks.model.Trackpoint
import cat.rumb.app.data.opentracks.model.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "DashboardReader"

private const val EXTRAS_PROTOCOL_VERSION = "PROTOCOL_VERSION"
private const val EXTRAS_OPENTRACKS_IS_RECORDING_THIS_TRACK = "EXTRAS_OPENTRACKS_IS_RECORDING_THIS_TRACK"
private const val EXTRAS_SHOULD_KEEP_SCREEN_ON = "EXTRAS_SHOULD_KEEP_SCREEN_ON"
private const val EXTRAS_SHOW_WHEN_LOCKED = "EXTRAS_SHOULD_KEEP_SCREEN_ON" // matches OpenTracks (upstream copy-paste)
private const val EXTRAS_SHOW_FULLSCREEN = "EXTRAS_SHOULD_FULLSCREEN"

/**
 * Consumes track/trackpoint/marker data from OpenTracks via the Dashboard API and exposes it as
 * observable [StateFlow]s. Registers a [ContentObserver] and re-queries only the delta while
 * recording. Ported from OSMDashboard's DashboardReader + MapDataReader, decoupled from any renderer.
 */
class DashboardReader(
    intent: Intent,
    private val contentResolver: ContentResolver,
) {
    val tracksUri: Uri
    val trackpointsUri: Uri
    val waypointsUri: Uri?
    val protocolVersion: Int
    val isRecording: Boolean
    val keepScreenOn: Boolean
    val showOnLockScreen: Boolean
    val showFullscreen: Boolean

    private var lastTrackPointId: Long? = null
    private var lastWaypointId: Long? = null
    private var contentObserver: ContentObserver? = null

    private val _segments = MutableStateFlow<List<Segment>>(emptyList())
    val segments: StateFlow<List<Segment>> = _segments.asStateFlow()

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _statistics = MutableStateFlow<TrackStatistics?>(null)
    val statistics: StateFlow<TrackStatistics?> = _statistics.asStateFlow()

    private val accumulated = mutableListOf<MutableList<Trackpoint>>()

    init {
        require(intent.isDashboardAction()) { "Intent is not an OpenTracks Dashboard action" }
        @Suppress("DEPRECATION")
        val uris = intent.getParcelableArrayListExtra<Uri>(APIConstants.ACTION_DASHBOARD_PAYLOAD)
            ?: error("Missing dashboard payload")
        protocolVersion = intent.getIntExtra(EXTRAS_PROTOCOL_VERSION, 1)
        tracksUri = APIConstants.getTracksUri(uris)
        trackpointsUri = APIConstants.getTrackpointsUri(uris)
        waypointsUri = APIConstants.getWaypointsUri(uris)
        keepScreenOn = intent.getBooleanExtra(EXTRAS_SHOULD_KEEP_SCREEN_ON, false)
        showOnLockScreen = intent.getBooleanExtra(EXTRAS_SHOW_WHEN_LOCKED, false)
        showFullscreen = intent.getBooleanExtra(EXTRAS_SHOW_FULLSCREEN, false)
        isRecording = intent.getBooleanExtra(EXTRAS_OPENTRACKS_IS_RECORDING_THIS_TRACK, false)
    }

    /** Performs the initial read and, if recording, starts observing for live updates. */
    fun start() {
        readTracks()
        readTrackpoints()
        readWaypoints()
        if (isRecording) startContentObserver()
    }

    fun stop() = unregisterContentObserver()

    private fun readTracks() {
        val tracks = TrackReader.readTracks(contentResolver, tracksUri, protocolVersion)
        _tracks.value = tracks
        _statistics.value = TrackStatistics.fromTracks(tracks)
    }

    private fun readTrackpoints() {
        val delta = TrackpointReader.readTrackpointsBySegments(
            resolver = contentResolver,
            data = trackpointsUri,
            lastId = lastTrackPointId,
            protocolVersion = protocolVersion,
        )
        if (delta.isEmpty()) {
            Log.d(TAG, "No new trackpoints received")
            return
        }
        val lastSeg = delta.segments.lastOrNull { it.isNotEmpty() }
        if (lastSeg != null) lastTrackPointId = lastSeg.last().id
        mergeSegments(delta.segments)
        _segments.value = accumulated.map { it.toList() }
    }

    private fun readWaypoints() {
        val uri = waypointsUri ?: return
        val newWaypoints = WaypointReader.readWaypoints(contentResolver, uri, lastWaypointId, protocolVersion)
        if (newWaypoints.isEmpty()) return
        lastWaypointId = newWaypoints.last().id
        _waypoints.value = _waypoints.value + newWaypoints
    }

    /**
     * Merges freshly-read [delta] segments into [accumulated]. The first new segment continues the
     * last accumulated one when it belongs to the same track and the boundary is not a pause.
     */
    private fun mergeSegments(delta: List<Segment>) {
        if (delta.isEmpty()) return
        val iterator = delta.iterator()
        val first = iterator.next()
        val tail = accumulated.lastOrNull()
        val continues = tail != null && tail.isNotEmpty() && first.isNotEmpty() &&
            tail.last().trackId == first.first().trackId && !tail.last().isPause
        if (continues) {
            tail!!.addAll(first)
        } else if (first.isNotEmpty()) {
            accumulated.add(first.toMutableList())
        }
        while (iterator.hasNext()) {
            accumulated.add(iterator.next().toMutableList())
        }
    }

    private fun startContentObserver() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                when {
                    uri == null -> {}
                    tracksUri.toString().startsWith(uri.toString()) -> readTracks()
                    trackpointsUri.toString().startsWith(uri.toString()) -> readTrackpoints()
                    waypointsUri?.toString()?.startsWith(uri.toString()) == true -> readWaypoints()
                }
            }
        }
        contentObserver = observer
        contentResolver.registerContentObserver(tracksUri, false, observer)
        contentResolver.registerContentObserver(trackpointsUri, false, observer)
        waypointsUri?.let { contentResolver.registerContentObserver(it, false, observer) }
    }

    private fun unregisterContentObserver() {
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
        contentObserver = null
    }
}
