package com.kitsumed.shizucallrecorder.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsumed.shizucallrecorder.R
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.system.*
import com.kitsumed.shizucallrecorder.ui.viewmodels.*
import com.kitsumed.shizucallrecorder.utils.RecordingFileNameFormatter
import org.json.JSONObject
import com.kitsumed.shizucallrecorder.updates.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallReplaySettingsScreen(settings: SettingsViewModel, onSetupRecording: () -> Unit,
    onDetailChanged: (Boolean) -> Unit = {}) {
    var route by rememberSaveable { mutableStateOf("index") }
    val update by settings.updateTrigger.collectAsState()
    val nav: AppNavigationViewModel = viewModel()
    val status by nav.onboardingStatus.collectAsState()
    val picker: ContactPickerViewModel = viewModel()
    val contacts by picker.contactPickerState.collectAsState()
    val context = LocalContext.current
    val preferences = settings.preferences
    val updater: UpdateViewModel = viewModel()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { nav.refresh() }
    LaunchedEffect(route) { onDetailChanged(route != "index") }
    BackHandler(route != "index") {
        if(contacts != null) picker.dismissContactPicker()
        else route = when(route) {
            "permissions", "audio" -> "setup"
            "updates" -> "about"
            else -> "index"
        }
    }
    val folderPicker = rememberLauncherForActivityResult(PersistentFolderPickerContract()) { uri ->
        if(uri != null) { context.takePersistableFolderPermission(uri); preferences.setRecordingFolderUri(uri); settings.refresh(); nav.refresh() }
    }
    val back = { route = "index" }
    when(route) {
        "index" -> ReplaySettingsIndex(folder = preferences.getRecordingFolderUri()?.lastPathSegment,
            template = preferences.getFileNameTemplate(), version = settings.getAppVersion(),
            onRoute = { route = it }, onFolder = { folderPicker.launch(null) })
        "setup" -> ReplaySetupPage(status.shizukuRunning, status.isComplete(), back,
            { route = "permissions" }, { route = "audio" }, { folderPicker.launch(null) })
        "permissions" -> ReplayPage(stringResource(R.string.replay_permissions), { route = "setup" }) { padding ->
            Box(Modifier.padding(padding)) { PermissionsScreen(status, onPermissionGranted = { nav.refresh() }) }
        }
        "audio" -> ReplayPage(stringResource(R.string.settings_section_audio), { route = "setup" }) { padding ->
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) { AudioSection(preferences, update, settings) }
        }
        "exclusions" -> if(contacts != null) {
            val selection = contacts!!
            ReplayContactsPage(selection, onBack = picker::dismissContactPicker, onConfirm = {
                picker.confirmContactPicker(it)
                if(selection.type == ContactPickerType.INCOMING) settings.setIgnoreContactsModeIncoming(AppPreferences.IgnoreContactsMode.SELECTED)
                else settings.setIgnoreContactsModeOutgoing(AppPreferences.IgnoreContactsMode.SELECTED)
                settings.refresh()
            })
        } else ReplayPage(stringResource(R.string.replay_exclusions), back) { padding ->
            Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.replay_exclusions_body))
                ReplaySettingRow(stringResource(R.string.settings_select_contacts_incoming), icon = Icons.AutoMirrored.Filled.CallReceived) {
                    picker.openContactPicker(ContactPickerType.INCOMING) }
                ReplaySettingRow(stringResource(R.string.settings_select_contacts_outgoing), icon = Icons.AutoMirrored.Filled.CallMade) {
                    picker.openContactPicker(ContactPickerType.OUTGOING) }
                TextButton(onClick = { route = "advanced" }) { Text(stringResource(R.string.replay_advanced)) }
            }
        }
        "template" -> ReplayTemplatePage(preferences.getFileNameTemplate(), preferences.getCallDetectionMode(), back) {
            settings.setFileNameTemplate(it); back()
        }
        "licenses" -> ReplayLicensesPage(back)
        "support" -> ReplaySupportPage(back)
        "language" -> ReplayPage(stringResource(R.string.settings_section_visual), back) { padding ->
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) { VisualSection(preferences, update, settings) }
        }
        "about" -> ReplayPage(stringResource(R.string.replay_about), back) { padding ->
            Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                WaveMark(72.dp, true); Text("CallReplay", style = MaterialTheme.typography.headlineMedium)
                Text(settings.getAppVersion()); Text(stringResource(R.string.history_fork_attribution))
                Button(onClick = { route = "updates" }) { Text(stringResource(R.string.updates_check)) }
                val uri = LocalUriHandler.current
                TextButton(onClick = { uri.openUri("https://github.com/kitsumed/ShizuCallRecorder") }) { Text(stringResource(R.string.replay_donate)) }
                TextButton(onClick = { uri.openUri("https://github.com/Uriel1010/CallReplay") }) { Text(stringResource(R.string.replay_github)) }
            }
        }
        "updates" -> UpdateScreen(updater, onBack = { route = "about" })
        "advanced" -> ReplayPage(stringResource(R.string.replay_advanced), back) { padding -> Box(Modifier.padding(padding)) { SettingsScreen(settings) } }
    }
}

