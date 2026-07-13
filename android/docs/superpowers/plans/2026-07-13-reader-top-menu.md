# 阅读器完整顶部栏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 阅读器显示完整顶部栏、书内搜索，并将底部书签替换为亮/暗主题切换。

**Architecture:** 在 `ReaderSearchCatalog` 中隔离纯搜索与导航逻辑，`ReaderScreen` 仅管理菜单和搜索栏的展示与跳转。书签继续由 ViewModel 的既有持久化方法负责，主题快捷切换复用既有 `setTheme`。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、JUnit 4、Android Gradle Plugin。

## Global Constraints

- 保持 Compose 的 `@Composable` 和现有阅读器状态流模式。
- 仅支持 `ReaderTheme.LIGHT` 与 `ReaderTheme.DARK` 快捷互切。
- 安卓端按钮点击态不得使用阴影或按压投影。
- 不覆盖现有未提交的章节窗口改动。

---

### Task 1: 阅读内搜索目录

**Files:**
- Create: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderSearchCatalog.kt`
- Test: `app/src/test/kotlin/com/aibook/android/feature/reader/ReaderSearchCatalogTest.kt`

**Interfaces:**
- Consumes: `ReaderChapter`。
- Produces: `ReaderSearchMatch(chapterIndex: Int, lineIndex: Int)`、`ReaderSearchCatalog.find(chapters, query)`、`ReaderSearchCatalog.nextIndex(currentIndex, count, forward)`。

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `find returns matches across chapters ignoring case`() {
    val matches = ReaderSearchCatalog.find(chapters, "earth")
    assertEquals(listOf(ReaderSearchMatch(0, 0), ReaderSearchMatch(1, 0)), matches)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.aibook.android.feature.reader.ReaderSearchCatalogTest`

Expected: FAIL because `ReaderSearchCatalog` does not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
data class ReaderSearchMatch(val chapterIndex: Int, val lineIndex: Int)

object ReaderSearchCatalog {
    fun find(chapters: List<ReaderChapter>, query: String): List<ReaderSearchMatch> =
        if (query.isBlank()) emptyList() else chapters.flatMap { chapter ->
            chapter.content.lineSequence().mapIndexedNotNull { line, text ->
                ReaderSearchMatch(chapter.index, line).takeIf { text.contains(query, ignoreCase = true) }
            }.toList()
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.aibook.android.feature.reader.ReaderSearchCatalogTest`

Expected: PASS.

### Task 2: 视图模型搜索定位

**Files:**
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderViewModel.kt`
- Test: `app/src/test/kotlin/com/aibook/android/feature/reader/ReaderSearchCatalogTest.kt`

**Interfaces:**
- Consumes: `ReaderSearchMatch`。
- Produces: `fun openSearchMatch(match: ReaderSearchMatch)`，触发既有滚动或翻页定位状态。

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `nextIndex wraps after final match`() {
    assertEquals(0, ReaderSearchCatalog.nextIndex(currentIndex = 1, count = 2, forward = true))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.aibook.android.feature.reader.ReaderSearchCatalogTest`

Expected: FAIL because `nextIndex` does not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
fun openSearchMatch(match: ReaderSearchMatch) {
    selectChapter(match.chapterIndex)
    _state.update { it.copy(bookmarkNavigation = BookmarkNavigation(System.nanoTime(), match.chapterIndex, match.lineIndex, 0)) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.aibook.android.feature.reader.ReaderSearchCatalogTest`

Expected: PASS.

### Task 3: 顶部栏、搜索栏与主题快捷按钮

**Files:**
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt`
- Test: `app/src/test/kotlin/com/aibook/android/feature/reader/ReaderSearchCatalogTest.kt`

**Interfaces:**
- Consumes: `ReaderSearchCatalog.find`、`ReaderSearchCatalog.nextIndex`、`ReaderViewModel.openSearchMatch`、`ReaderViewModel.setTheme`。
- Produces: 菜单展开时的顶部栏、书内搜索交互、底部主题快捷入口。

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `previousIndex wraps before first match`() {
    assertEquals(1, ReaderSearchCatalog.nextIndex(currentIndex = 0, count = 2, forward = false))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.aibook.android.feature.reader.ReaderSearchCatalogTest`

Expected: FAIL because backwards navigation is missing.

- [ ] **Step 3: Write minimal implementation**

```kotlin
ReaderTopBar(
    title = state.book?.title ?: "在线书籍",
    chapterTitle = state.currentChapterTitle,
    isBookmarked = state.isCurrentPositionBookmarked,
    onBack = onBack,
    onSearch = { searchVisible = true },
    onToggleBookmark = onToggleBookmark
)
```

Use `OutlinedTextField` for search; navigate using match index and invoke `onOpenSearchMatch`. Replace the bottom `ReaderAction` bookmark with a sun/moon action calling `onToggleQuickTheme`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.aibook.android.feature.reader.ReaderSearchCatalogTest`

Expected: PASS.

### Task 4: Full verification

**Files:**
- Modify: none.

- [ ] **Step 1: Compile Android app**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run reader unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.aibook.android.feature.reader.*'`

Expected: all selected tests pass.
