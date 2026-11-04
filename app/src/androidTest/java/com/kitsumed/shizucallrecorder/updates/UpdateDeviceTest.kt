package com.kitsumed.shizucallrecorder.updates

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsumed.shizucallrecorder.BuildConfig
import com.kitsumed.shizucallrecorder.ui.screens.UpdateContent
import com.kitsumed.shizucallrecorder.ui.screens.UpdatePreferences
import com.kitsumed.shizucallrecorder.ui.theme.ShizuCallRecorderTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Synthetic metadata only. Never reads callers, recordings or the real release network. */
@RunWith(AndroidJUnit4::class)
class UpdateDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val candidate = AppRelease(23, "2.2.0", "v2.2.0",
        "https://github.com/Uriel1010/CallReplay/releases/download/v2.2.0/CallReplay-2.2.0-release.apk",
        "a".repeat(64), 4_000_000, 30, "שיפורים ותיקונים · Synthetic release notes",
        "https://github.com/Uriel1010/CallReplay/releases/tag/v2.2.0")

    @Test fun validatesReleaseManifestAndRejectsMismatches() {
        val asset = JSONObject().put("name", "CallReplay-2.2.0-release.apk")
            .put("size", candidate.size).put("browser_download_url", candidate.apkUrl)
        val release = JSONObject().put("draft", false).put("tag_name", candidate.tag)
            .put("assets", JSONArray().put(asset))
        val manifest = JSONObject().put("schemaVersion", 1).put("packageName", BuildConfig.APPLICATION_ID)
            .put("versionCode", 23).put("versionName", "2.2.0").put("minSdk", 30)
            .put("apk", "CallReplay-2.2.0-release.apk").put("size", candidate.size).put("sha256", candidate.sha256)
        assertEquals(23L, UpdateRepository.parseRelease(release, manifest).versionCode)
        fun rejects(body: JSONObject) {
            try { UpdateRepository.parseRelease(release, body); fail("Invalid manifest accepted") }
            catch(error: UpdateException) { assertEquals(UpdateFailure.INVALID_RELEASE, error.reason) }
        }
        rejects(JSONObject(manifest.toString()).put("packageName", "another.app"))
        rejects(JSONObject(manifest.toString()).put("sha256", "invalid"))
        rejects(JSONObject(manifest.toString()).put("size", 1))
        rejects(JSONObject(manifest.toString()).put("schemaVersion", 2))
        release.put("draft", true); rejects(manifest)
    }
    @Test fun hebrewAvailableUpdateAndLabeledPreferences() {
        var downloaded = false
        show(UpdateState(stage = UpdateStage.AVAILABLE, release = candidate), download = { downloaded = true })
        compose.onNodeWithText("בדיקה אוטומטית").assertIsOff().performClick()
        compose.onNodeWithText("הורדת עדכון").performScrollTo().performClick()
        assertTrue(downloaded); capture("updates-available-light")
    }
    @Test fun darkDownloadProgressCanBeCancelled() {
        var cancelled = false
        show(UpdateState(stage = UpdateStage.DOWNLOADING, release = candidate, progress = 42), dark = true,
            cancel = { cancelled = true })
        compose.onNodeWithText("הכללת גרסאות מקדימות").assertIsNotEnabled()
        compose.onNodeWithText("הורדה: 42%").performScrollTo().assertIsDisplayed()
        capture("updates-progress-dark")
        compose.onNodeWithText("ביטול").performScrollTo().performClick(); assertTrue(cancelled)
    }
    @Test fun readyUpdateRequiresExplicitInstall() {
        var installed = false
        show(UpdateState(stage = UpdateStage.READY, release = candidate), install = { installed = true })
        assertFalse(installed)
        compose.onNodeWithText("התקנת עדכון").performScrollTo().performClick()
        assertTrue(installed); capture("updates-ready-light")
    }
    @Test fun networkErrorProvidesManualRetry() {
        var checked = false
        show(UpdateState(error = UpdateFailure.NETWORK), check = { checked = true })
        compose.onNodeWithText("בדיקת עדכונים").performScrollTo().performClick()
        assertTrue(checked); capture("updates-error-light")
    }
    private fun show(state: UpdateState, dark: Boolean = false, download: () -> Unit = {},
        cancel: () -> Unit = {}, install: () -> Unit = {}, check: () -> Unit = {}) {
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag("he")) }
            val localized = LocalContext.current.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config,
                LocalResources provides localized.resources, LocalLayoutDirection provides LayoutDirection.Rtl) {
                ShizuCallRecorderTheme(darkTheme = dark) { Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                        UpdatePreferences(state, {}, {})
                        Spacer(Modifier.height(20.dp))
                        UpdateContent(state, check, download, cancel, install)
                    }
                } }
            }
        }
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        val directory = File(compose.activity.filesDir, "reference-test-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