@Composable
internal fun ReplaySetupPage(shizukuReady: Boolean, complete: Boolean, onBack: () -> Unit,
    onPermissions: () -> Unit, onAudio: () -> Unit, onFolder: () -> Unit) {
    ReplayPage(stringResource(R.string.replay_setup), onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.replay_setup_intro), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.replay_setup_body))
            ReplaySettingRow(stringResource(R.string.permission_shizuku_open),
                stringResource(if(shizukuReady) R.string.permissions_status_granted else R.string.permission_shizuku_not_running),
                Icons.Default.CheckCircle, onPermissions)
            ReplaySettingRow(stringResource(R.string.replay_permissions), icon = Icons.Default.Security, onClick = onPermissions)
            ReplaySettingRow(stringResource(R.string.settings_audio_source), icon = Icons.Default.GraphicEq, onClick = onAudio)
            ReplaySettingRow(stringResource(R.string.settings_recording_folder_label), icon = Icons.Default.FolderOpen, onClick = onFolder)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack, enabled = complete, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.replay_done)) }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ReplaySettingsIndex(modifier: Modifier = Modifier, folder: String?, template: String,
    version: String, onRoute: (String) -> Unit, onFolder: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.general_settings), fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }) { padding ->
    Column(modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReplaySettingRow(stringResource(R.string.replay_setup), stringResource(R.string.replay_setup_hint), Icons.Default.MicNone) { onRoute("setup") }
        ReplaySettingRow(stringResource(R.string.settings_recording_folder_label), folder, Icons.Default.FolderOpen, onFolder)
        ReplaySettingRow(stringResource(R.string.replay_exclusions), stringResource(R.string.replay_exclusions_hint), Icons.Default.PeopleOutline) { onRoute("exclusions") }
        ReplaySettingRow(stringResource(R.string.settings_file_name_template), template, Icons.Default.Code) { onRoute("template") }
        Spacer(Modifier.height(12.dp))
        ReplaySettingRow(stringResource(R.string.settings_theme_mode), stringResource(R.string.settings_language), Icons.Default.DarkMode) { onRoute("language") }
        ReplaySettingRow(stringResource(R.string.replay_libraries), icon = Icons.Default.Description) { onRoute("licenses") }
        ReplaySettingRow(stringResource(R.string.sponsor_title), icon = Icons.Default.FavoriteBorder) { onRoute("support") }
        ReplaySettingRow(stringResource(R.string.replay_about), version, Icons.Default.Info) { onRoute("about") }
        TextButton(onClick = { onRoute("advanced") }) { Text(stringResource(R.string.replay_advanced)) }
    }
    }
}

