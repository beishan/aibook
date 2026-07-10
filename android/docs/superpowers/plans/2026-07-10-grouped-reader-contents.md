# 阅读器分卷目录实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留经典目录的同时，为 Android 阅读器新增可持久化切换的分卷目录样式。

**Architecture:** 用 `ReaderContentsStyle` 表示目录偏好并通过现有 DataStore 持久化；用独立纯逻辑 `ReaderContentsCatalog` 将扁平 `ReaderChapter` 分组并推导阅读状态；Compose 页面仅根据设置选择经典列表或新的分卷卡片列表。章节解析、数据库和其它页面不变。

**Tech Stack:** Kotlin 2、Jetpack Compose Material 3、AndroidX DataStore Preferences、Kotlin Test、Gradle。

## Global Constraints

- `CLASSIC` 必须是默认目录样式，现有目录的布局和行为不得改变。
- 新样式只参考图片中部的目录卡片，不新增搜索、排序、顶部或底部导航。
- 分卷目录只默认展开当前阅读所在卷，其余卷折叠。
- 卷标题章节必须保留为可点击章节，原始章节索引不得改变。
- 点击态不得使用阴影或按压投影，只使用颜色、透明度、边框或轻量背景变化。
- 不修改章节解析器、数据库、书籍详情页或应用全局设置。
- 保留工作区中用户已有的未提交修改；提交时只能暂存本任务自己的代码块。

---

## 文件结构

- 新建 `core/model/src/main/kotlin/com/aibook/android/core/model/ReaderContentsStyle.kt`：目录样式枚举和安全持久化解码。
- 修改 `core/model/src/main/kotlin/com/aibook/android/core/model/BookModels.kt`：`ReaderSettings` 增加目录样式字段。
- 新建 `core/model/src/test/kotlin/com/aibook/android/core/model/ReaderContentsStyleTest.kt`：默认值与未知值回退测试。
- 修改 `core/data/src/main/kotlin/com/aibook/android/core/data/prefs/ReaderSettingsStore.kt`：DataStore 读取和写入目录样式。
- 新建 `core/data/src/test/kotlin/com/aibook/android/core/data/prefs/ReaderSettingsStoreTest.kt`：真实 Preferences DataStore 往返测试。
- 新建 `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderContentsCatalog.kt`：分卷、当前卷定位和阅读状态纯逻辑。
- 新建 `app/src/test/kotlin/com/aibook/android/feature/reader/ReaderContentsCatalogTest.kt`：分卷逻辑测试。
- 修改 `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderViewModel.kt`：同步、设置、重置和恢复目录样式。
- 修改 `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt`：阅读设置切换项及分卷卡片 UI。

---

### Task 1: 目录样式领域模型

**Files:**
- Create: `core/model/src/main/kotlin/com/aibook/android/core/model/ReaderContentsStyle.kt`
- Modify: `core/model/src/main/kotlin/com/aibook/android/core/model/BookModels.kt:96-108`
- Test: `core/model/src/test/kotlin/com/aibook/android/core/model/ReaderContentsStyleTest.kt`

**Interfaces:**
- Produces: `enum class ReaderContentsStyle { CLASSIC, GROUPED; companion object { fun fromStoredValue(value: String?): ReaderContentsStyle } }`
- Produces: `ReaderSettings.contentsStyle: ReaderContentsStyle`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.aibook.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderContentsStyleTest {
    @Test
    fun `reader settings use classic contents by default`() {
        assertEquals(ReaderContentsStyle.CLASSIC, ReaderSettings().contentsStyle)
    }

    @Test
    fun `stored contents style falls back to classic for missing or unknown values`() {
        assertEquals(ReaderContentsStyle.CLASSIC, ReaderContentsStyle.fromStoredValue(null))
        assertEquals(ReaderContentsStyle.CLASSIC, ReaderContentsStyle.fromStoredValue("future-style"))
        assertEquals(ReaderContentsStyle.GROUPED, ReaderContentsStyle.fromStoredValue("GROUPED"))
    }
}
```

- [ ] **Step 2: 运行测试并确认按预期失败**

Run: `./gradlew :core:model:test --tests '*ReaderContentsStyleTest'`

Expected: FAIL，提示 `ReaderContentsStyle` 或 `contentsStyle` 未定义。

- [ ] **Step 3: 添加最小实现**

`ReaderContentsStyle.kt`：

```kotlin
package com.aibook.android.core.model

