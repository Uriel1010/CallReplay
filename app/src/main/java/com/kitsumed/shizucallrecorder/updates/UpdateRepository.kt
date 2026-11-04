package com.kitsumed.shizucallrecorder.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.kitsumed.shizucallrecorder.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class AppRelease(val versionCode: Long, val versionName: String, val tag: String,
    val apkUrl: String, val sha256: String, val size: Long, val minSdk: Int,
    val notes: String, val pageUrl: String)

enum class UpdateFailure { NETWORK, RATE_LIMIT, INVALID_RELEASE, INVALID_APK, INCOMPATIBLE, INSTALL, BUSY }
class UpdateException(val reason: UpdateFailure) : IOException(reason.name)

/** Public GitHub releases only; no account, token, caller metadata or recording data is sent. */
open class UpdateRepository(private val context: Context) {
    companion object {
        const val REPOSITORY = "https://github.com/Uriel1010/CallReplay"
        const val API = "https://api.github.com/repos/Uriel1010/CallReplay/releases"
        const val MANIFEST = "callreplay-update.json"
        private const val MAX_APK = 200L * 1024 * 1024

        internal fun releaseAsset(url: String, tag: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
                uri.port == -1 && uri.query == null && uri.fragment == null &&
                uri.path.startsWith("/Uriel1010/CallReplay/releases/download/$tag/") &&
                uri.path.split('/').none { it == "." || it == ".." } &&
                !tag.contains('/') && tag.matches(Regex("[A-Za-z0-9._-]+"))
        }.getOrDefault(false)

        internal suspend fun copyVerified(input: InputStream, output: OutputStream,
            release: AppRelease, progress: (Int) -> Unit) {
            if(release.size !in 1..MAX_APK) throw UpdateException(UpdateFailure.INVALID_APK)
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var lastPercent = -1
            val buffer = ByteArray(64 * 1024)
            while(true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                coroutineContext.ensureActive()
                if(count < 0) break
                total += count
                if(total > release.size || total > MAX_APK) throw UpdateException(UpdateFailure.INVALID_APK)
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
                val percent = (total * 100 / release.size).toInt()
                if(percent != lastPercent) { progress(percent); lastPercent = percent }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if(total != release.size || actual != release.sha256) throw UpdateException(UpdateFailure.INVALID_APK)
        }

        internal fun parseRelease(release: JSONObject, manifest: JSONObject): AppRelease {
            fun invalid(): Nothing = throw UpdateException(UpdateFailure.INVALID_RELEASE)
            if(release.optBoolean("draft", true) || manifest.optInt("schemaVersion") != 1) invalid()
            val tag = release.optString("tag_name")
            val apkName = manifest.optString("apk")
            val assets = release.optJSONArray("assets") ?: invalid()
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                .singleOrNull { it.optString("name") == apkName && apkName.endsWith("-release.apk") } ?: invalid()
            val url = asset.optString("browser_download_url")
            val hash = manifest.optString("sha256").lowercase()
            val size = manifest.optLong("size")
            val version = manifest.optLong("versionCode")
            val name = manifest.optString("versionName")
            val minSdk = manifest.optInt("minSdk")
            if(!releaseAsset(url, tag) || !hash.matches(Regex("[0-9a-f]{64}")) || size !in 1..MAX_APK ||
                asset.optLong("size") != size || version <= 0 || name.isBlank() || name.length > 80 || minSdk < 30 ||
                manifest.optString("packageName") != BuildConfig.APPLICATION_ID) invalid()
            return AppRelease(version, name, tag, url, hash, size, minSdk,
                release.optString("body").take(16000), "$REPOSITORY/releases/tag/$tag")
        }
    }

    open suspend fun check(previews: Boolean): AppRelease? = withContext(Dispatchers.IO) {
        val json = fetchText(if(previews) "$API?per_page=20" else "$API/latest", 2 * 1024 * 1024) ?: return@withContext null
        val releases = if(previews) JSONArray(json) else JSONArray().put(JSONObject(json))
        val candidates = mutableListOf<AppRelease>()
        for(index in 0 until releases.length()) {
            coroutineContext.ensureActive()
            val release = releases.getJSONObject(index)
            if(release.optBoolean("draft", true) || (!previews && release.optBoolean("prerelease"))) continue
            val assets = release.optJSONArray("assets") ?: continue
            val manifests = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name") == MANIFEST }
            if(manifests.isEmpty()) continue // Older pre-updater releases have no manifest.
            if(manifests.size != 1) throw UpdateException(UpdateFailure.INVALID_RELEASE)
            val manifestUrl = manifests.single().optString("browser_download_url")
            if(!releaseAsset(manifestUrl, release.optString("tag_name"))) throw UpdateException(UpdateFailure.INVALID_RELEASE)
            val body = fetchText(manifestUrl, 65536) ?: throw UpdateException(UpdateFailure.INVALID_RELEASE)
            candidates += parseRelease(release, JSONObject(body))
        }
        candidates.maxByOrNull { it.versionCode }?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    open suspend fun download(release: AppRelease, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        if(!releaseAsset(release.apkUrl, release.tag)) throw UpdateException(UpdateFailure.INVALID_RELEASE)
        if(release.minSdk > Build.VERSION.SDK_INT) throw UpdateException(UpdateFailure.INCOMPATIBLE)
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "${release.versionCode}-${release.sha256.take(12)}.apk")
        directory.listFiles()?.filter { it.extension == "apk" && it != target }?.forEach { it.delete() }
        val part = File.createTempFile("download-", ".part", directory)
        try {
            val connection = connect(release.apkUrl)
            try {
                if(connection.responseCode in listOf(403, 429)) throw UpdateException(UpdateFailure.RATE_LIMIT)
                if(connection.responseCode != 200) throw UpdateException(UpdateFailure.NETWORK)
                connection.inputStream.use { input -> part.outputStream().use { output ->
                    copyVerified(input, output, release, progress)
                } }
                validateApk(part, release)
                coroutineContext.ensureActive()
                if(target.exists()) target.delete()
                if(!part.renameTo(target)) throw IOException("Could not save update")
                target
            } finally { connection.disconnect() }
        } finally { part.delete() }
    }

    @Suppress("DEPRECATION")
    open fun validateApk(file: File, release: AppRelease) {
        val manager = context.packageManager
        val apk = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw UpdateException(UpdateFailure.INVALID_APK)
        val current = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if(apk.packageName != context.packageName || apk.longVersionCode != release.versionCode ||
            apk.longVersionCode <= current.longVersionCode || apk.versionName != release.versionName ||
            (apk.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT ||
            (apk.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            throw UpdateException(UpdateFailure.INVALID_APK)
        val trusted = current.signingInfo ?: throw UpdateException(UpdateFailure.INVALID_APK)
        val candidate = apk.signingInfo ?: throw UpdateException(UpdateFailure.INVALID_APK)
        val compatible = if(trusted.hasMultipleSigners() || candidate.hasMultipleSigners()) {
            trusted.apkContentsSigners.toSet() == candidate.apkContentsSigners.toSet()
        } else {
            candidate.signingCertificateHistory?.contains(trusted.apkContentsSigners.single()) == true
        }
        if(!compatible) throw UpdateException(UpdateFailure.INVALID_APK)
        // Android's installer performs the authoritative APK signature/lineage verification.
    }

    private fun fetchText(url: String, maxBytes: Int): String? {
        val connection = connect(url)
        try {
            when(connection.responseCode) {
                404 -> return null
                403, 429 -> throw UpdateException(UpdateFailure.RATE_LIMIT)
                200 -> Unit
                else -> throw UpdateException(UpdateFailure.NETWORK)
            }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while(true) {
                    val count = input.read(buffer)
                    if(count < 0) break
                    if(output.size() + count > maxBytes) throw UpdateException(UpdateFailure.INVALID_RELEASE)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }

    private fun connect(initial: String): HttpURLConnection {
        var next = initial
        repeat(6) {
            val uri = URI(next)
            val host = uri.host.orEmpty()
            if(uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443) ||
                !(host == "api.github.com" || host == "github.com" || host.endsWith(".githubusercontent.com")))
                throw UpdateException(UpdateFailure.INVALID_RELEASE)
            val connection = URL(next).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "CallReplay/${BuildConfig.VERSION_NAME}")
            if(host == "api.github.com") connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if(connection.responseCode in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: throw UpdateException(UpdateFailure.NETWORK)
                    next = URI(next).resolve(location).toString(); connection.disconnect()
                } else return connection
            } catch(error: Exception) { connection.disconnect(); throw error }
        }
        throw UpdateException(UpdateFailure.NETWORK)
    }
}
