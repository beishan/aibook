# 汗牛充栋 · iOS 阅读客户端

## 一、项目概述

**项目名称**：汗牛充栋 iOS 客户端

**项目定位**：汗牛充栋图书管理系统的 iOS 原生客户端，与现有安卓客户端功能对齐，为 iPhone / iPad 用户提供本地书籍管理、OPDS 书源接入、在线阅读及后端服务器同步能力。

**核心目标**：以 iOS 原生体验复刻安卓客户端全部功能，实现"买书、存书、读书"在 Apple 生态的完整覆盖。

**参考基准**：现有安卓客户端（`android/` 目录），功能、数据模型、UI 风格、导航结构均以此为基准。

---

## 二、技术架构

### 2.1 技术栈

| 层次 | 技术选型 | 说明 |
|---|---|---|
| 语言 | Swift 6 | 与安卓 Kotlin 对应 |
| UI 框架 | SwiftUI | 与安卓 Jetpack Compose 对应 |
| 导航 | NavigationStack (iOS 16+) | 与安卓 Navigation Compose 对应 |
| 依赖注入 | 手写 ServiceLocator | 与安卓保持一致风格 |
| 数据库 | SwiftData (iOS 17+) | 与安卓 Room 对应 |
| 偏好存储 | UserDefaults + @AppStorage | 与安卓 DataStore 对应 |
| 网络 (API) | URLSession + Codable | 与安卓 Retrofit + OkHttp 对应 |
| 网络 (OPDS) | URLSession + Foundation XMLParser | 与安卓 OkHttp + DOM Parser 对应 |
| EPUB 解析 | Readium Swift Toolkit 3.x | 与安卓 Readium Kotlin Toolkit 对应 |
| HTML 解析 | SwiftSoup | 与安卓 Jsoup 对应 |
| 图片加载 | Kingfisher | 与安卓 Coil 对应 |
| 最低系统版本 | iOS 17 | SwiftData + @Observable 最低要求 |

### 2.2 项目结构

```
ios/
├── AiBook.xcodeproj
├── AiBook/
│   ├── App/                           # 应用入口
│   │   ├── AiBookApp.swift            # @main，注册 ServiceLocator
│   │   └── ContentView.swift          # TabView 根视图
│   │
│   ├── Core/                          # 核心层
│   │   ├── Model/                     # 纯 Swift 数据模型
│   │   ├── Network/                   # 网络层（API + OPDS）
│   │   ├── Data/                      # 持久化层（SwiftData + Repository + Prefs）
│   │   ├── Reader/                    # 阅读器引擎
│   │   └── DI/                        # 依赖注入
│   │
│   ├── Feature/                       # 功能模块
│   │   ├── Shelf/                     # 书架
│   │   ├── Store/                     # 书城
│   │   ├── Opds/                      # 发现 / OPDS
│   │   ├── Reader/                    # 阅读器
│   │   ├── Importer/                  # 本地书籍导入
│   │   └── Settings/                  # 设置
│   │
│   ├── Navigation/                    # 路由定义
│   ├── UI/                            # 主题、通用组件、扩展
│   └── Resources/                     # 资源文件
│
├── AiBookTests/
├── Package.swift                      # SPM 依赖
└── README.md
```

### 2.3 模块依赖关系

```
App (Feature 层)
 ├── Core/Model       （无依赖，纯模型）
 ├── Core/Reader      （依赖 Model）
 ├── Core/Network     （依赖 Model）
 ├── Core/Data        （依赖 Model、Network、Reader）
 └── Core/DI          （依赖以上全部，组装依赖图）
```

---

## 三、数据模型

### 3.1 领域模型（与安卓 `core/model/BookModels.kt` 完全对齐）

#### 书籍格式

```swift
enum BookFormat: String, CaseIterable {
    case epub, txt, pdf, markdown, html, htm
    // extension: 文件扩展名
    // displayName: 显示名称
}
```

#### 阅读状态

```swift
enum ReadingStatus: String {
    case unread, reading, finished, wanted
}
```

#### 书籍模型

```swift
struct LocalBook {
    let id: String
    var title: String
    var author: String
    var format: BookFormat
    var uri: String             // 文件存储路径
    var sha256: String
    var coverUri: String?
    var folderId: String?
    var status: ReadingStatus
    var favorite: Bool
    var shelved: Bool
    var visibleInStore: Bool
    var importedAt: Date
    var lastReadAt: Date?
    var progress: ReadingProgress
}
```

