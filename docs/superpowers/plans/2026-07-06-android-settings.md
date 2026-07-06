# Android Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a usable Android settings center that wires existing settings state into the UI and adds a synchronization settings subpage.

**Architecture:** Keep the existing single settings feature package. Add a small pure summary helper for testable copy, extend `SettingsScreen.kt` with grouped settings UI and a new `SyncConnectionSettingsScreen`, and wire one new route through `Screen.kt` and `AiBookApp.kt`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, AndroidX ViewModel, Kotlin Flow.

## Global Constraints

- Follow existing Compose style: `DesignPage`, `SoftCard`, `DesignTokens`, Material icons.
- Do not add new backend APIs, database tables, or dependencies.
- Do not change existing settings persistence contracts.
- Keep changes scoped to Android settings and navigation.

---

### Task 1: Add Testable Settings Summary Copy

**Files:**
- Create: `android/app/src/main/kotlin/com/aibook/android/feature/settings/SettingsSummary.kt`
- Test: `android/app/src/test/kotlin/com/aibook/android/feature/settings/SettingsSummaryTest.kt`

**Interfaces:**
- Produces: `object SettingsSummary` with `connectionSubtitle(serverUrl: String, isLoggedIn: Boolean, username: String?): String`
- Consumes: no app state directly.

- [ ] **Step 1: Write failing tests for connection summaries.**
- [ ] **Step 2: Run `./gradlew :app:testDebugUnitTest --tests com.aibook.android.feature.settings.SettingsSummaryTest` and confirm unresolved symbol failure.**
- [ ] **Step 3: Implement `SettingsSummary.connectionSubtitle`.**
- [ ] **Step 4: Re-run the same test and confirm pass.**

### Task 2: Rework Settings Home

**Files:**
- Modify: `android/app/src/main/kotlin/com/aibook/android/feature/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `SettingsSummary.connectionSubtitle(...)`
- Produces: `SettingsScreen(onSyncConnectionClick: () -> Unit = {})`

- [ ] **Step 1: Add `onSyncConnectionClick` parameter.**
- [ ] **Step 2: Reorganize settings home into grouped cards.**
- [ ] **Step 3: Show live summaries for reader theme, font scale, line height, and connection status.**
- [ ] **Step 4: Keep existing scan, storage, privacy, about, and theme callbacks working.**

### Task 3: Add Sync Connection Subpage

**Files:**
- Modify: `android/app/src/main/kotlin/com/aibook/android/feature/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `SettingsViewModel` methods `updateServerUrlInput`, `saveServerUrl`, `updateLoginUsername`, `updateLoginPassword`, `login`, `logout`, `setWifiOnlySync`.
- Produces: `@Composable fun SyncConnectionSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory))`

- [ ] **Step 1: Add server address input and save action.**
- [ ] **Step 2: Add login form, login status, and logout action.**
- [ ] **Step 3: Add Wi-Fi-only sync switch wired to `setWifiOnlySync`.**
- [ ] **Step 4: Use existing `SettingsSubPage`, `SoftCard`, and `SwitchLine` patterns.**

### Task 4: Wire Navigation

**Files:**
- Modify: `android/app/src/main/kotlin/com/aibook/android/navigation/Screen.kt`
- Modify: `android/app/src/main/kotlin/com/aibook/android/AiBookApp.kt`

**Interfaces:**
- Produces: `Screen.SyncConnectionSettings`
- Consumes: `SyncConnectionSettingsScreen`

- [ ] **Step 1: Add route object.**
- [ ] **Step 2: Add route to settings bottom-tab mapping.**
- [ ] **Step 3: Pass `onSyncConnectionClick` from settings home.**
- [ ] **Step 4: Add composable destination for sync settings.**

### Task 5: Verify

**Files:**
- No code changes expected.

**Commands:**
- `git diff --check`
- `./gradlew :app:testDebugUnitTest --tests com.aibook.android.feature.settings.SettingsSummaryTest`
- `./gradlew :app:assembleDebug`

**Expected:** diff check passes, targeted test passes, debug build completes.
