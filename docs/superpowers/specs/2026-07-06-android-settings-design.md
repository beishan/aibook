# Android Settings Design

## Goal

完善 Android 端设置页，使其成为可用的设置中心，而不是静态展示页。

## Scope

- 保留现有 Compose、Material3、DesignTokens、SoftCard 视觉风格。
- 设置首页按阅读与外观、书库与扫描、同步与连接、存储与缓存、隐私与安全、关于分组。
- 既有主题、扫描目录、存储缓存、隐私、关于入口继续沿用现有路由。
- 新增同步与连接设置子页，接入已有 `SettingsViewModel` 的服务器地址、登录状态、Wi-Fi 同步开关。
- 阅读偏好在设置首页直接展示当前主题、字号比例、行距摘要，并跳转现有主题设置页。

## Non-Goals

- 不实现任务中心、API Token、多用户管理、备份导出等后端依赖较重的系统管理能力。
- 不新增数据库表或网络接口。
- 不重构设置相关文件结构。

## Data Flow

`SettingsScreen` 通过 `SettingsViewModel.uiState` 读取设置状态。首页只做摘要和导航；同步与连接子页负责修改服务器地址、登录、退出登录、Wi-Fi 同步开关。所有可持久化状态继续由 `ServerRepository`、`ServerConfigStore`、`ReaderSettingsStore` 保存。

## UI Behavior

- 首页显示服务器连接摘要：未配置、已配置未登录、已登录用户名。
- 同步与连接页包含服务器地址输入、保存按钮、登录表单、退出登录按钮、仅 Wi-Fi 同步开关。
- 隐私页中的开关改为真实可点击的本地 UI 状态，不伪造后端保存。
- 存储页继续展示占位统计，但文案明确为本机缓存与导入文件管理。

## Testing

- 新增纯函数测试覆盖设置摘要文案。
- 编译验证 Android app。
