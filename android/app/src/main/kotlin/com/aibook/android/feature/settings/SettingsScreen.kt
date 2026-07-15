package com.aibook.android.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.data.repository.CacheCleanupWorker
import com.aibook.android.di.ServiceLocator
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SoftCard
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SettingsScreen(
    onThemeClick: () -> Unit = {},
    onScanDirectoriesClick: () -> Unit = {},
    onSyncConnectionClick: () -> Unit = {},
    onStorageClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember { appVersionName(context) }

    LaunchedEffect(state.serverUrl) {
        if (state.serverUrlInput.isBlank() && state.serverUrl.isNotBlank()) {
            viewModel.updateServerUrlInput(state.serverUrl)
        }
    }

    DesignPage(
        title = "设置",
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionLabel("阅读与外观")
            SoftCard {
                SettingsLine(
                    Icons.Default.ColorLens,
                    "页面主题",
                    "${appThemeModeLabel(state.appThemeMode)} · ${readerThemeLabel(state.readerTheme)}",
                    onClick = onThemeClick
                )
                SettingsLine(
                    Icons.Default.FormatSize,
                    "字体与排版",
                    "字号 ${"%.0f".format(state.fontScale * 100)}% · 行距 ${"%.2f".format(state.lineHeight)}",
                    showDivider = false,
                    onClick = onThemeClick
                )
            }

            SectionLabel("书库与扫描")
            SoftCard {
                SettingsLine(
                    Icons.Default.Menu,
                    "扫描目录管理",
                    "管理本地书籍扫描规则与目录",
                    showDivider = false,
                    onClick = onScanDirectoriesClick
                )
            }

            SectionLabel("同步与连接")
            SoftCard {
                SettingsLine(
                    Icons.Default.CloudSync,
                    "服务器与同步",
                    SettingsSummary.connectionSubtitle(
                        serverUrl = state.serverUrl,
                        isLoggedIn = state.isLoggedIn,
                        username = state.username
                    ),
                    onClick = onSyncConnectionClick
                )
                SettingsLine(
                    Icons.Default.Storage,
                    "存储与缓存",
                    "管理本机缓存、导入书籍与下载文件",
                    showDivider = false,
                    onClick = onStorageClick
                )
            }

            SectionLabel("隐私与安全")
            SettingsRowCard(
                icon = { Icon(Icons.Default.Lock, null, tint = DesignTokens.Accent) },
                title = "隐私与权限",
                subtitle = SettingsSummary.privacySubtitle(
                    personalizedRecommendations = state.personalizedRecommendations,
                    usageStatistics = state.usageStatistics
                ),
                onClick = onPrivacyClick
            )

            SectionLabel("关于")
            SettingsRowCard(
                icon = { AboutAppIcon() },
                title = "关于",
                subtitle = "版本信息、用户协议与帮助",
                trailing = "版本 $versionName",
                onClick = onAboutClick
            )
        }
    }
}

private fun readerThemeLabel(theme: com.aibook.android.core.model.ReaderTheme): String {
    return when (theme) {
        com.aibook.android.core.model.ReaderTheme.LIGHT -> "明亮"
        com.aibook.android.core.model.ReaderTheme.PAPER -> "纸张"
        com.aibook.android.core.model.ReaderTheme.GREEN -> "护眼"
        com.aibook.android.core.model.ReaderTheme.GRAY -> "灰色"
        com.aibook.android.core.model.ReaderTheme.DARK -> "深色"
    }
}

private fun appThemeModeLabel(mode: com.aibook.android.core.model.AppThemeMode): String {
    return when (mode) {
        com.aibook.android.core.model.AppThemeMode.SYSTEM -> "跟随系统"
        com.aibook.android.core.model.AppThemeMode.LIGHT -> "浅色"
        com.aibook.android.core.model.AppThemeMode.DARK -> "深色"
    }
}

@Composable
private fun AboutAppIcon() {
    Box(
        modifier = Modifier
            .size(54.dp)
            .background(Color(0xFFFFF1E2), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "书架",
            color = DesignTokens.Accent,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun SettingsRowCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: String? = null,
    onClick: () -> Unit = {}
) {
    SoftCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    showDivider: Boolean = true,
    onClick: () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = DesignTokens.Accent)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showDivider) {
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    }
}

