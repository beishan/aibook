package com.aibook.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.model.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.serverUrl) {
        if (state.serverUrlInput.isBlank() && state.serverUrl.isNotBlank()) {
            viewModel.updateServerUrlInput(state.serverUrl)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        // 服务器配置
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("服务器配置", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.serverUrlInput,
                    onValueChange = viewModel::updateServerUrlInput,
                    label = { Text("服务器地址") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    singleLine = true,
                    placeholder = { Text("http://192.168.1.100:8080") }
                )
                Button(onClick = viewModel::saveServerUrl, modifier = Modifier.fillMaxWidth()) {
                    Text("保存服务器地址")
                }
            }
        }

        // 认证
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("账户", style = MaterialTheme.typography.titleLarge)

                if (state.isLoggedIn) {
                    Text("已登录：${state.username ?: "未知用户"}")
                    OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                        Text("退出登录")
                    }
                } else {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.loginFormUsername,
                        onValueChange = viewModel::updateLoginUsername,
                        label = { Text("用户名") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.loginFormPassword,
                        onValueChange = viewModel::updateLoginPassword,
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Button(
                        onClick = viewModel::login,
                        enabled = !state.isLoggingIn,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isLoggingIn) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        } else {
                            Text("登录")
                        }
                    }
                }

                state.loginMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        // 同步设置
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("同步", style = MaterialTheme.typography.titleLarge)
                Text("仅在 Wi-Fi 下下载书籍和同步进度")
                Switch(
                    checked = state.wifiOnlySync,
                    onCheckedChange = viewModel::setWifiOnlySync
                )
            }
        }

        // 阅读器偏好
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("阅读器", style = MaterialTheme.typography.titleLarge)
                Text("字号 ${(state.fontScale * 100).toInt()}%")
                Slider(
                    value = state.fontScale,
                    onValueChange = viewModel::setFontScale,
                    valueRange = 0.8f..1.6f
                )
                Text("行距 ${(state.lineHeight * 100).toInt()}%")
                Slider(
                    value = state.lineHeight,
                    onValueChange = viewModel::setLineHeight,
                    valueRange = 1.0f..2.0f
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReaderTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = state.readerTheme == theme,
                            onClick = { viewModel.setReaderTheme(theme) },
                            label = { Text(themeLabel(theme)) }
                        )
                    }
                }
            }
        }

        // 关于
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关于", style = MaterialTheme.typography.titleLarge)
                Text("汗牛充栋 · 阅读书籍管理系统")
                Text("Android 客户端 v0.1.0", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun themeLabel(theme: ReaderTheme): String = when (theme) {
    ReaderTheme.LIGHT -> "白"
    ReaderTheme.PAPER -> "纸"
    ReaderTheme.DARK -> "黑"
}
