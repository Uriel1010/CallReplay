/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 * Copyright (C) 2026-present kitsumed (Med)
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Additional terms under Section 7 apply; see LICENSE.
 */
package com.kitsumed.shizucallrecorder.ui.screens

import android.text.format.DateFormat
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.recording.RecordingHistoryEntry
import com.kitsumed.shizucallrecorder.system.recordingShareIntent
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingHistoryViewModel
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingHistoryState
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingPlaybackState
import com.kitsumed.shizucallrecorder.ui.viewmodels.SettingsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import kotlinx.coroutines.launch

/** Recents is the home destination; the existing settings screen remains one tap away. */
@Composable
fun RecorderHomeScreen(settingsViewModel: SettingsViewModel) {
    var settingsSelected by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = settingsSelected) { settingsSelected = false }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = !settingsSelected, onClick = { settingsSelected = false },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.history_title)) }
                )
                NavigationBarItem(
                    selected = settingsSelected, onClick = { settingsSelected = true },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.history_settings)) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            if (settingsSelected) {
                SettingsScreen(viewModel = settingsViewModel)
            } else {
                RecordingHistoryScreen(onOpenSettings = { settingsSelected = true })
            }
        }
    }
}

@Composable
private fun RecordingHistoryScreen(
    onOpenSettings: () -> Unit,
    viewModel: RecordingHistoryViewModel = viewModel()
) {
    val state by viewModel.history.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopPlayback() }
    DisposableEffect(viewModel) { onDispose { viewModel.stopPlayback() } }

    RecordingHistoryContent(
        state, playback, onOpenSettings, viewModel::refresh, viewModel::toggle, viewModel::seek,
        onShare = { entry ->
            try {
                val mimeType = context.contentResolver.getType(entry.uri)
                val intent = recordingShareIntent(entry.uri, mimeType, entry.fileName)
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.general_share)))
            } catch (_: Exception) {
                scope.launch { snackbar.showSnackbar(context.getString(R.string.history_share_failed)) }
            }
        },
        snackbarHostState = snackbar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingHistoryContent(
    state: RecordingHistoryState,
    playback: RecordingPlaybackState,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (RecordingHistoryEntry) -> Unit,
    onSeek: (Int) -> Unit,
    onShare: (RecordingHistoryEntry) -> Unit,
    snackbarHostState: SnackbarHostState? = null
) {
    val uriHandler = LocalUriHandler.current
    var query by rememberSaveable { mutableStateOf("") }
    var directionToken by rememberSaveable { mutableStateOf<String?>(null) }
    val direction = CallDirection.fromToken(directionToken)

    val entries = remember(state.entries, query, direction) {
        val search = query.trim()
        state.entries.filter { entry ->
            (direction == null || entry.direction == direction) &&
                (search.isEmpty() || listOf(entry.callerName.orEmpty(), entry.phoneNumber, entry.fileName)
                    .any { it.contains(search, ignoreCase = true) })
        }
    }
    val zone = ZoneId.systemDefault()
    val groups = remember(entries, zone) {
        entries.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
    }
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr)) {
        Scaffold(
            snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_title)) },
                    actions = {
                        IconButton(onClick = onRefresh, enabled = !state.loading) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.history_refresh))
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).testTag("recording-history-list"),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item(key = "controls") {
                    Column {
                        Text(
                            stringResource(R.string.history_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        TextButton(
                            onClick = { uriHandler.openUri("https://github.com/kitsumed/ShizuCallRecorder") },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(stringResource(R.string.history_fork_attribution), style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            label = { Text(stringResource(R.string.history_search)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, stringResource(R.string.history_clear_search))
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Row(
                            Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = direction == null, onClick = { directionToken = null },
                                label = { Text(stringResource(R.string.history_all)) }
                            )
                            CallDirection.entries.forEach { option ->
                                FilterChip(
                                    selected = direction == option,
                                    onClick = { directionToken = option.token },
                                    label = { Text(stringResource(option.labelResId)) }
                                )
                            }
                        }
                        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                when {
                    state.folderUnavailable || state.loadFailed -> item(key = "status") { HistoryMessage(
                        message = stringResource(if (state.folderUnavailable) R.string.history_folder_missing else R.string.history_load_failed),
                        action = stringResource(R.string.history_settings), onAction = onOpenSettings
                    ) }
                    !state.loading && entries.isEmpty() -> item(key = "status") { HistoryMessage(
                        message = stringResource(if (state.entries.isEmpty()) R.string.history_empty else R.string.history_no_matches)
                    ) }
                    else -> {
                        groups.forEach { (date, calls) ->
                            item(key = "date:$date") {
                                Text(
                                    dateLabel(date), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                                )
                            }
                            items(calls, key = { it.uri.toString() }) { entry ->
                                RecordingHistoryRow(entry, playback, { onToggle(entry) }, onSeek, { onShare(entry) })
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMessage(message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun RecordingHistoryRow(
    entry: RecordingHistoryEntry,
    playback: RecordingPlaybackState,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onShare: () -> Unit
) {
    val selected = playback.uri == entry.uri
    val title = entry.callerName?.takeIf { it.isNotBlank() }
        ?: entry.phoneNumber.takeIf { it.isNotBlank() } ?: entry.fileName
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(
                    imageVector = when (entry.direction) {
                        CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
                        CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
                        null -> Icons.Default.AudioFile
                    },
                    contentDescription = null, modifier = Modifier.padding(12.dp)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr), fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!entry.callerName.isNullOrBlank() && entry.phoneNumber.isNotBlank()) {
                    Text(entry.phoneNumber, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                }
                val direction = entry.direction?.let { stringResource(it.labelResId) }
                    ?: stringResource(R.string.history_imported)
                val time = DateFormat.getTimeFormat(context).format(Date(entry.startedAt))
                Text("$direction · $time", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected && playback.preparing) {
                CircularProgressIndicator(Modifier.size(48.dp).padding(12.dp))
            } else {
                IconButton(onClick = onToggle) {
                    val playing = selected && playback.playing
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (playing) R.string.history_pause else R.string.history_play, title),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Default.Share,
                    stringResource(R.string.history_share, title),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (selected && playback.failed) {
            Text(stringResource(R.string.history_play_failed), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp))
        } else if (selected && !playback.preparing && playback.duration > 0) {
            var scrub by remember(entry.uri) { mutableStateOf<Float?>(null) }
            val seekLabel = stringResource(R.string.history_seek)
            Slider(
                value = scrub ?: playback.position.toFloat(),
                onValueChange = { scrub = it },
                onValueChangeFinished = { scrub?.let { onSeek(it.toInt()) }; scrub = null },
                valueRange = 0f..playback.duration.toFloat(),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekLabel }
            )
            Text(
                stringResource(R.string.history_progress,
                    formatPlaybackTime(scrub?.toInt() ?: playback.position), formatPlaybackTime(playback.duration)),
                style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun dateLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> stringResource(R.string.history_today)
    LocalDate.now().minusDays(1) -> stringResource(R.string.history_yesterday)
    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

internal fun formatPlaybackTime(milliseconds: Int): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
