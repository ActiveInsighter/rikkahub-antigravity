package me.rerere.rikkahub.data.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ApkPatchSmokeTest {
    @Test
    fun rebuildsSignedApkByteForByteWithAndroidNativeLibrary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        assumeTrue(assets.list("apk-update")?.contains("base.apk") == true)
        val context = instrumentation.targetContext
        val workspace = kotlin.io.path.createTempDirectory(context.cacheDir.toPath(), "apk-patch-test-").toFile()
        try {
            fun extract(name: String) = File(workspace, name).also { file ->
                assets.open("apk-update/$name").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val (size, expectedHash) = assets.open("apk-update/expected.txt").bufferedReader().use { it.readLines() }
            val expectedSize = size.toLong()
            val output = File(workspace, "new.apk")
            ApkPatcher.reconstruct(extract("base.apk"), extract("update.hdiff"), output, expectedSize)
            assertEquals(expectedSize, output.length())
            assertEquals(expectedHash, sha256(output))
        } finally {
            workspace.deleteRecursively()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHexString()
    }
}
