package com.kitsumed.shizucallrecorder

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitsumed.shizucallrecorder.system.recordingShareIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingShareDeviceTest {
    @Test
    fun shareIncludesAudioDocumentAndReadOnlyGrant() {
        val uri = Uri.parse("content://callreplay.tests/recording.m4a")
        val intent = recordingShareIntent(uri, "audio/mp4", "recording.m4a")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("audio/mp4", intent.type)
        assertEquals(uri, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertEquals("recording.m4a", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @Test
    fun unknownMimeUsesAudioFallback() {
        val uri = Uri.parse("content://callreplay.tests/recording")
        assertEquals("audio/*", recordingShareIntent(uri, null, "recording").type)
        assertEquals("audio/*", recordingShareIntent(uri, "application/octet-stream", "recording").type)
    }
}
