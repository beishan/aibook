# 阅读操作增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Android 阅读器加入可调亮度、触摸锁定、自动翻页/滚动和横竖屏适配。

**Architecture:** 持久偏好进入 `ReaderSettings` 与 DataStore；会话态锁定和自动阅读停留在 Compose 阅读页面。纯 Kotlin 策略类负责速度换算，分页器与纵向列表分别消费统一的运行状态。

**Tech Stack:** Kotlin、Jetpack Compose、DataStore Preferences、Coroutines、Android Window/Activity orientation API、kotlin.test

## Global Constraints

- 最低 Android API 为 29，不新增第三方依赖。
- 按钮点击态不使用阴影或按压投影。
- 自动阅读离开阅读页或进入后台必须停止；系统返回键不受触摸锁定影响。
- 亮度范围 10%–100%；分页间隔 3–30 秒；纵向速度为慢、中、快三档。

---

### Task 1: 阅读操作模型与纯策略

**Files:**
- Modify: `core/model/src/main/kotlin/com/aibook/android/core/model/BookModels.kt`
- Create: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderAutoPlayPolicy.kt`
- Test: `app/src/test/kotlin/com/aibook/android/feature/reader/ReaderAutoPlayPolicyTest.kt`

**Interfaces:**
- Produces: `ReaderOrientationMode`, `ReaderAutoScrollSpeed`, `ReaderAutoPlayPolicy.pageDelayMillis(Int): Long`、`scrollStepPx(ReaderAutoScrollSpeed, Float): Float`。

- [ ] **Step 1: 写失败测试**，断言页间隔会限制在 3–30 秒，慢/中/快三档滚动距离严格递增。
- [ ] **Step 2: 运行 `./gradlew :app:testDebugUnitTest --tests '*ReaderAutoPlayPolicyTest'`**，预期因策略类不存在而失败。
- [ ] **Step 3: 实现枚举、`ReaderSettings` 新字段和纯策略**：亮度默认 0.6，方向默认 `SYSTEM`，分页默认 8 秒，滚动默认 `MEDIUM`。
- [ ] **Step 4: 再次运行同一测试**，预期 PASS。

### Task 2: 设置持久化与 ViewModel 接线

**Files:**
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/prefs/ReaderSettingsStore.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderViewModel.kt`

**Interfaces:**
- Consumes: Task 1 的模型枚举。
- Produces: `brightness`、`orientationMode`、`autoPageIntervalSeconds`、`autoScrollSpeed` Flow/setter，以及 ViewModel 同名设置方法。

- [ ] **Step 1: 为四项偏好增加强类型 Preferences key 与安全默认值**，枚举解析失败回退默认值。
- [ ] **Step 2: 在 ViewModel 初始化时收集四个 Flow**，同步写入 `ReaderUiState.settings`。
- [ ] **Step 3: 增加 setter，并把字段纳入取消设置恢复和重置设置流程**。
- [ ] **Step 4: 运行 `./gradlew :app:compileDebugKotlin`**，预期 BUILD SUCCESSFUL。

### Task 3: Activity 窗口控制与会话状态

**Files:**
- Create: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderWindowEffects.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Produces: `Activity.findActivity()`、`ReaderWindowEffects(settings, keepScreenOnOverride)`；会话态 `touchLocked`、`autoPlaying`。

- [ ] **Step 1: 实现 Context 到 Activity 的安全解包**，无 Activity 时保持 no-op。
- [ ] **Step 2: 用 `DisposableEffect` 应用窗口亮度与 requestedOrientation**；跟随系统设置 `BRIGHTNESS_OVERRIDE_NONE`，离开阅读器恢复原方向和亮度。
- [ ] **Step 3: 自动阅读期间设置 `FLAG_KEEP_SCREEN_ON`**，停止时恢复用户 `screenAlwaysOn` 偏好。
- [ ] **Step 4: 生命周期进入后台时把 `autoPlaying` 置为 false**。

### Task 4: 自动翻页、自动滚动与防误触

**Files:**
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: Task 1 策略与 Task 3 会话态。
- Produces: `ReaderTextContent(..., autoPlaying, touchLocked, onAutoPlayStopped)` 行为。

- [ ] **Step 1: 分页模式在运行态按 `pageDelayMillis` 调用 `pagerState.animateScrollToPage`**；接近页尾先触发下一章加载，全书末尾停止。
- [ ] **Step 2: 纵向模式以约 16ms 节拍调用 `scrollState.scrollBy`**，使用密度换算慢/中/快三档，无法继续滚动且无下一章时停止。
- [ ] **Step 3: `userScrollEnabled = !touchLocked`，锁定覆盖层消费正文指针事件**；中央按钮使用 `detectTapGestures(onLongPress)` 配合 2000ms 长按时长检测后解锁。
- [ ] **Step 4: 手动翻页/拖动、打开子页面或笔记对话框时暂停自动阅读；返回键继续执行原有返回逻辑**。

### Task 5: 操作面板与横竖屏布局

**Files:**
- Modify: `app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: 持久设置 setter 与会话态回调。
- Produces: “排版/操作”分栏、自动阅读浮层、横屏双栏设置页和正文宽度约束。

- [ ] **Step 1: 阅读设置页增加“排版/操作”页签**；原设置放在排版页，操作页增加跟随系统亮度、10%–100% 滑条、触摸锁定、自动阅读开关/速度与方向三段选择。
- [ ] **Step 2: 自动阅读运行时显示暂停/速度浮层**，颜色来自当前阅读主题，无阴影按压效果。
- [ ] **Step 3: 通过 `LocalConfiguration.current.orientation` 判断横屏**；横屏设置内容用双栏，顶栏和正文上下边距减小，正文使用 `widthIn(max = 760.dp)` 居中。
- [ ] **Step 4: 打开目录、搜索、主题、设置或高亮编辑时统一暂停自动阅读**。

### Task 6: 回归验证

**Files:**
- Test: `app/src/test/kotlin/com/aibook/android/feature/reader/ReaderAutoPlayPolicyTest.kt`

- [ ] **Step 1: 运行 `./gradlew :app:testDebugUnitTest`**，预期所有单元测试 PASS。
- [ ] **Step 2: 运行 `./gradlew :app:compileDebugKotlin`**，预期 BUILD SUCCESSFUL。
- [ ] **Step 3: 检查 `git diff --check`**，预期无空白错误，并确认未覆盖工作区中无关改动。
