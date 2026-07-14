# PDF, MOBI, AZW3, and Markdown Offline Reading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add offline PDF, Markdown, DRM-free MOBI, and DRM-free AZW3 reading while preserving the existing reader experience for reflowable formats.

**Architecture:** Reflowable formats are normalized behind `BookContentLoader` into `ReaderBookContent`; PDF uses a dedicated `PdfDocumentController` and Compose screen. Markdown uses CommonMark 0.28.0, while MOBI/KF8 parsing is isolated in a new Android library wrapping vendored libmobi 0.12 through JNI.

**Tech Stack:** Kotlin 2.2.21, Jetpack Compose, Room 2.8.3, CommonMark Java 0.28.0, Android `PdfRenderer`, Android NDK/CMake, libmobi 0.12, Jsoup 1.18.1.

## Global Constraints

- Support Android API 29 through API 36.
- Parse every supported format offline on the device.
- Support only unencrypted and DRM-free PDF, MOBI, and AZW3 files.
- Do not add DOC, DOCX, CBR, CBZ, KFX, AZW4, PDF text selection, PDF highlighting, PDF full-text search, or PDF two-page mode.
- Preserve current theme, pagination, search, bookmarks, highlights, notes, and progress for Markdown, MOBI, and AZW3.
- Android button pressed states must not use shadows or pressed projections.
- Preserve unrelated dirty-worktree changes; stage only files belonging to each task.

---

### Task 1: Extend format detection and imports

**Files:**
- Modify: `core/model/src/main/kotlin/com/aibook/android/core/model/BookModels.kt`
- Modify: `core/model/src/test/kotlin/com/aibook/android/core/model/ImportPolicyTest.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/importer/LocalBookImport.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/shelf/ShelfViewModel.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/store/StoreViewModel.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/opds/OpdsViewModel.kt`

**Interfaces:**
- Produces: `BookFormat.MOBI`, `BookFormat.AZW3`, and user-facing format labels.
- Consumes: existing `BookFormat.fromFileName(fileName: String)`.

- [ ] **Step 1: Add failing format tests**

```kotlin
@Test
fun kindleExtensionsAreRecognizedCaseInsensitively() {
    assertEquals(BookFormat.MOBI, BookFormat.fromFileName("book.mobi"))
    assertEquals(BookFormat.AZW3, BookFormat.fromFileName("BOOK.AZW3"))
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :core:model:test --tests '*ImportPolicyTest*'`

Expected: compilation fails because `MOBI` and `AZW3` do not exist.

- [ ] **Step 3: Add the two enum values and MIME support**

```kotlin
enum class BookFormat(val extension: String, val displayName: String) {
    EPUB("epub", "EPUB"),
    TXT("txt", "TXT"),
    PDF("pdf", "PDF"),
    MOBI("mobi", "MOBI"),
    AZW3("azw3", "AZW3"),
    MARKDOWN("md", "Markdown"),
    HTML("html", "HTML"),
    HTM("htm", "HTML")
}
```

Add `application/x-mobipocket-ebook`, `application/vnd.amazon.ebook`, and `application/octet-stream` to `supportedBookMimeTypes`. Update all format-support messages to `支持 EPUB、TXT、PDF、MOBI、AZW3、Markdown、HTML`.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `./gradlew :core:model:test :app:testDebugUnitTest`

Expected: both tasks pass.

- [ ] **Step 5: Commit**

```bash
git add core/model/src/main/kotlin/com/aibook/android/core/model/BookModels.kt core/model/src/test/kotlin/com/aibook/android/core/model/ImportPolicyTest.kt app/src/main/kotlin/com/aibook/android/feature/importer/LocalBookImport.kt app/src/main/kotlin/com/aibook/android/feature/shelf/ShelfViewModel.kt app/src/main/kotlin/com/aibook/android/feature/store/StoreViewModel.kt app/src/main/kotlin/com/aibook/android/feature/opds/OpdsViewModel.kt
git commit -m "feat: 增加 MOBI 与 AZW3 格式识别"
```

### Task 2: Introduce the unified reflowable-content contract

**Files:**
- Create: `core/reader/src/main/kotlin/com/aibook/android/core/reader/BookContentLoader.kt`
- Create: `core/reader/src/main/kotlin/com/aibook/android/core/reader/BookContentLoaderRegistry.kt`
- Create: `core/reader/src/test/kotlin/com/aibook/android/core/reader/BookContentLoaderRegistryTest.kt`
- Modify: `core/reader/src/main/kotlin/com/aibook/android/core/reader/ReaderContent.kt`
- Modify: `core/reader/build.gradle.kts`

