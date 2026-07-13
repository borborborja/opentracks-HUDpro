package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cat.hudpro.opentracks.BuildConfig
import cat.hudpro.opentracks.R
import cat.hudpro.opentracks.data.prefs.ViewerPreferences
import cat.hudpro.opentracks.data.update.ApkInstaller
import cat.hudpro.opentracks.data.update.UpdateInfo
import cat.hudpro.opentracks.data.update.UpdateRepository
import kotlinx.coroutines.launch

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object Downloading : UpdateState
    data class Error(val message: String) : UpdateState
}

private val TABS = listOf(
    R.string.settings_tab_units,
    R.string.settings_tab_recording,
    R.string.settings_tab_sync,
    R.string.settings_tab_appearance,
    R.string.settings_tab_route,
    R.string.settings_tab_audio,
    R.string.settings_tab_app,
)

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDebugLog: () -> Unit = {}, onOpenSensors: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { ViewerPreferences.get(context) }
    var tab by remember { mutableIntStateOf(0) }

    DetailScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                TABS.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(title)) })
                }
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tab) {
                    0 -> UnitsSection(prefs)
                    1 -> RecordingSection(prefs, onOpenSensors)
                    2 -> SyncSection()
                    3 -> TrackAppearanceSection(prefs)
                    4 -> FollowRouteSection(prefs)
                    5 -> AudioAnnouncementsSection(prefs)
                    else -> AppSection(onOpenDebugLog)
                }
            }
        }
    }
}

// --- Units ---

@Composable
private fun UnitsSection(prefs: ViewerPreferences) {
    var dist by remember { mutableStateOf(prefs.distanceUnit) }
    var elev by remember { mutableStateOf(prefs.elevationUnit) }
    var spd by remember { mutableStateOf(prefs.speedUnit) }

    Text(stringResource(R.string.settings_units_distance), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(dist == "KM", { dist = "KM"; prefs.distanceUnit = "KM" }, label = { Text(stringResource(R.string.settings_units_kilometers)) })
        FilterChip(dist == "MILE", { dist = "MILE"; prefs.distanceUnit = "MILE" }, label = { Text(stringResource(R.string.settings_units_miles)) })
    }
    Text(stringResource(R.string.settings_units_elevation), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(elev == "METER", { elev = "METER"; prefs.elevationUnit = "METER" }, label = { Text(stringResource(R.string.settings_units_meters)) })
        FilterChip(elev == "FOOT", { elev = "FOOT"; prefs.elevationUnit = "FOOT" }, label = { Text(stringResource(R.string.settings_units_feet)) })
    }
    Text(stringResource(R.string.settings_units_speed), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(spd == "KMH", { spd = "KMH"; prefs.speedUnit = "KMH" }, label = { Text("km/h") })
        FilterChip(spd == "MPH", { spd = "MPH"; prefs.speedUnit = "MPH" }, label = { Text("mph") })
    }
    Text(
        stringResource(R.string.settings_units_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 8.dp),
    )
}

// --- Sync (Endurain now; more services later) ---

