package com.kitsumed.shizucallrecorder.updates

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdateIntegrityTest {
    private val bytes = ByteArray(150_000) { (it % 251).toByte() }
    private fun release(data: ByteArray = bytes) = AppRelease(23, "2.2.0", "v2.2.0",
        "https://github.com/Uriel1010/CallReplay/releases/download/v2.2.0/CallReplay-2.2.0-release.apk",
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) },
        data.size.toLong(), 30, "", "https://github.com/Uriel1010/CallReplay/releases/tag/v2.2.0")

    @Test fun acceptsOnlyOwnHttpsReleaseAssets() {
        assertTrue(UpdateRepository.releaseAsset(release().apkUrl, "v2.2.0"))
    }
    @Test fun rejectsOtherRepositoriesAndHosts() {
        listOf(release().apkUrl.replace("Uriel1010", "someone"),
            release().apkUrl.replace("github.com", "github.com.attacker.example"),
            release().apkUrl.replace("github.com", "attacker.example/github.com")).forEach {
            assertFalse(it, UpdateRepository.releaseAsset(it, "v2.2.0"))
        }
    }
    @Test fun rejectsCredentialsQueriesAndFragments() {
        listOf(release().apkUrl.replace("github.com", "user@github.com"), release().apkUrl + "?x=1",
            release().apkUrl + "#fragment", release().apkUrl.replace("github.com", "github.com:443")).forEach {
            assertFalse(it, UpdateRepository.releaseAsset(it, "v2.2.0"))
        }
    }
    @Test fun rejectsPlaintextAndWrongTag() {
        assertFalse(UpdateRepository.releaseAsset(release().apkUrl.replace("https:", "http:"), "v2.2.0"))
        assertFalse(UpdateRepository.releaseAsset(release().apkUrl, "v2.3.0"))
    }
    @Test fun rejectsEncodedAndLiteralTraversal() {
        listOf("../evil.apk", "%2e%2e/evil.apk", ".%2e%2fevil.apk", "./evil.apk").forEach {
            val url = release().apkUrl.substringBeforeLast('/') + "/" + it
            assertFalse(url, UpdateRepository.releaseAsset(url, "v2.2.0"))
        }
    }
    @Test fun verifiesExactBytesAndReportsBoundedProgress() = runBlocking {
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Int>()
        UpdateRepository.copyVerified(ByteArrayInputStream(bytes), output, release(), progress::add)
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(100, progress.last())
        assertTrue(progress.all { it in 0..100 })
        assertTrue(progress.zipWithNext().all { it.first < it.second })
    }
    @Test fun rejectsTruncatedDownload() = rejects(bytes.copyOf(bytes.size - 1), release())
    @Test fun rejectsOversizedDownload() = rejects(bytes + byteArrayOf(1), release())
    @Test fun rejectsTamperedDownload() = rejects(bytes.copyOf().apply { this[42] = 99 }, release())
    @Test fun rejectsZeroAndUnboundedMetadataSize() {
        rejects(bytes, release().copy(size = 0))
        rejects(bytes, release().copy(size = 201L * 1024 * 1024))
    }
    @Test fun cancellationStopsBeforeAnotherChunkIsPublished() = runBlocking {
        val output = ByteArrayOutputStream()
        var callbacks = 0
        val task = launch {
            UpdateRepository.copyVerified(ByteArrayInputStream(bytes), output, release()) {
                callbacks++
                cancel(CancellationException("User cancelled download"))
            }
        }
        task.join()
        assertTrue(task.isCancelled)
        assertEquals(1, callbacks)
        assertTrue(output.size() < bytes.size)
    }
    private fun rejects(data: ByteArray, metadata: AppRelease) = runBlocking {
        try {
            UpdateRepository.copyVerified(ByteArrayInputStream(data), ByteArrayOutputStream(), metadata) {}
            fail("Unverified bytes must be rejected")
        } catch(error: UpdateException) { assertEquals(UpdateFailure.INVALID_APK, error.reason) }
    }
}
