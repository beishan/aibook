# Android 本地 + OPDS 阅读器：Codex 实现包

这是一套为 **Kotlin + Jetpack Compose + Material 3** 准备的产品与界面实现说明。视觉截图位于 `images/`，页面结构、字段、状态与跳转位于 `screens/`。

## 产品定位

一个优先保护本地书库的 Android 阅读器：

- **书架**：只管理“当前正在阅读 / 主动加入书架”的书。
- **书城**：聚合本地已导入书籍和多个 OPDS 数据源的全部目录；支持分类、检索、排序、书籍详情和加入书架。
- **发现**：导入本地文件、配置扫描目录、管理多个 OPDS 数据源、查看导入与同步状态。
- **设置**：页面主题、阅读偏好、字体排版、扫描规则、OPDS 同步、缓存、隐私、关于。
- **阅读器**：阅读正文、章节目录、进度、书签、笔记/划线、主题与排版调整。

## 目标设备

- 设计基准：小米 17 Pro Max 竖屏
- 使用 edge-to-edge；内容避开状态栏、手势导航条与圆角区域
- 优先适配大屏 Android 手机，同时能在普通手机上自然缩放

## 先读这些文件

1. `design-system.md`：颜色、排版、间距、组件规范。
2. `navigation.md`：路由与页面流。
3. `data-model.md`：核心实体、存储和状态。
4. `screens/`：逐页实现需求。
5. `prompts/`：可以直接粘给 Codex 的分阶段提示词。

## 推荐技术栈

- Kotlin、Jetpack Compose、Material 3、Navigation Compose
- Room：书籍、阅读进度、书架、笔记/书签、OPDS 数据源与同步记录
- DataStore：主题、阅读偏好、扫描目录开关、用户设置
- WorkManager：本地目录扫描、OPDS 后台同步、封面缓存清理
- OkHttp + Retrofit：OPDS 请求
- Coil：封面图片缓存
- EPUB：优先接入成熟 Android EPUB renderer；PDF 使用 PdfRenderer；TXT 直接渲染

> 注意：不要直接依赖示例截图中的真实书名、作者、封面、评分和站点地址。它们只用于视觉占位和信息密度示例。

## 最小可运行范围（MVP）

1. 书架：加入/移除书架、继续阅读、阅读进度。
2. 发现：文件选择器导入 EPUB/TXT/PDF；扫描目录；OPDS 源增删改启停。
3. 书城：本地与 OPDS 聚合列表、分类筛选、搜索、书籍详情。
4. 阅读器：TXT / EPUB 正文、目录、字号、行距、主题、进度保存。
5. 设置：页面主题、阅读主题、缓存清理、关于。

## 给 Codex 的入口提示词

将 `prompts/00-master-prompt.md` 的内容直接发送给 Codex，并把这个目录放到 Android 工程根目录或作为附件提供。
