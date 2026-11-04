package com.kitsumed.shizucallrecorder.updates

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsumed.shizucallrecorder.BuildConfig
import com.kitsumed.shizucallrecorder.services.recording.RecordingForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File

enum class UpdateStage { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY }
data class UpdateState(val stage: UpdateStage = UpdateStage.IDLE, val release: AppRelease? = null,
    val progress: Int = 0, val error: UpdateFailure? = null, val autoCheck: Boolean = false,
    val previews: Boolean = false, val announce: Boolean = false, val apk: File? = null)

class UpdateViewModel @JvmOverloads constructor(application: Application,
    private val repository: UpdateRepository = UpdateRepository(application)) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UpdateState(autoCheck = preferences.getBoolean("auto", false),
        previews = preferences.getBoolean("previews", false)))
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun setAutomatic(enabled: Boolean) {
        preferences.edit().putBoolean("auto", enabled).apply()
        _state.update { it.copy(autoCheck = enabled, announce = if(enabled) it.announce else false) }
    }
    fun setPreviews(enabled: Boolean) {
        if(_state.value.stage == UpdateStage.DOWNLOADING) cancelDownload()
        preferences.edit().putBoolean("previews", enabled).apply()
        _state.update { it.copy(previews = enabled, release = null, apk = null, stage = UpdateStage.IDLE, error = null, announce = false) }
        job?.cancel()
    }
    fun onForeground() {
        if(_state.value.autoCheck && System.currentTimeMillis() - preferences.getLong("last_check", 0) >= 86400000L)
            check(automatic = true)
    }
    fun check(automatic: Boolean = false) {
        if(job?.isActive == true) return
        if(automatic && installBlocked(getApplication())) return
        preferences.edit().putLong("last_check", System.currentTimeMillis()).apply()
        job = viewModelScope.launch {
            _state.update { it.copy(stage = UpdateStage.CHECKING, release = null, error = null, announce = false, apk = null) }
            try {
                val release = repository.check(_state.value.previews)
                _state.update { it.copy(release = release, stage = if(release == null) UpdateStage.CURRENT else UpdateStage.AVAILABLE,
                    announce = automatic && it.autoCheck && release != null) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) {
                coroutineContext.ensureActive()
                _state.update { it.copy(stage = UpdateStage.IDLE, error = failure(error), announce = false) }
            }
        }
    }
    fun download() {
        val release = _state.value.release ?: return
        if(job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(stage = UpdateStage.DOWNLOADING, progress = 0, error = null, apk = null) }
            try {
                val file = repository.download(release) { value -> _state.update { it.copy(progress = value) } }
                _state.update { it.copy(stage = UpdateStage.READY, apk = file, error = null) }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) {
                coroutineContext.ensureActive()
                _state.update { it.copy(stage = UpdateStage.AVAILABLE, error = failure(error)) }
            }
        }
    }
    fun cancelDownload() {
        job?.cancel(); job = null
        _state.update { it.copy(stage = if(it.release == null) UpdateStage.IDLE else UpdateStage.AVAILABLE, progress = 0, apk = null) }
    }
    fun dismiss() { _state.update { it.copy(announce = false) } }
    fun install(context: Context) {
        val snapshot = _state.value
        val file = snapshot.apk ?: return
        val release = snapshot.release ?: return
        if(installBlocked(context)) { _state.update { it.copy(error = UpdateFailure.BUSY) }; return }
        try {
            if(!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")))
                return
            }
            repository.validateApk(file, release)
            context.startActivity(installerIntent(context, file))
            _state.update { it.copy(announce = false, error = null) }
        } catch(_: Exception) { _state.update { it.copy(error = UpdateFailure.INSTALL) } }
    }
    private fun failure(error: Exception) = (error as? UpdateException)?.reason ?: UpdateFailure.NETWORK

    companion object {
        fun installerIntent(context: Context, file: File): Intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.updates", file),
                "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        @Suppress("DEPRECATION")
        fun installBlocked(context: Context): Boolean {
            if(RecordingForegroundService.isRunning) return true
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                runCatching { context.getSystemService(TelephonyManager::class.java).callState != TelephonyManager.CALL_STATE_IDLE }.getOrDefault(true)
        }
    }
}