@Composable
private fun SyncSection() {
    val context = LocalContext.current
    val endurainPrefs = remember { cat.hudpro.opentracks.data.prefs.EndurainPreferences.get(context) }
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(endurainPrefs.host ?: "") }
    var apiKey by remember { mutableStateOf(endurainPrefs.apiKey ?: "") }
    var status by remember { mutableStateOf<String?>(null) }

    Text(stringResource(R.string.settings_sync_endurain), style = MaterialTheme.typography.titleSmall)
    androidx.compose.material3.OutlinedTextField(
        value = host,
        onValueChange = { host = it },
        label = { Text(stringResource(R.string.settings_sync_server_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    androidx.compose.material3.OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        label = { Text(stringResource(R.string.settings_sync_api_key_label)) },
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            endurainPrefs.host = host
            endurainPrefs.apiKey = apiKey
            status = context.getString(R.string.settings_sync_saved_testing)
            scope.launch {
                val repo = cat.hudpro.opentracks.data.endurain.EndurainRepository(endurainPrefs)
                status = repo.testConnection().fold(
                    onSuccess = { context.getString(R.string.settings_sync_connected, "$it") },
                    onFailure = { context.getString(R.string.settings_error, "${it.message}") },
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.settings_sync_save_test)) }
    status?.let { Card { Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) } }
    Text(
        stringResource(R.string.settings_sync_upload_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )

    Text(stringResource(R.string.settings_sync_coming_soon), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    Text(
        stringResource(R.string.settings_sync_coming_soon_items),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

// --- Native recording engine ---

@Composable
private fun RecordingSection(prefs: ViewerPreferences, onOpenSensors: () -> Unit) {
    var interval by remember { mutableIntStateOf(prefs.recGpsIntervalSec) }
    var minDist by remember { mutableFloatStateOf(prefs.recMinDistanceM) }
    var accuracy by remember { mutableFloatStateOf(prefs.recMaxAccuracyM) }
    var autoPause by remember { mutableStateOf(prefs.recAutoPause) }
    var barometer by remember { mutableStateOf(prefs.recBarometer) }

    Text(stringResource(R.string.settings_rec_gps_interval), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 5).forEach { s ->
            FilterChip(interval == s, { interval = s; prefs.recGpsIntervalSec = s }, label = { Text("${s}s") })
        }
    }

    Text(stringResource(R.string.settings_rec_min_distance, minDist.toInt()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Slider(
        value = minDist,
        onValueChange = { minDist = it; prefs.recMinDistanceM = it },
        valueRange = 1f..15f,
        steps = 13,
    )

    Text(stringResource(R.string.settings_rec_max_accuracy, accuracy.toInt()), style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.settings_rec_accuracy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    Slider(
        value = accuracy,
        onValueChange = { accuracy = it; prefs.recMaxAccuracyM = it },
        valueRange = 10f..100f,
        steps = 8,
    )

    ToggleRow(stringResource(R.string.settings_rec_auto_pause), autoPause) { autoPause = it; prefs.recAutoPause = it }
    ToggleRow(stringResource(R.string.settings_rec_barometer), barometer) { barometer = it; prefs.recBarometer = it }

    OutlinedButton(onClick = onOpenSensors, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(stringResource(R.string.settings_rec_ble_sensors))
    }
    Text(
        stringResource(R.string.settings_rec_paired_sensors, prefs.bleSensorAddrs.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
    Text(
        stringResource(R.string.settings_rec_apply_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 8.dp),
    )
}

// --- App (version / update / debug / about) ---

@Composable
private fun AppSection(onOpenDebugLog: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { UpdateRepository() }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }

    LanguageCard()

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings_app_installed_version), style = MaterialTheme.typography.labelMedium)
            Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleMedium)
        }
    }

    val networkError = stringResource(R.string.settings_update_network_error)
    Button(
        onClick = {
            state = UpdateState.Checking
            scope.launch {
                state = try {
                    repo.checkForUpdate()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
                } catch (e: Exception) {
                    UpdateState.Error(e.message ?: networkError)
                }
            }
        },
        enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.settings_check_update)) }

    when (val s = state) {
        is UpdateState.Checking -> Text(stringResource(R.string.settings_update_checking))
        is UpdateState.UpToDate -> Text(stringResource(R.string.settings_update_up_to_date))
        is UpdateState.Error -> Text(stringResource(R.string.settings_error, s.message), color = MaterialTheme.colorScheme.error)
        is UpdateState.Downloading ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_update_downloading, (progress * 100).toInt()))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        is UpdateState.Available -> Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_update_new_version, s.info.version), style = MaterialTheme.typography.titleMedium)
                Text(s.info.changelog, style = MaterialTheme.typography.bodySmall)
                val downloadError = stringResource(R.string.settings_update_download_error)
                Button(
                    onClick = {
                        if (!ApkInstaller.canInstall(context)) {
                            ApkInstaller.requestInstallPermission(context)
                            return@Button
                        }
                        state = UpdateState.Downloading
                        progress = 0f
                        scope.launch {
                            try {
                                val file = ApkInstaller.download(context, s.info.apkUrl) { progress = it }
                                ApkInstaller.install(context, file)
                                state = UpdateState.Idle
                            } catch (e: Exception) {
                                state = UpdateState.Error(e.message ?: downloadError)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_update_download_install)) }
            }
        }
        UpdateState.Idle -> {}
    }

    // Debug: full app/viewer diagnostics.
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var diag by remember { mutableStateOf<String?>(null) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_debug_section), style = MaterialTheme.typography.labelMedium)
            OutlinedButton(
                onClick = onOpenDebugLog,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_debug_log_button)) }
            OutlinedButton(
                onClick = { scope.launch { diag = buildDiagnostics(context) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_debug_full)) }
            OutlinedButton(
                onClick = { diag = cat.hudpro.opentracks.data.opentracks.OpenTracksRecording.diagnostics(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_debug_test_recording)) }
            diag?.let { report ->
                OutlinedButton(
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(report)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_debug_copy)) }
                Text(report, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.settings_about_desc),
                style = MaterialTheme.typography.bodySmall)
            Text("github.com/borborborja/opentracks-HUDpro", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// --- App language (per-app locales, API 33+) ---

@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lang_title), style = MaterialTheme.typography.labelMedium)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
                var current by remember {
                    mutableStateOf(localeManager.applicationLocales.toLanguageTags().substringBefore('-'))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = current.isEmpty(),
                        onClick = {
                            current = ""
                            localeManager.applicationLocales = android.os.LocaleList.getEmptyLocaleList()
                        },
                        label = { Text(stringResource(R.string.lang_system)) },
                    )
                    listOf("ca" to "Català", "es" to "Español", "en" to "English").forEach { (code, label) ->
                        FilterChip(
                            selected = current == code,
                            onClick = {
                                current = code
                                localeManager.applicationLocales = android.os.LocaleList.forLanguageTags(code)
                            },
                            label = { Text(label) },
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.lang_hint_old),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

// --- Track appearance (moved from the HUD designer) ---

@Composable
private fun TrackAppearanceSection(prefs: ViewerPreferences) {
    val palette = listOf("#E63946", "#3A86FF", "#2A9D8F", "#F4A261", "#FFD166", "#9B5DE5")
    var mode by remember { mutableStateOf(cat.hudpro.opentracks.data.map.TrackColorMode.byName(prefs.trackColorMode)) }
    var color by remember { mutableStateOf(prefs.trackColor) }

    Text(stringResource(R.string.settings_appearance_track), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cat.hudpro.opentracks.data.map.TrackColorMode.entries.forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = { mode = m; prefs.trackColorMode = m.name },
                label = { Text(stringResource(m.labelRes)) },
            )
        }
    }
    if (mode == cat.hudpro.opentracks.data.map.TrackColorMode.SINGLE) {
        ColorPalette(palette, color) { color = it; prefs.trackColor = it }
    }
}