enum class ReaderContentsStyle {
    CLASSIC,
    GROUPED;

    companion object {
        fun fromStoredValue(value: String?): ReaderContentsStyle =
            entries.firstOrNull { it.name == value } ?: CLASSIC
    }
}
```

在 `ReaderSettings` 末尾增加字段：

```kotlin
val screenAlwaysOn: Boolean = false,
val contentsStyle: ReaderContentsStyle = ReaderContentsStyle.CLASSIC
```

- [ ] **Step 4: 运行测试并确认通过**

Run: `./gradlew :core:model:test --tests '*ReaderContentsStyleTest'`

Expected: PASS，0 failures。

- [ ] **Step 5: 只提交本任务代码块**

```bash
git add core/model/src/main/kotlin/com/aibook/android/core/model/ReaderContentsStyle.kt core/model/src/test/kotlin/com/aibook/android/core/model/ReaderContentsStyleTest.kt
git add -p core/model/src/main/kotlin/com/aibook/android/core/model/BookModels.kt
git commit -m "feat: 添加阅读器目录样式模型"
```

在交互式暂存中只选择 `ReaderSettings.contentsStyle` 代码块，不暂存用户已有的 `LocalBook.description` 修改。

### Task 2: 分卷与阅读状态纯逻辑

**Files:**
- Create: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderContentsCatalog.kt`
- Test: `app/src/test/kotlin/com/aibook/android/feature/reader/ReaderContentsCatalogTest.kt`

**Interfaces:**
- Consumes: `ReaderChapter(index, title, href, content, imageUri)`
- Produces: `ReaderContentsGroup(title: String, chapters: List<ReaderChapter>)`
- Produces: `ReaderChapterReadState { READ, CURRENT, UNREAD }`
- Produces: `ReaderContentsCatalog.group(chapters)`, `currentGroupIndex(groups, currentChapterIndex)`, `readState(chapterIndex, currentChapterIndex)`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderChapter
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderContentsCatalogTest {
    private fun chapter(index: Int, title: String) =
        ReaderChapter(index, title, "chapter-$index", "content")

    @Test
    fun `groups common volume headings and keeps heading chapters`() {
        val groups = ReaderContentsCatalog.group(
            listOf(
                chapter(0, "序章"),
                chapter(1, "第一卷 星火初燃"),
                chapter(2, "第一章 少年出山"),
                chapter(3, "卷二 暗潮涌动"),
                chapter(4, "第二章 夜探玄铁坊")
            )
        )

        assertEquals(listOf("正文", "第一卷 星火初燃", "卷二 暗潮涌动"), groups.map { it.title })
        assertEquals(listOf(0), groups[0].chapters.map { it.index })
        assertEquals(listOf(1, 2), groups[1].chapters.map { it.index })
        assertEquals(listOf(3, 4), groups[2].chapters.map { it.index })
    }

    @Test
    fun `uses body group when no volume heading exists`() {
        val groups = ReaderContentsCatalog.group(listOf(chapter(0, "第一章"), chapter(1, "第二章")))
        assertEquals(listOf("正文"), groups.map { it.title })
        assertEquals(listOf(0, 1), groups.single().chapters.map { it.index })
    }

    @Test
    fun `finds current group and derives chapter read states`() {
        val groups = ReaderContentsCatalog.group(
            listOf(chapter(0, "上卷"), chapter(1, "第一章"), chapter(2, "下卷"), chapter(3, "第二章"))
        )
        assertEquals(1, ReaderContentsCatalog.currentGroupIndex(groups, 3))
        assertEquals(ReaderChapterReadState.READ, ReaderContentsCatalog.readState(1, 2))
        assertEquals(ReaderChapterReadState.CURRENT, ReaderContentsCatalog.readState(2, 2))
        assertEquals(ReaderChapterReadState.UNREAD, ReaderContentsCatalog.readState(3, 2))
    }
}
```

- [ ] **Step 2: 运行测试并确认按预期失败**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReaderContentsCatalogTest'`

