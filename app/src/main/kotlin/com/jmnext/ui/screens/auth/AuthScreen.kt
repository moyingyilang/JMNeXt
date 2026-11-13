package com.jmnext.ui.screens.auth

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.jmnext.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jmnext.data.JmRepository
import com.jmnext.ui.LocalRepository
import com.jmnext.ui.toUserMessage
import com.jmnext.ui.components.GlassLevel
import com.jmnext.ui.components.GlassSurface
import com.jmnext.ui.components.GlassTopBar
import com.jmnext.ui.theme.JmTheme
import com.jmnext.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 三种表单形态。 */
enum class AuthMode(@StringRes val labelRes: Int) {
    Login(com.jmnext.R.string.auth_login),
    Register(com.jmnext.R.string.auth_register),
    Forgot(com.jmnext.R.string.auth_forgot),
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val submitting: Boolean = false,
    /** 失败提示（红色展示）。 */
    val error: String? = null,
    @StringRes val errorRes: Int? = null,
    /** 成功提示（用于注册/找回这类不直接进入登录态的操作）。 */
    val notice: String? = null,
    @StringRes val noticeRes: Int? = null,
    val loggedIn: Boolean = false,
)

class AuthViewModel(private val repo: JmRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setMode(mode: AuthMode) = _state.update {
        it.copy(mode = mode, error = null, notice = null)
    }

    fun login(username: String, password: String) {
        if (!validate(username.isNotBlank(), R.string.auth_need_username)) return
        if (!validate(password.isNotBlank(), R.string.auth_need_password)) return

        submit {
            repo.bootstrap()
            val info = repo.login(username.trim(), password)
            _state.update { it.copy(loggedIn = true, noticeRes = R.string.auth_welcome_fmt, notice = info.displayName) }
        }
    }

    fun register(
        username: String,
        password: String,
        passwordConfirm: String,
        email: String,
        gender: String,
    ) {
        if (!validate(username.isNotBlank(), R.string.auth_need_username)) return
        if (!validate(email.contains('@'), R.string.auth_bad_email)) return
        if (!validate(password.length >= 6, R.string.auth_short_password)) return
        // 两次输入不一致是最常见的注册失败原因，本地先拦掉，省一次往返
        if (!validate(password == passwordConfirm, R.string.auth_password_mismatch)) return

        submit {
            val result = repo.register(
                username = username.trim(),
                password = password,
                passwordConfirm = passwordConfirm,
                email = email.trim(),
                gender = gender,
            )
            if (result.isOk) {
                // 注册接口不返回凭证，成功后引导去登录而不是假装已登录
                _state.update {
                    it.copy(mode = AuthMode.Login, notice = result.msg?.takeIf { it.isNotBlank() }, noticeRes = R.string.auth_register_ok)
                }
            } else {
                _state.update { it.copy(error = result.msg?.takeIf { it.isNotBlank() }, errorRes = R.string.auth_register_failed) }
            }
        }
    }

    fun forgot(email: String) {
        if (!validate(email.contains('@'), R.string.auth_bad_email)) return
        submit {
            val result = repo.forgotPassword(email.trim())
            if (result.isOk) {
                _state.update { it.copy(mode = AuthMode.Login, notice = result.msg?.takeIf { it.isNotBlank() }, noticeRes = R.string.auth_reset_sent) }
            } else {
                _state.update { it.copy(error = result.msg?.takeIf { it.isNotBlank() }, errorRes = R.string.auth_send_failed) }
            }
        }
    }

    private fun validate(ok: Boolean, @StringRes messageRes: Int): Boolean {
        if (!ok) _state.update { it.copy(error = null, errorRes = messageRes) }
        return ok
    }

    private fun submit(block: suspend () -> Unit) {
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, error = null, notice = null) }
        viewModelScope.launch {
            val result = runCatching { block() }
            _state.update { prev ->
                // 只有**抛异常**才算这次提交失败。注册/忘记密码这两个接口的失败
                // 是「HTTP 成功但业务失败」，错误由 block 内部写进 state.error，
                // 若无条件用 exceptionOrNull() 覆盖，就会把它清回 null ——
                // 表现出来是「按钮转一下，然后什么都没有」。
                val thrown = result.exceptionOrNull()?.toUserMessage()
                prev.copy(submitting = false, error = thrown ?: prev.error)
            }
        }
    }
}

