package me.rerere.rikkahub.data.update

import kotlinx.serialization.Serializable
import java.time.Instant

private val sha256Pattern = Regex("[a-f0-9]{64}")
private val supportedAbis = setOf("arm64-v8a", "x86_64", "universal")
internal const val MAX_APK_SIZE = 512L * 1024 * 1024

@Serializable
internal data class SignedUpdateCatalog(val payload: String, val signature: String)

@Serializable
internal data class ApkUpdateCatalog(
    val schemaVersion: Int,
    val packageName: String,
    val builds: List<ApkBuild>,
) {
    fun validate(expectedPackage: String) {
        require(schemaVersion == 1 && packageName == expectedPackage)
        require(builds.size <= 10 && builds.zipWithNext().all { (a, b) -> a.versionCode > b.versionCode })
        builds.forEach { build ->
            require(build.versionCode in 100_001..2_100_000_000 && build.buildNumber > 0)
            require(build.versionName.length in 1..80 && build.commit.matches(Regex("[a-f0-9]{40}")))
            Instant.parse(build.publishedAt)
            require(build.apks.map { it.abi }.toSet() == supportedAbis && build.apks.size == supportedAbis.size)
            build.apks.forEach { apk ->
                require(apk.size in 1..MAX_APK_SIZE && sha256Pattern.matches(apk.sha256))
                require(apk.patches.size <= 10 && apk.patches.distinctBy { it.baseSha256 }.size == apk.patches.size)
                apk.patches.forEach { patch ->
                    require(patch.baseVersionCode in 1 until build.versionCode)
                    require(patch.size in 1 until apk.size)
                    require(sha256Pattern.matches(patch.sha256) && sha256Pattern.matches(patch.baseSha256))
                }
            }
        }
    }
}

@Serializable
internal data class ApkBuild(
    val versionCode: Int,
    val versionName: String,
    val buildNumber: Int,
    val publishedAt: String,
    val commit: String,
    val apks: List<ApkAsset>,
) {
    fun select(abis: List<String>, installedVersion: Long, installedSha256: String?): ApkUpdateChoice? {
        if (versionCode <= installedVersion) return null
        val compatible = (abis.filter { it in supportedAbis } + "universal")
            .mapNotNull { abi -> apks.find { it.abi == abi } }
        val full = compatible.firstOrNull() ?: return null
        val delta = compatible.mapNotNull { apk ->
            val patch = apk.patches.find {
                it.baseVersionCode.toLong() == installedVersion && it.baseSha256 == installedSha256 &&
                    it.size < full.size
            }
            patch?.let { ApkUpdateChoice(this, apk, full, it) }
        }.minByOrNull { it.patch!!.size }
        return delta ?: ApkUpdateChoice(this, full, full)
    }
}

@Serializable
internal data class ApkAsset(
    val abi: String,
    val size: Long,
    val sha256: String,
    val patches: List<ApkDelta> = emptyList(),
) {
    fun key(versionCode: Int) = "builds/$versionCode/$sha256/$abi.apk"
}

@Serializable
internal data class ApkDelta(
    val baseVersionCode: Int,
    val baseSha256: String,
    val size: Long,
    val sha256: String,
) {
    fun key(versionCode: Int, target: ApkAsset) =
        "builds/$versionCode/${target.sha256}/${target.abi}-from-$baseSha256-$sha256.hdiff"
}

internal data class ApkUpdateChoice(
    val build: ApkBuild,
    val target: ApkAsset,
    val full: ApkAsset,
    val patch: ApkDelta? = null,
)