// --- Follow route + off-route alert (moved from the HUD designer) ---

@Composable
private fun FollowRouteSection(prefs: ViewerPreferences) {
    val palette = listOf("#3A86FF", "#E63946", "#2A9D8F", "#F4A261", "#FFD166", "#9B5DE5")
    var color by remember { mutableStateOf(prefs.followColor) }
    var width by remember { mutableFloatStateOf(prefs.followWidth) }
    var arrows by remember { mutableStateOf(prefs.followArrows) }
    var progress by remember { mutableStateOf(prefs.followProgress) }
    var threshold by remember { mutableFloatStateOf(prefs.offRouteThresholdM.toFloat()) }
    var sound by remember { mutableStateOf(prefs.offRouteSound) }
    var vibrate by remember { mutableStateOf(prefs.offRouteVibrate) }

    Text(stringResource(R.string.settings_route_follow), style = MaterialTheme.typography.labelLarge)
    ColorPalette(palette, color) { color = it; prefs.followColor = it }
    Text(stringResource(R.string.settings_route_width, width.toInt()), style = MaterialTheme.typography.bodySmall)
    Slider(value = width, onValueChange = { width = it; prefs.followWidth = it }, valueRange = 3f..12f)
    ToggleRow(stringResource(R.string.settings_route_arrows), arrows) { arrows = it; prefs.followArrows = it }
    ToggleRow(stringResource(R.string.settings_route_progress), progress) { progress = it; prefs.followProgress = it }

    Text(stringResource(R.string.settings_route_off_route), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Text(stringResource(R.string.settings_route_threshold, threshold.toInt()), style = MaterialTheme.typography.bodySmall)
    Slider(
        value = threshold,
        onValueChange = { threshold = it; prefs.offRouteThresholdM = it.toInt() },
        valueRange = 10f..100f,
        steps = 8,
    )
    ToggleRow(stringResource(R.string.settings_route_sound), sound) { sound = it; prefs.offRouteSound = it }
    ToggleRow(stringResource(R.string.settings_route_vibration), vibrate) { vibrate = it; prefs.offRouteVibrate = it }
}

// --- Audio announcements (moved from the HUD designer) ---

@Composable
private fun AudioAnnouncementsSection(prefs: ViewerPreferences) {
    var enabled by remember { mutableStateOf(prefs.announceEnabled) }
    var voice by remember { mutableStateOf(prefs.announceMode == "VOICE") }
    var lang by remember { mutableStateOf(cat.hudpro.opentracks.viewer.audio.AnnounceLang.byCode(prefs.announceLang)) }
    var byDist by remember { mutableStateOf(prefs.announceByDistance) }
    var distKm by remember { mutableFloatStateOf(prefs.announceDistanceKm) }
    var byTime by remember { mutableStateOf(prefs.announceByTime) }
    var timeMin by remember { mutableFloatStateOf(prefs.announceTimeMin.toFloat()) }
    var fDist by remember { mutableStateOf(prefs.annDistanceTime) }
    var fPace by remember { mutableStateOf(prefs.annPace) }
    var fSplit by remember { mutableStateOf(prefs.annSplitPace) }
    var fElev by remember { mutableStateOf(prefs.annElevation) }
    var fHr by remember { mutableStateOf(prefs.annHeartRate) }
    var offSpoken by remember { mutableStateOf(prefs.offRouteSpoken) }

    Text(stringResource(R.string.settings_audio_title), style = MaterialTheme.typography.labelLarge)
    ToggleRow(stringResource(R.string.settings_audio_enable), enabled) { enabled = it; prefs.announceEnabled = it }
    if (!enabled) return

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(voice, { voice = true; prefs.announceMode = "VOICE" }, label = { Text(stringResource(R.string.settings_audio_voice)) })
        FilterChip(!voice, { voice = false; prefs.announceMode = "BEEP" }, label = { Text(stringResource(R.string.settings_audio_beeps)) })
    }
    if (voice) {
        Text(stringResource(R.string.settings_audio_language), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cat.hudpro.opentracks.viewer.audio.AnnounceLang.entries.forEach { l ->
                FilterChip(lang == l, { lang = l; prefs.announceLang = l.code }, label = { Text(l.label) })
            }
        }
    }

    ToggleRow(stringResource(R.string.settings_every_km, distKm.toInt()), byDist) { byDist = it; prefs.announceByDistance = it }
    if (byDist) {
        Slider(value = distKm, onValueChange = { distKm = it; prefs.announceDistanceKm = it }, valueRange = 1f..10f, steps = 8)
    }
    ToggleRow(stringResource(R.string.settings_every_min, timeMin.toInt()), byTime) { byTime = it; prefs.announceByTime = it }
    if (byTime) {
        Slider(value = timeMin, onValueChange = { timeMin = it; prefs.announceTimeMin = it.toInt() }, valueRange = 1f..30f, steps = 28)
    }

    if (voice) {
        Text(stringResource(R.string.settings_audio_announce_what), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        ToggleRow(stringResource(R.string.settings_audio_distance_time), fDist) { fDist = it; prefs.annDistanceTime = it }
        ToggleRow(stringResource(R.string.settings_audio_pace), fPace) { fPace = it; prefs.annPace = it }
        ToggleRow(stringResource(R.string.settings_audio_split_pace), fSplit) { fSplit = it; prefs.annSplitPace = it }
        ToggleRow(stringResource(R.string.settings_audio_elevation), fElev) { fElev = it; prefs.annElevation = it }
        ToggleRow(stringResource(R.string.settings_audio_heart_rate), fHr) { fHr = it; prefs.annHeartRate = it }
        ToggleRow(stringResource(R.string.settings_audio_off_route_spoken), offSpoken) { offSpoken = it; prefs.offRouteSpoken = it }
    }
}

// --- Shared bits ---

@Composable
private fun ColorPalette(palette: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        palette.forEach { hex ->
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(hex)))
                    .border(
                        if (selected == hex) 3.dp else 1.dp,
                        if (selected == hex) Color.Black else Color.Gray,
                        CircleShape,
                    )
                    .clickable { onPick(hex) },
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange)
        Text("  $label", style = MaterialTheme.typography.bodyMedium)
    }
}
