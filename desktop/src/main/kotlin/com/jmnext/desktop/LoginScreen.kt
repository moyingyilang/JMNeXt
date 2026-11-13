package com.jmnext.desktop

import kotlinx.coroutines.CancellationException

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.launch

/**
 * 登录 / 注册 / 找回密码（桌面端）。
 *
 * 补的是功能对齐审计发现的缺口：桌面端此前**只有登录**，而 Android 有注册与找回密码
 * （AuthScreen 的三种模式）。字段、校验与取值照 Android 抄：
 *  - 邮箱必须含 @（Android 的 validate 就是这么判的）；
 *  - 注册要 用户名 / 密码 / 确认密码 / 邮箱 / 性别（gender 取 "m" 或 "f"）；
 *  - 找回密码只发邮箱，成功后服务端发重置邮件。
 *
 * 失败时直接显示服务端原文 —— 官方接口对失败原因的措辞比"操作失败"更能说明问题。
 */
@Composable
fun LoginScreen(repository: JmRepository, onDone: () -> Unit) {
    var mode by remember { mutableStateOf("login") }   // login / register / forgot
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("m") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when (mode) {
                "register" -> tl("注册")
                "forgot" -> tl("找回密码")
                else -> tl("登录")
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            tl("登录后才能使用收藏、追更与评论。"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { mode = "login"; message = null }) { Text((if (mode == "login") "· " else "") + tl("登录")) }
            TextButton(onClick = { mode = "register"; message = null }) { Text((if (mode == "register") "· " else "") + tl("注册")) }
            TextButton(onClick = { mode = "forgot"; message = null }) { Text((if (mode == "forgot") "· " else "") + tl("找回密码")) }
        }

        if (mode != "forgot") {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(tl("用户名")) },
                singleLine = true,
                modifier = Modifier.width(360.dp),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(tl("密码")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(360.dp),
            )
        }

        if (mode == "register") {
            OutlinedTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                label = { Text(tl("确认密码")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(360.dp),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(tl("邮箱")) },
                singleLine = true,
                modifier = Modifier.width(360.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tl("性别："), style = MaterialTheme.typography.labelMedium)
                listOf("m" to tl("男"), "f" to tl("女")).forEach { (key, label) ->
                    TextButton(onClick = { gender = key }) { Text((if (gender == key) "· " else "") + label) }
                }
            }
        }

        if (mode == "forgot") {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(tl("注册邮箱")) },
                singleLine = true,
                modifier = Modifier.width(360.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (mode) {
                "register" -> Button(
                    enabled = !busy && username.isNotBlank() && password.isNotBlank() && email.isNotBlank(),
                    onClick = {
                        if (!email.contains('@')) {
                            message = "请输入有效的邮箱"
                        } else if (password != passwordConfirm) {
                            message = "两次输入的密码不一致"
                        } else {
                            busy = true
                            message = null
                            scope.launch {
                                runCatching {
                                    repository.register(
                                        username = username.trim(),
                                        password = password,
                                        passwordConfirm = passwordConfirm,
                                        email = email.trim(),
                                        gender = gender,
                                    )
                                }
                                    .onSuccess { r ->
                                        message = r.msg ?: "注册请求已提交"
                                        Log.line("登录", "注册结果：" + message + "（isOk=" + r.isOk + "）")
                                        if (r.isOk) {
                                            mode = "login"
                                            password = ""
                                            passwordConfirm = ""
                                        }
                                    }
                                    .onFailure {
                                        if (it is CancellationException) return@onFailure
                                        message = "注册失败：${it.message}"
                                        Log.error("登录", "注册失败", it)
                                    }
                                busy = false
                            }
                        }
                    },
                ) { Text(if (busy) tl("提交中…") else tl("注册")) }

                "forgot" -> Button(
                    enabled = !busy && email.isNotBlank(),
                    onClick = {
                        if (!email.contains('@')) {
                            message = "请输入有效的邮箱"
                        } else {
                            busy = true
                            message = null
                            scope.launch {
                                runCatching { repository.forgotPassword(email.trim()) }
                                    .onSuccess { r ->
                                        message = r.msg ?: "重置邮件已发送，请查收"
                                        Log.line("登录", "找回密码结果：" + message + "（isOk=" + r.isOk + "）")
                                    }
                                    .onFailure {
                                        if (it is CancellationException) return@onFailure
                                        message = "找回密码失败：${it.message}"
                                        Log.error("登录", "找回密码失败", it)
                                    }
                                busy = false
                            }
                        }
                    },
                ) { Text(if (busy) tl("提交中…") else tl("发送重置邮件")) }

                else -> Button(
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            runCatching { repository.login(username.trim(), password) }
                                .onSuccess { info ->
                                    message = "欢迎回来，${info.username ?: info.uid ?: ""}"
                                    Log.line("登录", "成功：${info.username} uid=${info.uid}")
                                    password = ""
                                    onDone()
                                }
                                .onFailure {
                                    if (it is CancellationException) return@onFailure
                                    message = "登录失败：${it.message}"
                                    Log.error("登录", "失败", it)
                                }
                            busy = false
                        }
                    },
                ) { Text(if (busy) tl("登录中…") else tl("登录")) }
            }

            if (repository.auth.isLoggedIn) {
                TextButton(onClick = {
                    scope.launch {
                        repository.logout()
                        Log.line("登录", "已退出")
                        message = "已退出登录"
                    }
                }) { Text(tl("退出登录")) }
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
