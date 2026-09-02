# Codex Implementation Prompt

请读取当前目录中的 `design-tokens.json`、`navigation.json`、`components/`、`screens/`，为一个 Android 小说阅读器实现完整 Jetpack Compose UI。

## 项目目标
支持三类书籍来源：
- LOCAL：本地书籍
- OPDS：多个 OPDS 服务
- BACKEND：自定义后端书籍管理服务

## 架构约束
- Kotlin + Jetpack Compose + Material 3
- 单 Activity
- Navigation Compose
- MVVM
- UI 状态使用 `StateFlow`
- 页面组件必须可 Preview
- 业务模型与来源模型分离

建议模型：
```kotlin
data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val cover: String?,
    val description: String?,
    val progress: Float?,
    val inShelf: Boolean,
    val favorite: Boolean,
    val downloaded: Boolean,
    val source: BookSource
)

data class BookSource(
    val type: BookSourceType,
    val sourceId: String?,
    val sourceName: String
)

enum class BookSourceType { LOCAL, OPDS, BACKEND }
```

## 开发顺序
1. Theme / DesignTokens
2. App Navigation + BottomNav
3. 公共组件
4. 书架模块
5. 本地书城模块
6. OPDS 模块
7. 后端书库模块
8. 搜索 / 下载
9. 设置

## 视觉要求
严格按照 design-tokens 中的配色、圆角、间距和排版规格实现，不自行发明新的视觉体系。