#### 阅读进度

```swift
struct ReadingProgress {
    var chapterHref: String?
    var chapterTitle: String?
    var chapterIndex: Int
    var lineIndex: Int
    var scrollOffset: Double
    var percent: Double
    var positionLabel: String
}
```

#### 书架文件夹

```swift
struct ShelfFolder {
    let id: String
    var name: String
    var createdAtEpochMillis: Int64
}
```

#### 阅读器设置

```swift
struct ReaderSettings {
    var fontScale: Double           // 0.8 ~ 2.0
    var fontType: ReaderFontType
    var customFontName: String?
    var customFontPath: String?
    var lineHeight: Double          // 1.2 ~ 2.5
    var theme: ReaderTheme
    var paragraphSpacing: ParagraphSpacing
    var textAlignment: TextAlignment
    var pageTurnMode: PageTurnMode
    var autoBrightness: Bool
    var screenAlwaysOn: Bool
}

enum ReaderFontType: String { case system, serif, sansSerif, monospace, custom }
enum ReaderTheme: String { case light, paper, green, gray, dark }
enum ParagraphSpacing: String { case none, small, large }
enum TextAlignment: String { case left, center, right, justify }
enum PageTurnMode: String { case simulation, slide, cover, pan, vertical }
```

#### 应用主题

```swift
enum AppThemeMode: String { case system, light, dark }

enum AccentColor: String, CaseIterable {
    case orange, green, blue, purple, red
    // 每个 case 对应一个十六进制色值
}
```

### 3.2 阅读器模型（与安卓 `core/reader/` 对齐）

```swift
struct ReaderChapter {
    let index: Int
    let title: String
    let href: String
    var content: [String]       // 纯文本段落数组
    var imageUri: String?
}

struct ReaderPage {
    let index: Int
    let text: String
    let progress: Double
}

struct ReaderBookmark {
    let id: String
    let bookId: String
    let chapterHref: String
    let chapterTitle: String
    let progress: Double
    let createdAt: Date
}

struct ReaderHighlight {
    let id: String
    let bookId: String
    let chapterHref: String
    let startOffset: Int
    let endOffset: Int
    let excerpt: String
    let note: String?
    let createdAt: Date
}
```

### 3.3 书城模型（与安卓 `StoreCatalog.kt` 对齐）

```swift
struct StoreBook {
    let kind: StoreItemKind         // local / opds
    let sourceId: String?
    let sourceName: String?
    let title: String
    let author: String?
    let format: BookFormat
    let acquisitionHref: String?
    let acquisitionType: String?
    var downloadedLocalId: String?
    var coverUri: String?
    var shelved: Bool
}

enum StoreItemKind { case local, opds }

struct StoreCatalogFilter {
    var sourceId: String?
    var format: BookFormat?
    var category: String?
    var query: String?
    var sort: StoreSortOption
}

enum StoreSortOption: String { case recent, title, author, source }
```

### 3.4 OPDS 模型（与安卓 `OpdsModels.kt` 对齐）

```swift
struct OpdsFeed {
    let title: String?
    let entries: [OpdsEntry]
}

struct OpdsEntry {
    let title: String
    let author: String?
    let summary: String?
    let acquisitionLink: OpdsLink?
    let alternateLink: OpdsLink?
    let coverLink: OpdsLink?
}

struct OpdsLink {
    let href: String
    let type: String?
    let rel: String?
}

struct OpdsConnection: Identifiable {
    let id: String
    var name: String
    var baseUrl: String
    var username: String?
    var password: String?
    var enabled: Bool
    var lastSyncedAt: Date?
    var bookCount: Int
    var syncState: OpdsSyncState
    var lastErrorMessage: String?
}

enum OpdsSyncState: String { case idle, syncing, success, failed }
```

---

## 四、功能模块详细需求

### 4.1 书架模块

#### 书架首页

- **继续阅读卡片**：展示最近阅读的书籍，显示书名、作者、进度百分比，点击直接进入阅读器
- **书籍网格**：3 列封面网格，展示书名和格式标识
- **文件夹筛选**：顶部水平滚动 Chip 栏，支持"全部 / 未归类 / 自定义文件夹"切换
- **排序切换**：支持按最近阅读、导入时间、书名、收藏优先排序
- **空状态**：无书籍时展示导入引导卡片

