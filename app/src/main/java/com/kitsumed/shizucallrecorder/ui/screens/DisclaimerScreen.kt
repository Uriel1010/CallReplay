/* Copyright (C) 2026 kitsumed (Med), Uriel Maznur. GPL-3.0-or-later; see LICENSE. */
package com.kitsumed.shizucallrecorder.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsumed.shizucallrecorder.BuildConfig
import com.kitsumed.shizucallrecorder.R
import kotlinx.coroutines.delay

@Composable
fun DisclaimerScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    var accepted by rememberSaveable { mutableStateOf(false) }
    var openedLegal by rememberSaveable { mutableStateOf(false) }
    var legal by rememberSaveable { mutableStateOf(false) }
    var remaining by rememberSaveable { mutableIntStateOf(if(BuildConfig.DEBUG) 4 else 30) }
    LaunchedEffect(Unit) { while(remaining > 0) { delay(1000); remaining-- } }
    BackHandler(legal) { legal = false }
    if(legal) ReplayPage(stringResource(R.string.general_licenses), { legal = false }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(stringResource(R.string.disclaimer_body), style = MaterialTheme.typography.bodyMedium.copy(
                textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
            Button(onClick = { openedLegal = true; legal = false }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Text(stringResource(R.string.general_continue)) }
        }
    } else Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(8.dp)); WaveMark(40.dp, square = true)
            Text("CallReplay", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.replay_welcome), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.replay_disclaimer_intro), style = MaterialTheme.typography.bodySmall)
            OutlinedCard(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(R.string.replay_consent to Icons.Default.Balance,
                        R.string.replay_device_limit to Icons.Default.Call,
                        R.string.replay_concurrent to Icons.Default.PeopleOutline,
                        R.string.replay_caller_limit to Icons.Default.PersonOutline,
                        R.string.replay_capture_limit to Icons.Default.WarningAmber).forEach { (text, icon) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(text), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            val uri = LocalUriHandler.current
            TextButton(onClick = { uri.openUri("https://github.com/Uriel1010/CallReplay#install-and-set-up") }) {
                Text(stringResource(R.string.settings_open_github_Wiki)) }
            FilledTonalButton(onClick = { legal = true }) { Text(stringResource(R.string.replay_legal)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(accepted, { accepted = it }, enabled = openedLegal)
                Text(stringResource(if(openedLegal) R.string.replay_acknowledge else R.string.replay_read_first),
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onContinue, enabled = accepted && remaining == 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if(remaining > 0) stringResource(R.string.disclaimer_wait, remaining) else stringResource(R.string.general_continue))
            }
            Text(stringResource(R.string.history_fork_attribution), style = MaterialTheme.typography.labelSmall)
        }
    }
}
