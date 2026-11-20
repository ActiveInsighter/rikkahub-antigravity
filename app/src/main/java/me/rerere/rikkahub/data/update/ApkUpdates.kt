package me.rerere.rikkahub.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.BuildConfig
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal val usesAntigravityUpdates = !BuildConfig.IS_PLAY_STORE && BuildConfig.DEBUG

private const val TAG = "ApkUpdates"
private const val CATALOG_LIMIT = 1024 * 1024L
private const val DISK_RESERVE = 8 * 1024 * 1024L

internal data class ApkBuildList(
    val builds: List<ApkBuild> = emptyList(),
    val choices: Map<Int, ApkUpdateChoice> = emptyMap(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

internal sealed interface ApkTransfer {
    data object Idle : ApkTransfer
    data class Downloading(
        val build: ApkBuild,
        val downloaded: Long,
        val total: Long,
        val incremental: Boolean,
        val fallback: Boolean,
    ) : ApkTransfer
    data class Patching(val build: ApkBuild) : ApkTransfer
    data class Verifying(val build: ApkBuild) : ApkTransfer
    data class Ready(val build: ApkBuild, val file: File) : ApkTransfer
    data class Failed(val build: ApkBuild, val lowStorage: Boolean) : ApkTransfer
}

internal class ApkUpdates(
    private val context: Context,
    client: OkHttpClient,
    private val appScope: AppScope,
) {
    private val client = client.newBuilder()
        .followRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val baseUrl = BuildConfig.APK_UPDATE_BASE_URL.takeIf { it.isNotBlank() }?.toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = File(context.cacheDir, "apk-updates")
    private val installedApk = File(context.applicationInfo.sourceDir)
    private val installedInfo by lazy { context.packageManager.getPackageInfo(context.packageName, signingFlags) }
    val installedVersion: Long get() = PackageInfoCompat.getLongVersionCode(installedInfo)
    val configured: Boolean get() = baseUrl != null

    private val _catalog = MutableStateFlow(ApkBuildList())
    val catalog = _catalog.asStateFlow()
    private val _transfer = MutableStateFlow<ApkTransfer>(ApkTransfer.Idle)
    val transfer = _transfer.asStateFlow()

    // Entry points are called on the main thread. Both jobs survive leaving the screen.
    private var refreshJob: Job? = null
    private var transferJob: Job? = null
    private val activeCall = AtomicReference<Call?>()
    private var autoInstallFile: File? = null
    private var baseHash: String? = null

    fun refresh() {
        if (!usesAntigravityUpdates || !configured || refreshJob?.isActive == true) return
        _catalog.value = _catalog.value.copy(loading = true, failed = false)
        refreshJob = appScope.launch {
            try {
                _catalog.value = withContext(Dispatchers.IO) {
                    val builds = fetchCatalog()
                    if (baseHash == null && context.applicationInfo.splitSourceDirs.isNullOrEmpty() &&
                        installedApk.canRead() && builds.any { build ->
                            build.apks.any { apk -> apk.patches.any { it.baseVersionCode.toLong() == installedVersion } }
                        }
                    ) {
                        try {
                            baseHash = hash(installedApk)
                        } catch (e: IOException) {
                            Log.w(TAG, "Cannot read the installed APK", e)
                        }
                    }
                    val choices = builds.mapNotNull { build ->
                        build.select(Build.SUPPORTED_ABIS.toList(), installedVersion, baseHash)
                            ?.let { build.versionCode to it }
                    }.toMap()
                    ApkBuildList(builds, choices, loading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cannot load APK catalog", e)
                _catalog.value = _catalog.value.copy(loading = false, failed = true)
            }
        }
    }

    fun download(choice: ApkUpdateChoice, fullOnly: Boolean = false) {
        if (!usesAntigravityUpdates || !configured || choice.build.versionCode <= installedVersion ||
            transferJob?.isCompleted == false
        ) return
        val previousReady = _transfer.value as? ApkTransfer.Ready
        transferJob = appScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { prepare(choice, fullOnly, previousReady?.file) }
                autoInstallFile = result
                _transfer.value = ApkTransfer.Ready(choice.build, result)
            } catch (e: CancellationException) {
                _transfer.value = ApkTransfer.Idle
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cannot prepare APK", e)
                _transfer.value = ApkTransfer.Failed(choice.build, lowStorage = e is UpdateStorageException)
            }
        }
    }

    fun cancel() {
        // The UI only offers cancellation during download. Keep the job active until cleanup finishes.
        if (_transfer.value is ApkTransfer.Downloading) {
            transferJob?.cancel()
            activeCall.get()?.cancel()
        }
    }

    fun consumeAutoInstall(file: File): Boolean {
        if (autoInstallFile != file) return false
        autoInstallFile = null
        return true
    }

    fun installIntent(ready: ApkTransfer.Ready): Intent {
        check(usesAntigravityUpdates && ready == _transfer.value && ready.build.versionCode > installedVersion)
        if (!ready.file.isFile) {
            _transfer.value = ApkTransfer.Failed(ready.build, lowStorage = false)
            throw IOException("The prepared APK is no longer in the cache")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", ready.file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun installPermissionIntent() = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        "package:${context.packageName}".toUri(),
    )

    private fun fetchCatalog(): List<ApkBuild> {
        val request = Request.Builder().url(url("catalog.json")).cacheControl(CacheControl.FORCE_NETWORK).build()
        val envelope = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Catalog HTTP ${response.code}" }
            val source = response.body.source()
            val data = Buffer()
            while (data.size <= CATALOG_LIMIT && source.read(data, minOf(8192, CATALOG_LIMIT + 1 - data.size)) != -1L) {
                check(data.size <= CATALOG_LIMIT) { "Catalog exceeds size limit" }
            }
            json.decodeFromString<SignedUpdateCatalog>(data.readUtf8())
        }
        val payload = Base64.getDecoder().decode(envelope.payload)
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(installedInfo.certificates().single().inputStream())
        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(certificate)
            update(payload)
        }
        check(verifier.verify(Base64.getDecoder().decode(envelope.signature))) { "Catalog signature mismatch" }
        return json.decodeFromString<ApkUpdateCatalog>(payload.decodeToString())
            .also { it.validate(context.packageName) }.builds
    }

    private suspend fun prepare(choice: ApkUpdateChoice, fullOnly: Boolean, previousReady: File?): File {
        check(cache.mkdirs() || cache.isDirectory)
        // An installer that is still reading the previous URI can finish while a new transfer starts.
        cache.listFiles()?.filter { it != previousReady?.parentFile }?.forEach { it.deleteRecursively() }
        val folder = kotlin.io.path.createTempDirectory(cache.toPath(), "download-").toFile()
        val output = File(folder, "RikkaHub-${choice.build.buildNumber}.apk")
        val patch = File(folder, "update.hdiff")
        var ready = false
        try {
            val delta = choice.patch.takeUnless { fullOnly }
            if (delta != null) {
                try {
                    requireSpace(delta.size + choice.target.size)
                    downloadFile(choice.build, delta.key(choice.build.versionCode, choice.target), patch,
                        delta.size, delta.sha256, incremental = true, fallback = false)
                    _transfer.value = ApkTransfer.Patching(choice.build)
                    ApkPatcher.reconstruct(installedApk, patch, output, choice.target.size)
                    _transfer.value = ApkTransfer.Verifying(choice.build)
                    check(output.length() == choice.target.size && hash(output) == choice.target.sha256)
                    verifyApk(output, choice.build.versionCode)
                    ready = true
                    return output
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Incremental download failed; trying full APK", e)
                    output.delete()
                } catch (e: LinkageError) {
                    Log.w(TAG, "Patch engine unavailable; trying full APK", e)
                    output.delete()
                } finally {
                    patch.delete()
                }
            }

            requireSpace(choice.full.size)
            downloadFile(choice.build, choice.full.key(choice.build.versionCode), output,
                choice.full.size, choice.full.sha256, incremental = false, fallback = delta != null)
            _transfer.value = ApkTransfer.Verifying(choice.build)
            verifyApk(output, choice.build.versionCode)
            ready = true
            return output
        } finally {
            if (!ready) folder.deleteRecursively()
        }
    }

    private suspend fun downloadFile(
        build: ApkBuild,
        key: String,
        destination: File,
        expectedSize: Long,
        expectedHash: String,
        incremental: Boolean,
        fallback: Boolean,
    ) {
        val context = currentCoroutineContext()
        context.ensureActive()
        val request = Request.Builder().url(url(key)).build()
        val digest = MessageDigest.getInstance("SHA-256")
        val call = client.newCall(request)
        activeCall.set(call)
        _transfer.value = ApkTransfer.Downloading(build, 0, expectedSize, incremental, fallback)
        try {
            context.ensureActive()
            call.execute().use { response ->
                check(response.isSuccessful) { "Artifact HTTP ${response.code}" }
                val body = response.body
                check(body.contentLength().let { it == -1L || it == expectedSize })
                var received = 0L
                var lastReported = 0L
                destination.outputStream().buffered().use { output ->
                    val input = body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        context.ensureActive()
                        val length = input.read(buffer)
                        if (length == -1) break
                        received += length
                        check(received <= expectedSize)
                        digest.update(buffer, 0, length)
                        output.write(buffer, 0, length)
                        val now = System.nanoTime()
                        if (now - lastReported >= 100_000_000 || received == expectedSize) {
                            lastReported = now
                            _transfer.value = ApkTransfer.Downloading(build, received, expectedSize, incremental, fallback)
                        }
                    }
                }
                context.ensureActive()
                check(received == expectedSize && digest.digest().toHexString() == expectedHash)
            }
        } catch (e: IOException) {
            context.ensureActive()
            throw e
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(apk: File, expectedVersion: Int) {
        val info = checkNotNull(context.packageManager.getPackageArchiveInfo(apk.absolutePath, signingFlags))
        check(info.packageName == context.packageName)
        check(PackageInfoCompat.getLongVersionCode(info) == expectedVersion.toLong() && expectedVersion > installedVersion)
        val expectedSigners = installedInfo.certificates().map { it.toHexString() }.toSet()
        val actualSigners = info.certificates().map { it.toHexString() }.toSet()
        check(expectedSigners.isNotEmpty() && actualSigners == expectedSigners) { "APK signer mismatch" }
    }

    private fun requireSpace(bytes: Long) {
        if (StatFs(cache.absolutePath).availableBytes < bytes + DISK_RESERVE) throw UpdateStorageException()
    }

    private fun url(key: String) = checkNotNull(baseUrl).newBuilder().addPathSegments(key).build()

    private suspend fun hash(file: File): String {
        val context = currentCoroutineContext()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                context.ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHexString()
    }

    private class UpdateStorageException : IOException()
}

@Suppress("DEPRECATION")
private val signingFlags: Int
    get() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

@Suppress("DEPRECATION")
private fun PackageInfo.certificates(): List<ByteArray> =
    (if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures)
        ?.map { it.toByteArray() }.orEmpty()
