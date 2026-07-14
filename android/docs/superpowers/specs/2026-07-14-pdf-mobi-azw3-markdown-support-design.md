# PDF、MOBI、AZW3 与 Markdown 离线阅读支持设计

## 1. 背景与目标

汗牛充栋 Android 客户端目前可导入 PDF 与 Markdown，但 PDF 打开后仅显示“正在开发中”，Markdown 仍按普通文本读取；MOBI 与 AZW3 尚未进入格式枚举、导入和阅读链路。

本次开发在手机端离线完成以下格式支持：

- PDF：分页渲染、连续阅读、缩放、页码跳转、书签和进度恢复。
- Markdown：解析 CommonMark 语义并接入现有文本阅读器。
- MOBI：解析无 DRM 的 KF7/MOBI 文件并接入现有文本阅读器。
- AZW3：解析无 DRM 的 KF8/AZW3 文件并接入现有文本阅读器。

本次不支持 DOC、DOCX、CBR、CBZ，也不支持绕过 DRM、密码或文件加密。检测到受保护文件时，应用必须展示明确错误，不得崩溃或尝试解密。

## 2. 设计原则

1. 优先保持现有阅读体验一致：Markdown、MOBI、AZW3 继续使用现有目录、主题、字体、字号、行距、翻页、搜索、书签、高亮、批注和进度能力。
2. PDF 使用独立阅读界面：PDF 的定位模型是页码、缩放和页面偏移，不强行转换为段落模型。
3. 全部解析在本机完成，不依赖 NAS 转换或外部在线服务。
4. 大文件按需读取和渲染，避免一次把整本 PDF 或全部图片放入内存。
5. 解析器通过稳定接口隔离，避免继续扩大 `ReaderViewModel` 的格式分支。
6. Android 按钮点击态不使用阴影或按压投影，通过颜色、透明度、边框或轻量背景变化反馈。

## 3. 范围

### 3.1 本次包含

- `BookFormat` 增加 MOBI、AZW3。
- 文件选择器、扫描目录和 MIME 类型识别新增 `.mobi`、`.azw3`。
- EPUB、TXT、HTML、Markdown、MOBI、AZW3 通过统一内容加载接口进入文本阅读器。
- Markdown 使用 CommonMark AST 解析。
- MOBI/AZW3 使用 libmobi 的 Android 原生库解析。
- PDF 使用 Android `PdfRenderer` 渲染。
- PDF 当前页、页面偏移与缩放状态保存和恢复。
- PDF 页书签；文本格式继续使用现有章节书签和高亮。
- 解析缓存、图片资源缓存及删除清理。
- 加密、DRM、损坏文件、空间不足和不支持变体的错误分类。
- 单元测试、Compose UI 测试、文档、许可证信息和验收清单更新。

### 3.2 本次不包含

- DOC、DOCX、CBR、CBZ、KFX、AZW4。
- DRM 破解或密码输入、解密。
- PDF 文本选择、高亮、批注和全文搜索。
- PDF 表单编辑、签名和内容修改。
- 双页 PDF 模式。
- MOBI/AZW3 原始 CSS 的像素级还原。
- NAS 转换、云端转换或在线解析。

## 4. 总体架构

### 4.1 统一文本内容加载

在 `core:reader` 中新增统一接口：

```kotlin
interface BookContentLoader {
    val supportedFormats: Set<BookFormat>

    suspend fun load(request: BookContentRequest): BookContentResult
}

data class BookContentRequest(
    val bookId: String,
    val file: File,
    val format: BookFormat,
    val preferredChapterHref: String?,
    val cacheDirectory: File
)

sealed interface BookContentResult {
    data class Success(val content: ReaderBookContent) : BookContentResult
    data class Failure(val error: BookContentError) : BookContentResult
}

data class ReaderBookContent(
    val title: String?,
    val author: String?,
    val coverPath: String?,
    val chapters: List<ReaderChapter>
)
```