Expected: FAIL，提示 `ReaderContentsCatalog` 未定义。

- [ ] **Step 3: 添加最小实现**

```kotlin
package com.aibook.android.feature.reader

import com.aibook.android.core.reader.ReaderChapter

data class ReaderContentsGroup(val title: String, val chapters: List<ReaderChapter>)

enum class ReaderChapterReadState { READ, CURRENT, UNREAD }

object ReaderContentsCatalog {
    private val volumeHeading = Regex(
        "^(?:第[0-9零〇一二两三四五六七八九十百千]+卷|卷[0-9零〇一二两三四五六七八九十百千]+|[上中下终序]卷)(?:\\s|　|[:：·._-]|$).*"
    )

    fun group(chapters: List<ReaderChapter>): List<ReaderContentsGroup> {
        if (chapters.isEmpty()) return emptyList()
        val groups = mutableListOf<ReaderContentsGroup>()
        var title = "正文"
        var items = mutableListOf<ReaderChapter>()
        chapters.forEach { chapter ->
            if (volumeHeading.matches(chapter.title.trim())) {
                if (items.isNotEmpty()) groups += ReaderContentsGroup(title, items.toList())
                title = chapter.title.ifBlank { "正文" }
                items = mutableListOf()
            }
            items += chapter
        }
        if (items.isNotEmpty()) groups += ReaderContentsGroup(title, items.toList())
        return groups
    }

    fun currentGroupIndex(groups: List<ReaderContentsGroup>, currentChapterIndex: Int): Int =
        groups.indexOfFirst { group -> group.chapters.any { it.index == currentChapterIndex } }
            .takeIf { it >= 0 } ?: 0

    fun readState(chapterIndex: Int, currentChapterIndex: Int): ReaderChapterReadState = when {
        chapterIndex < currentChapterIndex -> ReaderChapterReadState.READ
        chapterIndex == currentChapterIndex -> ReaderChapterReadState.CURRENT
        else -> ReaderChapterReadState.UNREAD
    }
}
```

- [ ] **Step 4: 运行测试并确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReaderContentsCatalogTest'`

Expected: PASS，0 failures。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/aibook/android/feature/reader/ReaderContentsCatalog.kt app/src/test/kotlin/com/aibook/android/feature/reader/ReaderContentsCatalogTest.kt
git commit -m "feat: 添加阅读器章节分卷逻辑"
```

### Task 3: DataStore 与 ViewModel 接线

**Files:**
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/prefs/ReaderSettingsStore.kt`
- Test: `core/data/src/test/kotlin/com/aibook/android/core/data/prefs/ReaderSettingsStoreTest.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderViewModel.kt:98-110,424-520`

**Interfaces:**
- Consumes: `ReaderContentsStyle.fromStoredValue(String?)`
- Produces: `ReaderSettingsStore.contentsStyle: Flow<ReaderContentsStyle>`
- Produces: `suspend ReaderSettingsStore.setContentsStyle(style: ReaderContentsStyle)`
- Produces: `ReaderViewModel.setContentsStyle(style: ReaderContentsStyle)`

- [ ] **Step 1: 让 Store 可注入 DataStore，并写失败的持久化往返测试**

将 Store 构造调整为 `ReaderSettingsStore(private val dataStore: DataStore<Preferences>)`，保留 `constructor(context: Context) : this(context.readerSettingsStore)`。测试使用真实 Preferences DataStore：

