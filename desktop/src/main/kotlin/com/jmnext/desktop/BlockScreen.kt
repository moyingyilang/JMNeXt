package com.jmnext.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.data.prefs.BlockKind
import com.jmnext.data.prefs.BlockStoreApi
import com.jmnext.desktop.i18n.I18n
import com.jmnext.desktop.i18n.tl

/**
 * 屏蔽设置（2.0.0 桌面端）。
 *
 * 数据层（BlockStore）在两端共用，这里只是界面：三类名单的增删。
 *
 * 过滤**不在界面做** —— 列表出口统一在仓储层过滤（PagedList.hidden 带出被挡条数），
 * 界面只负责显示"有 N 条被挡掉"。这一条是从 Android 端照抄的约定，
 * 各页自己过滤迟早会漏掉一处，而漏掉的表现是"屏蔽没生效"，很难发现。
 */
@Composable
fun BlockScreen(repository: JmRepository, modifier: Modifier = Modifier) {
    val store = repository.blockStore
    if (store == null) {
        Column(modifier.fillMaxSize().padding(24.dp)) {
            Text(tl("屏蔽设置"), style = MaterialTheme.typography.titleLarge)
            Text(tl("本地屏蔽存储未初始化。"), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val rules by store.state.collectAsState()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(tl("屏蔽设置"), style = MaterialTheme.typography.titleLarge)
        Text(
            tl("命中的作品会从所有列表里隐藏（首页、搜索、分类、周刊、随机、收藏…）。" +
                "被挡掉的条数会在对应页面顶部提示，不会静默消失。"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )

        BlockSection(
            title = tl("关键词"),
            hint = tl("例如：NTR。命中标题、作者、简介里的任意一处即隐藏。"),
            values = rules.words,
            onAdd = { store.addWord(it) },
            onRemove = { store.removeWord(it) },
        )
        BlockSection(
            title = tl("标签"),
            hint = tl("命中作品标签即隐藏。标签来自作品详情，列表接口不下发，因此会多一次请求。"),
            values = rules.tags,
            onAdd = { store.addTag(it) },
            onRemove = { store.removeTag(it) },
        )
        BlockSection(
            title = tl("分类名"),
            hint = tl("例如：同人。命中分区或分类名即隐藏。"),
            values = rules.categories,
            onAdd = { store.addCategory(it) },
            onRemove = { store.removeCategory(it) },
        )
    }
}

@Composable
private fun BlockSection(
    title: String,
    hint: String,
    values: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(I18n.trf("%1\$s（%2\$d）", title, values.size), style = MaterialTheme.typography.titleMedium)
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(I18n.trf("添加%1\$s", title)) },
                singleLine = true,
                modifier = Modifier.width(320.dp),
            )
            Button(
                enabled = input.isNotBlank(),
                onClick = {
                    val v = input.trim()
                    // 已存在就不重复添加：BlockStore 的名单是 Set，重复与否用户看不出来，
                    // 但「已经在名单里」这个提示是有用的
                    if (v.isNotEmpty()) {
                        onAdd(v)
                        System.err.println("[屏蔽] 添加$title：$v")
                        input = ""
                    }
                },
            ) { Text(tl("添加")) }
        }

        if (values.isEmpty()) {
            Text(
                tl("（空）"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                values.sorted().forEach { v ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(320.dp))
                        TextButton(onClick = {
                            onRemove(v)
                            System.err.println("[屏蔽] 移除$title：$v")
                        }) { Text(tl("移除")) }
                    }
                }
            }
        }
    }
}

/** 界面用不到 isBlocked 的提示场景先留个空实现位置，避免将来忘记它的存在。 */
@Suppress("unused")
private fun BlockStoreApi.alreadyBlocked(type: BlockKind, value: String): Boolean = isBlocked(type, value)