**Interfaces:**
- Produces: `BookContentRequest`, `BookContentResult`, `ReaderBookContent`, `BookContentError`, `BookContentLoader`, and `BookContentLoaderRegistry`.
- Consumes: `BookFormat` from `core:model`.

- [ ] **Step 1: Add the model dependency and failing registry tests**

```kotlin
private class FakeLoader(override val supportedFormats: Set<BookFormat>) : BookContentLoader {
    override suspend fun load(request: BookContentRequest): BookContentResult =
        BookContentResult.Success(ReaderBookContent(chapters = emptyList()))
}

@Test
fun registryReturnsLoaderForSupportedFormat() {
    val loader = FakeLoader(setOf(BookFormat.MARKDOWN))
    assertSame(loader, BookContentLoaderRegistry(listOf(loader)).loaderFor(BookFormat.MARKDOWN))
}

@Test
fun registryReturnsNullForUnsupportedFormat() {
    assertNull(BookContentLoaderRegistry(emptyList()).loaderFor(BookFormat.MOBI))
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :core:reader:test --tests '*BookContentLoaderRegistryTest*'`

Expected: compilation fails because the loader contract is missing.

- [ ] **Step 3: Implement the contract and registry**

```kotlin
data class BookContentRequest(
    val bookId: String,
    val file: File,
    val format: BookFormat,
    val preferredChapterHref: String? = null,
    val cacheDirectory: File
)

data class ReaderBookContent(
    val title: String? = null,
    val author: String? = null,
    val coverPath: String? = null,
    val chapters: List<ReaderChapter>
)

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

sealed interface BookContentResult {
    data class Success(val content: ReaderBookContent) : BookContentResult
    data class Failure(val error: BookContentError) : BookContentResult
}

interface BookContentLoader {
    val supportedFormats: Set<BookFormat>
    suspend fun load(request: BookContentRequest): BookContentResult
}

class BookContentLoaderRegistry(private val loaders: List<BookContentLoader>) {
    fun loaderFor(format: BookFormat): BookContentLoader? =
        loaders.firstOrNull { format in it.supportedFormats }
}
```

Add `implementation(project(":core:model"))` to `core:reader`.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `./gradlew :core:reader:test`

Expected: all reader tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/reader
git commit -m "refactor: 统一可重排书籍内容加载接口"
```

### Task 3: Parse Markdown into the existing chapter model

**Files:**
- Modify: `core/reader/build.gradle.kts`
- Create: `core/reader/src/main/kotlin/com/aibook/android/core/reader/MarkdownBookContentLoader.kt`
- Create: `core/reader/src/test/kotlin/com/aibook/android/core/reader/MarkdownBookContentLoaderTest.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Produces: `MarkdownBookContentLoader` supporting `BookFormat.MARKDOWN`.
- Consumes: `BookContentRequest` and returns `BookContentResult.Success` with stable chapter hrefs `markdown:<index>`.

- [ ] **Step 1: Add CommonMark and failing parser tests**

Add `implementation("org.commonmark:commonmark:0.28.0")`, `implementation("org.commonmark:commonmark-ext-gfm-tables:0.28.0")`, and `implementation("org.jsoup:jsoup:1.18.1")`.

```kotlin
@Test
fun h1AndH2CreateStableChapters() = runTest {
    val file = tempFile("# One\nText\n## Two\n- A\n- B")
    val result = loader.load(request(file)) as BookContentResult.Success
    assertEquals(listOf("One", "Two"), result.content.chapters.map { it.title })
    assertEquals(listOf("markdown:0", "markdown:1"), result.content.chapters.map { it.href })
    assertTrue(result.content.chapters[1].content.contains("• A"))
}

@Test
fun contentBeforeFirstHeadingBecomesPreface() = runTest {
    val result = loader.load(request(tempFile("intro\n# Chapter"))) as BookContentResult.Success
    assertEquals("前言", result.content.chapters.first().title)
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :core:reader:test --tests '*MarkdownBookContentLoaderTest*'`

Expected: compilation fails because `MarkdownBookContentLoader` is missing.

- [ ] **Step 3: Implement AST traversal**

