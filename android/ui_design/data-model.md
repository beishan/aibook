# 数据模型与本地存储

## Kotlin 核心模型（建议）

```kotlin
enum class BookSourceType { LOCAL, OPDS }
enum class BookFormat { EPUB, PDF, TXT, MOBI, AZW3, UNKNOWN }
enum class DownloadState { NOT_REQUIRED, NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, FAILED }
enum class SyncState { IDLE, SYNCING, SUCCESS, FAILED }

data class Book(
    val id: String,
    val title: String,
    val author: String? = null,
    val coverUri: String? = null,
    val description: String? = null,
    val categories: List<String> = emptyList(),
    val format: BookFormat = BookFormat.UNKNOWN,
    val sourceType: BookSourceType,
    val sourceId: String? = null,
    val localUri: String? = null,
    val remoteAcquisitionUrl: String? = null,
    val publishedAt: Long? = null,
    val addedAt: Long,
    val updatedAt: Long,
    val rating: Float? = null,
    val isInShelf: Boolean = false,
    val downloadState: DownloadState = DownloadState.NOT_REQUIRED
)

data class ReadingProgress(
    val bookId: String,
    val locator: String, // CFI for EPUB; page/offset for PDF/TXT
    val chapterTitle: String? = null,
    val progressPercent: Float,
    val lastReadAt: Long,
    val totalReadingMillis: Long = 0L
)

data class OpdsSource(
    val id: String,
    val name: String,
    val catalogUrl: String,
    val username: String? = null,
    val passwordEncrypted: String? = null,
    val enabled: Boolean = true,
    val syncMode: OpdsSyncMode = OpdsSyncMode.INCREMENTAL,
    val lastSyncAt: Long? = null,
    val syncState: SyncState = SyncState.IDLE,
    val lastErrorMessage: String? = null
)

enum class OpdsSyncMode { FULL, INCREMENTAL }

data class ScanDirectory(
    val id: String,
    val treeUri: String,
    val displayName: String,
    val enabled: Boolean,
    val lastScanAt: Long? = null,
    val discoveredCount: Int = 0
)

data class Annotation(
    val id: String,
    val bookId: String,
    val type: AnnotationType,
    val locatorStart: String,
    val locatorEnd: String? = null,
    val selectedText: String? = null,
    val noteText: String? = null,
    val colorToken: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

enum class AnnotationType { BOOKMARK, HIGHLIGHT, NOTE }
```

## Room 表建议

- `books`
- `reading_progress`
- `opds_sources`
- `opds_catalog_entries`（按源缓存元数据）
- `scan_directories`
- `annotations`
- `downloads`
- `book_collections` / `collection_items`（二期：自定义书单）

## DataStore 偏好建议

- 页面主题：系统 / 浅色米白 / 纯白 / 深色护眼 / 墨黑夜间
- 强调色：默认暖橙 + 可选色
- 阅读主题、字体、字号、行距、段距、对齐方式、翻页动画、亮度、常亮
- 自动扫描开关、Wi‑Fi 下自动同步、封面下载策略、缓存上限

## 文件与权限

- 使用 Storage Access Framework（`ACTION_OPEN_DOCUMENT`、`ACTION_OPEN_DOCUMENT_TREE`）导入文件与选择扫描目录。
- 不依赖全盘文件读取权限作为默认路径；仅在必要时解释用途并申请。
- 密码等凭据使用 Android Keystore 加密后再持久化。