@Composable
internal fun ReplayTemplatePage(initial: String, mode: com.kitsumed.shizucallrecorder.services.callDetection.CallDetectionMode,
    onBack: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val context = LocalContext.current
    val preview = remember(value) { RecordingFileNameFormatter.formatFileName(context,
        com.kitsumed.shizucallrecorder.data.call.EnrichedCallData(normalisedPhoneNumber = "050-123-4567",
            direction = com.kitsumed.shizucallrecorder.data.call.CallDirection.INCOMING, callerName = "Example"),
        com.kitsumed.shizucallrecorder.data.recording.RecordingCodec.AAC, customFormat = value) }
    ReplayPage(stringResource(R.string.settings_file_name_template), onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp).testTag("filename-editor"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.replay_template_body))
            OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.settings_file_name_template)) },
                modifier = Modifier.fillMaxWidth(), isError = value.isBlank(), textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
            RecordingFileNameFormatter.FileNamePlaceholder.entries.filter { mode in it.supportedModes }.forEach { placeholder ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(placeholder.tag, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr)) }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(placeholder.descriptionResId), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(stringResource(R.string.settings_file_name_template_placeholders_preview, preview),
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr))
            Button(onClick = { onSave(value) }, enabled = value.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.replay_save)) }
            TextButton(onClick = { value = AppPreferences.DefaultsValue.FILE_NAME_TEMPLATE }) { Text(stringResource(R.string.general_reset)) }
        }
    }
}

@Composable
internal fun ReplayContactsPage(state: ContactPickerState, onBack: () -> Unit, onConfirm: (Set<String>) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember(state) { mutableStateOf(state.selectedContactsLookupId) }
    ReplayPage(stringResource(R.string.replay_exclusions), onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.replay_exclusions_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.contact_search_hint)) },
                shape = CircleShape, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(state.contacts.filter { it.name.contains(query, true) || it.displayNumbers.contains(query) }, key = { it.lookupKey }) { contact ->
                    Row(Modifier.fillMaxWidth().clickable {
                        selected = if(contact.lookupKey in selected) selected - contact.lookupKey else selected + contact.lookupKey
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Text(contact.name.take(1)) } }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(contact.name, fontWeight = FontWeight.Bold)
                            Text(contact.displayNumbers, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)) }
                        Checkbox(contact.lookupKey in selected, onCheckedChange = {
                            selected = if(it) selected + contact.lookupKey else selected - contact.lookupKey })
                    }
                }
            }
            Button(onClick = { onConfirm(selected) }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Text(stringResource(R.string.replay_save_selection, selected.size)) }
        }
    }
}

@Composable
internal fun ReplayLicensesPage(onBack: () -> Unit) {
    val resources = LocalResources.current
    val libraries = remember(resources) { runCatching {
        val root = JSONObject(resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() })
        val array = root.getJSONArray("libraries")
        List(array.length()) { array.getJSONObject(it) }
    }.getOrDefault(emptyList()) }
    val licenseDefinitions = remember(resources) { runCatching {
        JSONObject(resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }).optJSONObject("licenses")
    }.getOrNull() }
    ReplayPage(stringResource(R.string.replay_libraries), onBack) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.replay_libraries_body)) }
            items(libraries) { library ->
                var expanded by remember { mutableStateOf(false) }
                val license = library.optJSONArray("licenses")?.let { a -> List(a.length()) { a.getString(it) }.joinToString() }.orEmpty()
                OutlinedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(library.optString("name"), fontWeight = FontWeight.Bold)
                        Text(license, style = MaterialTheme.typography.bodySmall)
                        if(expanded) {
                            Text(library.optString("description"), Modifier.padding(top = 12.dp))
                            library.optJSONArray("licenses")?.let { ids ->
                                for(index in 0 until ids.length()) {
                                    licenseDefinitions?.optJSONObject(ids.getString(index))?.let { definition ->
                                        Text(definition.optString("name"), Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
                                        Text(definition.optString("content"), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            val uri = LocalUriHandler.current
                            library.optString("website").takeIf { it.startsWith("https://") }?.let { website ->
                                TextButton(onClick = { uri.openUri(website) }) { Text(stringResource(R.string.general_licenses)) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReplaySupportPage(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    ReplayPage(stringResource(R.string.sponsor_title), onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(Modifier.height(32.dp)); Icon(Icons.Default.FavoriteBorder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
            Text(stringResource(R.string.sponsor_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.replay_support_body))
            Spacer(Modifier.height(48.dp))
            FilledTonalButton(onClick = { uri.openUri("https://github.com/Uriel1010/CallReplay") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.replay_github)) }
            FilledTonalButton(onClick = { uri.openUri("https://github.com/sponsors/kitsumed") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.replay_donate)) }
        }
    }
}
