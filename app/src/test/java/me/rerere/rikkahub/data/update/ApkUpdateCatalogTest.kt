package me.rerere.rikkahub.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ApkUpdateCatalogTest {
    private val baseHash = "a".repeat(64)
    private val delta = ApkDelta(100_010, baseHash, 150, "b".repeat(64))
    private val x86 = ApkAsset("x86_64", 1000, "c".repeat(64))
    private val arm = ApkAsset("arm64-v8a", 1000, "d".repeat(64))
    private val universal = ApkAsset("universal", 1200, "e".repeat(64), listOf(delta))
    private val build = ApkBuild(
        versionCode = 100_014,
        versionName = "3.3.0",
        buildNumber = 14,
        publishedAt = "2026-11-20T10:00:00Z",
        commit = "a".repeat(40),
        apks = listOf(arm, x86, universal),
    )

    @Test
    fun universalInstallationUsesExactBaseAndKeepsDeviceSpecificFullFallback() {
        val choice = build.select(listOf("x86_64"), delta.baseVersionCode.toLong(), baseHash)!!
        assertEquals(delta, choice.patch)
        assertEquals(universal, choice.target)
        assertEquals(x86, choice.full)
    }

    @Test
    fun mismatchedBaseOrSplitInstallationUsesFullApk() {
        for (digest in listOf(null, "f".repeat(64))) {
            val choice = build.select(listOf("arm64-v8a"), delta.baseVersionCode.toLong(), digest)!!
            assertEquals(arm, choice.full)
            assertNull(choice.patch)
        }
    }

    @Test
    fun noUpgradeForSameOrOlderBuild() {
        assertNull(build.select(listOf("x86_64"), 100_014, baseHash))
        assertNull(build.select(listOf("x86_64"), 100_015, baseHash))
    }

    @Test
    fun validatesCatalogIdentityAndRejectsDuplicateBuilds() {
        val packageName = "me.rerere.rikkahub.debug"
        val catalog = ApkUpdateCatalog(1, packageName, listOf(build))
        catalog.validate(packageName)
        assertThrows(IllegalArgumentException::class.java) { catalog.validate("me.rerere.rikkahub") }
        assertThrows(IllegalArgumentException::class.java) {
            catalog.copy(builds = listOf(build, build)).validate(packageName)
        }
    }
}
