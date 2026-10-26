package com.jmnext.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jmnext.JmApp
import com.jmnext.data.sync.SyncConfig
import com.jmnext.data.sync.SyncReport
import kotlinx.coroutines.launch

/**
 * WebDAV 同步页。
 *
 * 同步三类数据：阅读历史、收藏、屏蔽名单（关键词/标签/分类）。
 * 与 haka_comic 的交互保持一致：地址、用户名、密码（可显隐切换）、路径、自动同步开关，
 * 外加“立即同步”和一份可读的结果报告。
 *
 * 密码在本地以明文存在 KeyValueStore（见 SyncSettings 的注释），
 * 所以界面明确提示使用应用专用密码 —— 这是 Basic 认证的固有限制。
 */
@Composable
fun WebDavScreen(app: JmApp, onBack: () -> Unit) {
    val settings = remember { app.syncSettings }
    var cfg by remember { mutableStateOf(settings.load()) }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<SyncReport?>(null) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun save(next: SyncConfig) {
        cfg = next
        settings.save(next)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("WebDAV 同步", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        }

        Text(
            "同步阅读历史、收藏与屏蔽名单。多台设备指向同一个文件即可互相同步。",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = cfg.url,
            onValueChange = { save(cfg.copy(url = it.trim())) },
            label = { Text("服务器地址") },
            placeholder = { Text("https://dav.example.com/remote.php/dav/files/用户名") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = cfg.user,
            onValueChange = { save(cfg.copy(user = it)) },
            label = { Text("用户名") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = cfg.pass,
            onValueChange = { save(cfg.copy(pass = it)) },
            label = { Text("密码（建议应用专用密码）") },
            singleLine = true,
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            trailingIcon = {
                TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "隐藏" else "显示") }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = cfg.path,
            onValueChange = { save(cfg.copy(path = it.trim().ifBlank { "/JMNeXt/sync.json" })) },
            label = { Text("远端文件路径") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("开启自动同步", modifier = Modifier.padding(end = 12.dp))
            Switch(checked = cfg.enabled, onCheckedChange = { save(cfg.copy(enabled = it)) })
        }

        Text(
            "地址形如 https://dav.example.com/remote.php/dav/files/用户名（Nextcloud），" +
                "或 https://dav.jianguoyun.com/dav/（坚果云）。密码请用应用专用密码。",
            style = MaterialTheme.typography.labelSmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !busy && cfg.isUsable,
                onClick = {
                    busy = true; message = ""; report = null
                    scope.launch {
                        app.syncService().syncNow().fold(
                            onSuccess = { report = it; message = "同步完成" },
                            onFailure = { message = "同步失败：${it.message}" },
                        )
                        busy = false
                    }
                },
            ) { Text(if (busy) "同步中…" else "立即同步") }
            if (busy) CircularProgressIndicator(Modifier.padding(4.dp))
        }

        if (message.isNotEmpty()) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (report != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }

        report?.let { r ->
            Text(
                buildString {
                    appendLine("历史 ${r.historyCount} 条、收藏 ${r.favoriteCount} 条、屏蔽规则 ${r.blockedCount} 条")
                    if (r.historyConflicts + r.favoriteConflicts > 0) {
                        appendLine("冲突覆盖：历史 ${r.historyConflicts} 条、收藏 ${r.favoriteConflicts} 条（取较新一方）")
                    }
                    append(if (r.remoteExisted) "已合并远端文件并回写" else "远端文件不存在，已新建")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
