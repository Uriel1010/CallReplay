/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 * Copyright (C) 2026-present kitsumed (Med)
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Additional terms under Section 7 apply; see LICENSE.
 */
package com.kitsumed.shizucallrecorder.system

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kitsumed.shizucallrecorder.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Shares a fully prepared, app-owned attachment. Receivers need no SAF-provider access.
 */
internal fun recordingShareIntent(uri: Uri, mimeType: String?, fileName: String): Intent {
    require(uri.scheme == "content") { "Only content documents can be shared" }
    return Intent(Intent.ACTION_SEND).apply {
        type = mimeType?.takeIf { it.startsWith("audio/") } ?: "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, fileName)
        clipData = ClipData.newRawUri(fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

class RecordingShareProvider : FileProvider() {
    // Some device MIME maps identify .m4a as audio/mpeg. Every file in this
    // provider is a prepared MP4/AAC attachment, so expose its actual container.
    override fun getType(uri: Uri): String = "audio/mp4"
}

internal fun shareFileName(original: String): String {
    val base = original.substringBeforeLast('.', original)
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cc}\\p{Cf}]"), "_").trim(' ', '.')
        .take(60).trimEnd { Character.isHighSurrogate(it) }.ifBlank { "CallRecording" }
    return "$base.m4a"
}

internal suspend fun prepareRecordingShare(context: Context, source: Uri, originalName: String): Intent =
    withContext(Dispatchers.IO) {
        require(source.scheme == "content") { "Only content documents can be shared" }
        val root = File(context.filesDir, "shared_recordings").apply { mkdirs() }
        // Keep completed exports for delayed imports; never remove the currently shared file on return.
        val expiry = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        root.listFiles()?.filter { it.lastModified() < expiry }?.forEach { it.deleteRecursively() }
        val directory = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val name = shareFileName(originalName)
            val part = File(directory, "export.part")
            ShareAudioExporter.export(context, source, part)
            coroutineContext.ensureActive()
            val target = File(directory, name)
            if(!part.renameTo(target)) throw java.io.IOException("Cannot finalize share attachment")
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.recordings", target)
            recordingShareIntent(uri, "audio/mp4", name)
        } catch(error: Throwable) { directory.deleteRecursively(); throw error }
    }
