/* Copyright (C) 2026 Uriel Maznur. GPL-3.0-or-later; see LICENSE. */
package com.kitsumed.shizucallrecorder.system

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** Produces actual AAC-LC in an MP4 container; changing a filename alone is insufficient. */
internal object ShareAudioExporter {
    suspend fun export(context: Context, uri: Uri, output: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            if(isAacLc(format)) remux(extractor, format, output)
            else transcode(extractor, format, output)
        } finally { extractor.release() }
    }

    internal fun isAacLc(format: MediaFormat): Boolean {
        if(format.getString(MediaFormat.KEY_MIME) != MediaFormat.MIMETYPE_AUDIO_AAC) return false
        if(format.getInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC) !=
            MediaCodecInfo.CodecProfileLevel.AACObjectLC) return false
        // AudioSpecificConfig's first five bits are authoritative, including HE-AAC variants.
        val config = format.getByteBuffer("csd-0")?.duplicate()
        // Longer/extended configurations may signal implicit SBR; convert conservatively.
        return config != null && config.remaining() == 2 &&
            (config.get().toInt() and 0xff) ushr 3 == MediaCodecInfo.CodecProfileLevel.AACObjectLC
    }

    private suspend fun remux(extractor: MediaExtractor, format: MediaFormat, file: File) {
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            val track = muxer.addTrack(format)
            muxer.start(); started = true
            val info = MediaCodec.BufferInfo()
            var buffer = ByteBuffer.allocateDirect(256 * 1024)
            var first = -1L
            var samples = 0
            while(true) {
                coroutineContext.ensureActive()
                if(extractor.sampleSize > buffer.capacity()) buffer = sampleBuffer(extractor.sampleSize)
                buffer.clear()
                val count = extractor.readSampleData(buffer, 0)
                if(count < 0) break
                if(first < 0) first = extractor.sampleTime
                info.set(0, count, (extractor.sampleTime - first).coerceAtLeast(0), 0)
                muxer.writeSampleData(track, buffer, info)
                samples++
                extractor.advance()
            }
            if(samples == 0) throw IOException("Empty recording")
            muxer.stop(); started = false
        } finally {
            if(started) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private suspend fun transcode(extractor: MediaExtractor, input: MediaFormat, file: File) {
        if(input.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_RAW) {
            AacSink(input, file).use { sink ->
                var buffer = ByteBuffer.allocateDirect(256 * 1024)
                while(true) {
                    coroutineContext.ensureActive()
                    if(extractor.sampleSize > buffer.capacity()) buffer = sampleBuffer(extractor.sampleSize)
                    buffer.clear()
                    val count = extractor.readSampleData(buffer, 0)
                    if(count < 0) break
                    buffer.position(0); buffer.limit(count)
                    sink.write(pcm16(buffer, input))
                    extractor.advance()
                }
                sink.finish()
            }
            return
        }
        val decoder = MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME)!!)
        var sink: AacSink? = null
        var started = false
        try {
            input.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            decoder.configure(input, null, null, 0)
            decoder.start(); started = true
            val info = MediaCodec.BufferInfo()
            var sourceEnded = false
            var outputEnded = false
            var lastProgress = SystemClock.elapsedRealtime()
            while(!outputEnded) {
                coroutineContext.ensureActive()
                if(SystemClock.elapsedRealtime() - lastProgress > 30000) throw IOException("Audio decoder stalled")
                if(!sourceEnded) {
                    val index = decoder.dequeueInputBuffer(0)
                    if(index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        buffer.clear()
                        if(extractor.sampleSize > buffer.capacity()) throw IOException("Audio packet too large")
                        val count = extractor.readSampleData(buffer, 0)
                        if(count < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sourceEnded = true
                        } else {
                            decoder.queueInputBuffer(index, 0, count, extractor.sampleTime.coerceAtLeast(0), 0)
                            extractor.advance()
                        }
                        lastProgress = SystemClock.elapsedRealtime()
                    }
                }
                when(val index = decoder.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if(sink != null) throw IOException("Audio format changed mid-recording")
                        sink = AacSink(decoder.outputFormat, file)
                    }
                    else -> if(index >= 0) {
                        try {
                            if(info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val pcm = decoder.getOutputBuffer(index)!!
                                pcm.position(info.offset); pcm.limit(info.offset + info.size)
                                val output = sink ?: AacSink(decoder.outputFormat, file).also { sink = it }
                                output.write(pcm16(pcm, decoder.outputFormat))
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            lastProgress = SystemClock.elapsedRealtime()
                        } finally { decoder.releaseOutputBuffer(index, false) }
                    }
                }
            }
            (sink ?: throw IOException("No decoded audio")).finish()
        } finally {
            sink?.close()
            if(started) runCatching { decoder.stop() }
            decoder.release()
        }
    }

    private fun sampleBuffer(size: Long): ByteBuffer {
        if(size !in 1..4L * 1024 * 1024) throw IOException("Audio packet too large")
        return ByteBuffer.allocateDirect(size.toInt())
    }

    private fun pcm16(source: ByteBuffer, format: MediaFormat): ByteBuffer {
        source.order(ByteOrder.LITTLE_ENDIAN)
        return when(format.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)) {
            AudioFormat.ENCODING_PCM_16BIT -> source
            AudioFormat.ENCODING_PCM_FLOAT -> {
                if(source.remaining() % 4 != 0) throw IOException("Incomplete float PCM sample")
                ByteBuffer.allocate(source.remaining() / 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                    while(source.remaining() >= 4) {
                        val value = source.float
                        putShort((if(value.isFinite()) value.coerceIn(-1f, 1f) * 32767 else 0f).toInt().toShort())
                    }
                    flip()
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> ByteBuffer.allocate(source.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                while(source.hasRemaining()) putShort((((source.get().toInt() and 255) - 128) shl 8).toShort())
                flip()
            }
            else -> throw IOException("Unsupported PCM encoding")
        }
    }

    private class AacSink(format: MediaFormat, file: File) : Closeable {
        private val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        private val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        private var muxer: MediaMuxer? = null
        private var started = false
        private var track = -1
        private var frames = 0L
        private var packets = 0
        private var ended = false
        private var lastProgress = SystemClock.elapsedRealtime()
        init {
            try {
                if(channels !in 1..2 || rate <= 0) throw IOException("Unsupported audio channel layout")
                val output = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, channels * 32000)
                    setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                }
                codec.configure(output, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } catch(error: Exception) { codec.release(); throw error }
        }
        suspend fun write(pcm: ByteBuffer) {
            if(pcm.remaining() % (channels * 2) != 0) throw IOException("Incomplete PCM frame")
            while(pcm.hasRemaining()) {
                val index = inputBuffer()
                val target = codec.getInputBuffer(index)!!
                target.clear()
                val count = minOf(target.remaining(), pcm.remaining()) / (channels * 2) * (channels * 2)
                if(count == 0) throw IOException("Audio encoder input too small")
                val chunk = pcm.duplicate().apply { limit(position() + count) }
                target.put(chunk); pcm.position(pcm.position() + count)
                codec.queueInputBuffer(index, 0, count, frames * 1000000L / rate, 0)
                frames += count / (channels * 2)
                lastProgress = SystemClock.elapsedRealtime()
                drain()
            }
        }
        suspend fun finish() {
            if(frames == 0L) throw IOException("Empty recording")
            codec.queueInputBuffer(inputBuffer(), 0, 0, frames * 1000000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            while(!ended) drain(10000)
            if(!started || packets == 0) throw IOException("Empty encoded audio")
            muxer!!.stop(); started = false
        }
        private suspend fun inputBuffer(): Int {
            while(true) {
                checkProgress()
                val index = codec.dequeueInputBuffer(10000)
                if(index >= 0) return index
                drain()
            }
        }
        private suspend fun checkProgress() {
            coroutineContext.ensureActive()
            if(SystemClock.elapsedRealtime() - lastProgress > 30000) throw IOException("Audio encoder stalled")
        }
        private suspend fun drain(timeout: Long = 0) {
            checkProgress()
            val info = MediaCodec.BufferInfo()
            while(true) {
                checkProgress()
                when(val index = codec.dequeueOutputBuffer(info, timeout)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if(started) throw IOException("AAC output format changed")
                        track = muxer!!.addTrack(codec.outputFormat)
                        muxer!!.start(); started = true
                    }
                    else -> if(index >= 0) {
                        try {
                            if(info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                if(!started) throw IOException("Missing AAC track")
                                muxer!!.writeSampleData(track, codec.getOutputBuffer(index)!!, info)
                                packets++
                            }
                            ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            lastProgress = SystemClock.elapsedRealtime()
                        } finally { codec.releaseOutputBuffer(index, false) }
                        if(ended) return
                    }
                }
            }
        }
        override fun close() {
            if(started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { codec.stop() }
            codec.release()
        }
    }
}
