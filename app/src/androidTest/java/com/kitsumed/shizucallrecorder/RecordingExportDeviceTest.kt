package com.kitsumed.shizucallrecorder

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsumed.shizucallrecorder.data.recording.RecordingCodec
import com.kitsumed.shizucallrecorder.services.shell.recording.encoding.AudioEncoder
import com.kitsumed.shizucallrecorder.system.ShareAudioExporter
import com.kitsumed.shizucallrecorder.system.prepareRecordingShare
import com.kitsumed.shizucallrecorder.system.shareFileName
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class RecordingExportDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root get() = File(context.filesDir, "shared_recordings").apply { mkdirs() }
    private val owned = mutableListOf<File>()
    @After fun cleanup() { owned.forEach { it.deleteRecursively() } }

    @Test fun monoWavBecomesRealAacLcWithMatchingDurationAndUntouchedSource() = runBlocking {
        val source = wav(1)
        val original = hash(source)
        val shared = prepare(source)
        assertAudio(shared, 1)
        assertEquals(original, hash(source))
    }
    @Test fun stereoKeepsBothDistinctAudioChannels() = runBlocking {
        val shared = prepare(wav(2))
        assertAudio(shared, 2)
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, stream(shared), null); extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            decoder = codec; codec.configure(format, null, null, 0); codec.start()
            var inputEnded = false; var ended = false
            var left = 0L; var right = 0L; var difference = 0L
            val info = MediaCodec.BufferInfo()
            val deadline = android.os.SystemClock.elapsedRealtime() + 10000
            while(!ended) {
                check(android.os.SystemClock.elapsedRealtime() < deadline)
                if(!inputEnded) {
                    val i = codec.dequeueInputBuffer(0)
                    if(i >= 0) {
                        val buffer = codec.getInputBuffer(i)!!; buffer.clear()
                        val n = extractor.readSampleData(buffer, 0)
                        codec.queueInputBuffer(i, 0, n.coerceAtLeast(0), extractor.sampleTime.coerceAtLeast(0),
                            if(n < 0) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if(n < 0) inputEnded = true else extractor.advance()
                    }
                }
                val i = codec.dequeueOutputBuffer(info, 10000)
                if(i >= 0) {
                    val buffer = codec.getOutputBuffer(i)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset); buffer.limit(info.offset + info.size)
                    while(buffer.remaining() >= 4) {
                        val l = buffer.short.toInt(); val r = buffer.short.toInt()
                        left += abs(l); right += abs(r); difference += abs(l-r)
                    }
                    ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(i, false)
                }
            }
            assertTrue("Left voice was lost", left > 100000)
            assertTrue("Right voice was lost", right > 100000)
            assertTrue("Stereo was collapsed", difference > 100000)
        } finally { decoder?.let { runCatching { it.stop() }; it.release() }; extractor.release() }
    }
    @Test fun defaultOpusRecordingExportsAsPlayableM4a() = runBlocking {
        val source = encoded(RecordingCodec.OPUS)
        val original = hash(source)
        val shared = prepare(source)
        assertAudio(shared, 1)
        assertEquals(original, hash(source))
    }
    @Test fun amrWidebandExportsAsPlayableM4aWhenSupported() = runBlocking {
        assumeTrue(RecordingCodec.AMR_WB.isSupported())
        assertAudio(prepare(encoded(RecordingCodec.AMR_WB)), 1)
    }
    @Test fun highEfficiencyAacExportsAsLcWhenSupported() = runBlocking {
        assumeTrue(RecordingCodec.HE_AAC_V1.isSupported())
        val source = encoded(RecordingCodec.HE_AAC_V1)
        // HE decoders may expand mono to stereo. Preserve the decoded layout.
        val retriever = MediaMetadataRetriever()
        val sourceDuration = try {
            retriever.setDataSource(source.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        } finally { retriever.release() }
        println("Synthetic HE-AAC source duration: $sourceDuration")
        assertAudio(prepare(source), decodedChannels(source), sourceDuration)
    }
    @Test fun extendedAacConfigurationsRequireConversion() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 16000, 1)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(byteArrayOf(0x14, 0x08)))
        assertTrue(ShareAudioExporter.isAacLc(format))
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectHE)
        assertFalse(ShareAudioExporter.isAacLc(format))
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(byteArrayOf(0x14, 0x08, 0x56, 0xe5.toByte())))
        assertFalse(ShareAudioExporter.isAacLc(format))
    }
    @Test fun aacRemuxPreservesEveryPacketAndSurvivesOriginalDeletion() = runBlocking {
        val first = prepare(wav(1))
        val source = file(stream(first))
        val originalPackets = packets(stream(first))
        val second = prepare(source)
        assertEquals(originalPackets, packets(stream(second)))
        source.parentFile!!.deleteRecursively()
        assertAudio(second, 1)
    }
    @Test fun anotherAppCanReadCompleteAttachmentAndItsFilename() = runBlocking {
        val source = wav(1)
        val shared = prepare(source, "Call_2026-11-05_14-28.ogg")
        val expected = hash(file(stream(shared)))
        source.parentFile!!.deleteRecursively()
        val action = "${BuildConfig.APPLICATION_ID}.SHARE_RESULT.${UUID.randomUUID()}"
        val result = AtomicReference<Intent>()
        val latch = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { result.set(intent); latch.countDown() }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
        try {
            shared.component = ComponentName(instrumentation.context.packageName, ShareAttachmentReceiver::class.java.name)
            shared.putExtra("reply", action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shared)
            assertTrue("Receiving app did not finish", latch.await(15, TimeUnit.SECONDS))
            val reply = result.get()
            assertFalse("Receiving app could not read attachment", reply.getBooleanExtra("failed", false))
            assertNotEquals(android.os.Process.myUid(), reply.getIntExtra("receiverUid", -1))
            assertTrue("Receiver gained write access", reply.getBooleanExtra("writeDenied", false))
            assertEquals(expected, reply.getStringExtra("hash"))
            assertEquals("Call_2026-11-05_14-28.m4a", reply.getStringExtra("name"))
            assertEquals("audio/mp4", reply.getStringExtra("mime"))
            assertTrue(reply.getLongExtra("size", 0) > 1000)
        } finally { context.unregisterReceiver(receiver) }
    }
    @Test fun corruptRecordingFailsWithoutLeavingAnAttachment() = runBlocking {
        val source = sourceFile("bad.ogg").apply { writeText("not audio") }
        val before = root.listFiles()!!.map { it.name }.toSet()
        try { prepare(source); fail("Corrupt audio must not open a share intent") }
        catch(_: java.io.IOException) { }
        assertEquals(before, root.listFiles()!!.map { it.name }.toSet())
        assertEquals("not audio", source.readText())
    }
    @Test fun cancelledConversionRemovesIncompleteExports() = runBlocking {
        val source = wav(2, 120)
        val before = root.listFiles()!!.map { it.name }.toSet()
        try { withTimeout(10) { prepare(source) }; fail("Expected cancellation") }
        catch(_: TimeoutCancellationException) { }
        assertEquals(before, root.listFiles()!!.map { it.name }.toSet())
        assertTrue(source.exists())
    }
    @Test fun outputNamesHaveARealM4aExtensionAndNoPathTraversal() {
        assertEquals("שיחה_אמא.m4a", shareFileName("שיחה_אמא.ogg"))
        assertEquals("CallRecording.m4a", shareFileName(".ogg"))
        val name = shareFileName("../../folder\\call\u202e.wav")
        assertFalse(name.contains('/')); assertFalse(name.contains('\\')); assertFalse(name.contains('\u202e'))
        assertTrue(name.endsWith(".m4a")); assertTrue(name.length <= 104)
        assertTrue(shareFileName("漢😀".repeat(100) + ".ogg").toByteArray().size <= 255)
    }

    private suspend fun prepare(source: File, name: String = source.name): Intent {
        val result = prepareRecordingShare(context, uri(source), name)
        owned += file(stream(result)).parentFile!!
        assertEquals("audio/mp4", result.type)
        assertEquals(stream(result), result.clipData!!.getItemAt(0).uri)
        assertTrue(result.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, result.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return result
    }
    private fun stream(intent: Intent) = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)!!
    private fun file(uri: Uri) = File(root, uri.pathSegments.drop(1).joinToString("/"))
    private fun uri(file: File) = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.recordings", file)
    private fun sourceFile(name: String): File {
        val directory = File(root, "test-${UUID.randomUUID()}").apply { mkdirs() }
        owned += directory
        return File(directory, name)
    }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun pcm(channels: Int, seconds: Int = 2): ByteArray = ByteBuffer.allocate(16000 * seconds * channels * 2)
        .order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(16000 * seconds) { sample -> repeat(channels) { channel ->
                putShort((sin(2.0 * Math.PI * (440 + channel * 220) * sample / 16000) * 8000).toInt().toShort())
            } }
        }.array()
    private fun wav(channels: Int, seconds: Int = 2): File {
        val data = pcm(channels, seconds)
        return sourceFile("test.wav").apply { outputStream().use { out ->
            out.write(ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVEfmt ".toByteArray()); putInt(16)
                putShort(1); putShort(channels.toShort()); putInt(16000); putInt(16000 * channels * 2)
                putShort((channels * 2).toShort()); putShort(16); put("data".toByteArray()); putInt(data.size)
            }.array()); out.write(data)
        } }
    }
    private fun encoded(codec: RecordingCodec): File {
        val file = sourceFile("test${codec.containerExtension}")
        FileOutputStream(file).use { out ->
            val encoder = AudioEncoder(out.fd, codec, 1, codec.defaultBitRate)
            try { encoder.encode(ByteBuffer.wrap(pcm(1)), 0); encoder.finish() }
            finally { encoder.release() }
        }
        return file
    }
    private fun assertAudio(intent: Intent, channels: Int, expectedDuration: Long = 2000) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, stream(intent), null)
            assertEquals(1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            assertTrue("Output is not real AAC-LC", ShareAudioExporter.isAacLc(format))
            assertEquals(channels, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            extractor.selectTrack(0)
            assertTrue(extractor.readSampleData(ByteBuffer.allocate(65536), 0) > 0)
        } finally { extractor.release() }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, stream(intent))
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Audio duration changed: $duration, source: $expectedDuration",
                duration in (expectedDuration - 200)..(expectedDuration + 300))
        } finally { retriever.release() }
    }
    private fun decodedChannels(source: File): Int {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(source.absolutePath); extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            decoder = codec; codec.configure(format, null, null, 0); codec.start()
            val deadline = android.os.SystemClock.elapsedRealtime() + 10000
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            while(android.os.SystemClock.elapsedRealtime() < deadline) {
                if(!inputEnded) {
                    val index = codec.dequeueInputBuffer(0)
                    if(index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!; buffer.clear()
                        val count = extractor.readSampleData(buffer, 0)
                        codec.queueInputBuffer(index, 0, count.coerceAtLeast(0), extractor.sampleTime.coerceAtLeast(0),
                            if(count < 0) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if(count < 0) inputEnded = true else extractor.advance()
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10000)
                if(index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    println("Synthetic HE-AAC: encoded channels=${format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)}, decoded channels=$channels")
                    return channels
                }
                if(index >= 0) codec.releaseOutputBuffer(index, false)
            }
            error("Synthetic decoder did not announce its format")
        } finally { decoder?.let { runCatching { it.stop() }; it.release() }; extractor.release() }
    }
    private fun packets(uri: Uri): List<String> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null); extractor.selectTrack(0)
            val buffer = ByteBuffer.allocate(65536)
            val result = mutableListOf<String>()
            while(true) {
                buffer.clear(); val count = extractor.readSampleData(buffer, 0)
                if(count < 0) break
                val bytes = ByteArray(count); buffer.position(0); buffer.get(bytes)
                result += MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                extractor.advance()
            }
            return result
        } finally { extractor.release() }
    }
}
