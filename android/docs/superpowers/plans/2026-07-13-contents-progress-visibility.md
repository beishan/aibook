# 目录阅读进度卡片显示设置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增加全局持久化开关，让用户控制目录阅读进度卡片是否显示。

**Architecture:** 设置值进入 `ReaderSettings` 和 DataStore，由 `ReaderViewModel` 统一收集及修改。设置页提供开关，目录页根据该值条件渲染现有卡片。

**Tech Stack:** Kotlin、Jetpack Compose、DataStore Preferences、Coroutines Test、Gradle

## Global Constraints

- 默认值必须为 `true`，保持升级后的现有界面。
- 设置全局生效，不增加书籍级数据库字段。
- 不改变进度卡片本身的视觉样式。

---

### Task 1: 持久化设置模型

**Files:**
- Modify: `core/data/src/test/kotlin/com/aibook/android/core/data/prefs/ReaderSettingsStoreTest.kt`
- Modify: `core/model/src/main/kotlin/com/aibook/android/core/model/BookModels.kt`
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/prefs/ReaderSettingsStore.kt`

**Interfaces:**
- Produces: `ReaderSettings.showContentsProgress: Boolean`
- Produces: `ReaderSettingsStore.showContentsProgress: Flow<Boolean>` 和 `setShowContentsProgress(Boolean)`。

- [ ] **Step 1: 写失败测试**

```kotlin
assertEquals(true, store.showContentsProgress.first())
store.setShowContentsProgress(false)
assertEquals(false, store.showContentsProgress.first())
```

- [ ] **Step 2: 运行测试确认 RED**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*ReaderSettingsStoreTest'`
Expected: FAIL，提示显示设置接口不存在。

- [ ] **Step 3: 增加模型字段、布尔 Preferences key、Flow 和 setter**

```kotlin
val showContentsProgress: Boolean = true
val showContentsProgress = dataStore.data.map { it[Keys.SHOW_CONTENTS_PROGRESS] ?: true }
suspend fun setShowContentsProgress(show: Boolean) {
    dataStore.edit { it[Keys.SHOW_CONTENTS_PROGRESS] = show }
}
```

- [ ] **Step 4: 重跑测试确认 GREEN**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*ReaderSettingsStoreTest'`
Expected: PASS。

### Task 2: ViewModel 与界面接线

**Files:**
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: Task 1 的设置 Flow 和 setter。
- Produces: `ReaderViewModel.setShowContentsProgress(Boolean)`。

- [ ] **Step 1: ViewModel 收集设置并接入取消与重置流程**

```kotlin
readerSettingsStore.showContentsProgress.collect { value ->
    _state.update { it.copy(settings = it.settings.copy(showContentsProgress = value)) }
}
```

- [ ] **Step 2: 排版设置页在目录样式后增加开关**

```kotlin
SwitchSetting(
    "显示目录阅读进度",
    "在目录顶部显示阅读章节、进度与时长",
    settings.showContentsProgress,
    viewModel::setShowContentsProgress
)
```

- [ ] **Step 3: 目录页按开关条件渲染卡片**

```kotlin
if (state.settings.showContentsProgress) {
    ContentsProgressCard(state)
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

### Task 3: 完整回归

- [ ] **Step 1: 运行全部 App 和 core:data 单元测试**

Run: `./gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 检查差异**

Run: `git diff --check`
Expected: 无输出，退出码 0。
