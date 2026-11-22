package me.rerere.rikkahub.ui.pages.examdocs

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.rikkahub.data.examdocs.DocsAccountRepository
import me.rerere.rikkahub.data.examdocs.DocsCloudError
import me.rerere.rikkahub.data.examdocs.DocsProgressRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

@Composable
fun ExamDocsAccountPage() {
    val accounts = koinInject<DocsAccountRepository>()
    val repository = koinInject<DocsProgressRepository>()
    val account by accounts.state.collectAsStateWithLifecycle()
    val syncing by repository.syncing.collectAsStateWithLifecycle()
    val syncError by repository.syncError.collectAsStateWithLifecycle()
    val storageError by repository.storageError.collectAsStateWithLifecycle()
    val user = account.user
    val pending by remember(user?.id) { user?.let { repository.pending(it.id) } ?: flowOf(0) }
        .collectAsStateWithLifecycle(initialValue = 0)
    val position by remember(user?.id) { user?.let { repository.position(it.id) } ?: flowOf(null) }
        .collectAsStateWithLifecycle(initialValue = null)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var sentTo by rememberSaveable { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: DocsCloudError) {
                error = failure.message
            } catch (_: Exception) {
                error = "操作未完成，请检查网络或设备存储后重试"
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Exam Docs 账号") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = insets + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (account.restoring) item {
                Text("正在读取账号…", style = MaterialTheme.typography.bodyMedium)
            }

            if (user != null) item {
                val lastSynced = position?.syncedAt?.let {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                }
                val status = when {
                    account.needsLogin -> "登录已过期，待同步记录保存在本机"
                    syncing == user.id -> "正在同步…"
                    syncError?.first == user.id -> syncError?.second
                    pending > 0 -> "$pending 条记录等待联网同步"
                    lastSynced != null -> "上次同步 $lastSynced"
                    else -> "等待首次同步"
                }
                CardGroup {
                    item(
                        leadingContent = { CardGroupIcon(HugeIcons.UserCircle) },
                        headlineContent = { Text(user.email) },
                        supportingContent = { status?.let { Text(it) } },
                    )
                    if (!account.needsLogin) item(
                        onClick = if (syncing == user.id) null else { { repository.requestSync(user.id, force = true) } },
                        leadingContent = { CardGroupIcon(HugeIcons.Refresh03) },
                        headlineContent = { Text("立即同步") },
                    )
                    item(
                        onClick = if (busy) null else { {
                            perform {
                                accounts.signOut()
                                code = ""
                                sentTo = null
                            }
                        } },
                        headlineContent = { Text("退出登录") },
                        supportingContent = { Text("此账号的记录仍保存在本机") },
                    )
                }
            }

            if (!account.restoring && (user == null || account.needsLogin)) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (accounts.configured) "用网页端同一个邮箱登录，继续同步答题、收藏和笔记。"
                        else "此安装包未配置 Exam Docs 云端账号，作答可保存在本机。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (accounts.configured) {
                        val address = sentTo
                        if (address == null) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("邮箱") },
                                singleLine = true,
                                enabled = !busy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                enabled = !busy && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches(),
                                onClick = {
                                    val target = email.trim()
                                    perform {
                                        accounts.sendOtp(target)
                                        sentTo = target
                                    }
                                },
                            ) { Text("发送验证码") }
                        } else {
                            Text("验证码已发送至 $address", style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it.filter { char -> char in '0'..'9' }.take(10) },
                                label = { Text("邮箱验证码") },
                                singleLine = true,
                                enabled = !busy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = {
                                    perform {
                                        accounts.verifyOtp(address, code)
                                        code = ""
                                        sentTo = null
                                    }
                                },
                                enabled = !busy && code.length >= 6,
                            ) { Text("登录") }
                            TextButton(onClick = { sentTo = null; code = ""; error = null }, enabled = !busy) {
                                Text("更换邮箱或重新发送")
                            }
                        }
                    }
                }
            }

            val message = error ?: storageError
            if (message != null) item {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
