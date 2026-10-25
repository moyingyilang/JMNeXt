package com.jmnext.uiweb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.coroutines.launch

/** 页面栈。Android 端是 NavHost，这里用最小状态机，行为等价。 */
private sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data object Favorites : Screen
    data object Settings : Screen
    data class Detail(val id: String) : Screen
    data class Reader(val chapterId: String) : Screen
}

@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var screen by remember { mutableStateOf<Screen>(Screen.Login) }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val s = screen) {
                Screen.Login -> LoginScreen(onDone = { screen = Screen.Home })
                Screen.Home -> HomeScreen(
                    onOpen = { screen = Screen.Detail(it) },
                    onFavorites = { screen = Screen.Favorites },
                    onSettings = { screen = Screen.Settings },
                )
                Screen.Favorites -> FavoritesScreen(
                    onOpen = { screen = Screen.Detail(it) },
                    onBack = { screen = Screen.Home },
                )
                Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
                is Screen.Detail -> DetailScreen(
                    id = s.id,
                    onRead = { screen = Screen.Reader(it) },
                    onBack = { screen = Screen.Home },
                )
                is Screen.Reader -> ReaderScreen(chapterId = s.chapterId, onBack = { screen = Screen.Home })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Shell(title: String, onBack: (() -> Unit)? = null, onFavorites: (() -> Unit)? = null, onSettings: (() -> Unit)? = null, body: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                if (onBack != null) Text("返回", Modifier.padding(12.dp).clickable { onBack() })
            },
            actions = {
                if (onFavorites != null) Text("收藏", Modifier.padding(12.dp).clickable { onFavorites() })
                if (onSettings != null) Text("设置", Modifier.padding(12.dp).clickable { onSettings() })
            },
        )
    }) { pad -> Box(Modifier.padding(pad).fillMaxSize()) { body() } }
}

@Composable
private fun LoginScreen(onDone: () -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("JMNeXt", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Compose / wasm 版 · 与 Android、桌面端同一 API", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(user, { user = it }, label = { Text("用户名") }, singleLine = true)
        OutlinedTextField(pass, { pass = it }, label = { Text("密码") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = !busy && user.isNotBlank() && pass.isNotBlank(),
                onClick = {
                    busy = true; msg = ""
                    scope.launch {
                        Api.login(user.trim(), pass).fold(
                            onSuccess = { onDone() },
                            onFailure = { msg = "登录失败：${it.message}" },
                        )
                        busy = false
                    }
                },
            ) { Text(if (busy) "登录中…" else "登录") }
            if (busy) CircularProgressIndicator(Modifier.padding(4.dp))
        }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun HomeScreen(onOpen: (String) -> Unit, onFavorites: () -> Unit, onSettings: () -> Unit) {
    var list by remember { mutableStateOf<List<Api.Album>>(emptyList()) }
    var err by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Api.home().fold(
            onSuccess = { list = it; if (it.isEmpty()) err = "首页为空" },
            onFailure = { err = "加载失败：${it.message}" },
        )
        loading = false
    }

    Shell("首页", onFavorites = onFavorites, onSettings = onSettings) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            if (loading) CircularProgressIndicator()
            if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { a ->
                    Surface2 { Text(a.title, Modifier.fillMaxWidth().clickable { onOpen(a.id) }) }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(id: String, onRead: (String) -> Unit, onBack: () -> Unit) {
    var d by remember { mutableStateOf<Api.Detail?>(null) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        Api.album(id).fold(onSuccess = { d = it }, onFailure = { err = "加载失败：${it.message}" })
    }

    Shell("详情", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
            val cur = d
            if (cur == null) { CircularProgressIndicator(); return@Column }
            Text(cur.title, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        Api.toggleFavorite(cur.id).onSuccess { fav -> d = cur.copy(favorited = fav) }
                    }
                }) { Text(if (cur.favorited) "取消收藏" else "收藏") }
                Text("共 ${cur.chapters.size} 章", Modifier.padding(top = 12.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cur.chapters) { c ->
                    Surface2 { Text(c.title, Modifier.fillMaxWidth().clickable { onRead(c.id) }) }
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(chapterId: String, onBack: () -> Unit) {
    Shell("阅读", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 图片走 /img 同源代理，服务端已做反切片与缓存；这里先渲染前若干页占位，
            // 等确认渲染链路后再接 LazyColumn + 进度回传。
            Text("章节 $chapterId")
            Text("图片渲染与翻页在下一步接入（/img/$chapterId/0 …）", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FavoritesScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    var list by remember { mutableStateOf<List<Api.Album>>(emptyList()) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        Api.favorites().fold(onSuccess = { list = it }, onFailure = { err = "加载失败：${it.message}" })
    }
    Shell("收藏", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
            if (list.isEmpty() && err.isEmpty()) Text("还没有收藏")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list) { a -> Surface2 { Text(a.title, Modifier.fillMaxWidth().clickable { onOpen(a.id) }) } }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    Shell("设置", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("主题、阅读方向、字号等设置项在下一步接入 /api/settings")
        }
    }
}

@Composable
private fun Surface2(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) { Box(Modifier.padding(12.dp)) { content() } }
}

fun main() {
    ComposeViewport { App() }
}