由 `BookContentLoaderRegistry` 根据 `BookFormat` 选择加载器。EPUB、文本、Markdown 和 MOBI 加载器均输出 `ReaderBookContent`，`ReaderViewModel` 只处理加载状态和统一结果，不了解具体解析细节。

PDF 不进入该接口，由 PDF 专用控制器管理文档生命周期和页面渲染。

### 4.2 模块边界

- `core:model`：格式枚举、PDF 阅读位置模型和统一错误模型。
- `core:reader`：统一加载接口、EPUB/TXT/HTML/Markdown 加载器、章节转换。
- `core:mobi`：libmobi 源码、CMake/JNI 桥接、MOBI/KF8 原始解析结果。
- `core:data`：书籍导入、解析缓存路径、阅读进度和删除清理。
- `app`：格式路由、PDF Compose 界面、现有文本阅读器接入和错误展示。

`core:mobi` 只向 Kotlin 暴露结构化结果，不把 libmobi 指针或 native 生命周期泄漏给 UI：

```kotlin
interface MobiDocumentParser {
    fun parse(filePath: String, outputDirectory: String): MobiParseResult
}

sealed interface MobiParseResult {
    data class Success(val document: MobiDocument) : MobiParseResult
    data class Failure(val error: MobiParseError) : MobiParseResult
}
```

## 5. 格式实现

### 5.1 PDF

使用 Android `PdfRenderer`，最低 API 21，满足项目最低 API 29。

`PdfDocumentController` 负责：

- 在 IO 线程打开 seekable `ParcelFileDescriptor` 和 `PdfRenderer`。
- 暴露页数和页面宽高比。
- 每次只打开一个 `PdfRenderer.Page`。
- 按目标显示宽度渲染 ARGB Bitmap。
- 串行化渲染请求并忽略过期请求。
- 页面或文档关闭时释放 Bitmap、Page、Renderer 和文件描述符。

`PdfPageBitmapCache` 使用有界 LRU，缓存当前页和相邻页。缓存上限按 Bitmap 字节数计算，而非固定页数；默认不超过运行时最大堆的八分之一，并设置合理的绝对上限。

`PdfReaderScreen` 默认纵向连续分页：

- LazyColumn 展示页面。
- 当前可见页及前后页按需渲染。
- 双指缩放范围为 1.0–4.0。
- 双击在 1.0 和 2.0 间切换。
- 放大后允许拖动查看页面。
- 顶栏显示书名、当前页/总页数和页书签。
- 底栏提供上一页、页码滑杆、下一页。
- 单击正文切换工具栏显隐。

PDF 进度使用现有 `ReadingProgress` 字段兼容保存：

- `chapterIndex`：零基页码。
- `chapterTitle`：`第 N 页`。
- `percent`：若总页数大于 1，则为 `pageIndex / (pageCount - 1)`；单页文档为 0。
- `scrollOffset`：当前页内纵向偏移像素。
- 新增可空 `pdfZoom` 字段保存缩放倍数；数据库迁移必须保留现有书籍和进度。

PDF 书签复用 `ReaderBookmark`：`chapterIndex` 保存页码，`chapterTitle` 保存页标签，`lineIndex` 固定为 0。

### 5.2 Markdown

使用 CommonMark Java 解析 AST，不以正则替代语法解析。

章节规则：

- 第一个一级或二级标题之前的内容归入“前言”。
- 一级和二级标题创建章节。
- 三级及以下标题保留为段内标题，不额外切章。
- 文件没有一级或二级标题时生成单一“正文”章节。

AST 到文本阅读模型的转换：

- 段落保留纯文本内容。
- 列表项添加项目符号或有序序号。
- 引用段落添加可识别引用前缀。
- 代码块保留换行和等宽显示标记；阅读器根据段落类型使用等宽字体。
- 分隔线转换为独立分隔段。
- 链接保留可见文本并附加目标地址。
- 图片保留替代文本；本地相对图片在授权可读时复制到缓存并作为章节图片。
- 原始 HTML 默认转为可读文本，不执行脚本。