#### 管理模式

- 长按进入管理模式，支持多选
- 批量操作：收藏/取消收藏、移入文件夹、从书架移除
- 移入文件夹弹窗：选择已有文件夹或新建文件夹

#### 书籍详情页

- 展示：封面、书名、作者、格式标识、阅读进度
- 操作：开始阅读、加入/移出书架、收藏/取消收藏、删除

#### 本地书籍导入

- 通过系统文件选择器（`UIDocumentPickerViewController`）导入
- 支持格式：EPUB、TXT、PDF、Markdown、HTML、HTM
- 导入流程：复制到私有目录 → 计算 SHA-256 → 去重检测 → 解析元数据（EPUB 提取封面和书名）→ 写入数据库
- 导入结果反馈：新增 N 本、恢复 N 本、重复 N 本、不支持 N 本、失败 N 本

### 4.2 书城模块

#### 书城首页

- 聚合展示：本地书籍 + OPDS 缓存条目，统一为 `StoreBook` 模型
- **4 种视图模式**：3 列网格、带封面列表、紧凑列表、4 列小网格
- 视图模式持久化存储
- 支持管理模式（批量移除本地书籍）

#### 分类筛选页

- 搜索框：按书名/作者搜索
- 筛选维度：来源、格式、分类
- 排序选项：最近、书名、作者、来源

#### 远程书籍详情页

- 展示 OPDS 书籍的封面、书名、作者、简介
- 操作：下载到本地

#### 本地与 OPDS 去重

- 按书名 + 格式匹配，已下载的 OPDS 书籍自动关联本地条目

### 4.3 发现 / OPDS 模块

#### 发现首页

- 导入文件入口卡片
- 扫描目录入口卡片
- OPDS 连接列表：显示连接名称、书本数量、同步状态
- 每个连接支持：同步、浏览、编辑、删除操作

#### 添加/编辑 OPDS 源

- 表单字段：名称、URL、用户名、密码
- 支持独立的添加源页面（从设置或发现页进入）

#### OPDS 目录浏览

- 分类 Chip 栏
- 排行区域、最近更新区域、完整书单
- 支持深层目录遍历（导航栈，最大深度 4 层）
- 每本书显示下载按钮

#### OPDS 同步

- 递归遍历 OPDS 目录树，收集所有可获取的书籍条目
- 同步结果缓存到本地数据库
- 同步状态管理：空闲 → 同步中 → 成功/失败
- 失败时记录错误信息

### 4.4 阅读器模块

#### 支持的格式

| 格式 | 解析方式 |
|---|---|
| EPUB | Readium Swift Toolkit 解析 → SwiftSoup 提取纯文本 |
| TXT | 按空行/固定行数分章 |
| Markdown | 按标题分章，保留结构 |
| HTML | SwiftSoup 解析纯文本 |
| PDF | 暂不支持阅读，显示"即将推出"提示 |

#### 阅读界面

- **主阅读区**：文本内容展示，点击切换控件显隐
- **底部控制栏**：进度滑块、上一章/下一章按钮
- **目录面板**：章节目录列表，点击跳转
- **设置面板**：阅读参数调节
- **主题面板**：阅读背景色切换

#### 渲染模式

- **垂直滚动模式**（`vertical`）：LazyColumn 等效，无限滚动，自动加载下一章
- **分页模式**（`simulation / slide / cover / pan`）：水平翻页，带翻页动画效果

#### 阅读设置

| 设置项 | 范围 | 说明 |
|---|---|---|
| 字号 | 0.8x ~ 2.0x | 字体缩放比例 |
| 字体 | 系统/衬线/无衬线/等宽/自定义 | 自定义支持导入 .ttf/.otf |
| 行间距 | 1.2 ~ 2.5 | 行高倍数 |
| 段间距 | 无/小/大 | 段落间距 |
| 文字对齐 | 左/居中/右/两端 | 段落对齐方式 |
| 翻页模式 | 仿真/滑动/覆盖/平移/垂直 | 翻页动画类型 |
| 自动亮度 | 开/关 | 阅读时自动调节亮度 |
| 屏幕常亮 | 开/关 | 阅读时保持屏幕亮起 |
| 作用范围 | 全局/本书 | 设置可全局生效或仅当前书 |

#### 阅读主题

