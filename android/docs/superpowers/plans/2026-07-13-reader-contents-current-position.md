# 目录当前章节定位修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打开经典目录或分卷目录时直接显示当前阅读章节，并保持搜索结果使用真实章节索引。

**Architecture:** 将列表位置计算放入可单测的 `ReaderContentsCatalog` 纯函数。Compose 目录列表用计算结果初始化各自的 `LazyListState`，不使用进入页面后的动画跳转。

**Tech Stack:** Kotlin、Jetpack Compose LazyColumn、kotlin.test、Gradle

## Global Constraints

- 当前章节不存在或目录为空时回退到列表索引 0。
- 搜索后不强制重新定位或抢夺用户滚动。
- 不新增依赖，不调整目录视觉样式。

---

### Task 1: 当前章节列表位置计算

**Files:**
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderContentsCatalog.kt`
- Modify: `app/src/test/kotlin/com/aibook/android/feature/reader/ReaderContentsCatalogTest.kt`

**Interfaces:**
- Produces: `ReaderContentsCatalog.chapterListPosition(List<ReaderChapter>, Int): Int`
- Produces: `ReaderContentsCatalog.visibleItemPosition(List<ReaderContentsListItem>, Int): Int`

- [ ] **Step 1: 写失败测试**

```kotlin
assertEquals(1, ReaderContentsCatalog.chapterListPosition(listOf(chapter(7, "七"), chapter(12, "十二")), 12))
assertEquals(0, ReaderContentsCatalog.chapterListPosition(emptyList(), 12))
assertEquals(2, ReaderContentsCatalog.visibleItemPosition(visibleItems, 12))
```

- [ ] **Step 2: 运行测试确认 RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReaderContentsCatalogTest'`
Expected: FAIL，提示两个定位函数不存在。

- [ ] **Step 3: 实现最小纯函数**

```kotlin
fun chapterListPosition(chapters: List<ReaderChapter>, currentChapterIndex: Int): Int =
    chapters.indexOfFirst { it.index == currentChapterIndex }.takeIf { it >= 0 } ?: 0

fun visibleItemPosition(items: List<ReaderContentsListItem>, currentChapterIndex: Int): Int =
    items.indexOfFirst { it is ReaderContentsListItem.Chapter && it.chapter.index == currentChapterIndex }
        .takeIf { it >= 0 } ?: 0
```

- [ ] **Step 4: 重跑目录测试确认 GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReaderContentsCatalogTest'`
Expected: PASS。

### Task 2: 经典与分卷目录初始定位

**Files:**
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: Task 1 的两个位置计算函数。
- Produces: 经典和分卷目录各自绑定带初始索引的 `LazyListState`。

- [ ] **Step 1: 经典目录初始化状态并使用真实章节索引**

```kotlin
val initialIndex = remember(chapters, state.currentChapterIndex) {
    ReaderContentsCatalog.chapterListPosition(chapters, state.currentChapterIndex)
}
val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
LazyColumn(state = listState) { /* rows use chapter.index */ }
```

- [ ] **Step 2: 分卷目录按展开后的可见条目初始化状态**

```kotlin
val initialIndex = remember(visibleItems, currentChapterIndex) {
    ReaderContentsCatalog.visibleItemPosition(visibleItems, currentChapterIndex)
}
val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
LazyColumn(state = listState) { /* existing grouped rows */ }
```

- [ ] **Step 3: 编译验证接口和 Compose 状态接线**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

### Task 3: 完整回归

**Files:**
- Verify: `app/src/test/kotlin/com/aibook/android/feature/reader/ReaderContentsCatalogTest.kt`

- [ ] **Step 1: 运行全部 App 单元测试**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 检查差异**

Run: `git diff --check`
Expected: 无输出，退出码 0。