@Composable
fun StorageCacheScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ServiceLocator.get(context.applicationContext).bookRepository }
    val books by remember { repository.observeBooks() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val prefs = remember { context.getSharedPreferences("storage_settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var showDownloads by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(false) }
    var showAutoClean by remember { mutableStateOf(false) }
    var pendingDownloadDelete by remember { mutableStateOf<LocalBook?>(null) }
    val snapshot by produceState<StorageSnapshot?>(null, books, refresh) {
        value = withContext(Dispatchers.IO) { storageSnapshot(context, books) }
    }
    val autoCleanDays = prefs.getInt("auto_clean_days", 30)
    val downloadLocation = prefs.getString("download_location", "internal") ?: "internal"

    LaunchedEffect(autoCleanDays) {
        CacheCleanupWorker.configure(context, autoCleanDays)
    }

    SettingsSubPage(title = "存储与缓存", onBack = onBack) {
        message?.let { SoftCard(color = Color.White) { Text(it, color = DesignTokens.SoftText) } }
        StorageUsageCard(snapshot)
        SectionLabel("清理与管理")
        SoftCard {
            DetailLine(Icons.Default.Book, "缓存数据", "阅读解析与临时文件", trailing = bytesLabel(snapshot?.cacheBytes))
            DetailLine(Icons.Default.Folder, "书籍文件", "应用管理的本地书籍", trailing = "${snapshot?.bookCount ?: books.size} 本")
            DetailLine(Icons.Default.Download, "下载文件", "从 OPDS 与书城下载的文件", trailing = bytesLabel(snapshot?.downloadBytes), showDivider = false)
        }
        SectionLabel("存储设置")
        SoftCard {
            DetailLine(
                Icons.Default.Delete, "清理缓存", "删除可重新生成的解析与临时文件",
                trailing = bytesLabel(snapshot?.cacheBytes), actionText = "立即清理",
                onClick = {
                    scope.launch {
                        val cleared = withContext(Dispatchers.IO) { clearAppCache(context) }
                        message = "已清理 ${bytesLabel(cleared)} 缓存"
                        refresh++
                    }
                }
            )
            DetailLine(Icons.Default.Storage, "管理下载", "查看并删除应用管理的书籍文件", onClick = { showDownloads = true })
            DetailLine(
                Icons.Default.Folder, "下载位置",
                if (downloadLocation == "external") "外部应用目录" else "内部应用目录",
                onClick = { showLocation = true }
            )
            DetailLine(
                Icons.Default.AutoDelete, "自动清理缓存", "后台每日检查并清理超过期限的缓存",
                trailing = if (autoCleanDays == 0) "已关闭" else "$autoCleanDays 天",
                showDivider = false,
                onClick = { showAutoClean = true }
            )
        }
        Button(
            onClick = {
                scope.launch {
                    val cleared = withContext(Dispatchers.IO) { clearAppCache(context) }
                    message = "清理完成，释放 ${bytesLabel(cleared)}"
                    refresh++
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Delete, null)
            Text("清理缓存并优化空间", fontWeight = FontWeight.Bold)
        }
    }

    if (showDownloads) {
        AlertDialog(
            onDismissRequest = { showDownloads = false },
            title = { Text("管理书籍文件", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).height(360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (books.isEmpty()) Text("暂无书籍文件", color = DesignTokens.SoftText)
                    books.forEach { book ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(bytesLabel(File(book.uri).takeIf(File::isFile)?.length()), color = DesignTokens.SoftText)
                            }
                            TextButton(onClick = { showDownloads = false; pendingDownloadDelete = book }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDownloads = false }) { Text("完成") } }
        )
    }
    if (showLocation) {
        ChoiceDialog(
            title = "下载位置",
            choices = listOf("internal" to "内部应用目录", "external" to "外部应用目录"),
            selected = downloadLocation,
            onDismiss = { showLocation = false },
            onSelect = { value ->
                prefs.edit().putString("download_location", value).apply()
                message = "后续下载将保存到${if (value == "external") "外部" else "内部"}应用目录"
                showLocation = false
                refresh++
            }
        )
    }
    pendingDownloadDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDownloadDelete = null },
            title = { Text("永久删除《${book.title}》？", fontWeight = FontWeight.Bold) },
            text = { Text("将同时删除书籍记录、阅读数据与本地文件，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDownloadDelete = null
                    scope.launch {
                        repository.deleteBook(book.id)
                        message = "已删除《${book.title}》"
                        refresh++
                    }
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDownloadDelete = null }) { Text("取消") } }
        )
    }
    if (showAutoClean) {
        ChoiceDialog(
            title = "自动清理缓存",
            choices = listOf(0 to "关闭", 7 to "7 天", 30 to "30 天", 90 to "90 天"),
            selected = autoCleanDays,
            onDismiss = { showAutoClean = false },
            onSelect = { days ->
                prefs.edit().putInt("auto_clean_days", days).apply()
                CacheCleanupWorker.configure(context, days)
                showAutoClean = false
                refresh++
            }
        )
    }
}