| 主题 | 背景色 | 文字色 |
|---|---|---|
| 默认白 | #FFFFFF | #333333 |
| 纸质 | #F5E6D3 | #5B4636 |
| 护眼绿 | #CCE8CF | #3E5E3F |
| 浅灰 | #E6E6E6 | #333333 |
| 暗黑 | #1A1A1A | #CCCCCC |

#### 进度管理

- 阅读位置自动保存（章节索引 + 行索引 + 滚动偏移）
- 支持远程书籍：通过后端 API 同步进度

### 4.5 设置模块

#### 主题设置

- 应用主题模式：跟随系统 / 浅色 / 深色
- 强调色选择：橙色、绿色、蓝色、紫色、红色
- 与安卓端视觉风格保持一致（暖色系设计，背景 #FFFCF8，强调色默认橙色 #D47A1F）

#### 扫描目录管理

- 添加/启用/禁用/删除扫描目录
- 单独扫描或全部扫描
- 扫描统计信息展示

#### 同步连接设置

- 服务器 URL 配置
- 登录/登出表单（用户名 + 密码）
- 仅 WiFi 同步开关
- 服务器认证 Token 管理

#### 存储缓存

- 存储使用量可视化
- 缓存清理工具

#### 隐私权限

- 权限管理入口
- 个性化推荐开关
- 使用统计开关

#### 关于

- 应用信息、版本号
- 开源许可
- 帮助信息

---

## 五、导航结构

### 5.1 底部导航栏（4 个 Tab）

| Tab | 图标 | 路由 |
|---|---|---|
| 书架 | book.fill | `shelf` |
| 书城 | books.vertical | `store` |
| 发现 | compass | `opds` |
| 设置 | gearshape | `settings` |

### 5.2 完整页面路由

| 页面 | 路由 | 参数 | 底部栏 |
|---|---|---|---|
| 书架 | `shelf` | — | ✅ |
| 书籍详情 | `book/{bookId}` | bookId: String | ❌ |
| 阅读器 | `reader/{bookId}` | bookId: String | ❌ |
| 远程阅读器 | `remote-reader/{bookId}` | bookId: Int64 | ❌ |
| 书城 | `store` | — | ✅ |
| 书城分类 | `store-category` | — | ✅ |
| 远程书籍详情 | `store-remote-book/{bookId}` | bookId: String | ❌ |
| 发现 | `opds` | — | ✅ |
| 添加 OPDS 源 | `opds-add-source` | connectionId?: String | ✅ |
| 设置 | `settings` | — | ✅ |
| 主题设置 | `theme-settings` | — | ✅ |
| 扫描目录 | `scan-directories` | — | ✅ |
| 同步连接设置 | `sync-connection-settings` | — | ✅ |
| 存储缓存 | `storage-cache` | — | ✅ |
| 隐私权限 | `privacy-permissions` | — | ✅ |
| 关于 | `about` | — | ✅ |

---

## 六、网络层设计

### 6.1 后端 API 对接

与安卓端使用相同的后端 API：

| 接口 | 方法 | 说明 |
|---|---|---|
| `api/auth/login` | POST | 登录，返回 Token |
| `api/auth/register` | POST | 注册 |
| `api/books` | GET | 分页书籍列表 |
| `api/books/search` | GET | 关键词搜索 |
| `api/books/favorites` | GET | 收藏书籍 |
| `api/books/{id}` | GET | 书籍详情 |
| `api/books/{id}/content-processed` | GET | 远程阅读文本内容 |
| `api/books/{id}/reading-progress` | POST | 保存阅读进度 |
| `api/books/{id}/reading-time` | PUT | 更新阅读时长 |

- 认证方式：Bearer Token，通过 `Authorization` 请求头传递
- Token 本地持久化存储

### 6.2 OPDS 协议

- 传输层：`URLSession` 同步请求
- 解析层：`Foundation.XMLParser`（SAX 模式）解析 OPDS Atom Feed
- 支持 Basic Auth 认证
- 相对 URL 自动补全
- Doctype / 外部实体防护

---

## 七、本地存储设计

### 7.1 SwiftData 数据表

