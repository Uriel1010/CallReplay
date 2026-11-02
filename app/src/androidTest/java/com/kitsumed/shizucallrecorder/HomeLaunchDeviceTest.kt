package com.kitsumed.shizucallrecorder

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsumed.shizucallrecorder.ui.screens.RecorderHomeScreen
import com.kitsumed.shizucallrecorder.ui.theme.ShizuCallRecorderTheme
import com.kitsumed.shizucallrecorder.ui.viewmodels.SettingsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies home routing without modifying the user's disclaimer, folder, or recording preferences. */
@RunWith(AndroidJUnit4::class)
class HomeLaunchDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun callLogsOpenByDefaultAndLauncherEntryResetsSettings() {
        val launch = mutableIntStateOf(0)
        compose.setContent {
            ShizuCallRecorderTheme(dynamicColor = false) {
                val settings: SettingsViewModel = viewModel()
                RecorderHomeScreen(settings, launch.intValue, recordingReady = false)
            }
        }
        val logs = compose.activity.getString(R.string.history_title)
        val settings = compose.activity.getString(R.string.history_settings)
        compose.onAllNodesWithText(logs).assertCountEquals(2)
        compose.onNodeWithText(compose.activity.getString(R.string.history_setup)).assertIsDisplayed()
        compose.onNode(hasText(settings) and hasClickAction()).performClick()
        compose.onAllNodesWithText(logs).assertCountEquals(1)
        compose.runOnIdle { launch.intValue++ }
        compose.onAllNodesWithText(logs).assertCountEquals(2)
        compose.onNodeWithText(compose.activity.getString(R.string.history_setup)).assertIsDisplayed()
    }
}
