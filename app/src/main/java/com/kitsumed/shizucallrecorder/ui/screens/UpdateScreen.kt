package com.kitsumed.shizucallrecorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.kitsumed.shizucallrecorder.BuildConfig
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.updates.*
import java.util.Locale

@Composable
internal fun UpdateScreen(model: UpdateViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ReplayPage(stringResource(R.string.updates_title), onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.updates_installed, BuildConfig.VERSION_NAME))
            UpdatePreferences(state, model::setAutomatic, model::setPreviews)
            UpdateContent(state, { model.check() }, model::download, model::cancelDownload, { model.install(context) })
            Text(stringResource(R.string.updates_privacy), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun UpdateHost(model: UpdateViewModel, enabled: Boolean) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if(enabled) model.onForeground() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val blocked by produceState(true, state.announce, enabled, lifecycle) {
        if(state.announce && enabled) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while(isActive) {
                value = UpdateViewModel.installBlocked(context)
                delay(1000)
            }
        }
    }
    if(state.announce && enabled && !blocked) {
        AlertDialog(onDismissRequest = model::dismiss,
            title = { Text(stringResource(R.string.updates_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UpdateContent(state, { model.check() }, model::download, model::cancelDownload, { model.install(context) })
            } }, confirmButton = { TextButton(onClick = model::dismiss) { Text(stringResource(R.string.updates_later)) } })
    }
}

@Composable
internal fun UpdatePreferences(state: UpdateState, onAutomatic: (Boolean) -> Unit, onPreviews: (Boolean) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(state.autoCheck, role = Role.Switch, onValueChange = onAutomatic),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.updates_auto), Modifier.weight(1f).padding(end = 12.dp))
            Switch(state.autoCheck, onCheckedChange = null)
        }
        Text(stringResource(R.string.updates_auto_body), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp)
            .toggleable(state.previews, enabled = state.stage != UpdateStage.DOWNLOADING,
                role = Role.Switch, onValueChange = onPreviews),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.updates_previews), Modifier.weight(1f).padding(end = 12.dp))
            Switch(state.previews, onCheckedChange = null, enabled = state.stage != UpdateStage.DOWNLOADING)
        }
    }
}

@Composable
internal fun UpdateContent(state: UpdateState, onCheck: () -> Unit, onDownload: () -> Unit,
    onCancel: () -> Unit, onInstall: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        state.error?.let { error -> Text(stringResource(when(error) {
            UpdateFailure.NETWORK -> R.string.updates_network
            UpdateFailure.RATE_LIMIT -> R.string.updates_rate_limit
            UpdateFailure.INVALID_RELEASE -> R.string.updates_invalid_release
            UpdateFailure.INVALID_APK -> R.string.updates_invalid_apk
            UpdateFailure.INCOMPATIBLE -> R.string.updates_incompatible
            UpdateFailure.INSTALL -> R.string.updates_install_failed
            UpdateFailure.BUSY -> R.string.updates_busy
        }), color = MaterialTheme.colorScheme.error) }
        when(state.stage) {
            UpdateStage.CHECKING -> {
                LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.updates_checking))
            }
            UpdateStage.CURRENT -> Text(stringResource(R.string.updates_current))
            else -> Unit
        }
        state.release?.let { release ->
            Text(stringResource(R.string.updates_available, release.versionName), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.updates_size, String.format(Locale.ROOT, "%.1f", release.size / 1048576.0)))
            if(release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodyMedium)
            if(state.stage == UpdateStage.AVAILABLE) Button(onClick = onDownload) { Text(stringResource(R.string.updates_download)) }
            if(state.stage == UpdateStage.DOWNLOADING) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.updates_downloading, state.progress))
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.general_cancel)) }
            }
            if(state.stage == UpdateStage.READY) {
                Text(stringResource(R.string.updates_ready))
                Button(onClick = onInstall) { Text(stringResource(R.string.updates_install)) }
            }
            TextButton(onClick = { uri.openUri(release.pageUrl) }) { Text(stringResource(R.string.updates_open_release)) }
        }
        if(state.stage != UpdateStage.CHECKING && state.stage != UpdateStage.DOWNLOADING)
            OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.updates_check)) }
    }
}
