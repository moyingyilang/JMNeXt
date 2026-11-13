package com.jmnext.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 「我的」（2.0.0 桌面端）：账号信息与每日签到。
 *
 * 关于签到有两条必须写清的约定：
 *  1. **只在用户按下按钮时调用** `dailyCheck`（它是对服务端的写操作），
 *     页面加载时只读 `daily`，绝不自动签到。
 *  2. 开发期间我**没有**在自己的验证里调用这个写接口 —— 用的是用户账号，
 *     所以"签到成功"这条路径的真实结果**未经我验证**，只验证过读取与显示。
 *     界面上如实显示服务端返回的 `msg`（例如"已經簽到過了"），不做本地猜测。
 *
 * 主题切换、悬浮栏、壁纸等桌面端不适用的设置项没有搬过来 —— 那些是 Android 的形态。
 */
@Composable
fun ProfileScreen(repository: JmRepository, onGoLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    val authState by repository.auth.state.collectAsState()

    var dailyId by remember { mutableStateOf<String?>(null) }
    var eventName by remember { mutableStateOf<String?>(null) }
    var checkMsg by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // 只读：拿签到活动的 id 与名称，供按钮使用。不在这里签到。
    LaunchedEffect(authState.loggedIn, authState.member?.uid) {
        val uid = authState.member?.uid
        if (!authState.loggedIn || uid.isNullOrBlank()) {
            status = "登录后可查看账号信息与签到"
            return@LaunchedEffect
        }
        runCatching { repository.daily(uid) }
            .onSuccess {
                dailyId = it.dailyId
                eventName = it.eventName
                status = "签到活动：${it.eventName ?: "(未命名)"}"
                System.err.println("[我的] 读取签到活动成功（未签到）")
            }
            .onFailure {
                if (it is CancellationException) return@onFailure
                status = "签到活动读取失败：${it.message}"
                System.err.println("[我的] $status")
            }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(tl("我的"), style = MaterialTheme.typography.titleLarge)

        Card(tl("账号")) {
            val member = authState.member
            when {
                !authState.loggedIn -> {
                    Text(tl("未登录"), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onGoLogin, modifier = Modifier.padding(top = 6.dp)) { Text(tl("去登录")) }
                }
                member == null -> Text(tl("已登录（会员信息未取到）"), style = MaterialTheme.typography.bodyMedium)
                else -> {
                    InfoRow(tl("用户名"), member.username ?: tl("(无)"))
                    InfoRow("UID", member.uid ?: tl("(无)"))
                    InfoRow(tl("等级"), "${member.level} ${member.levelName ?: ""}")
                    member.coin?.let { InfoRow(tl("金币"), it) }
                    InfoRow(tl("免广告特权"), if (member.adFree) tl("已开通") else tl("未开通"))
                }
            }
        }

        Card(tl("每日签到")) {
StatusLine(status, busy)
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    enabled = !busy && authState.loggedIn && !dailyId.isNullOrBlank(),
                    onClick = {
                        val uid = authState.member?.uid ?: return@Button
                        val id = dailyId ?: return@Button
                        busy = true
                        checkMsg = null
                        scope.launch {
                            runCatching { repository.dailyCheck(uid = uid, dailyId = id) }
                                .onSuccess {
                                    // 服务端的 msg 就是权威结果（例如"已經簽到過了"），不做本地推断
                                    checkMsg = it.msg ?: if (it.code == 200) "签到成功" else "签到返回 code=${it.code}"
                                    System.err.println("[我的] 签到返回：$checkMsg")
                                }
                                .onFailure {
                                    if (it is CancellationException) return@onFailure
                                    checkMsg = "签到失败：${it.message}"
                                    System.err.println("[我的] $checkMsg")
                                }
                            busy = false
                        }
                    },
                ) { Text(if (busy) tl("提交中…") else tl("签到")) }
                Text(
                    eventName ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            checkMsg?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        Card(tl("说明")) {
            Text(
                tl("桌面端与 Android 端共用同一份数据层；界面按桌面形态重做（左侧常驻导航、" +
                    "阅读页纵向连续滚动）。Android 特有的设置项（悬浮底栏、壁纸、动效性格）在桌面上没有对应物，未搬过来。"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) { content() }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
