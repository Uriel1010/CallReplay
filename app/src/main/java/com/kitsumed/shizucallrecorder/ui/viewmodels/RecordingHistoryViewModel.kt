/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 * Copyright (C) 2026-present kitsumed (Med)
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Additional terms under Section 7 apply; see LICENSE.
 */
package com.kitsumed.shizucallrecorder.ui.viewmodels

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.data.recording.RecordingHistoryEntry
import com.kitsumed.shizucallrecorder.data.recording.RecordingHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecordingHistoryState(
    val entries: List<RecordingHistoryEntry> = emptyList(),
    val loading: Boolean = true,
    val folderUnavailable: Boolean = false,
    val loadFailed: Boolean = false
)

data class RecordingPlaybackState(
    val uri: Uri? = null,
    val preparing: Boolean = false,
    val playing: Boolean = false,
    val position: Int = 0,
    val duration: Int = 0,
    val failed: Boolean = false,
    val speed: Float = 1f,
    val muted: Boolean = false
)

/** Foreground-only playback. Release on leaving Recents; never play over another audio app or call. */
class RecordingHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val preferences = AppPreferences(context)
    private val _history = MutableStateFlow(RecordingHistoryState())
    val history = _history.asStateFlow()
    private val _playback = MutableStateFlow(RecordingPlaybackState())
    val playback = _playback.asStateFlow()
    private var refreshJob: Job? = null
    private var player: MediaPlayer? = null
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change != AudioManager.AUDIOFOCUS_GAIN) stopPlayback()
        }.build()
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = stopPlayback()
    }
    private var receiverRegistered = false

    init {
        viewModelScope.launch {
            while (isActive) {
                if (_playback.value.playing) {
                    player?.let { current ->
                        runCatching { current.currentPosition }.onSuccess { position ->
                            _playback.update { it.copy(position = position) }
                        }
                    }
                }
                delay(250)
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val folder = preferences.getRecordingFolderUri()
            if (folder == null) {
                stopPlayback()
                _history.value = RecordingHistoryState(loading = false, folderUnavailable = true)
                return@launch
            }
            _history.update { it.copy(loading = true, loadFailed = false) }
            val result = withContext(Dispatchers.IO) {
                runCatching { RecordingHistoryRepository(context).use { it.load(folder) } }
            }
            result.onSuccess { entries ->
                if (entries.none { it.uri == _playback.value.uri }) stopPlayback()
                _history.value = RecordingHistoryState(entries = entries, loading = false)
            }.onFailure {
                stopPlayback()
                _history.value = RecordingHistoryState(loading = false, loadFailed = true)
            }
        }
    }

    /**
     * MediaPlayer is prepared asynchronously and only used in valid states.
     * https://developer.android.com/media/platform/mediaplayer/state-resources
     */
    fun toggle(entry: RecordingHistoryEntry) {
        val state = _playback.value
        if (state.uri == entry.uri && !state.preparing && !state.failed && player != null) {
            if (state.playing) {
                runCatching { player?.pause() }.onFailure { playbackFailed() }
                _playback.update { it.copy(playing = false) }
                audioManager.abandonAudioFocusRequest(focusRequest)
            } else if (requestFocus()) {
                runCatching { player?.start() }.onSuccess {
                    _playback.update { it.copy(playing = true) }
                }.onFailure { playbackFailed() }
            }
            return
        }
        stopPlayback()
        _playback.value = RecordingPlaybackState(uri = entry.uri, preparing = true)
        try {
            val current = MediaPlayer()
            player = current
            current.setAudioAttributes(attributes)
            current.setOnPreparedListener {
                if (player !== current) return@setOnPreparedListener
                _playback.update { it.copy(preparing = false, duration = current.duration) }
                if (requestFocus()) {
                    runCatching { current.start() }.onSuccess {
                        _playback.update { it.copy(playing = true) }
                    }.onFailure { playbackFailed() }
                }
            }
            current.setOnCompletionListener {
                if (player !== current) return@setOnCompletionListener
                _playback.update { it.copy(playing = false, position = 0) }
                current.seekTo(0)
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
            current.setOnErrorListener { _, _, _ ->
                if (player === current) playbackFailed()
                true
            }
            ContextCompat.registerReceiver(context, noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            current.setDataSource(context, entry.uri)
            current.prepareAsync()
        } catch (_: Exception) {
            playbackFailed()
        }
    }

    fun seek(position: Int) {
        if (_playback.value.preparing || _playback.value.failed) return
        val target = position.coerceIn(0, _playback.value.duration)
        runCatching { player?.seekTo(target) }.onSuccess {
            _playback.update { it.copy(position = target) }
        }.onFailure { playbackFailed() }
    }

    fun setSpeed(speed: Float) {
        if (_playback.value.preparing || _playback.value.failed || player == null) return
        val wasPlaying = _playback.value.playing
        runCatching {
            player?.playbackParams = android.media.PlaybackParams().setSpeed(speed)
            // MediaPlayer may start automatically when playback parameters change.
            if (!wasPlaying) player?.pause()
        }.onSuccess { _playback.update { it.copy(speed = speed) } }.onFailure { playbackFailed() }
    }

    fun toggleMute() {
        val muted = !_playback.value.muted
        runCatching { player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f) }
            .onSuccess { _playback.update { it.copy(muted = muted) } }
    }

    suspend fun delete(entry: RecordingHistoryEntry): Boolean = withContext(Dispatchers.IO) {
        if (_playback.value.uri == entry.uri) withContext(Dispatchers.Main) { stopPlayback() }
        runCatching { androidx.documentfile.provider.DocumentFile.fromSingleUri(context, entry.uri)?.delete() == true }
            .getOrDefault(false)
    }

    fun stopPlayback() {
        val current = player
        player = null
        current?.release()
        audioManager.abandonAudioFocusRequest(focusRequest)
        if (receiverRegistered) {
            context.unregisterReceiver(noisyReceiver)
            receiverRegistered = false
        }
        _playback.value = RecordingPlaybackState()
    }

    private fun requestFocus(): Boolean {
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) playbackFailed()
        return granted
    }

    private fun playbackFailed() {
        val uri = _playback.value.uri
        stopPlayback()
        _playback.value = RecordingPlaybackState(uri = uri, failed = true)
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
