/* Copyright (C) 2026 kitsumed (Med), Uriel Maznur. GPL-3.0-or-later; see LICENSE. */
package com.kitsumed.shizucallrecorder.ui.screens

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.recording.RecordingHistoryEntry
import com.kitsumed.shizucallrecorder.system.recordingShareIntent
import com.kitsumed.shizucallrecorder.ui.viewmodels.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.*
import java.util.Date

@Composable
fun RecorderHomeScreen(settingsViewModel: SettingsViewModel, launchGeneration: Int = 0,
    recordingReady: Boolean = true, onSetupRecording: () -> Unit = {}) {
    var settings by rememberSaveable(launchGeneration) { mutableStateOf(false) }
    var detail by remember(launchGeneration) { mutableStateOf(false) }
    BackHandler(settings) { settings = false }
    ReplayHomeLayout(settings, detail, { settings = it }) {
        if(settings) CallReplaySettingsScreen(settingsViewModel, onSetupRecording, onDetailChanged = { detail = it })
        else RecordingHistoryScreen(onOpenSettings = { settings = true },
            onSetupRecording = if(recordingReady) null else onSetupRecording, onPlayerChanged = { detail = it })
    }
}

@Composable
internal fun ReplayHomeLayout(settings: Boolean, detail: Boolean, onSelectSettings: (Boolean) -> Unit,
    content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val destinations = listOf(R.string.history_title to Icons.AutoMirrored.Filled.List,
            R.string.history_settings to Icons.Default.Settings)
        Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
            if (!detail && !wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                destinations.forEachIndexed { index, (label, icon) -> NavigationBarItem(
                    selected = settings == (index == 1), onClick = { onSelectSettings(index == 1) },
                    icon = { Icon(icon, null) }, label = { Text(stringResource(label)) }) }
            }
        }) { padding -> Row(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            if(wide && !detail) NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                destinations.forEachIndexed { index, (label, icon) -> NavigationRailItem(
                    selected = settings == (index == 1), onClick = { onSelectSettings(index == 1) },
                    icon = { Icon(icon, null) }, label = { Text(stringResource(label)) }) }
            }
            Box(Modifier.weight(1f)) {
                content()
            }
        } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingHistoryScreen(onOpenSettings: () -> Unit, onSetupRecording: (() -> Unit)?,
    onPlayerChanged: (Boolean) -> Unit, viewModel: RecordingHistoryViewModel = viewModel()) {
    val state by viewModel.history.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val shareTitle = stringResource(R.string.general_share)
    val shareError = stringResource(R.string.history_share_failed)
    val deleteError = stringResource(R.string.delete_recording_confirmation_failed)
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var actionsEntry by remember { mutableStateOf<RecordingHistoryEntry?>(null) }
    var deleting by remember { mutableStateOf<RecordingHistoryEntry?>(null) }
    var info by remember { mutableStateOf<RecordingHistoryEntry?>(null) }
    val selected = state.entries.find { it.uri.toString() == selectedUri }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopPlayback() }
    DisposableEffect(Unit) { onDispose { viewModel.stopPlayback() } }
    LaunchedEffect(selected) { onPlayerChanged(selected != null) }
    BackHandler(selected != null) { selectedUri = null }
    val share: (RecordingHistoryEntry) -> Unit = { entry ->
        runCatching { context.startActivity(Intent.createChooser(recordingShareIntent(entry.uri,
            context.contentResolver.getType(entry.uri), entry.fileName), shareTitle))
        }.onFailure { scope.launch { snackbar.showSnackbar(shareError) } }
    }
    if(selected != null) RecordingPlayerContent(selected, playback, { selectedUri = null },
        { viewModel.toggle(selected) }, viewModel::seek, viewModel::setSpeed, viewModel::toggleMute,
        { share(selected) }, { actionsEntry = selected }, snackbar)
    else RecordingHistoryContent(state, playback, onOpenSettings, viewModel::refresh, viewModel::toggle,
        viewModel::seek, share, snackbar, onSetupRecording,
        onOpen = { entry -> selectedUri = entry.uri.toString(); if(playback.uri != entry.uri) viewModel.toggle(entry) },
        onActions = { actionsEntry = it })
    actionsEntry?.let { entry ->
        RecordingActionsSheet(onDismiss = { actionsEntry = null }, onContinue = {
                    selectedUri = entry.uri.toString()
                    if(playback.uri != entry.uri || !playback.playing) viewModel.toggle(entry)
                    actionsEntry = null
                }, onShare = { actionsEntry = null; share(entry) },
            onDelete = { deleting = entry; actionsEntry = null }, onInfo = { info = entry; actionsEntry = null })
    }
    deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.delete_recording_confirmation_title)) },
        text = { Text(stringResource(R.string.delete_recording_confirmation_message)) },
        confirmButton = { TextButton(onClick = {
            deleting = null
            scope.launch { if(viewModel.delete(entry)) { selectedUri = null; viewModel.refresh() }
                else snackbar.showSnackbar(deleteError) }
        }) { Text(stringResource(R.string.general_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.general_cancel)) } }) }
    info?.let { entry -> AlertDialog(onDismissRequest = { info = null }, title = { Text(stringResource(R.string.replay_file_info)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(entry.fileName, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
            Text(DateFormat.getDateFormat(context).format(Date(entry.startedAt)))
            Text(entry.phoneNumber, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
        } }, confirmButton = { TextButton(onClick = { info = null }) { Text(stringResource(R.string.general_close)) } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingActionsSheet(onDismiss: () -> Unit, onContinue: () -> Unit, onShare: () -> Unit,
    onDelete: () -> Unit, onInfo: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            ActionLine(stringResource(R.string.replay_continue), Icons.Default.PlayArrow, onContinue)
            ActionLine(stringResource(R.string.general_share), Icons.Default.Share, onShare)
            ActionLine(stringResource(R.string.general_delete), Icons.Default.DeleteOutline, onDelete)
            ActionLine(stringResource(R.string.replay_file_info), Icons.Default.Info, onInfo)
        }
    }
}

@Composable
private fun ActionLine(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Icon(icon, null); Spacer(Modifier.width(18.dp)); Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingHistoryContent(state: RecordingHistoryState, playback: RecordingPlaybackState,
    onOpenSettings: () -> Unit, onRefresh: () -> Unit, onToggle: (RecordingHistoryEntry) -> Unit,
    onSeek: (Int) -> Unit, onShare: (RecordingHistoryEntry) -> Unit,
    snackbarHostState: SnackbarHostState? = null, onSetupRecording: (() -> Unit)? = null,
    onOpen: (RecordingHistoryEntry) -> Unit = {}, onActions: (RecordingHistoryEntry) -> Unit = {},
    initialSearchExpanded: Boolean = false) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(initialSearchExpanded) }
    var directionToken by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val direction = CallDirection.fromToken(directionToken)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    val entries = state.entries.filter { entry -> (direction == null || entry.direction == direction) &&
        (query.isBlank() || listOf(entry.callerName.orEmpty(), entry.phoneNumber, entry.fileName)
            .any { it.contains(query.trim(), ignoreCase = true) }) }
    val groups = entries.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } }, topBar = {
            Column {
                if(searchOpen) OutlinedTextField(query, { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.history_search)) }, placeholder = { Text(stringResource(R.string.history_search_hint)) },
                    shape = CircleShape, modifier = Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    leadingIcon = { IconButton(onClick = { searchOpen = false; query = ""; keyboard?.hide() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.replay_back)) } },
                    trailingIcon = { if(query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, stringResource(R.string.history_clear_search)) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }))
                else TopAppBar(title = { Text("CallReplay", fontWeight = FontWeight.Bold) }, actions = {
                    IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, stringResource(R.string.history_search)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.replay_more)) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.history_refresh)) }, enabled = !state.loading,
                                onClick = { menu = false; onRefresh() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.general_settings)) }, onClick = { menu = false; onOpenSettings() })
                        }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(null, CallDirection.INCOMING, CallDirection.OUTGOING).forEach { option ->
                        FilterChip(selected = direction == option, onClick = { directionToken = option?.token },
                            label = { Text(stringResource(when(option) { null -> R.string.history_all
                                CallDirection.INCOMING -> R.string.history_incoming; else -> R.string.history_outgoing })) },
                            shape = CircleShape, colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                    }
                }
                if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().testTag("recording-history-list"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if(onSetupRecording != null) item { TextButton(onClick = onSetupRecording) {
                Icon(Icons.Default.MicNone, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.history_setup)) } }
            when {
                state.folderUnavailable || state.loadFailed -> item { Column(Modifier.padding(20.dp)) {
                    Text(stringResource(if(state.folderUnavailable) R.string.history_folder_missing else R.string.history_load_failed))
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.history_settings)) }
                } }
                !state.loading && entries.isEmpty() -> item { Text(stringResource(
                    if(state.entries.isEmpty()) R.string.history_empty else R.string.history_no_matches), Modifier.padding(20.dp)) }
                else -> groups.forEach { (date, recordings) ->
                    item(key = "date:$date") { Text(dateLabel(date), Modifier.padding(horizontal = 8.dp).padding(top = 12.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelLarge) }
                    items(recordings, key = { it.uri.toString() }) { entry -> RecordingCard(entry, playback,
                        { onToggle(entry) }, onSeek, { onShare(entry) }, { onOpen(entry) }, { onActions(entry) }) }
                }
            }
            item { TextButton(onClick = { uriHandler.openUri("https://github.com/kitsumed/ShizuCallRecorder") }) {
                Text(stringResource(R.string.history_fork_attribution), style = MaterialTheme.typography.labelSmall) } }
        }
    }
}

