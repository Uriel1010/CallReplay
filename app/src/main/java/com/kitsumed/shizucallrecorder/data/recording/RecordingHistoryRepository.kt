/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 * Copyright (C) 2026-present kitsumed (Med)
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Additional terms under Section 7 apply; see LICENSE.
 */
package com.kitsumed.shizucallrecorder.data.recording

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData

data class RecordingHistoryEntry(
    val uri: Uri,
    val fileName: String,
    val startedAt: Long,
    val callerName: String? = null,
    val phoneNumber: String = "",
    val direction: CallDirection? = null
)

/**
 * Private caller metadata keyed by the actual document URI, independent of filename templates.
 * Audio stays in the user's SAF folder. Listing that folder also discovers older recordings and
 * automatically omits files deleted outside the app. Folder scanning runs on Dispatchers.IO.
 * See https://developer.android.com/training/data-storage/shared/documents-files.
 */
class RecordingHistoryRepository(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "recording_history.db", null, 1
) {
    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE recordings (
                uri TEXT PRIMARY KEY,
                started_at INTEGER NOT NULL,
                caller_name TEXT,
                phone_number TEXT NOT NULL,
                direction TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(uri: Uri, startedAt: Long, metadata: EnrichedCallData) {
        val values = ContentValues().apply {
            put("uri", uri.toString())
            put("started_at", startedAt)
            put("caller_name", metadata.callerName)
            put("phone_number", metadata.getBestNumber())
            put("direction", metadata.direction.token)
        }
        check(writableDatabase.insertWithOnConflict("recordings", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }

    fun load(folderUri: Uri): List<RecordingHistoryEntry> {
        val metadata = mutableMapOf<String, RecordingHistoryEntry>()
        readableDatabase.query("recordings", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val uri = cursor.getString(cursor.getColumnIndexOrThrow("uri"))
                metadata[uri] = RecordingHistoryEntry(
                    uri = Uri.parse(uri), fileName = "",
                    startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                    callerName = cursor.getString(cursor.getColumnIndexOrThrow("caller_name")),
                    phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow("phone_number")),
                    direction = CallDirection.fromToken(cursor.getString(cursor.getColumnIndexOrThrow("direction")))
                )
            }
        }

        val root = DocumentFile.fromTreeUri(appContext, folderUri)
        check(root != null && root.exists() && root.canRead()) { "Recording folder is unavailable" }
        val entries = mutableListOf<RecordingHistoryEntry>()
        val pending = ArrayDeque<DocumentFile>().apply { add(root) }
        val visited = mutableSetOf<Uri>()
        while (pending.isNotEmpty()) {
            val directory = pending.removeFirst()
            if (!visited.add(directory.uri)) continue
            directory.listFiles().forEach { file ->
                if (file.isDirectory) {
                    pending.add(file)
                } else {
                    val name = file.name ?: return@forEach
                    val known = metadata[file.uri.toString()]
                    val isAudio = file.type?.startsWith("audio/") == true ||
                        name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
                    if (file.isFile && file.length() > 0 && (known != null || isAudio)) {
                        entries.add(known?.copy(fileName = name) ?: RecordingHistoryEntry(
                            uri = file.uri, fileName = name, startedAt = file.lastModified()
                        ))
                    }
                }
            }
        }
        return entries.sortedWith(compareByDescending<RecordingHistoryEntry> { it.startedAt }
            .thenBy { it.uri.toString() })
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf("m4a", "mp4", "aac", "ogg", "opus", "wav", "flac", "mp3", "amr", "3gp")
    }
}
