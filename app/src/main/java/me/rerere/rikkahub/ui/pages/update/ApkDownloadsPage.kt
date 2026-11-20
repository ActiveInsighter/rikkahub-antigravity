package me.rerere.rikkahub.ui.pages.update

import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.update.ApkBuild
import me.rerere.rikkahub.data.update.ApkTransfer
import me.rerere.rikkahub.data.update.ApkUpdateChoice
import me.rerere.rikkahub.data.update.ApkUpdates
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.openUrl
import org.koin.compose.koinInject
import java.text.DateFormat
import java.time.Instant
import java.util.Date

private const val RELEASES_URL = "https://github.com/ActiveInsighter/rikkahub-antigravity/releases"

@Composable
internal fun ApkDownloadsPage(updates: ApkUpdates = koinInject()) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val catalog by updates.catalog.collectAsStateWithLifecycle()
    val transfer by updates.transfer.collectAsStateWithLifecycle()
    val ready = transfer as? ApkTransfer.Ready
    var requestedPermissionFor by rememberSaveable { mutableStateOf<String?>(null) }

    fun launchInstaller(intent: () -> Intent) {
        try {
            context.startActivity(intent())
        } catch (_: Exception) {
            Toast.makeText(context, R.string.apk_updates_install_failed, Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val current = updates.transfer.value as? ApkTransfer.Ready
        if (current?.file?.path == requestedPermissionFor && current != null &&
            context.packageManager.canRequestPackageInstalls()
        ) {
            launchInstaller { updates.installIntent(current) }
        }
        requestedPermissionFor = null
    }

    fun install(apk: ApkTransfer.Ready) {
        if (context.packageManager.canRequestPackageInstalls()) {
            launchInstaller { updates.installIntent(apk) }
        } else {
            requestedPermissionFor = apk.file.path
        }
    }

    LaunchedEffect(updates) { updates.refresh() }
    LaunchedEffect(ready, lifecycle) {
        if (ready != null && lifecycle == Lifecycle.State.RESUMED && updates.consumeAutoInstall(ready.file)) {
            install(ready)
        }
    }

    if (ready != null && requestedPermissionFor == ready.file.path) {
        AlertDialog(
            onDismissRequest = { requestedPermissionFor = null },
            title = { Text(stringResource(R.string.apk_updates_permission_title)) },
            text = { Text(stringResource(R.string.apk_updates_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        permissionLauncher.launch(updates.installPermissionIntent())
                    } catch (_: Exception) {
                        requestedPermissionFor = null
                        Toast.makeText(context, R.string.apk_updates_install_failed, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.apk_updates_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { requestedPermissionFor = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    val busy = transfer is ApkTransfer.Downloading || transfer is ApkTransfer.Patching ||
        transfer is ApkTransfer.Verifying
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apk_updates_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    if (updates.configured) {
                        IconButton(onClick = updates::refresh, enabled = !catalog.loading) {
                            Icon(HugeIcons.Refresh, stringResource(R.string.apk_updates_refresh))
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.apk_updates_current_version, BuildConfig.VERSION_NAME, updates.installedVersion),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (transfer != ApkTransfer.Idle) {
                item {
                    TransferCard(transfer, onCancel = updates::cancel, onInstall = ::install)
                }
            }
            if (updates.configured && catalog.loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            if (!updates.configured || catalog.failed || (!catalog.loading && catalog.builds.isEmpty())) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(when {
                                !updates.configured -> R.string.apk_updates_unconfigured
                                catalog.failed -> R.string.apk_updates_load_failed
                                else -> R.string.apk_updates_empty
                            }))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (catalog.failed) {
                                    FilledTonalButton(onClick = updates::refresh) {
                                        Text(stringResource(R.string.apk_updates_refresh))
                                    }
                                }
                                TextButton(onClick = { context.openUrl(RELEASES_URL) }) {
                                    Text(stringResource(R.string.apk_updates_open_github))
                                }
                            }
                        }
                    }
                }
            }
            items(catalog.builds, key = { it.versionCode }) { build ->
                val choice = catalog.choices[build.versionCode]
                BuildCard(
                    build = build,
                    installedVersion = updates.installedVersion,
                    latest = build == catalog.builds.firstOrNull(),
                    choice = choice,
                    enabled = !busy && ready?.build != build,
                    onDownload = { fullOnly -> if (choice != null) updates.download(choice, fullOnly) },
                )
            }
        }
    }
}

@Composable
private fun BuildCard(
    build: ApkBuild,
    installedVersion: Long,
    latest: Boolean,
    choice: ApkUpdateChoice?,
    enabled: Boolean,
    onDownload: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date.from(Instant.parse(build.publishedAt)))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.apk_updates_build, build.versionName, build.buildNumber),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Text("$date · ${build.commit.take(8)}", style = MaterialTheme.typography.bodySmall)
            val status = when {
                build.versionCode.toLong() == installedVersion -> R.string.apk_updates_installed
                build.versionCode < installedVersion -> R.string.apk_updates_older
                latest -> R.string.apk_updates_latest
                else -> null
            }
            if (status != null) {
                Text(stringResource(status), style = MaterialTheme.typography.labelMedium)
            }
            if (choice != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val fullSize = Formatter.formatShortFileSize(context, choice.full.size)
                    val patch = choice.patch
                    if (patch != null) {
                        Button(onClick = { onDownload(false) }, enabled = enabled) {
                            Text(stringResource(R.string.apk_updates_delta,
                                Formatter.formatShortFileSize(context, patch.size)))
                        }
                    }
                    FilledTonalButton(onClick = { onDownload(true) }, enabled = enabled) {
                        Text(stringResource(R.string.apk_updates_full, fullSize))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferCard(
    transfer: ApkTransfer,
    onCancel: () -> Unit,
    onInstall: (ApkTransfer.Ready) -> Unit,
) {
    val context = LocalContext.current
    val build = when (transfer) {
        ApkTransfer.Idle -> return
        is ApkTransfer.Downloading -> transfer.build
        is ApkTransfer.Patching -> transfer.build
        is ApkTransfer.Verifying -> transfer.build
        is ApkTransfer.Ready -> transfer.build
        is ApkTransfer.Failed -> transfer.build
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.apk_updates_build, build.versionName, build.buildNumber),
                style = MaterialTheme.typography.titleMediumEmphasized)
            when (transfer) {
                is ApkTransfer.Downloading -> {
                    val status = when {
                        transfer.incremental -> R.string.apk_updates_downloading_delta
                        transfer.fallback -> R.string.apk_updates_downloading_fallback
                        else -> R.string.apk_updates_downloading_full
                    }
                    Text(stringResource(status))
                    LinearProgressIndicator(
                        progress = { transfer.downloaded.toFloat() / transfer.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.apk_updates_progress,
                        Formatter.formatShortFileSize(context, transfer.downloaded),
                        Formatter.formatShortFileSize(context, transfer.total)))
                    TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
                }
                is ApkTransfer.Patching, is ApkTransfer.Verifying -> {
                    Text(stringResource(if (transfer is ApkTransfer.Patching) {
                        R.string.apk_updates_patching
                    } else {
                        R.string.apk_updates_verifying
                    }))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is ApkTransfer.Ready -> {
                    Text(stringResource(R.string.apk_updates_ready))
                    Button(onClick = { onInstall(transfer) }) { Text(stringResource(R.string.apk_updates_install)) }
                }
                is ApkTransfer.Failed -> {
                    Text(
                        stringResource(if (transfer.lowStorage) R.string.apk_updates_no_space else R.string.apk_updates_download_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                ApkTransfer.Idle -> Unit
            }
        }
    }
}
