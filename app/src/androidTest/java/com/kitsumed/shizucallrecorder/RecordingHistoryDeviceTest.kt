package com.kitsumed.shizucallrecorder

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData
import com.kitsumed.shizucallrecorder.data.recording.RecordingHistoryEntry
import com.kitsumed.shizucallrecorder.data.recording.RecordingHistoryRepository
import com.kitsumed.shizucallrecorder.ui.viewmodels.RecordingHistoryViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Uses silent test audio and private synthetic metadata. Never places or records a phone call. */
@RunWith(AndroidJUnit4::class)
class RecordingHistoryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var activity: ActivityScenario<MainActivity>
    private lateinit var model: RecordingHistoryViewModel
    private lateinit var store: ViewModelStore
    private val files = mutableListOf<File>()
    private val metadataUri = Uri.parse("content://callreplay.device.tests/metadata")

    @Before
    fun setUp() {
        activity = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.runOnMainSync {
            model = RecordingHistoryViewModel(context.applicationContext as Application)
            store = ViewModelStore().apply { put("playback-test", model) }
        }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { store.clear() }
        activity.close()
        files.forEach { it.delete() }
        RecordingHistoryRepository(context).use {
            it.writableDatabase.delete("recordings", "uri = ?", arrayOf(metadataUri.toString()))
        }
    }

    @Test
    fun playPauseSeekAndStop() {
        val entry = silentRecording("playback-test.wav")
        onMain { model.toggle(entry) }
        await { model.playback.value.playing }
        assertTrue(model.playback.value.duration in 3900..4100)
        onMain { model.toggle(entry) }
        assertFalse(model.playback.value.playing)
        onMain { model.seek(1000) }
        assertEquals(1000, model.playback.value.position)
        onMain { model.toggle(entry) }
        await { model.playback.value.playing && model.playback.value.position > 1000 }
        onMain { model.stopPlayback() }
        assertNull(model.playback.value.uri)
        assertFalse(model.playback.value.playing)
    }

    @Test
    fun switchingRecordingsReleasesPreviousPlayer() {
        val first = silentRecording("first-test.wav")
        val second = silentRecording("second-test.wav")
        onMain { model.toggle(first) }
        await { model.playback.value.playing }
        onMain { model.toggle(second) }
        await { model.playback.value.playing && model.playback.value.uri == second.uri }
        assertFalse(model.playback.value.failed)
        onMain { model.stopPlayback() }
        assertNull(model.playback.value.uri)
    }

    @Test
    fun missingAudioReportsErrorAndCanRecover() {
        val missing = RecordingHistoryEntry(
            Uri.fromFile(File(context.cacheDir, "missing-${System.nanoTime()}.wav")), "missing.wav", 0
        )
        onMain { model.toggle(missing) }
        await { model.playback.value.failed }
        assertFalse(model.playback.value.playing)
        val valid = silentRecording("recovery-test.wav")
        onMain { model.toggle(valid) }
        await { model.playback.value.playing }
        assertFalse(model.playback.value.failed)
    }

    @Test
    fun completionAllowsReplay() {
        val entry = silentRecording("completion-test.wav")
        onMain { model.toggle(entry) }
        await { model.playback.value.playing }
        await { !model.playback.value.playing }
        assertEquals(0, model.playback.value.position)
        onMain { model.toggle(entry) }
        await { model.playback.value.playing }
    }

    @Test
    fun callerMetadataSurvivesReopeningAndDuplicateSave() {
        val metadata = EnrichedCallData(
            normalisedPhoneNumber = "+972500000000", direction = CallDirection.INCOMING,
            callerName = "Test O'Brien שלום"
        )
        RecordingHistoryRepository(context).use { repository ->
            repository.save(metadataUri, 123456789L, metadata)
            repository.save(metadataUri, 123456789L, metadata)
        }
        RecordingHistoryRepository(context).use { repository ->
            repository.readableDatabase.query("recordings", null, "uri = ?",
                arrayOf(metadataUri.toString()), null, null, null).use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(metadata.callerName, cursor.getString(cursor.getColumnIndexOrThrow("caller_name")))
                assertEquals(metadata.getBestNumber(), cursor.getString(cursor.getColumnIndexOrThrow("phone_number")))
                assertEquals("incoming", cursor.getString(cursor.getColumnIndexOrThrow("direction")))
                assertEquals(123456789L, cursor.getLong(cursor.getColumnIndexOrThrow("started_at")))
            }
        }
    }

    private fun silentRecording(name: String): RecordingHistoryEntry {
        val sampleRate = 8000
        val dataSize = sampleRate * 2 * 4
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
        buffer.putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize)
        val file = File(context.cacheDir, name).apply { writeBytes(buffer.array()) }
        files.add(file)
        return RecordingHistoryEntry(Uri.fromFile(file), name, System.currentTimeMillis())
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        fail("Playback condition timed out: ${model.playback.value}")
    }
}
