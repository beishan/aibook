# Store Local Book Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a non-destructive action that hides locally imported books from the Android store catalog and restores them when the same file is imported again.

**Architecture:** Persist local store visibility on `LocalBook`/`BookEntity`. Route visibility changes through `BookRepository`, filter local store entries in `StoreCatalog`, and expose a store management UI in `BookStoreScreen`.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Coroutines/StateFlow, kotlin.test.

## Global Constraints

- Do not delete book files for this feature.
- Removing from store must also remove from shelf.
- Re-importing the same file after removal must restore store visibility.
- Android button pressed state must avoid shadow or pressed elevation effects.
- Preserve unrelated dirty worktree changes.

---

### Task 1: Persist Store Visibility

**Files:**
- Modify: `core/model/src/main/kotlin/com/aibook/android/core/model/BookModels.kt`
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/db/BookEntity.kt`
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/mapper/EntityMappers.kt`
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/db/AiBookDatabase.kt`
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/db/BookDao.kt`

**Interfaces:**
- Produces: `LocalBook.visibleInStore: Boolean`
- Produces: `BookDao.setStoreVisible(id: String, visibleInStore: Boolean)`
- Produces: `BookDao.restoreStoreVisibility(id: String)`

Steps:
- [ ] Add `visibleInStore: Boolean = true` to `LocalBook`.
- [ ] Add `visibleInStore: Boolean = true` to `BookEntity`.
- [ ] Map `visibleInStore` both ways in `EntityMappers.kt`.
- [ ] Increment Room database version from 7 to 8.
- [ ] Add DAO update methods for hiding/restoring visibility.

### Task 2: Restore On Duplicate Import

**Files:**
- Modify: `core/data/src/main/kotlin/com/aibook/android/core/data/repository/BookRepository.kt`

**Interfaces:**
- Produces: `BookRepository.removeFromStore(id: String)`
- Produces: duplicate import behavior that restores hidden existing books.

Steps:
- [ ] In `importBook`, when `getBySha256` returns an existing hidden book, delete the temporary copied file, call `restoreStoreVisibility(existing.id)`, and return `ImportResult.Duplicate` with the restored domain object.
- [ ] Apply the same behavior to `importDownloadedBook`.
- [ ] Add `removeFromStore(id)` that sets `visibleInStore=false`, `shelved=false`, and clears `folderId`.

### Task 3: Filter Store Catalog

**Files:**
- Modify: `app/src/main/kotlin/com/aibook/android/feature/store/StoreCatalog.kt`
- Modify: `app/src/main/kotlin/com/aibook/android/feature/store/StoreViewModel.kt`
- Modify: `app/src/test/kotlin/com/aibook/android/feature/store/StoreCatalogTest.kt`

**Interfaces:**
- Consumes: `LocalBook.visibleInStore`.
- Produces: `StoreCatalog.LocalInput.visibleInStore`.
- Produces: `StoreViewModel.removeLocalBookFromStore(book: StoreBook)`.
- Produces: `StoreViewModel.removeSelectedLocalBooksFromStore()`.

Steps:
- [ ] Add `visibleInStore` to `StoreCatalog.LocalInput`.
- [ ] Keep hidden local books out of local catalog items.
- [ ] Keep local matching data available so OPDS entries can still show downloaded status for existing files.
- [ ] Add tests proving hidden local books are excluded and visible books remain.
- [ ] Map `LocalBook.visibleInStore` in `StoreViewModel`.
- [ ] Add ViewModel methods for single and selected local removal.

### Task 4: Add Store Management UI

**Files:**
- Modify: `app/src/main/kotlin/com/aibook/android/feature/store/BookStoreScreen.kt`

**Interfaces:**
- Consumes: `StoreViewModel.removeLocalBookFromStore`.
- Consumes: `StoreViewModel.removeSelectedLocalBooksFromStore`.

Steps:
- [ ] Add store management mode and selected IDs state.
- [ ] Add top-level “管理/取消” action.
- [ ] In management mode, selecting a local book toggles selection; OPDS books still open normally unless already downloaded as local.
- [ ] Add a management bar with select-all-local and remove-from-store actions.
- [ ] Add a per-local-book action label “移出书城” outside management mode.
- [ ] Disable destructive wording; use “移出书城” and “不会删除文件” messaging.

### Task 5: Verify

**Files:**
- Test: `app/src/test/kotlin/com/aibook/android/feature/store/StoreCatalogTest.kt`
- Build: Gradle tasks.

Steps:
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.aibook.android.feature.store.StoreCatalogTest`.
- [ ] Run a compile/test task that exercises Room schema generation, such as `./gradlew :core:data:testDebugUnitTest` if available.
- [ ] If Gradle cannot run due to sandbox or environment issues, capture the exact failure and retry with approved escalation only if it is a sandbox/network problem.
