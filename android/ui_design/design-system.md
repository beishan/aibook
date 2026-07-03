# 设计系统

## 视觉方向

- 风格：现代、安静、温暖、阅读优先；参考主流阅读应用的信息层级，但不复制任意产品界面。
- 主背景：浅米白，避免纯白造成长时间阅读疲劳。
- 强调色：暖橙，用于主要按钮、选中态、阅读进度、状态标签。
- 卡片：低对比白色/浅米色，柔和阴影，圆角克制。
- 图标：线性为主，选中态可填充；使用 Material Symbols Rounded 或项目统一矢量图标。

## 色彩 Token

```kotlin
val AppBackground = Color(0xFFFAF8F5)
val Surface = Color(0xFFFFFFFF)
val SurfaceWarm = Color(0xFFFFF8F0)
val Primary = Color(0xFFD9821D)
val PrimaryContainer = Color(0xFFFFE9D0)
val OnPrimary = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1E1E1E)
val TextSecondary = Color(0xFF747474)
val Outline = Color(0xFFEAE5DF)
val OpdsTag = Color(0xFFE8F2E0)
val LocalTag = Color(0xFFFFEEE0)
val Success = Color(0xFF30A46C)
val Warning = Color(0xFFD9821D)
val Error = Color(0xFFCF4B45)
```

## 排版 Token

- 页面大标题：30sp / SemiBold / 行高 38sp
- 区块标题：22sp / SemiBold / 行高 30sp
- 卡片标题：17sp / Medium / 行高 24sp
- 正文：16sp / Regular / 行高 25sp
- 辅助信息：13sp / Regular / 行高 18sp
- 标签：12sp / Medium
- 阅读正文默认：18sp，行距 1.75；允许用户在 14–30sp 范围内调整。

## 尺寸与间距

- 页面水平边距：20dp；窄屏可降到 16dp。
- 顶部安全区后首屏标题间距：16dp。
- 区块纵向间距：24dp。
- 普通卡片圆角：16dp；大功能卡片：24dp；胶囊标签：12dp。
- 按钮高度：48dp；底部导航高度遵循 Material 3 NavigationBar。
- 列表行最小触控高度：56dp；包含副标题时建议 72–88dp。

## 交互状态

- 加载：保留页面骨架屏，避免大面积空白。
- 空态：说明原因 + 一个清晰的主要操作；例如“还没有书籍，去导入”或“还没有 OPDS 数据源，添加数据源”。
- 错误：在来源卡片中显示可读错误和“重试”；不只展示网络异常码。
- 删除书籍：仅从书架移除与删除本地文件必须是两个不同操作，并在危险操作前确认。
- OPDS 书籍：清晰区分“已加入书架”“可下载”“已下载”“仅在线目录”。

## 可访问性

- 所有可点击项带 contentDescription。
- 颜色不是唯一状态表达方式，启用/失败/来源还应有文字。
- 正文字号、行距、主题、对齐方式均可调。
- 暗色和护眼主题下必须保持正文对比度。