Use one configured CommonMark `Parser`. Accumulate paragraphs until H1/H2, emit bullets for list items, `> ` for block quotes, fenced content for code blocks, and visible text plus URL for links. Escape raw HTML by parsing it through Jsoup text extraction; never execute or fetch remote resources. Return `CorruptedFile` for unreadable input and a single `正文` chapter for heading-free input.

- [ ] **Step 4: Route Markdown through the loader**

Construct the registry in `ReaderViewModel.Factory`. Replace the `BookFormat.MARKDOWN` branch that calls `readTxtFile` with `registry.loaderFor(book.format)?.load(request)`, then call the existing `applyChapters` on success. Keep TXT/HTML behavior unchanged in this task.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `./gradlew :core:reader:test :app:testDebugUnitTest`

Expected: Markdown tests and existing reader tests pass.

- [ ] **Step 6: Commit**

```bash
git add core/reader app/src/main/kotlin/com/aibook/android/feature/reader/ReaderViewModel.kt app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt
git commit -m "feat: 支持 Markdown 章节化阅读"
```

### Task 4: Add PDF progress math and Room migration

**Files:**
- Create: `core/model/src/main/kotlin/com/aibook/android/core/model/PdfReadingPosition.kt`
- Create: `core/model/src/test/kotlin/com/aibook/android/core/model/PdfReadingPositionTest.kt`
- Modify: `core/model/src/main/kotlin/com/aibook/android/core/model/BookModels.kt`
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/db/BookEntity.kt`
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/db/BookDao.kt`
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/db/AiBookDatabase.kt`
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/mapper/EntityMappers.kt`
- Modify: `core/data/build.gradle.kts`
- Create: `core/data/src/androidTest/kotlin/com/aibook/android/core/data/db/Migration12To13Test.kt`

**Interfaces:**
- Produces: `PdfReadingPosition.progress(pageIndex, pageCount)`, `clampPage`, and `ReadingProgress.pdfZoom`.
- Consumes: existing `ReadingProgress.chapterIndex` and `scrollOffset`.

- [ ] **Step 1: Add failing PDF position tests**

```kotlin
@Test fun lastPageIsComplete() = assertEquals(1f, PdfReadingPosition.progress(9, 10))
@Test fun singlePageStartsAtZero() = assertEquals(0f, PdfReadingPosition.progress(0, 1))
@Test fun restoredPageIsClamped() = assertEquals(4, PdfReadingPosition.clampPage(99, 5))
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :core:model:test --tests '*PdfReadingPositionTest*'`

Expected: compilation fails because `PdfReadingPosition` is missing.

- [ ] **Step 3: Implement pure progress functions**

```kotlin
object PdfReadingPosition {
    fun progress(pageIndex: Int, pageCount: Int): Float =
        if (pageCount <= 1) 0f else pageIndex.coerceIn(0, pageCount - 1).toFloat() / (pageCount - 1)

    fun clampPage(pageIndex: Int, pageCount: Int): Int =
        if (pageCount <= 0) 0 else pageIndex.coerceIn(0, pageCount - 1)
}
```

- [ ] **Step 4: Add `progressPdfZoom` and migration 12→13**

Add `val pdfZoom: Float? = null` to `ReadingProgress`, `val progressPdfZoom: Float? = null` to `BookEntity`, mapper coverage, DAO update parameter, database version 13, and:

```kotlin
private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN progressPdfZoom REAL")
    }
}
```

Add `androidTestImplementation("androidx.test:core:1.6.1")`, `androidTestImplementation("androidx.test.ext:junit:1.2.1")`, and `androidTestImplementation("androidx.room:room-testing:2.8.3")`. The instrumentation migration test must create a version-12 database with a book, migrate, and assert the row remains and `progressPdfZoom` is null.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `./gradlew :core:model:test :core:data:testDebugUnitTest`

Expected: model and migration tests pass.

- [ ] **Step 6: Commit**

```bash
git add core/model core/data
git commit -m "feat: 持久化 PDF 页码与缩放进度"
```

### Task 5: Build the native PDF controller and bitmap cache

**Files:**
- Create: `app/src/main/kotlin/com/aibook/android/feature/reader/pdf/PdfDocumentController.kt`
- Create: `app/src/main/kotlin/com/aibook/android/feature/reader/pdf/PdfPageBitmapCache.kt`
- Create: `app/src/main/kotlin/com/aibook/android/feature/reader/pdf/PdfRenderSizing.kt`
- Create: `app/src/test/kotlin/com/aibook/android/feature/reader/pdf/PdfRenderSizingTest.kt`
- Create: `app/src/test/kotlin/com/aibook/android/feature/reader/pdf/PdfPageBitmapCachePolicyTest.kt`