```kotlin
package com.aibook.android.core.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.aibook.android.core.model.ReaderContentsStyle
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSettingsStoreTest {
    @Test
    fun `contents style defaults to classic and persists grouped`() = runTest {
        val file = Files.createTempDirectory("reader-settings").resolve("prefs.preferences_pb").toFile()
        val store = ReaderSettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })
        )
        assertEquals(ReaderContentsStyle.CLASSIC, store.contentsStyle.first())
        store.setContentsStyle(ReaderContentsStyle.GROUPED)
        assertEquals(ReaderContentsStyle.GROUPED, store.contentsStyle.first())
    }
}
```

- [ ] **Step 2: 运行测试并确认按预期失败**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*ReaderSettingsStoreTest'`

Expected: FAIL，提示构造参数、`contentsStyle` 或 `setContentsStyle` 不存在。

- [ ] **Step 3: 实现 Store 读取和写入**

在 Keys 中加入：

```kotlin
val CONTENTS_STYLE = stringPreferencesKey("contents_style")
```

所有原有 `context.readerSettingsStore` 改为 `dataStore`，并加入：

```kotlin
val contentsStyle: Flow<ReaderContentsStyle> = dataStore.data.map {
    ReaderContentsStyle.fromStoredValue(it[Keys.CONTENTS_STYLE])
}

suspend fun setContentsStyle(style: ReaderContentsStyle) {
    dataStore.edit { it[Keys.CONTENTS_STYLE] = style.name }
}
```

- [ ] **Step 4: 运行 Store 测试并确认通过**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*ReaderSettingsStoreTest'`

Expected: PASS，0 failures。

- [ ] **Step 5: 在 ViewModel 中同步并更新该设置**

增加 import 和 collector：

```kotlin
import com.aibook.android.core.model.ReaderContentsStyle

viewModelScope.launch {
    readerSettingsStore.contentsStyle.collect { value ->
        _state.update { it.copy(settings = it.settings.copy(contentsStyle = value)) }
    }
}
```

增加 setter：

```kotlin
fun setContentsStyle(style: ReaderContentsStyle) {
    viewModelScope.launch { readerSettingsStore.setContentsStyle(style) }
}
```

在 `cancelSettings()` 恢复 `snapshot.contentsStyle`，在 `resetSettings()` 恢复 `defaults.contentsStyle`。

- [ ] **Step 6: 编译 ViewModel 接线**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 7: 提交**

```bash
git add core/data/src/main/kotlin/com/aibook/android/core/data/prefs/ReaderSettingsStore.kt core/data/src/test/kotlin/com/aibook/android/core/data/prefs/ReaderSettingsStoreTest.kt app/src/main/kotlin/com/aibook/android/feature/reader/ReaderViewModel.kt
git commit -m "feat: 持久化阅读器目录样式"
```

### Task 4: 阅读设置切换和分卷目录 Compose UI