为了支持代码块等语义，`ReaderChapter` 增加可选的块列表，同时保留 `content` 兼容现有搜索和进度逻辑：

```kotlin
data class ReaderBlock(
    val type: ReaderBlockType,
    val text: String,
    val imageUri: String? = null
)
```

### 5.3 MOBI 与 AZW3

使用 libmobi 解析无 DRM 的 KF7/MOBI 与 KF8/AZW3。

JNI 层职责保持最小：

1. 打开文件并调用 libmobi 加载。
2. 检查加密/DRM 状态；受保护时立即返回 `DRM_PROTECTED`。
3. 调用 rawml 解析，提取元数据、目录、HTML 文档和媒体资源。
4. 将 HTML、目录和元数据序列化到 Kotlin 可读取的结构。
5. 将图片写入传入的书籍缓存目录。
6. 无论成功或失败都释放 native 内存和文件句柄。

Kotlin `MobiBookContentLoader` 负责：

- 把 libmobi 的目录层级转换为章节顺序。
- 使用 Jsoup 清理脚本、危险 URL 和无关样式。
- 将 HTML 标题、段落、列表、引用和图片转换为 `ReaderBlock`。
- 修复章节间内部链接和图片路径。
- 若目录缺失，按 HTML 文档边界或标题生成稳定章节。
- 合并空章节，并为无标题章节生成“第 N 章”。

解析缓存目录：

```text
files/parsed-books/{bookId}/{sha256}/
├── manifest.json
├── chapters/
└── resources/
```

`manifest.json` 保存解析器版本、源文件哈希、格式、元数据和章节索引。源哈希或解析器版本改变时缓存失效。读取缓存失败时自动删除该缓存并重新解析一次；第二次失败返回明确错误。

## 6. 导入、元数据与删除

`BookFormat.fromFileName` 支持 `.mobi` 和 `.azw3`。文件选择器 MIME 类型加入 Kindle/Mobipocket 常见类型，同时保留 `application/octet-stream` 作为厂商文件管理器兼容后备。

导入流程继续先复制文件并计算 SHA-256。元数据提取规则：

- Markdown：文件名作为标题；若首个一级标题存在则优先使用该标题。
- MOBI/AZW3：读取 EXTH/头部中的书名、作者和封面。
- PDF：首版不新增 PDF 元数据解析依赖，标题继续由文件名得到。

删除书籍时同时删除：

- 应用私有目录中的原始书籍副本。
- 封面缓存。
- `parsed-books/{bookId}` 下的解析缓存和图片。
- PDF 页面 Bitmap 仅存在于内存，不写入永久缓存。

删除仍由 Repository 统一执行，UI 不直接操作缓存路径。

## 7. 导航与状态

打开本地书籍时：

1. 路由仍为 `reader/{bookId}`。
2. `ReaderScreen` 读取书籍格式。
3. PDF 转入 `PdfReaderScreen`。
4. 其他支持格式转入统一文本阅读器。

这避免改变书架、书城和详情页的调用协议。

加载状态统一为：

- `Idle`
- `Loading(stage, progress?)`
- `Ready`
- `Error(BookContentError)`

MOBI/AZW3 首次解析可显示“正在解析目录”“正在整理正文”“正在提取图片”；无法计算精确百分比时只显示阶段，不伪造进度。

## 8. 错误处理

```kotlin
sealed interface BookContentError {
    data object FileMissing : BookContentError
    data object PermissionLost : BookContentError
    data object DrmProtected : BookContentError
    data object PasswordProtected : BookContentError
    data object UnsupportedVariant : BookContentError
    data object CorruptedFile : BookContentError
    data object InsufficientStorage : BookContentError
    data class ParseFailed(val safeMessage: String) : BookContentError
}
```

界面文案必须给出用户可执行建议：

