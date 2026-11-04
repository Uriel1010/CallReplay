/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.kitsumed.shizucallrecorder.ui.screens.CallReplaySplashScreen
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.ui.screens.DisclaimerScreen
import com.kitsumed.shizucallrecorder.ui.screens.PermissionsScreen
import com.kitsumed.shizucallrecorder.ui.screens.RecorderHomeScreen
import com.kitsumed.shizucallrecorder.ui.theme.ShizuCallRecorderTheme
import com.kitsumed.shizucallrecorder.ui.screens.UpdateHost
import com.kitsumed.shizucallrecorder.updates.UpdateViewModel
import com.kitsumed.shizucallrecorder.ui.viewmodels.AppNavigationViewModel
import com.kitsumed.shizucallrecorder.ui.viewmodels.SettingsViewModel

/**
 * Call logs are the home destination after the one-time disclaimer.
 * Recording prerequisites are an explicit setup flow, so a stopped Shizuku service or revoked
 * recording permission never prevents browsing, playing, or sharing existing recordings.
 */
@Composable
fun AppNavigationScreen(launchGeneration: Int = 0) {
    val navigation: AppNavigationViewModel = viewModel()
    val settings: SettingsViewModel = viewModel()
    val updater: UpdateViewModel = viewModel()
    val status by navigation.onboardingStatus.collectAsStateWithLifecycle()
    val settingsUpdate by settings.updateTrigger.collectAsStateWithLifecycle()
    val preferences = settings.preferences
    var setupRequested by rememberSaveable(launchGeneration) { mutableStateOf(false) }
    var splashComplete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(450); splashComplete = true }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { navigation.refresh() }
    LaunchedEffect(settingsUpdate) { navigation.refresh() }
    LaunchedEffect(status) {
        if (status.isComplete()) setupRequested = false
    }
    BackHandler(enabled = setupRequested) { setupRequested = false }

    val darkTheme = when (preferences.getThemeMode()) {
        AppPreferences.ThemeMode.LIGHT -> false
        AppPreferences.ThemeMode.DARK -> true
        AppPreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    ShizuCallRecorderTheme(darkTheme = darkTheme, dynamicColor = preferences.isDynamicColorEnabled()) {
        UpdateHost(updater, enabled = status.disclaimerAccepted)
        when {
            !splashComplete -> CallReplaySplashScreen()
            !status.disclaimerAccepted -> DisclaimerScreen(onContinue = {
                preferences.setDisclaimerAccepted(true)
                navigation.refresh()
            })
            setupRequested && !status.isComplete() -> PermissionsScreen(
                status = status,
                onPermissionGranted = { navigation.refresh() }
            )
            else -> RecorderHomeScreen(
                settingsViewModel = settings,
                launchGeneration = launchGeneration,
                recordingReady = status.isComplete(),
                onSetupRecording = { setupRequested = true }
            )
        }
    }
}