@Composable
fun SyncConnectionSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.serverUrl) {
        if (state.serverUrlInput.isBlank() && state.serverUrl.isNotBlank()) {
            viewModel.updateServerUrlInput(state.serverUrl)
        }
    }

    SettingsSubPage(title = "服务器与同步", subtitle = "配置私有书库服务器、登录状态与同步规则", onBack = onBack) {
        SoftCard {
            DetailLine(
                Icons.Default.CloudSync,
                "连接状态",
                SettingsSummary.connectionSubtitle(
                    serverUrl = state.serverUrl,
                    isLoggedIn = state.isLoggedIn,
                    username = state.username
                ),
                trailing = if (state.serverUrl.isBlank()) "未配置" else "已配置",
                showDivider = false
            )
        }

        SectionLabel("服务器")
        SoftCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.serverUrlInput,
                    onValueChange = viewModel::updateServerUrlInput,
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://192.168.1.10:8080") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Button(
                    onClick = viewModel::saveServerUrl,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("保存服务器地址", fontWeight = FontWeight.Bold)
                }
            }
        }

        SectionLabel("账号")
        SoftCard {
            if (state.isLoggedIn) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailLine(
                        Icons.Default.Security,
                        "当前账号",
                        state.username ?: "当前用户",
                        trailing = "已登录",
                        showDivider = false
                    )
                    Button(
                        onClick = viewModel::logout,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("退出登录", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.loginFormUsername,
                        onValueChange = viewModel::updateLoginUsername,
                        label = { Text("用户名") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.loginFormPassword,
                        onValueChange = viewModel::updateLoginPassword,
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    state.loginMessage?.let {
                        Text(it, color = DesignTokens.SoftText)
                    }
                    Button(
                        onClick = viewModel::login,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !state.isLoggingIn,
                        colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (state.isLoggingIn) "登录中..." else "登录服务器", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SectionLabel("同步规则")
        SoftCard {
            SwitchLine(
                Icons.Default.Wifi,
                "仅在 Wi-Fi 下同步",
                "避免移动网络下载大文件或刷新 OPDS 缓存",
                checked = state.wifiOnlySync,
                onCheckedChange = viewModel::setWifiOnlySync,
                showDivider = false
            )
        }
    }
}

@Composable
fun PrivacyPermissionsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context.applicationContext) }
    val books by remember { locator.bookRepository.observeBooks() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val folders by remember { locator.bookRepository.observeShelfFolders() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var pendingExport by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showDeleteData by remember { mutableStateOf(false) }
    val notificationGranted = permissionRefresh.let {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefresh++
        message = if (granted) "通知权限已允许" else "通知权限未允许，可前往系统设置修改"
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExport) } }
                .onSuccess { message = "数据已导出" }
                .onFailure { message = "导出失败：${it.message ?: "未知错误"}" }
        }
    }

    SettingsSubPage(title = "隐私与权限", subtitle = "管理应用权限与隐私选项", onBack = onBack) {
        message?.let { SoftCard(color = Color.White) { Text(it, color = DesignTokens.SoftText) } }
        SoftCard(color = DesignTokens.WarmCard) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = DesignTokens.Accent, modifier = Modifier.size(42.dp))
                Column(Modifier.weight(1f)) {
                    Text("我们尊重并保护您的隐私", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("仅在提供服务时使用必要权限，不收集与阅读无关的个人信息。", color = DesignTokens.SoftText)
                }
                Icon(Icons.Default.ChevronRight, null, tint = DesignTokens.SoftText)
            }
        }
        SectionLabel("权限管理")
        SoftCard {
            PermissionLine(
                Icons.Default.Folder, "文件访问", "使用系统文件选择器，应用不会读取未选择的文件",
                status = "按需授权",
                onClick = { openAppSettings(context) }
            )
            PermissionLine(
                Icons.Default.Notifications, "通知权限", "用于书籍更新与后台任务提醒",
                status = if (notificationGranted) "已允许" else "未允许",
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openAppSettings(context)
                }
            )
            PermissionLine(
                Icons.Default.Wifi, "网络权限", "Android 安装时授予的普通权限",
                status = "已允许", showDivider = false,
                onClick = { openAppSettings(context) }
            )
        }
        SectionLabel("数据与隐私")
        SoftCard {
            SwitchLine(
                Icons.Default.PieChart,
                "个性化推荐",
                "基于您的阅读偏好，为您推荐更合适的书籍",
                checked = state.personalizedRecommendations,
                onCheckedChange = viewModel::setPersonalizedRecommendations
            )
            SwitchLine(
                Icons.Default.PieChart,
                "使用数据统计",
                "帮助我们优化产品体验（不包含个人信息）",
                checked = state.usageStatistics,
                showDivider = false,
                onCheckedChange = viewModel::setUsageStatistics
            )
        }
        SectionLabel("隐私选项")
        SoftCard {
            DetailLine(Icons.Default.Lock, "隐私政策", "查看本应用的数据处理说明", onClick = { showPrivacyPolicy = true })
            DetailLine(
                Icons.Default.Download, "导出个人数据", "导出书籍元数据、标签、评分和阅读进度",
                onClick = {
                    pendingExport = exportLibraryJson(books, folders.map { it.id to it.name })
                    exportLauncher.launch("汗牛充栋-数据导出.json")
                }
            )
            DetailLine(
                Icons.Default.Delete, "删除账号与本机数据", "清除登录信息、书籍、封面和阅读数据",
                showDivider = false,
                onClick = { showDeleteData = true }
            )
        }
        Text(
            "阅读，让知识更有温度",
            modifier = Modifier.fillMaxWidth(),
            color = DesignTokens.SoftText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }

    if (showPrivacyPolicy) {
        InfoDialog(
            title = "隐私政策",
            text = PRIVACY_POLICY,
            onDismiss = { showPrivacyPolicy = false }
        )
    }
    if (showDeleteData) {
        AlertDialog(
            onDismissRequest = { showDeleteData = false },
            title = { Text("删除全部本机数据？", fontWeight = FontWeight.Bold) },
            text = { Text("此操作不可撤销，将删除本机书籍文件、阅读进度、标签、评分、封面及登录信息。服务器端数据不会被自动删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteData = false
                    scope.launch {
                        locator.bookRepository.deleteAllLibraryData()
                        locator.serverRepository.logout()
                        message = "本机账号与书库数据已删除"
                    }
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteData = false }) { Text("取消") } }
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val versionName = remember { appVersionName(context) }
    var updateStatus by remember { mutableStateOf("点击检查 GitHub Releases") }
    var checkingUpdate by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogText by remember { mutableStateOf("") }

    SettingsSubPage(title = "关于", onBack = onBack) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline)
        ) {
            Row(
                modifier = Modifier.padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(86.dp).background(Color(0xFFFFE5C4), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("汗牛充栋", color = DesignTokens.Accent, fontWeight = FontWeight.ExtraBold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("汗牛充栋", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("让阅读，成为一种生活方式", color = DesignTokens.SoftText)
                    Text(
                        "版本 $versionName",
                        modifier = Modifier.background(Color(0xFFFFF1E2), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                        color = DesignTokens.Accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        SoftCard {
            DetailLine(
                Icons.Default.Info, "检查更新",
                if (checkingUpdate) "正在连接 GitHub…" else updateStatus,
                trailing = if (checkingUpdate) "检查中" else null,
                onClick = {
                    if (!checkingUpdate) scope.launch {
                        checkingUpdate = true
                        updateStatus = checkLatestRelease(versionName)
                        checkingUpdate = false
                    }
                }
            )
            DetailLine(
                Icons.Default.Menu, "版本更新记录", "查看 GitHub Releases",
                onClick = { openUrl(context, "https://github.com/beishan/aibook/releases") }
            )
            DetailLine(
                Icons.Default.Policy,
                "开源许可证",
                "libmobi · LGPL-3.0-or-later；commonmark-java · BSD-2-Clause",
                showDivider = false,
                onClick = {
                    dialogTitle = "开源许可证"
                    dialogText = readLicenseNotices(context)
                }
            )
        }
        SoftCard {
            DetailLine(
                Icons.Default.Info, "帮助与反馈", "前往 GitHub Issues 提交问题或建议",
                onClick = { openUrl(context, "https://github.com/beishan/aibook/issues") }
            )
            DetailLine(
                Icons.Default.Notifications, "邮件反馈", "通过系统邮件应用发送反馈",
                showDivider = false,
                onClick = { sendFeedbackEmail(context, versionName) }
            )
        }
        SoftCard {
            DetailLine(Icons.Default.Security, "用户协议", "阅读本地使用协议", onClick = { dialogTitle = "用户协议"; dialogText = USER_AGREEMENT })
            DetailLine(Icons.Default.Lock, "隐私政策", "了解我们如何保护您的隐私", onClick = { dialogTitle = "隐私政策"; dialogText = PRIVACY_POLICY })
            DetailLine(Icons.Default.Policy, "第三方信息共享清单", "本版本未集成广告或统计 SDK", onClick = { dialogTitle = "第三方信息共享清单"; dialogText = THIRD_PARTY_NOTICE })
            DetailLine(Icons.Default.Menu, "个人信息收集清单", "查看本机保存的数据类型", showDivider = false, onClick = { dialogTitle = "个人信息收集清单"; dialogText = PERSONAL_DATA_NOTICE })
        }
        Text(
            "汗牛充栋 · 让每一本书，都找到朋友\n© 2026 Miaomiao. All Rights Reserved.",
            modifier = Modifier.fillMaxWidth(),
            color = DesignTokens.SoftText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
    dialogTitle?.let { title -> InfoDialog(title, dialogText) { dialogTitle = null } }
}

@Composable
private fun SettingsSubPage(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium) }
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun StorageUsageCard(snapshot: StorageSnapshot?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline)
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(132.dp).background(Color(0xFFFFF1E2), RoundedCornerShape(66.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(bytesLabel(snapshot?.usedBytes), color = DesignTokens.Accent, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("已用空间", color = DesignTokens.SoftText)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("存储空间使用情况", fontWeight = FontWeight.Bold)
                StorageLegend("书籍文件", bytesLabel(snapshot?.bookBytes), DesignTokens.Accent)
                StorageLegend("缓存数据", bytesLabel(snapshot?.cacheBytes), Color(0xFFEFA24A))
                StorageLegend("下载文件", bytesLabel(snapshot?.downloadBytes), Color(0xFFF4C286))
                StorageLegend("其他数据", bytesLabel(snapshot?.otherBytes), Color(0xFFF5DDC7))
                HorizontalDivider(color = DesignTokens.Hairline)
                StorageLegend("设备总容量", bytesLabel(snapshot?.totalBytes), DesignTokens.SoftText)
                StorageLegend("设备可用", bytesLabel(snapshot?.availableBytes), DesignTokens.SoftText)
            }
        }
    }
}

@Composable
private fun StorageLegend(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
            Text(label, color = DesignTokens.SoftText)
        }
        Text(value, color = DesignTokens.SoftText)
    }
}

@Composable
private fun DetailLine(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: String? = null,
    actionText: String? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = DesignTokens.Accent, modifier = Modifier.size(34.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actionText?.let {
            Text(
                it,
                modifier = Modifier.background(DesignTokens.Accent, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        } ?: trailing?.let { Text(it, color = if (it.contains("30")) DesignTokens.Accent else DesignTokens.SoftText) }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DesignTokens.SoftText)
    }
    if (showDivider) HorizontalDivider(color = DesignTokens.Hairline)
}

@Composable
private fun PermissionLine(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    DetailLine(icon, title, subtitle, trailing = status, showDivider = showDivider, onClick = onClick)
}

@Composable
private fun SwitchLine(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = DesignTokens.Accent, modifier = Modifier.size(34.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = DesignTokens.SoftText)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = DesignTokens.Accent)
        )
    }
    if (showDivider) HorizontalDivider(color = DesignTokens.Hairline)
}

private data class StorageSnapshot(
    val bookBytes: Long,
    val downloadBytes: Long,
    val cacheBytes: Long,
    val otherBytes: Long,
    val totalBytes: Long,
    val availableBytes: Long,
    val bookCount: Int
) {
    val usedBytes: Long get() = bookBytes + downloadBytes + cacheBytes + otherBytes
}

private fun storageSnapshot(context: Context, books: List<LocalBook>): StorageSnapshot {
    var bookBytes = 0L
    var downloadBytes = 0L
    books.forEach { book ->
        val file = File(book.uri)
        val size = if (file.isFile) file.length() else directorySize(file)
        if (file.path.contains("/downloads/", true)) downloadBytes += size else bookBytes += size
    }
    val cacheBytes = directorySize(context.cacheDir) + directorySize(File(context.filesDir, "parsed-books"))
    val otherBytes = directorySize(File(context.filesDir, "covers")) + directorySize(File(context.filesDir, "reader_fonts"))
    val stat = StatFs(context.filesDir.path)
    return StorageSnapshot(bookBytes, downloadBytes, cacheBytes, otherBytes, stat.totalBytes, stat.availableBytes, books.size)
}

private fun directorySize(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf(::directorySize) ?: 0L
}

private fun clearAppCache(context: Context): Long {
    val targets = listOf(context.cacheDir, File(context.filesDir, "parsed-books"))
    val before = targets.sumOf(::directorySize)
    targets.forEach { root -> root.listFiles()?.forEach(File::deleteRecursively) }
    return before - targets.sumOf(::directorySize)
}

private fun bytesLabel(bytes: Long?): String {
    if (bytes == null) return "计算中"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                choices.forEach { (value, label) ->
                    Text(
                        label,
                        color = if (value == selected) DesignTokens.Accent else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 14.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

internal fun exportLibraryJson(books: List<LocalBook>, folders: List<Pair<String, String>>): String {
    val root = JSONObject()
    root.put("exportedAt", System.currentTimeMillis())
    root.put("formatVersion", 1)
    root.put("folders", JSONArray().apply {
        folders.forEach { (id, name) -> put(JSONObject().put("id", id).put("name", name)) }
    })
    root.put("books", JSONArray().apply {
        books.forEach { book ->
            put(JSONObject().apply {
                put("id", book.id)
                put("title", book.title)
                put("author", book.author ?: JSONObject.NULL)
                put("description", book.description ?: JSONObject.NULL)
                put("format", book.format.name)
                put("fileName", File(book.uri).name)
                put("sha256", book.sha256 ?: JSONObject.NULL)
                put("rating", book.rating ?: JSONObject.NULL)
                put("tags", JSONArray(book.tags))
                put("favorite", book.favorite)
                put("shelved", book.shelved)
                put("folderId", book.folderId ?: JSONObject.NULL)
                put("status", book.status.name)
                put("progress", book.progress.percent)
                put("chapterTitle", book.progress.chapterTitle ?: JSONObject.NULL)
                put("readingDurationSeconds", book.readingDurationSeconds)
            })
        }
    })
    return root.toString(2)
}

private fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "未知"

private suspend fun checkLatestRelease(currentVersion: String): String = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL("https://api.github.com/repos/beishan/aibook/releases/latest").openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "AiBook-Android")
        connection.inputStream.bufferedReader().use { reader ->
            val latest = JSONObject(reader.readText()).getString("tag_name").removePrefix("v")
            if (latest == currentVersion.removePrefix("v")) "当前已是最新版本 $currentVersion" else "发现新版本 $latest，点击更新记录下载"
        }
    }.getOrElse { "检查失败：${it.message ?: "暂无发布版本"}" }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun sendFeedbackEmail(context: Context, versionName: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "汗牛充栋 Android 反馈")
        putExtra(Intent.EXTRA_TEXT, "版本：$versionName\n设备：${Build.MANUFACTURER} ${Build.MODEL}\nAndroid：${Build.VERSION.RELEASE}\n\n反馈内容：")
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "选择反馈方式")) }
}