/**
 * 登录 / 注册 / 忘记密码。
 *
 * 三种形态共用一屏，用分段控件切换 —— 它们字段高度重叠，拆成三个页面会让用户在
 * 「我是不是点错了」上浪费注意力。注册成功后**回到登录态**而不是直接进入应用：
 * 注册接口不返回凭证，假装已登录会立刻在下一个请求上失败。
 */
@Composable
fun AuthScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    /** 为什么来到这一页（例如「收藏需要登录」）。由触发登录的页面传入。 */
    reason: String = "",
) {
    val repo = LocalRepository.current
    val vm: AuthViewModel = viewModel(
        factory = viewModelFactory { initializer { AuthViewModel(repo) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val c = JmTheme.colors

    // 用户名/邮箱/性别用 rememberSaveable：注册表单填到一半转屏不该被清空
    // （它们会被写进 saved instance state，页面被回收后也能恢复）。
    // **密码刻意用 remember**：saved instance state 会进 Bundle，
    // 而 Bundle 在进程被回收时可能落盘；明文口令不值得为了转屏便利付出这个代价。
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("m") }

    // 登录成功即回退到调用方（详情页/我的页），由它自行刷新
    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = stringResource(state.mode.labelRes),
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.auth_back),
                        tint = c.accent,
                    )
                }
            },
        )

        // 说清楚是为什么来的：用户点的是「收藏」，直接落到一个登录页而不给原因，
        // 就不知道那次点击是没生效、还是登录后会自动补上（目前不会，需要再点一次）
        if (reason.isNotBlank()) {
            Text(
                text = stringResource(R.string.auth_login_again_fmt, reason),
                style = MaterialTheme.typography.labelSmall,
                color = c.accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AuthMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { vm.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, AuthMode.entries.size),
                    ) {
                        Text(stringResource(mode.labelRes), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.auth_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            if (state.mode != AuthMode.Forgot) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.auth_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                )
            }

            if (state.mode == AuthMode.Register) {
                OutlinedTextField(
                    value = passwordConfirm,
                    onValueChange = { passwordConfirm = it },
                    label = { Text(stringResource(R.string.auth_confirm_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                )
            }

            if (state.mode != AuthMode.Login) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.auth_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                )
            }

            if (state.mode == AuthMode.Register) {
                Text(stringResource(R.string.auth_gender), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf("m" to stringResource(R.string.auth_male), "f" to stringResource(R.string.auth_female)).forEach { (key, label) ->
                        FilterChip(
                            selected = gender == key,
                            onClick = { gender = key },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            val errorText = state.errorRes?.let { stringResource(it) } ?: state.error
            val noticeText = state.noticeRes?.let { id ->
                if (id == R.string.auth_welcome_fmt) stringResource(id, state.notice.orEmpty()) else stringResource(id)
            } ?: state.notice
            errorText?.let {
                GlassSurface(level = GlassLevel.Card, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.error,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
            noticeText?.let {
                GlassSurface(level = GlassLevel.Card, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.accent,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            Button(
                onClick = {
                    when (state.mode) {
                        AuthMode.Login -> vm.login(username, password)
                        AuthMode.Register ->
                            vm.register(username, password, passwordConfirm, email, gender)
                        AuthMode.Forgot -> vm.forgot(email)
                    }
                },
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.submitting) stringResource(R.string.auth_wait) else stringResource(state.mode.labelRes))
            }

            Text(
                text = stringResource(R.string.auth_keystore_note),
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
            )
        }
    }
}
