package com.kitsumed.shizucallrecorder

import android.net.Uri
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.recording.RecordingHistoryEntry
import com.kitsumed.shizucallrecorder.ui.screens.RecordingHistoryContent
import com.kitsumed.shizucallrecorder.ui.theme.ShizuCallRecorderTheme
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingHistoryState
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingPlaybackState
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TestName
import java.io.File
import java.util.Locale

/** Exercises the production Recents UI with synthetic caller data; no setup bypass is persisted. */
@RunWith(AndroidJUnit4::class)
class RecordingHistoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val testName = TestName()
    private val now = System.currentTimeMillis()
    private val entries = listOf(
        RecordingHistoryEntry(Uri.parse("content://test/alice"), "custom-name.m4a", now,
            "Alice Test", "+972500000001", CallDirection.INCOMING),
        RecordingHistoryEntry(Uri.parse("content://test/bob"), "second.m4a", now - 60000,
            "Bob Test", "+972500000002", CallDirection.OUTGOING),
        RecordingHistoryEntry(Uri.parse("content://test/legacy"), "legacy-recording.wav", now - 120000)
    )

    @After
    fun captureTestScreen() {
        val directory = File(compose.activity.filesDir, "device-test-screenshots").apply { mkdirs() }
        File(directory, "${testName.methodName}.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    @Test
    fun hebrewLabelsSearchAndShareWorkWithRtlNumbers() {
        var shared: Uri? = null
        val hebrewEntries = entries.mapIndexed { index, entry ->
            if (index == 0) entry.copy(callerName = "נועה בדיקה") else entry
        }
        show(RecordingHistoryState(hebrewEntries, loading = false), hebrew = true,
            playback = RecordingPlaybackState(uri = entries.first().uri, duration = 60000),
            onShare = { shared = it.uri })
        compose.onNodeWithText("חיפוש שיחות").assertIsDisplayed()
        compose.onNodeWithText("חיפוש שיחות").performTextInput("נועה")
        compose.onNodeWithText("Bob Test").assertDoesNotExist()
        compose.onNodeWithText("חיפוש שיחות").performImeAction()
        compose.onNodeWithContentDescription("שיתוף ההקלטה של נועה בדיקה").performClick()
        compose.runOnIdle { assertEquals(entries.first().uri, shared) }
        compose.onNodeWithText("נועה בדיקה", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                action(layouts)
                assertEquals(ResolvedTextDirection.Rtl, layouts.single().getParagraphDirection(0))
            }
        compose.onNodeWithText("+972500000001", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                action(layouts)
                assertEquals(ResolvedTextDirection.Ltr, layouts.single().getParagraphDirection(0))
            }
    }

    @Test
    fun searchesNameNumberAndFilenameAndClearsQuery() {
        show()
        val search = compose.onNodeWithText(text(R.string.history_search))
        search.performTextInput("Alice")
        compose.onNodeWithText("Alice Test").assertIsDisplayed()
        compose.onNodeWithText("Bob Test").assertDoesNotExist()
        search.performTextReplacement("000002")
        compose.onNodeWithText("Bob Test").assertIsDisplayed()
        search.performTextReplacement("legacy-recording")
        compose.onNodeWithText("legacy-recording.wav").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.history_clear_search)).performClick()
        compose.onNodeWithText("Alice Test").assertIsDisplayed()
    }

    @Test
    fun filtersDirectionsAndShowsNoMatches() {
        show()
        compose.onNodeWithText(text(R.string.history_incoming), substring = false).performClick()
        compose.onNodeWithText("Alice Test").assertIsDisplayed()
        compose.onNodeWithText("Bob Test").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.history_outgoing), substring = false).performClick()
        compose.onNodeWithText("Bob Test").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.history_search)).performTextInput("does-not-exist")
        compose.onNodeWithText(text(R.string.history_no_matches)).assertIsDisplayed()
    }

    @Test
    fun playButtonTargetsTheCorrectRecording() {
        var selected: Uri? = null
        show(onToggle = { selected = it.uri })
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.history_play, "Alice Test"))
            .performClick()
        compose.runOnIdle { assertEquals(entries.first().uri, selected) }
    }

    @Test
    fun shareButtonTargetsTheCorrectRecording() {
        var selected: Uri? = null
        show(onShare = { selected = it.uri }, playback = RecordingPlaybackState(uri = entries.first().uri, duration = 60000))
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.history_share, "Alice Test"))
            .performClick()
        compose.runOnIdle { assertEquals(entries.first().uri, selected) }
    }

    @Test
    fun landscapeWithLargeTextKeepsSearchAndPlaybackReachable() {
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        compose.waitUntil(5000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        show(fontScale = 1.5f)
        compose.onNodeWithText(text(R.string.history_search)).performTextInput("Alice")
        compose.onNodeWithText(text(R.string.history_search)).performImeAction()
        val playLabel = compose.activity.getString(R.string.history_play, "Alice Test")
        compose.onNodeWithTag("recording-history-list").performScrollToNode(hasContentDescription(playLabel))
        compose.onNodeWithText("Alice Test").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.history_play, "Alice Test"))
            .assertIsDisplayed()
    }

    @Test
    fun phoneNumbersKeepTheirPlusPrefixInRtlLayouts() {
        show(forceRtl = true)
        compose.onNodeWithText("+972500000001", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                action(layouts)
                assertEquals(ResolvedTextDirection.Ltr, layouts.single().getParagraphDirection(0))
            }
    }

    @Test
    fun recordingSetupDoesNotBlockBrowsing() {
        var opened = false
        show(onSetup = { opened = true })
        compose.onNodeWithText("Alice Test").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.history_setup)).performClick()
        compose.runOnIdle { assertEquals(true, opened) }
    }

    @Test
    fun folderErrorOffersSettingsInDarkTheme() {
        var settingsOpened = false
        show(RecordingHistoryState(loading = false, folderUnavailable = true), dark = true,
            onSettings = { settingsOpened = true })
        compose.onNodeWithText(text(R.string.history_folder_missing)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.history_settings)).performClick()
        compose.runOnIdle { assertEquals(true, settingsOpened) }
    }

    private fun text(resource: Int) = compose.activity.getString(resource)

    private fun show(
        state: RecordingHistoryState = RecordingHistoryState(entries, loading = false),
        playback: RecordingPlaybackState = RecordingPlaybackState(),
        dark: Boolean = false,
        fontScale: Float = 1f,
        forceRtl: Boolean = false,
        hebrew: Boolean = false,
        onToggle: (RecordingHistoryEntry) -> Unit = {},
        onShare: (RecordingHistoryEntry) -> Unit = {},
        onSetup: (() -> Unit)? = null,
        onSettings: () -> Unit = {}
    ) {
        compose.setContent {
            val baseContext = LocalContext.current
            val configuration = LocalConfiguration.current
            val localizedContext = if (hebrew) {
                baseContext.createConfigurationContext(Configuration(configuration).apply {
                    setLocale(Locale.forLanguageTag("he"))
                    setLayoutDirection(Locale.forLanguageTag("he"))
                })
            } else baseContext
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalResources provides localizedContext.resources
            ) {
            ShizuCallRecorderTheme(darkTheme = dark, dynamicColor = false) {
                val density = LocalDensity.current
                val direction = if (forceRtl || hebrew) LayoutDirection.Rtl else LocalLayoutDirection.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                    LocalLayoutDirection provides direction
                ) {
                    RecordingHistoryContent(state, playback, onSettings, {}, onToggle, {}, onShare,
                        onSetupRecording = onSetup, initialSearchExpanded = true)
                }
            }
            }
        }
    }
}