**Interfaces:**
- Produces: `PdfDocumentInfo`, `PdfRenderedPage`, `open(file)`, `render(pageIndex, targetWidthPx)`, and `close()`.
- Consumes: local private PDF file paths from `LocalBook.uri`.

- [ ] **Step 1: Add failing render-sizing tests**

```kotlin
@Test
fun renderHeightPreservesAspectRatio() {
    assertEquals(1500, PdfRenderSizing.heightFor(600, 800, 1000))
}

@Test
fun bitmapBudgetUsesSmallerOfHeapFractionAndAbsoluteCap() {
    assertEquals(32 * 1024 * 1024, PdfRenderSizing.cacheBudget(512L * 1024 * 1024))
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*PdfRenderSizingTest*'`

Expected: compilation fails because sizing policy is missing.

- [ ] **Step 3: Implement pure sizing and LRU policy**

`heightFor` must use `targetWidth * pageHeight / pageWidth`, clamp every dimension to at least one, and cap cache at `min(maxHeap / 8, 32 MiB)`. Cache entries report `bitmap.allocationByteCount`; eviction recycles only bitmaps no longer displayed.

- [ ] **Step 4: Implement `PdfDocumentController`**

Open `ParcelFileDescriptor` and `PdfRenderer` on `Dispatchers.IO`. Synchronize `openPage`/`render`/`close` because one page may be open at a time. Render ARGB_8888 using `PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY`. Map `SecurityException` to `PasswordProtected`, `IOException` to `CorruptedFile`, and missing files to `FileMissing`.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest`

Expected: all app unit tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/aibook/android/feature/reader/pdf app/src/test/kotlin/com/aibook/android/feature/reader/pdf
git commit -m "feat: 添加 PDF 页面渲染控制器"
```

### Task 6: Implement the Compose PDF reader and routing

**Files:**
- Create: `app/src/main/kotlin/com/aibook/android/feature/reader/pdf/PdfReaderViewModel.kt`
- Create: `app/src/main/kotlin/com/aibook/android/feature/reader/pdf/PdfReaderScreen.kt`
- Create: `app/src/test/kotlin/com/aibook/android/feature/reader/pdf/PdfReaderViewModelTest.kt`
- Create: `app/src/androidTest/kotlin/com/aibook/android/feature/reader/pdf/PdfReaderScreenTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/kotlin/com/aibook/android/AiBookApp.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt`
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/repository/BookRepository.kt`

**Interfaces:**
- Produces: `PdfReaderUiState`, PDF page bookmarking, page navigation, zoom, and progress persistence.
- Consumes: `PdfDocumentController`, `BookRepository`, and `ReaderBookmarkRepository`.

- [ ] **Step 1: Add failing ViewModel tests**

```kotlin
@Test
fun openingRestoresClampedPageAndZoom() = runTest {
    viewModel.open(bookWithPdfProgress(page = 50, zoom = 2f), pageCount = 10)
    assertEquals(9, viewModel.state.value.currentPage)
    assertEquals(2f, viewModel.state.value.zoom)
}