private fun readLicenseNotices(context: Context): String {
    val files = listOf("licenses/libmobi-LGPL-3.0.txt", "licenses/commonmark-BSD-2-Clause.txt")
    return files.joinToString("\n\n──────────\n\n") { path ->
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrElse { "无法读取 $path" }
    }
}

private const val PRIVACY_POLICY = """汗牛充栋是一款本地优先的私有书库应用。书籍文件、阅读进度、标签和评分默认仅保存在本机；只有在您主动配置私有服务器并执行同步时，相关数据才会发送到该服务器。应用不集成广告 SDK，不出售个人信息。文件导入通过 Android 系统选择器完成，应用只能访问您明确选择的文件或目录。您可以随时导出或删除本机数据。"""

private const val USER_AGREEMENT = """您应仅导入、下载和阅读自己有权使用的内容。应用按现状提供，本地文件删除、服务器配置和同步操作由用户确认后执行。请在永久删除前自行备份重要书籍与阅读数据。"""

private const val THIRD_PARTY_NOTICE = """本版本未集成广告、用户画像或第三方统计 SDK。网络访问仅用于用户配置的私有服务器、OPDS 数据源以及主动执行的 GitHub 版本检查。开源组件的许可证可在“开源许可证”中查看。"""

private const val PERSONAL_DATA_NOTICE = """本机可能保存：书籍文件及封面、书名和作者等元数据、个人标签与评分、阅读进度、书签和批注、扫描目录授权、OPDS 连接配置以及私有服务器登录令牌。上述数据可通过隐私页面导出或删除。"""
