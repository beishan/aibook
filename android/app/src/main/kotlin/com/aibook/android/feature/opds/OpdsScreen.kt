package com.aibook.android.feature.opds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.network.opds.OpdsEntry
import com.aibook.android.core.network.opds.OpdsLink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsScreen(
    viewModel: OpdsViewModel = viewModel(factory = OpdsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OPDS 书库", style = MaterialTheme.typography.headlineMedium)
            if (state.currentFeed != null) {
                TextButton(onClick = { viewModel.navigateBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    Text("上一级")
                }
            }
        }

        if (state.showConnectionForm) {
            ConnectionForm(state, viewModel)
        } else if (state.currentFeed != null) {
            CatalogBrowser(state, viewModel)
        } else {
            ConnectionList(state, viewModel)
        }

        SnackbarHost(snackbarHostState)
    }
}

@Composable
private fun ConnectionForm(
    state: OpdsUiState,
    viewModel: OpdsViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.formName,
            onValueChange = { viewModel.updateFormField("name", it) },
            label = { Text("连接名称") },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.formBaseUrl,
            onValueChange = { viewModel.updateFormField("baseUrl", it) },
            label = { Text("OPDS 地址") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            singleLine = true,
            placeholder = { Text("http://192.168.1.100:8080/opds/") }
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.formUsername,
            onValueChange = { viewModel.updateFormField("username", it) },
            label = { Text("用户名（可选）") },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.formPassword,
            onValueChange = { viewModel.updateFormField("password", it) },
            label = { Text("密码（可选）") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.saveConnection() }) { Text("保存并连接") }
            TextButton(onClick = { viewModel.showConnectionForm(false) }) { Text("取消") }
        }
    }
}

@Composable
private fun ConnectionList(
    state: OpdsUiState,
    viewModel: OpdsViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Button(onClick = { viewModel.showConnectionForm(true) }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("添加连接")
        }
    }

    if (state.isLoading) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.connections.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("还没有 OPDS 连接", style = MaterialTheme.typography.titleMedium)
                        Text("点击「添加连接」配置你的汗牛充栋服务器 OPDS 地址。")
                    }
                }
            }
        }

        items(state.connections, key = { it.id }) { connection ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.selectConnection(connection) }
            ) {
                Row(
                    modifier = Modifier.padding(18.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(connection.name, style = MaterialTheme.typography.titleLarge)
                        Text(connection.baseUrl, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (connection.username == null) "未配置认证" else "已配置 Basic Auth",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = { viewModel.deleteConnection(connection.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogBrowser(
    state: OpdsUiState,
    viewModel: OpdsViewModel
) {
    val feed = state.currentFeed ?: return

    if (state.isLoading) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
    }

    Text(feed.title, style = MaterialTheme.typography.titleLarge)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(feed.entries, key = { it.title }) { entry ->
            OpdsEntryCard(entry)
        }
    }
}

@Composable
private fun OpdsEntryCard(entry: OpdsEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            entry.author?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            entry.summary?.let {
                Text(
                    it.take(200) + if (it.length > 200) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3
                )
            }
            entry.acquisitionLink?.let { link ->
                Text("可下载：${link.type ?: "未知格式"}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
