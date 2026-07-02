# Android Reader Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android reader client under `/Users/beibei/aiprojects/ai-book/android` that can manage local books, prepare for file import and reading, and connect to the existing OPDS service.

**Architecture:** The Android client is a separate Gradle multi-module project using Kotlin, Jetpack Compose, Room-ready data boundaries, Retrofit-ready network boundaries, and small feature modules. The first implementation creates the app shell, domain models, OPDS feed parser, local import policy, settings surface, and tests for pure Kotlin behavior.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android Gradle Plugin 9.2.0, compileSdk 36, minSdk 29, Room, DataStore, OkHttp, Retrofit, Coil, WorkManager.

## Global Constraints

- Project path: `/Users/beibei/aiprojects/ai-book/android`
- Android target: compile with Android 16 / API 36.
- Minimum supported Android version: Android 10 / API 29.
- UI: Jetpack Compose + Material 3.
- Backend integration: OPDS must support Basic Auth and connect to the existing `/opds/**` backend endpoints.
- Local import: support EPUB, TXT, PDF, MD, and HTML in the first architecture.
- No implementation code without tests for pure Kotlin behavior.

---

### Task 1: Project Skeleton

**Files:**
- Create: `/Users/beibei/aiprojects/ai-book/android/settings.gradle.kts`
- Create: `/Users/beibei/aiprojects/ai-book/android/build.gradle.kts`
- Create: `/Users/beibei/aiprojects/ai-book/android/gradle.properties`
- Create: `/Users/beibei/aiprojects/ai-book/android/app/build.gradle.kts`
- Create: `/Users/beibei/aiprojects/ai-book/android/core/model/build.gradle.kts`
- Create: `/Users/beibei/aiprojects/ai-book/android/core/network/build.gradle.kts`
- Create: `/Users/beibei/aiprojects/ai-book/android/core/data/build.gradle.kts`

**Interfaces:**
- Produces Gradle modules: `:app`, `:core:model`, `:core:network`, `:core:data`.

- [ ] Create version catalog-free Gradle build files with explicit dependency versions.
- [ ] Set `compileSdk = 36`, `minSdk = 29`, `targetSdk = 36`.
- [ ] Enable Compose in `:app`.

### Task 2: Domain Model And Import Policy

**Files:**
- Create: `/Users/beibei/aiprojects/ai-book/android/core/model/src/main/kotlin/com/aibook/android/core/model/BookModels.kt`
- Create: `/Users/beibei/aiprojects/ai-book/android/core/model/src/main/kotlin/com/aibook/android/core/model/ImportPolicy.kt`
- Test: `/Users/beibei/aiprojects/ai-book/android/core/model/src/test/kotlin/com/aibook/android/core/model/ImportPolicyTest.kt`

**Interfaces:**
- Produces: `BookFormat.fromFileName(fileName: String): BookFormat?`
- Produces: `ImportPolicy.isSupported(fileName: String): Boolean`
- Produces: `ImportPolicy.normalizedTitle(fileName: String): String`

- [ ] Write tests for supported formats: EPUB, TXT, PDF, MD, HTML.
- [ ] Write tests rejecting unsupported files.
- [ ] Implement minimal model and import policy.

### Task 3: OPDS Parser

**Files:**
- Create: `/Users/beibei/aiprojects/ai-book/android/core/network/src/main/kotlin/com/aibook/android/core/network/opds/OpdsModels.kt`
- Create: `/Users/beibei/aiprojects/ai-book/android/core/network/src/main/kotlin/com/aibook/android/core/network/opds/OpdsFeedParser.kt`
- Test: `/Users/beibei/aiprojects/ai-book/android/core/network/src/test/kotlin/com/aibook/android/core/network/opds/OpdsFeedParserTest.kt`

**Interfaces:**
- Produces: `OpdsFeedParser.parse(xml: String): OpdsFeed`
- Produces: `OpdsFeed(title: String, entries: List<OpdsEntry>)`

- [ ] Write tests for OPDS 1.2 Atom feeds.
- [ ] Write tests that extract acquisition links.
- [ ] Implement parser using Android-compatible XML pull parsing.

### Task 4: App Shell And Navigation

**Files:**
- Create: `/Users/beibei/aiprojects/ai-book/android/app/src/main/AndroidManifest.xml`
- Create: `/Users/beibei/aiprojects/ai-book/android/app/src/main/kotlin/com/aibook/android/MainActivity.kt`
- Create: `/Users/beibei/aiprojects/ai-book/android/app/src/main/kotlin/com/aibook/android/AiBookApp.kt`
- Create: `/Users/beibei/aiprojects/ai-book/android/app/src/main/kotlin/com/aibook/android/ui/theme/Theme.kt`
- Create: `/Users/beibei/aiprojects/ai-book/android/app/src/main/kotlin/com/aibook/android/feature/shelf/ShelfScreen.kt`
- Create: `/Users/beibei/aiprojects/ai-book/android/app/src/main/kotlin/com/aibook/android/feature/reader/ReaderScreen.kt`
- Create: `/Users/beibei/aiprojects/ai-book/android/app/src/main/kotlin/com/aibook/android/feature/opds/OpdsScreen.kt`
- Create: `/Users/beibei/aiprojects/ai-book/android/app/src/main/kotlin/com/aibook/android/feature/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `BookFormat`, `ImportPolicy`, `OpdsFeedParser`.
- Produces: a Compose app with bottom navigation for Shelf, OPDS, Reader, and Settings.

- [ ] Build Material 3 theme.
- [ ] Build screen navigation with sealed tab model.
- [ ] Add local import entry point UI using Android document picker contract placeholder.
- [ ] Add OPDS connection form fields and catalog preview placeholder.

### Task 5: Verification

**Files:**
- Read: all files under `/Users/beibei/aiprojects/ai-book/android`

- [ ] Run `find android -type f | sort`.
- [ ] Run `rg "compileSdk = 36|targetSdk = 36|minSdk = 29" android`.
- [ ] If Gradle and Android SDK exist, run `cd android && gradle test`.
- [ ] If unavailable, report the exact missing tool and the next command to run after Android Studio setup.