@Test
fun changingPagePersistsPdfProgress() = runTest {
    viewModel.onPageVisible(4, scrollOffset = 120)
    advanceUntilIdle()
    assertEquals(4, repository.lastProgress.chapterIndex)
    assertEquals("第 5 页", repository.lastProgress.chapterTitle)
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*PdfReaderViewModelTest*'`

Expected: compilation fails because PDF ViewModel is missing.

- [ ] **Step 3: Implement ViewModel and persistence**

Add a repository method accepting `pdfZoom`; save `chapterHref = "pdf:<pageIndex>"`, `chapterTitle = "第 ${pageIndex + 1} 页"`, `chapterIndex`, `scrollOffset`, and `PdfReadingPosition.progress`.

- [ ] **Step 4: Implement the screen**

Add `androidTestImplementation("androidx.compose.ui:ui-test-junit4")`, `debugImplementation("androidx.compose.ui:ui-test-manifest")`, and AndroidX test runner dependencies. Use a `LazyColumn`, render visible page plus neighbors, and expose test tags `pdf-page-<index>`, `pdf-previous`, `pdf-next`, and `pdf-page-slider`. Use `transformable` for 1x–4x pinch zoom and double-tap for 1x/2x. Toolbars use flat color/alpha feedback and no shadow elevation.

- [ ] **Step 5: Route PDF books**

Keep `reader/{bookId}`. At the route boundary load the book format once; show `PdfReaderScreen` for PDF and existing `ReaderScreen` otherwise. Remove the “PDF 阅读器正在开发中” branch.

- [ ] **Step 6: Run tests and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`

Expected: tests pass and debug APK builds.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/aibook/android/feature/reader/pdf app/src/test/kotlin/com/aibook/android/feature/reader/pdf app/src/androidTest/kotlin/com/aibook/android/feature/reader/pdf app/src/main/kotlin/com/aibook/android/AiBookApp.kt app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt core/data/src/main/kotlin/com/aibook/android/core/data/repository/BookRepository.kt
git commit -m "feat: 实现 PDF 离线阅读器"
```

### Task 7: Vendor libmobi and create the JNI boundary

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/mobi/build.gradle.kts`
- Create: `core/mobi/src/main/AndroidManifest.xml`
- Create: `core/mobi/src/main/cpp/CMakeLists.txt`
- Create: `core/mobi/src/main/cpp/mobi_jni.c`
- Create: `core/mobi/src/main/kotlin/com/aibook/android/core/mobi/MobiDocumentParser.kt`
- Create: `core/mobi/src/main/kotlin/com/aibook/android/core/mobi/NativeMobiDocumentParser.kt`
- Create: `core/mobi/src/test/kotlin/com/aibook/android/core/mobi/MobiParseResultTest.kt`
- Create: `third_party/libmobi/` from upstream tag `v0.12`
- Create: `third_party/libmobi/UPSTREAM.md`
- Create: `third_party/libmobi/COPYING`

**Interfaces:**
- Produces: `MobiParseResult`, `MobiDocument`, `MobiChapter`, `MobiResource`, and `NativeMobiDocumentParser.parse(filePath, outputDirectory)`.
- Consumes: libmobi `mobi_load_file`, `mobi_init_rawml`, and `mobi_parse_rawml`.

- [ ] **Step 1: Add failing Kotlin result-contract tests**

```kotlin
@Test
fun nativeStatusMapsDrmToDomainError() {
    assertEquals(MobiParseError.DRM_PROTECTED, NativeMobiStatus.toError(NativeMobiStatus.DRM))
}

@Test
fun unknownNativeStatusMapsToParseFailure() {
    assertEquals(MobiParseError.PARSE_FAILED, NativeMobiStatus.toError(999))
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :core:mobi:testDebugUnitTest`

Expected: Gradle reports that project `:core:mobi` does not exist.

- [ ] **Step 3: Add the Android library module**

Configure compileSdk 36, minSdk 29, NDK ABIs `arm64-v8a`, `armeabi-v7a`, `x86_64`, externalNativeBuild CMake, Kotlin serialization JSON, and JVM 17. Add `include(":core:mobi")`.

- [ ] **Step 4: Vendor the exact upstream source**

Download libmobi tag `v0.12`, verify its release archive hash against the recorded value obtained from the downloaded artifact, and copy only source, headers, COPYING, AUTHORS, ChangeLog, and required CMake inputs into `third_party/libmobi`. `UPSTREAM.md` records repository URL, tag, archive SHA-256, local patches, build flags, and source-offer URL. Do not commit generated binaries.

- [ ] **Step 5: Implement the native bridge**

Expose one JNI function returning an integer status and writing `manifest.json`, chapter HTML, and resources under `outputDirectory`. Validate both paths, reject encrypted content before rawml export, bound every allocation, JSON-escape metadata, and use a single cleanup label so every `FILE*`, `MOBIData*`, `MOBIRawml*`, and allocated buffer is freed on all exits.

- [ ] **Step 6: Implement Kotlin manifest reading and status mapping**

`NativeMobiDocumentParser` loads `aibook_mobi`, invokes native parsing on `Dispatchers.IO`, validates that the manifest and referenced files remain inside `outputDirectory`, then decodes the JSON into immutable data classes.

- [ ] **Step 7: Run native and Kotlin tests**

Run: `./gradlew :core:mobi:testDebugUnitTest :core:mobi:assembleDebug`

Expected: Kotlin tests pass and all three ABI libraries build.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts core/mobi third_party/libmobi
git commit -m "feat: 集成 libmobi 安卓解析模块"
```

### Task 8: Normalize MOBI/AZW3 and add parse caching

**Files:**
- Modify: `core/mobi/build.gradle.kts`
- Create: `core/mobi/src/main/kotlin/com/aibook/android/core/mobi/MobiBookContentLoader.kt`
- Create: `core/mobi/src/main/kotlin/com/aibook/android/core/mobi/ParsedBookCache.kt`
- Create: `core/mobi/src/test/kotlin/com/aibook/android/core/mobi/MobiBookContentLoaderTest.kt`
- Create: `core/mobi/src/test/kotlin/com/aibook/android/core/mobi/ParsedBookCacheTest.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/di/ServiceLocator.kt`

**Interfaces:**
- Produces: one loader supporting `{BookFormat.MOBI, BookFormat.AZW3}` and hash/version keyed parsed cache.
- Consumes: `MobiDocumentParser`, Jsoup, `BookContentRequest`, and existing `ReaderChapter`.

- [ ] **Step 1: Add failing normalization tests with a fake parser**

```kotlin
@Test
fun mobiTocAndHtmlBecomeReaderChapters() = runTest {
    val parser = FakeMobiParser(documentWithTwoChaptersAndImage())
    val result = loader(parser).load(request(BookFormat.AZW3)) as BookContentResult.Success
    assertEquals(listOf("Start", "Next"), result.content.chapters.map { it.title })
    assertTrue(result.content.chapters.first().content.contains("Hello"))
}

@Test
fun drmFailureMapsToBookContentError() = runTest {
    val result = loader(FakeMobiParser(MobiParseResult.Failure(MobiParseError.DRM_PROTECTED)))
        .load(request(BookFormat.MOBI))
    assertEquals(BookContentResult.Failure(BookContentError.DrmProtected), result)
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :core:mobi:testDebugUnitTest --tests '*MobiBookContentLoaderTest*'`

Expected: compilation fails because the MOBI loader is missing.

- [ ] **Step 3: Implement HTML normalization**

Use Jsoup to remove script/style/iframe/object, reject remote resource URLs, resolve exported local images, preserve headings/lists/quotes, generate stable hrefs `mobi:<spineIndex>`, and synthesize `第 N 章` only when no title exists. Never merge non-empty adjacent chapters.

- [ ] **Step 4: Implement cache validation and one retry**

Cache root is `files/parsed-books/{bookId}/{sha256}/`. Manifest includes `parserVersion = "libmobi-0.12-v1"`. A mismatched version/hash, path traversal, missing chapter, or invalid JSON invalidates the cache. On parse-cache read failure, delete that hash directory and parse once; return failure after the second error.

- [ ] **Step 5: Register the loader**

Add `implementation(project(":core:reader"))`, `implementation(project(":core:model"))`, and `implementation("org.jsoup:jsoup:1.18.1")` to `core:mobi`. Keep the JVM-only `core:reader` independent of Android modules. Construct parser/cache/loader in `ServiceLocator`, and inject it beside the Markdown loader into the registry used by `ReaderViewModel`.

- [ ] **Step 6: Run tests and verify GREEN**

Run: `./gradlew :core:mobi:testDebugUnitTest :core:reader:test :app:testDebugUnitTest`

Expected: MOBI normalization, cache, and existing reader tests pass.

- [ ] **Step 7: Commit**

```bash
git add core/mobi app/src/main/kotlin/com/aibook/android/feature/reader/ReaderViewModel.kt app/src/main/kotlin/com/aibook/android/di/ServiceLocator.kt
git commit -m "feat: 支持 MOBI 与 AZW3 章节化阅读"
```

### Task 9: Extract metadata and clean parsed resources

**Files:**
- Modify: `core/data/build.gradle.kts`
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/repository/BookRepository.kt`
- Create: `core/data/src/main/kotlin/com/aibook/android/core/data/repository/ParsedBookStorage.kt`
- Create: `core/data/src/test/kotlin/com/aibook/android/core/data/repository/ParsedBookStorageTest.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/importer/LocalBookImport.kt`

**Interfaces:**
- Produces: safe parsed-resource directory creation/deletion and best-effort Markdown/MOBI metadata extraction.
- Consumes: `MobiDocumentParser` metadata and CommonMark first-H1 parsing.

- [ ] **Step 1: Add failing path-safety and deletion tests**

```kotlin
@Test
fun deleteRemovesOnlyRequestedBookDirectory() {
    storage.directoryFor("book-a").resolve("x").apply { parentFile.mkdirs(); writeText("x") }
    storage.directoryFor("book-b").mkdirs()
    storage.deleteForBook("book-a")
    assertFalse(storage.directoryFor("book-a").exists())
    assertTrue(storage.directoryFor("book-b").exists())
}

@Test
fun invalidBookIdCannotEscapeRoot() {
    assertFailsWith<IllegalArgumentException> { storage.directoryFor("../outside") }
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*ParsedBookStorageTest*'`

Expected: compilation fails because storage helper is missing.

- [ ] **Step 3: Implement safe storage and repository cleanup**

Accept book IDs matching `[A-Za-z0-9_-]+`, verify canonical child paths remain under root, and recursively delete without following symlinks. `BookRepository.deleteBook` removes original, cover, and parsed directory after the database row is found.

- [ ] **Step 4: Add best-effort metadata**

Markdown first H1 overrides filename title. MOBI/AZW3 import extracts title, author, description, and cover without failing the import if metadata parsing fails. The first full read may still recreate the parsed cache.

- [ ] **Step 5: Run tests and verify GREEN**

Run: `./gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest`

Expected: cleanup, import, and app tests pass.

- [ ] **Step 6: Commit**

```bash
git add core/data app/src/main/kotlin/com/aibook/android/feature/importer/LocalBookImport.kt
git commit -m "feat: 管理多格式元数据与解析缓存"
```

### Task 10: Error UX, licenses, documentation, and final verification

**Files:**
- Create: `app/src/main/kotlin/com/aibook/android/feature/reader/BookContentErrorText.kt`
- Create: `app/src/test/kotlin/com/aibook/android/feature/reader/BookContentErrorTextTest.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/pdf/PdfReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/settings/SettingsScreen.kt`
- Create: `app/src/main/assets/licenses/libmobi-LGPL-3.0.txt`
- Create: `app/src/main/assets/licenses/commonmark-BSD-2-Clause.txt`
- Modify: `README.md`
- Modify: `ui_design/IMPLEMENTATION-CHECKLIST.md`

**Interfaces:**
- Produces: safe Chinese error messages and accurate support/license documentation.
- Consumes: `BookContentError`.

- [ ] **Step 1: Add failing error-copy tests**

```kotlin
@Test
fun drmMessageGivesActionableAdvice() {
    assertEquals("此书包含 DRM，当前仅支持无 DRM 的 MOBI/AZW3 文件", BookContentErrorText.forError(BookContentError.DrmProtected))
}

@Test
fun parseFailureDoesNotExposePaths() {
    val text = BookContentErrorText.forError(BookContentError.ParseFailed("/data/user/0/secret"))
    assertFalse(text.contains("/data/"))
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*BookContentErrorTextTest*'`

Expected: compilation fails because error text mapper is missing.

- [ ] **Step 3: Implement error UI and license page entries**

Map every domain error to fixed Chinese copy with retry/reimport guidance. Display retry only for parse/cache errors, reimport for missing/permission errors, and no bypass action for DRM/password errors. Populate the existing About license entry from bundled license assets.

- [ ] **Step 4: Update documentation**

README lists EPUB, TXT, PDF, Markdown, MOBI, and AZW3 as readable; states DRM/password exclusions; documents three ABI packages. Check off only actually verified checklist items. Remove “PDF 阅读器正在开发中” and stale supported-format copy.

- [ ] **Step 5: Run full verification**

Run: `./gradlew test lintDebug :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` with zero failed tests and zero lint errors.

Run when a device is connected: `./gradlew connectedDebugAndroidTest`

Expected with a device: `BUILD SUCCESSFUL`. Without a device, record the test as not run rather than passed.

- [ ] **Step 6: Inspect the APK and native ABIs**

Run: `unzip -l app/build/outputs/apk/debug/app-debug.apk | rg 'lib/(arm64-v8a|armeabi-v7a|x86_64)/libaibook_mobi.so|licenses/'`

Expected: one `libaibook_mobi.so` for each configured ABI plus both license files.

- [ ] **Step 7: Commit**

```bash
git add app README.md ui_design/IMPLEMENTATION-CHECKLIST.md
git commit -m "docs: 完成多格式阅读交付说明"
```
