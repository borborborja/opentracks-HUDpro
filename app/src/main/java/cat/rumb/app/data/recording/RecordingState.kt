package cat.rumb.app.data.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide bridge between [RecordingService] and the viewer: the service publishes a
 * [RecorderState] snapshot after every event; the viewer collects it. `null` means idle (no
 * recording and nothing pending to save).
 */
object NativeRecording {
    private val _state = MutableStateFlow<RecorderState?>(null)
    val state: StateFlow<RecorderState?> = _state

    /** True while a native recording is running (started and not yet finished). */
    val isActive: Boolean get() = _state.value?.isFinished == false

    internal fun publish(state: RecorderState) { _state.value = state }

    /** Called by the viewer once the finished recording has been saved or discarded. */
    fun clear() { _state.value = null }
}