- 文件不存在：重新导入或从书架移除。
- 权限失效：重新选择文件。
- DRM/密码保护：使用无 DRM、未加密版本。
- 格式变体不支持：转换为 EPUB 或 PDF 后重试。
- 文件损坏：重新获取文件。
- 空间不足：释放空间后重试。

底层路径、native 堆栈和敏感信息不得直接显示。

## 9. 性能与资源约束

- PDF 打开、页信息读取、渲染都在后台线程执行。
- 只保留可见页附近的 Bitmap；快速滑动时取消或丢弃过期结果。
- MOBI/AZW3 解析缓存避免每次打开重新解包。
- 大章节继续采用现有章节窗口机制，避免整本正文同时进入 Compose。
- JNI 仅接收私有文件绝对路径，不持有 Activity/Context。
- Native 方法失败必须返回结果码，不能让 C 异常或空指针跨越 JNI 边界。

## 10. 安全与许可证

- libmobi 以动态 `.so` 形式随 APK 分 ABI 打包。
- Release 包和“开源许可证”页面加入 libmobi LGPL-3.0 与 CommonMark BSD-2-Clause 文本。
- 文档说明所使用的 libmobi 版本、修改内容和对应源码获取方式。
- MOBI HTML 和 Markdown 原始 HTML 不执行 JavaScript，不加载远程资源。
- 不记录书籍正文、文件路径、账号或阅读内容到网络日志。

## 11. 测试策略

### 11.1 单元测试

- `BookFormat`：扩展名大小写、无扩展名、MOBI/AZW3 识别。
- Markdown：标题分章、无标题、列表、引用、代码块、链接、本地图片和原始 HTML。
- MOBI/KF7、KF8/AZW3：目录、正文、封面、图片、无目录降级。
- 错误：DRM、加密、损坏文件、空间不足和缓存损坏重试。
- PDF：页码百分比换算、恢复页约束、渲染尺寸、缓存淘汰。
- Repository：四种格式导入、哈希去重、元数据、删除缓存。
- ViewModel：PDF/文本格式路由、加载阶段、错误映射、进度保存。

### 11.2 Android 与 UI 测试

- 使用项目自制或公开领域的最小 PDF、MOBI、AZW3、Markdown 文件。
- PDF 打开、上下页、页码滑杆、书签、进度恢复、缩放工具栏。
- Markdown/MOBI/AZW3 打开后进入现有文本阅读器并展示目录。
- 加密/损坏样本显示正确错误且不崩溃。
- API 29 与 API 36 至少各验证一次。

### 11.3 验收命令

```bash
./gradlew test
./gradlew lintDebug
./gradlew :app:assembleDebug
./gradlew connectedDebugAndroidTest
```

只有在已连接模拟器或真机时要求执行 `connectedDebugAndroidTest`；无设备时必须明确报告未执行，不能将其描述为通过。

## 12. 交付与验收标准

1. PDF 可离线打开、连续翻页、缩放、跳页、添加页书签并恢复进度。
2. Markdown、MOBI、AZW3 可离线打开，并使用现有文本阅读器的目录、主题、翻页、搜索、书签、高亮和批注。
3. 无 DRM 的 KF7 与 KF8/AZW3 测试样本可正确显示正文、目录和至少一种内嵌图片。
4. DRM、密码保护和损坏文件显示明确错误，不发生 native 崩溃。
5. 删除书籍后解析缓存和资源文件同步清理。
6. README、支持格式提示、验收清单和许可证信息与实际能力一致。
7. 单元测试、lint 和 debug APK 构建通过；instrumentation 测试状态如实记录。

## 13. 实施顺序

本功能按可独立验收的顺序实施：

1. 格式模型与统一加载接口。
2. Markdown 解析并接入文本阅读器。
3. PDF 控制器、进度模型和阅读界面。
4. libmobi Android 构建与 JNI 最小桥接。
5. MOBI/AZW3 章节、资源和缓存接入。
6. 导入元数据、删除清理、错误页。
7. UI 测试、许可证和文档收尾。
