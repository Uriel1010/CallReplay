package com.kitsumed.shizucallrecorder

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.recording.RecordingHistoryEntry
import com.kitsumed.shizucallrecorder.services.callDetection.CallDetectionMode
import com.kitsumed.shizucallrecorder.ui.screens.*
import com.kitsumed.shizucallrecorder.ui.theme.ShizuCallRecorderTheme
import com.kitsumed.shizucallrecorder.ui.viewmodels.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Production screens rendered with synthetic data, never the user's recordings or contacts. */
@RunWith(AndroidJUnit4::class)
class ReferenceScreensDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val first = RecordingHistoryEntry(Uri.parse("content://synthetic/yossi"), "Call_2026-11-04_14-28.m4a",
        System.currentTimeMillis(), "יוסי לוי", "050-123-4567", CallDirection.OUTGOING)
    private val entries = listOf(first,
        first.copy(uri = Uri.parse("content://synthetic/bank"), callerName = "בנק הפועלים", phoneNumber = "*2407", direction = CallDirection.INCOMING),
        first.copy(uri = Uri.parse("content://synthetic/legacy"), callerName = null, phoneNumber = "", fileName = "Call_2026-11-03_19-14.m4a",
            direction = null, startedAt = first.startedAt - 86400000))

    @Test fun lightCallLogOpensSearchAndFilters() {
        show { ReplayHomeLayout(false, false, {}) { RecordingHistoryContent(RecordingHistoryState(entries, false), RecordingPlaybackState(), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("CallReplay").assertIsDisplayed(); capture("logs-light")
        compose.onNodeWithContentDescription("חיפוש שיחות").performClick()
        compose.onNodeWithText("חיפוש שיחות").performTextInput("בנק")
        compose.onNodeWithText("יוסי לוי").assertDoesNotExist()
        compose.onNodeWithText("בנק הפועלים").assertIsDisplayed(); capture("search-light")
    }
    @Test fun darkCallLogShowsSameActions() {
        show(dark = true) { ReplayHomeLayout(false, false, {}) { RecordingHistoryContent(RecordingHistoryState(entries, false), RecordingPlaybackState(), {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("CallReplay").assertIsDisplayed(); capture("logs-dark")
    }
    @Test fun playerControlsTargetSeekSpeedMuteAndShare() {
        var seek = -1; var speed = 0f; var muted = false; var shared = false; var toggled = false
        show { RecordingPlayerContent(first, RecordingPlaybackState(first.uri, playing = true, position = 156000, duration = 504000),
            {}, { toggled = true }, { seek = it }, { speed = it }, { muted = true }, { shared = true }, {}) }
        compose.onNodeWithContentDescription("פעולות נוספות").assertIsDisplayed()
        compose.onNodeWithContentDescription("חזרה 10 שניות").performClick(); assertEquals(146000, seek)
        compose.onNodeWithContentDescription("דילוג 10 שניות קדימה").performClick(); assertEquals(166000, seek)
        compose.onNodeWithText("1.0×").performClick(); compose.onNodeWithText("1.5×").performClick(); assertEquals(1.5f, speed)
        compose.onNodeWithContentDescription("השתקת השמע").performClick(); assertTrue(muted)
        compose.onNodeWithContentDescription("שיתוף ההקלטה של יוסי לוי").performClick(); assertTrue(shared)
        compose.onNodeWithContentDescription("השהיית ההקלטה של יוסי לוי").performClick(); assertTrue(toggled)
        capture("player-light")
    }
    @Test fun darkPlayerWithLargeTextRemainsScrollable() {
        show(dark = true, fontScale = 1.3f) { RecordingPlayerContent(first,
            RecordingPlaybackState(first.uri, playing = false, position = 156000, duration = 504000), {}, {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithContentDescription("שיתוף ההקלטה של יוסי לוי").performScrollTo().assertIsDisplayed()
        capture("player-dark-large-text")
    }
    @Test fun settingsIndexRoutesToTaskPages() {
        var route = ""
        show { ReplayHomeLayout(true, false, {}) { ReplaySettingsIndex(folder = "Document/CallReplay", template = "Call_{date}", version = "CallReplay 2.0.0", onRoute = { route = it }, onFolder = {}) } }
        compose.onNodeWithText("חריגות מהקלטה").performClick(); assertEquals("exclusions", route)
        capture("settings-light")
    }
    @Test fun darkSettingsIndex() {
        show(dark = true) { ReplayHomeLayout(true, false, {}) { ReplaySettingsIndex(folder = "Document/CallReplay", template = "Call_{date}", version = "CallReplay 2.0.0", onRoute = {}, onFolder = {}) } }
        capture("settings-dark")
    }
    @Test fun contactSelectionSearchAndSaveOnlyChosenContacts() {
        var saved = emptySet<String>()
        show { ReplayContactsPage(ContactPickerState(ContactPickerType.INCOMING, listOf(
            ContactEntry("one", "אמא", "050-111-2222"), ContactEntry("two", "אבא", "050-333-4444"),
            ContactEntry("three", "בנק הפועלים", "*2407")), setOf("one")), {}, { saved = it }) }
        compose.onNodeWithText("אבא").performClick()
        compose.onNodeWithText("שמירה (2 נבחרו)").performClick(); assertEquals(setOf("one", "two"), saved)
        capture("contacts-light")
    }
    @Test fun filenameTemplateSavesAndRejectsBlank() {
        var saved = ""
        show { ReplayTemplatePage("Call_{date}", CallDetectionMode.InCallService, {}, { saved = it }) }
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText("שמירה").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("Call_{phone_number}")
        compose.onNodeWithText("שמירה").performScrollTo().performClick(); assertEquals("Call_{phone_number}", saved)
        compose.onNodeWithTag("filename-editor").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
            it(0f, -10000f)
        }
        capture("template-light")
    }
    @Test fun licensesShowRealDependencies() {
        show { ReplayLicensesPage({}) }; compose.onNodeWithText("רישיונות של ספריות").assertIsDisplayed(); capture("licenses-light")
    }
    @Test fun supportExplainsForkAttribution() {
        show { ReplaySupportPage({}) }; compose.onNodeWithText("תמיכה בפרויקט המקורי").assertIsDisplayed(); capture("support-light")
    }
    @Test fun disclaimerRequiresReadingAndExplicitAcceptance() {
        var continued = false
        show { DisclaimerScreen({ continued = true }) }
        capture("disclaimer-light")
        compose.onNodeWithText("רישיון והצהרה מקוריים (באנגלית)").performScrollTo().performClick()
        assertFalse(continued); compose.onNodeWithText("רישיונות").assertIsDisplayed(); capture("disclaimer-legal")
        compose.onNodeWithText(compose.activity.getString(R.string.disclaimer_body), useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                action(layouts)
                assertEquals(androidx.compose.ui.text.style.ResolvedTextDirection.Ltr, layouts.single().getParagraphDirection(0))
            }
    }
    @Test fun splashMatchesIdentity() { show { CallReplaySplashScreen() }; capture("splash-light") }

    @Test fun recordingSetupRequiresAllStepsBeforeFinishing() {
        var openedAudio = false
        show { ReplaySetupPage(true, false, {}, {}, { openedAudio = true }, {}) }
        compose.onNodeWithText("סיום הגדרה").assertIsNotEnabled()
        compose.onNodeWithText("מקור שמע").performClick(); assertTrue(openedAudio)
        capture("setup-light")
    }

    @Test fun themePreferencePersistsAndCanBeRestored() {
        val context = compose.activity.applicationContext
        val preferences = com.kitsumed.shizucallrecorder.data.AppPreferences(context)
        val previous = preferences.getThemeMode()
        try {
            preferences.setThemeMode(com.kitsumed.shizucallrecorder.data.AppPreferences.ThemeMode.DARK)
            assertEquals(com.kitsumed.shizucallrecorder.data.AppPreferences.ThemeMode.DARK,
                com.kitsumed.shizucallrecorder.data.AppPreferences(context).getThemeMode())
        } finally { preferences.setThemeMode(previous) }
    }

    @Test fun recordingActionsSheetTargetsDeleteAndInfo() {
        var deleted = false
        show {
            RecordingPlayerContent(first, RecordingPlaybackState(first.uri, position = 156000, duration = 504000), {}, {}, {}, {}, {}, {}, {})
            RecordingActionsSheet({}, {}, {}, { deleted = true }, {})
        }
        compose.onNodeWithText("פרטי הקובץ").assertIsDisplayed()
        capture("actions-light")
        compose.onNodeWithText("מחיקה").performClick(); assertTrue(deleted)
    }

    private fun show(dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val context = LocalContext.current
            val config = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag("he")) }
            val localized = context.createConfigurationContext(config)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config,
                LocalResources provides localized.resources, LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, fontScale)) {
                ShizuCallRecorderTheme(darkTheme = dark) {
                    androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        val directory = File(compose.activity.filesDir, "reference-test-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