@Composable
private fun recordingTitle(entry: RecordingHistoryEntry) = entry.callerName?.takeIf { it.isNotBlank() }
    ?: entry.phoneNumber.takeIf { it.isNotBlank() } ?: stringResource(R.string.replay_unknown)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingCard(entry: RecordingHistoryEntry, playback: RecordingPlaybackState, onToggle: () -> Unit,
    onSeek: (Int) -> Unit, onShare: () -> Unit, onOpen: () -> Unit, onActions: () -> Unit) {
    val title = recordingTitle(entry)
    val context = LocalContext.current
    val selected = playback.uri == entry.uri
    val duration by produceState(0, entry.uri) { value = withContext(Dispatchers.IO) { runCatching {
        MediaMetadataRetriever().use { retriever -> retriever.setDataSource(context, entry.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0 }
    }.getOrDefault(0) } }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onActions)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Icon(when(entry.direction) { CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
                    CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade; null -> Icons.Default.GraphicEq },
                    contentDescription = entry.direction?.let { stringResource(if(it == CallDirection.INCOMING)
                        R.string.history_incoming else R.string.history_outgoing) },
                    tint = when(entry.direction) {
                        CallDirection.OUTGOING -> MaterialTheme.colorScheme.tertiary
                        CallDirection.INCOMING -> if(MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFF80BAFF) else Color(0xFF087DF5)
                        null -> MaterialTheme.colorScheme.primary
                    }, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr),
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.phoneNumber.takeIf { it.isNotBlank() } ?: entry.fileName,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(DateFormat.getTimeFormat(context).format(Date(entry.startedAt)) + if(duration > 0) "  ·  ${formatPlaybackTime(duration)}" else "",
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = onToggle) {
                    if(selected && playback.preparing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(if(selected && playback.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if(selected && playback.playing) R.string.history_pause else R.string.history_play, title))
                }
                // Share remains one tap away and accessible while the compact player is selected.
                if(selected) IconButton(onClick = onShare) { Icon(Icons.Default.Share, stringResource(R.string.history_share, title)) }
            }
            if(selected && playback.failed) Text(stringResource(R.string.history_play_failed), color = MaterialTheme.colorScheme.error)
            if(selected && playback.duration > 0 && !playback.failed) {
                val label = stringResource(R.string.history_seek)
                Slider(playback.position.toFloat(), { onSeek(it.toInt()) }, valueRange = 0f..playback.duration.toFloat(),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RecordingPlayerContent(entry: RecordingHistoryEntry, playback: RecordingPlaybackState,
    onBack: () -> Unit, onToggle: () -> Unit, onSeek: (Int) -> Unit, onSpeed: (Float) -> Unit,
    onMute: () -> Unit, onShare: () -> Unit, onMore: () -> Unit, snackbar: SnackbarHostState? = null) {
    val context = LocalContext.current
    val title = recordingTitle(entry)
    var speedMenu by remember { mutableStateOf(false) }
    var scrub by remember(entry.uri) { mutableStateOf<Float?>(null) }
    val active = playback.uri == entry.uri
    ReplayPage("", onBack, actions = { IconButton(onClick = onMore) {
        Icon(Icons.Default.MoreVert, stringResource(R.string.replay_more)) } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp)); WaveMark(112.dp); Spacer(Modifier.height(24.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(entry.phoneNumber.ifBlank { entry.fileName }, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr))
                Spacer(Modifier.height(16.dp))
                Text(DateFormat.getDateFormat(context).format(Date(entry.startedAt)) + " · " +
                    DateFormat.getTimeFormat(context).format(Date(entry.startedAt)), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(64.dp))
                CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                val seekLabel = stringResource(R.string.history_seek)
                Slider(scrub ?: (if(active) playback.position else 0).toFloat(), { scrub = it },
                    onValueChangeFinished = { scrub?.let { onSeek(it.toInt()) }; scrub = null },
                    enabled = active && !playback.preparing && playback.duration > 0,
                    valueRange = 0f..maxOf(1, playback.duration).toFloat(),
                    thumb = { Box(Modifier.size(16.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) },
                    track = { SliderDefaults.Track(it, modifier = Modifier.height(4.dp), thumbTrackGapSize = 0.dp, drawStopIndicator = null) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekLabel })
                CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatPlaybackTime(if(active) scrub?.toInt() ?: playback.position else 0))
                        Text(formatPlaybackTime(if(active) playback.duration else 0))
                    }
                }
                if(active && playback.failed) Text(stringResource(R.string.history_play_failed), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconButton(onClick = { onSeek((playback.position - 10000).coerceAtLeast(0)) }, enabled = active && playback.duration > 0) {
                        Icon(Icons.Default.Replay10, stringResource(R.string.replay_rewind)) }
                    FilledIconButton(onClick = onToggle, modifier = Modifier.size(76.dp)) {
                        if(active && playback.preparing) CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(if(active && playback.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            stringResource(if(active && playback.playing) R.string.history_pause else R.string.history_play, title), Modifier.size(36.dp)) }
                    IconButton(onClick = { onSeek(playback.position + 10000) }, enabled = active && playback.duration > 0) {
                        Icon(Icons.Default.Forward10, stringResource(R.string.replay_forward)) }
                }
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        FilledTonalButton(onClick = { speedMenu = true }, enabled = active && !playback.preparing && !playback.failed) {
                            Text("${playback.speed}×") }
                        DropdownMenu(speedMenu, { speedMenu = false }) {
                            listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed -> DropdownMenuItem(
                                text = { Text("${speed}×") }, onClick = { onSpeed(speed); speedMenu = false }) }
                        }
                    }
                    FilledTonalIconButton(onClick = onMute) { Icon(if(playback.muted) Icons.Default.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        stringResource(if(playback.muted) R.string.replay_unmute else R.string.replay_mute)) }
                    FilledTonalIconButton(onClick = onShare) { Icon(Icons.Default.Share, stringResource(R.string.history_share, title)) }
                }
                }
            }
            snackbar?.let { SnackbarHost(it, Modifier.align(Alignment.BottomCenter)) }
        }
    }
}

@Composable
private fun dateLabel(date: LocalDate): String = when(date) {
    LocalDate.now() -> stringResource(R.string.history_today)
    LocalDate.now().minusDays(1) -> stringResource(R.string.history_yesterday)
    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]))
}
internal fun formatPlaybackTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
