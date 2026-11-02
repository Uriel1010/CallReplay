/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 * Copyright (C) 2026-present kitsumed (Med)
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Additional terms under Section 7 apply; see LICENSE.
 */
package com.kitsumed.shizucallrecorder.system

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/**
 * Share the SAF audio document with temporary read access, without copying or modifying it.
 * https://developer.android.com/training/sharing/send
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