**Files:**
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt:178-187,838-964,1139-1270`

**Interfaces:**
- Consumes: `ReaderSettings.contentsStyle`
- Consumes: `ReaderContentsCatalog.group`, `currentGroupIndex`, `readState`
- Calls: `ReaderViewModel.setContentsStyle`

- [ ] **Step 1: 在阅读设置中增加目录样式切换**

在“翻页方式”后添加：

```kotlin
item {
    SegmentedSetting(
        "目录样式",
        listOf("经典", "分卷"),
        selected = settings.contentsStyle.ordinal,
        onSelect = { viewModel.setContentsStyle(ReaderContentsStyle.entries[it]) }
    )
}
```

- [ ] **Step 2: 将目录列表拆成经典与分卷分支**

保留现有 `LazyColumn` 为 `ClassicContentsList`，并根据设置选择：

```kotlin
when (state.settings.contentsStyle) {
    ReaderContentsStyle.CLASSIC -> ClassicContentsList(
        chapters = chapters,
        state = state,
        onChapterClick = onChapterClick
    )
    ReaderContentsStyle.GROUPED -> GroupedContentsList(
        chapters = chapters,
        currentChapterIndex = state.currentChapterIndex,
        errorMessage = state.errorMessage,
        onChapterClick = onChapterClick
    )
}
```

- [ ] **Step 3: 实现默认展开当前卷的列表状态**

```kotlin
val groups = remember(chapters) { ReaderContentsCatalog.group(chapters) }
val initialGroup = remember(groups, currentChapterIndex) {
    ReaderContentsCatalog.currentGroupIndex(groups, currentChapterIndex)
}
val expanded = remember(groups, currentChapterIndex) {
    mutableStateMapOf<Int, Boolean>().apply { if (groups.isNotEmpty()) put(initialGroup, true) }
}
```

用 `LazyColumn.itemsIndexed(groups)` 渲染 `GroupedContentsCard`，卡片头点击时执行 `expanded[index] = expanded[index] != true`。

- [ ] **Step 4: 实现参考图中部的分卷卡片与章节行**

`GroupedContentsCard` 使用白色 `Card`、`RoundedCornerShape(16.dp)`、`BorderStroke(1.dp, DesignTokens.Hairline)`；头部显示卷名和 `KeyboardArrowUp/KeyboardArrowDown`。展开时按原始索引渲染章节。

章节行核心样式：

```kotlin
val readState = ReaderContentsCatalog.readState(chapter.index, currentChapterIndex)
val selected = readState == ReaderChapterReadState.CURRENT
val tint = if (selected) DesignTokens.Accent else DesignTokens.SoftText
val status = when (readState) {
    ReaderChapterReadState.READ -> "已读"
    ReaderChapterReadState.CURRENT -> "当前阅读"
    ReaderChapterReadState.UNREAD -> "未读"
}

Row(
    Modifier
        .fillMaxWidth()
        .background(if (selected) DesignTokens.Accent.copy(alpha = 0.08f) else Color.Transparent)
        .clickable(onClick = onClick)
        .heightIn(min = 62.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Box(Modifier.width(4.dp).fillMaxHeight().background(if (selected) DesignTokens.Accent else Color.Transparent))
    Text((chapter.index + 1).toString().padStart(2, '0'), color = tint)
    Text(chapter.title.ifBlank { "第${chapter.index + 1}章" }, Modifier.weight(1f), color = if (selected) DesignTokens.Accent else MaterialTheme.colorScheme.onSurface)
    Text(status, color = tint)
}
```

章节之间加入 `HorizontalDivider(color = DesignTokens.Hairline)`；所有点击区域沿用默认颜色反馈，不增加 `shadow`。

- [ ] **Step 5: 编译 UI 并运行相关测试**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReaderContentsCatalogTest' :app:compileDebugKotlin`

Expected: tests PASS，BUILD SUCCESSFUL，0 Kotlin compile errors。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt
git commit -m "feat: 添加阅读器分卷目录界面"
```

### Task 5: 完整验证与范围审计

**Files:**
- Verify only; no planned production changes.

**Interfaces:**
- Verifies all interfaces and constraints from Tasks 1-4.

- [ ] **Step 1: 运行核心相关测试**

Run: `./gradlew :core:model:test :core:data:testDebugUnitTest :app:testDebugUnitTest`

Expected: BUILD SUCCESSFUL，0 failures。

- [ ] **Step 2: 构建 Debug APK**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL，生成 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 3: 检查格式与变更范围**

Run: `git diff --check`

Expected: 无输出，exit 0。

Run: `git status --short`

Expected: 只出现用户原有未提交修改；本任务提交包含的文件与本计划“文件结构”一致，不包含 `.gradle` 缓存和其它页面。

- [ ] **Step 4: 对照规格逐项检查**

确认默认经典样式、持久化切换、卷标题保留、当前卷默认展开、三种阅读状态、无阴影点击态、空目录兼容和非目标页面零改动均有对应代码或测试证据。
