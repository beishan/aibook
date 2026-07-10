package com.aibook.android.feature.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SoftCard

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
                trailing = "版本 1.3.2",
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
    SettingsSubPage(title = "存储与缓存", onBack = onBack) {
        StorageUsageCard()
        SectionLabel("清理与管理")
        SoftCard {
            DetailLine(Icons.Default.Book, "缓存数据", "用于提升阅读体验的临时数据", trailing = "3.2 GB")
            DetailLine(Icons.Default.Folder, "导入书籍", "本地导入的书籍文件", trailing = "128 本")
            DetailLine(Icons.Default.Download, "下载文件", "已下载的书籍和资源", trailing = "2.1 GB", showDivider = false)
        }
        SectionLabel("存储设置")
        SoftCard {
            DetailLine(Icons.Default.Delete, "清理缓存", "清理临时文件，释放存储空间", trailing = "3.2 GB", actionText = "立即清理")
            DetailLine(Icons.Default.Storage, "管理下载", "查看和管理已下载的文件")
            DetailLine(Icons.Default.Folder, "下载位置", "当前位置：/Android/data/com.aibook/files/Download")
            DetailLine(Icons.Default.AutoDelete, "自动清理缓存", "智能清理过期缓存，节省存储空间", trailing = "30 天", showDivider = false)
        }
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Delete, null)
            Text("清理缓存并优化空间", fontWeight = FontWeight.Bold)
        }
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

    SettingsSubPage(title = "隐私与权限", subtitle = "管理应用权限与隐私选项", onBack = onBack) {
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
            PermissionLine(Icons.Default.Folder, "存储权限", "用于书籍缓存、下载、书籍导入与备份")
            PermissionLine(Icons.Default.Notifications, "通知权限", "用于书籍更新、活动与系统通知提醒")
            PermissionLine(Icons.Default.Wifi, "网络权限", "用于书籍搜索、内容更新与云端同步", showDivider = false)
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
            DetailLine(Icons.Default.Lock, "隐私政策", "了解我们如何收集、使用和保护您的信息")
            DetailLine(Icons.Default.Policy, "个人信息管理", "管理您的账号信息与数据，支持导出与删除", showDivider = false)
        }
        Text(
            "阅读，让知识更有温度",
            modifier = Modifier.fillMaxWidth(),
            color = DesignTokens.SoftText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
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
                        "版本 1.3.2",
                        modifier = Modifier.background(Color(0xFFFFF1E2), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                        color = DesignTokens.Accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        SoftCard {
            DetailLine(Icons.Default.Info, "检查更新", "当前已是最新版本", trailing = "已是最新")
            DetailLine(Icons.Default.Menu, "版本更新记录", "查看历史版本更新内容")
            DetailLine(Icons.Default.Policy, "开源许可证", "查看开源组件许可信息", showDivider = false)
        }
        SoftCard {
            DetailLine(Icons.Default.Info, "帮助与反馈", "常见问题与使用帮助")
            DetailLine(Icons.Default.Notifications, "联系客服", "工作日 9:00-18:00 在线服务", showDivider = false)
        }
        SoftCard {
            DetailLine(Icons.Default.Security, "用户协议", "阅读我们的用户协议")
            DetailLine(Icons.Default.Lock, "隐私政策", "了解我们如何保护您的隐私")
            DetailLine(Icons.Default.Policy, "第三方信息共享清单", "查看第三方信息共享情况")
            DetailLine(Icons.Default.Menu, "个人信息收集清单", "查看个人信息收集情况", showDivider = false)
        }
        Text(
            "汗牛充栋 · 让每一本书，都找到朋友\n© 2026 Miaomiao. All Rights Reserved.",
            modifier = Modifier.fillMaxWidth(),
            color = DesignTokens.SoftText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
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
private fun StorageUsageCard() {
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
                    Text("12.6 GB", color = DesignTokens.Accent, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("已用空间", color = DesignTokens.SoftText)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("存储空间使用情况", fontWeight = FontWeight.Bold)
                StorageLegend("书籍文件", "6.8 GB", DesignTokens.Accent)
                StorageLegend("缓存数据", "3.2 GB", Color(0xFFEFA24A))
                StorageLegend("下载文件", "2.1 GB", Color(0xFFF4C286))
                StorageLegend("其他数据", "0.5 GB", Color(0xFFF5DDC7))
                HorizontalDivider(color = DesignTokens.Hairline)
                StorageLegend("可用空间", "115.4 GB", DesignTokens.SoftText)
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
    showDivider: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
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
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DesignTokens.SoftText)
    }
    if (showDivider) HorizontalDivider(color = DesignTokens.Hairline)
}

@Composable
private fun PermissionLine(icon: ImageVector, title: String, subtitle: String, showDivider: Boolean = true) {
    DetailLine(icon, title, subtitle, trailing = "已允许", showDivider = showDivider)
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