| 实体 | 说明 | 对应安卓表 |
|---|---|---|
| `BookEntity` | 书籍基本信息 + 阅读进度 | `books` |
| `OpdsConnectionEntity` | OPDS 源配置 | `opds_connections` |
| `OpdsCatalogEntryEntity` | OPDS 目录缓存 | `opds_catalog_entries` |
| `ShelfFolderEntity` | 书架文件夹 | `shelf_folders` |
| `ScanDirectoryEntity` | 扫描目录配置 | `scan_directories` |

### 7.2 UserDefaults 偏好

| 存储 | 说明 |
|---|---|
| `ReaderSettingsStore` | 阅读器设置（字体、主题、翻页模式等） |
| `ServerConfigStore` | 服务器配置（URL、Token、用户名、WiFi 同步等） |

### 7.3 文件存储

| 目录 | 说明 |
|---|---|
| `Documents/books/` | 书籍文件，命名 `{UUID}.{ext}` |
| `Documents/covers/` | 封面图片，命名 `{bookId}.{ext}` |
| `Documents/reader_fonts/` | 自定义字体，命名 `{UUID}.{ttf/otf}` |

---

## 八、第三方依赖

| 库 | 用途 | 安卓对应 |
|---|---|---|
| Readium Swift Toolkit 3.x | EPUB 解析 | Readium Kotlin Toolkit |
| SwiftSoup | HTML 解析转纯文本 | Jsoup |
| Kingfisher | 异步图片加载与缓存 | Coil |

通过 Swift Package Manager (SPM) 管理。

---

## 九、非功能需求

- **性能**：书架 1000 本以内列表滚动流畅（60fps）；EPUB 解析 50MB 文件 < 3s
- **兼容性**：最低支持 iOS 17，适配 iPhone 和 iPad（竖屏为主，iPad 支持横屏）
- **离线能力**：所有本地书籍、OPDS 缓存均可离线访问
- **数据安全**：Token 钥匙串存储；书籍文件存于 App 沙盒
- **与安卓一致性**：数据模型、API 接口、UI 布局结构保持一致，降低用户跨平台学习成本
- **本地化**：界面语言为简体中文

---

## 十、开发阶段规划

| 阶段 | 内容 | 预估工时 |
|---|---|---|
| Phase 1 | 项目脚手架搭建：Xcode 项目、SPM 依赖、ServiceLocator、SwiftData ModelContainer、Navigation 路由、TabView 框架 | 3-4 天 |
| Phase 2 | 书架模块：本地书籍导入、书籍网格展示、文件夹管理、排序、管理模式、书籍详情页 | 4-5 天 |
| Phase 3 | 阅读器核心：Readium EPUB 集成、TXT/MD/HTML 分章解析、垂直滚动渲染、分页翻页渲染 | 5-7 天 |
| Phase 4 | 阅读器增强：阅读设置面板、主题切换、进度保存与恢复、翻页动画效果 | 3-4 天 |
| Phase 5 | OPDS 模块：添加/编辑源、目录浏览、递归同步、缓存管理、书籍下载 | 4-5 天 |
| Phase 6 | 书城模块：本地 + OPDS 聚合、4 种视图模式、分类筛选、远程书籍详情与下载 | 3-4 天 |
| Phase 7 | 设置模块：主题设置、扫描目录、同步连接、存储缓存、隐私权限、关于页 | 3-4 天 |
| Phase 8 | 后端联调：登录认证、远程书籍阅读、进度同步 | 2-3 天 |
| Phase 9 | 打磨优化：动画过渡、暗黑模式适配、iPad 适配、性能优化 | 3-4 天 |
| **总计** | | **约 30-40 天** |

---

## 十一、与安卓端的差异说明

| 差异点 | 安卓 | iOS | 原因 |
|---|---|---|---|
| 系统最低版本 | Android 10 (API 29) | iOS 17 | SwiftData 和 @Observable 需要 iOS 17 |
| 数据库 | Room (SQLite) | SwiftData | 平台原生 ORM |
| EPUB 引擎 | Readium Kotlin 3.1 | Readium Swift 3.x | 同一框架的跨平台实现 |
| XML 解析 | javax.xml DOM | Foundation XMLParser (SAX) | 平台原生 API |
| 文件选择器 | OpenMultipleDocuments | UIDocumentPickerViewController | 平台原生组件 |
| DI 方案 | 手写 ServiceLocator | 手写 ServiceLocator | 保持一致 |
| 暗黑模式 | AppThemeMode 三选一 | 跟随系统 / 手动切换 | iOS 原生支持更成熟 |
