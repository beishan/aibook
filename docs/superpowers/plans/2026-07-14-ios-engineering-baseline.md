# iOS Engineering Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the existing iOS prototype into a single-repository Xcode 16+/Swift 6 project with a launchable four-tab shell, explicit concurrency boundaries, deterministic unit tests, and a UI smoke test.

**Architecture:** Preserve the existing SwiftUI, SwiftData, Repository, and ServiceLocator structure while moving the Xcode project and test targets into the main repository. Keep UI state and SwiftData on `@MainActor`, make cross-actor value models `Sendable`, move import preparation off the main actor, and expose persistence fallback state to the application shell.

**Tech Stack:** Xcode 16+, Swift 6, SwiftUI, Observation, SwiftData, URLSession, XCTest, iOS 17+

## Global Constraints

- Xcode 16+ and Swift 6 are the only release acceptance toolchain.
- Minimum deployment target is iOS 17.0.
- The main Git repository owns every iOS source, resource, test, and Xcode project file.
- Preserve all existing business source and the uncommitted nested Xcode project configuration before removing the nested Git metadata.
- Do not add Readium, Kingfisher, SwiftSoup, or another third-party dependency in this milestone.
- UI, ViewModels, UserDefaults stores, ServiceLocator, and SwiftData access run on `@MainActor`.
- Cross-actor domain values conform to `Sendable`; do not use broad `@unchecked Sendable` declarations.
- Command-line builds disable code signing and write DerivedData under `/tmp`.
- Existing unrelated worktree changes must not be staged or committed.

---

## File Map

**Move into the main iOS project root**

- `ios/AiBook/AiBook/AiBook.xcodeproj` → `ios/AiBook.xcodeproj`: canonical Xcode project, retaining the current uncommitted source references.
- `ios/AiBook/AiBook/AiBook/Assets.xcassets` → `ios/AiBook/Resources/Assets.xcassets`: application asset catalog.
- `ios/AiBook/AiBook/AiBook/Preview Content` → `ios/AiBook/Resources/Preview Content`: preview-only assets.
- `ios/AiBook/AiBook/AiBookTests` → `ios/AiBookTests`: unit-test target sources.
- `ios/AiBook/AiBook/AiBookUITests` → `ios/AiBookUITests`: UI-test target sources.

**Create**

- `ios/AiBookTests/BookFormatTests.swift`: filename parsing and supported-format tests.
- `ios/AiBookTests/ImportPolicyTests.swift`: import policy and title normalization tests.
- `ios/AiBookTests/ShelfBookSorterTests.swift`: deterministic shelf sorting tests.
- `ios/AiBookTests/SendableConformanceTests.swift`: compile-time checks for cross-actor domain values.
- `ios/AiBookTests/PersistenceBootstrapTests.swift`: in-memory bootstrap and fallback-state tests.
- `ios/AiBook/Core/Data/Database/PersistenceBootstrap.swift`: persistence container plus user-visible fallback diagnostic.
- `ios/AiBook/Core/Import/BookImportPreparer.swift`: off-main-actor file reading, hashing, and metadata preparation.

**Modify**

- `ios/AiBook.xcodeproj/project.pbxproj`: paths, target membership, Swift 6 settings, strict concurrency, and iOS 17 target.
- `ios/AiBook/App/AiBookApp.swift`: consume persistence bootstrap result and expose a startup diagnostic alert.
- `ios/AiBook/App/ContentView.swift`: add stable accessibility identifiers for the four tabs.
- `ios/AiBook/Core/DI/ServiceLocator.swift`: initialize from `PersistenceBootstrap` and expose the startup issue.
- `ios/AiBook/Core/Data/Database/AiBookContainer.swift`: provide throwing persistent and in-memory constructors.
- `ios/AiBook/Core/Model/BookModels.swift`: add `Sendable` to domain values crossing actor boundaries.
- `ios/AiBook/Core/Model/OpdsModels.swift`: add `Sendable` to OPDS value models.
- `ios/AiBook/Core/Model/StoreModels.swift`: add `Sendable` to store value models.
- `ios/AiBook/Core/Reader/ReaderModels.swift`: add `Sendable` to reader value models.
- `ios/AiBook/Core/Data/Prefs/ReaderSettingsStore.swift`: isolate UserDefaults access to `@MainActor`.
- `ios/AiBook/Core/Data/Prefs/ServerConfigStore.swift`: isolate UserDefaults access to `@MainActor`.
- `ios/AiBook/Core/Network/ApiClient.swift`: isolate configuration access and require sendable response values.
- `ios/AiBook/Feature/Importer/LocalBookImporter.swift`: use async import preparation without blocking UI.
- `ios/AiBook/Core/Data/Repository/BookRepository.swift`: persist a prepared import on `@MainActor`.
- Every `Feature/*/*ViewModel.swift`: explicitly add `@MainActor` before `@Observable`.
- `ios/AiBookUITests/AiBookUITests.swift`: four-tab launch smoke test.
- `ios/README.md`: canonical project layout and build/test instructions.

**Remove after verifying no references**

- `ios/AiBook/AiBook/.git`: nested repository metadata, first moved to `/tmp/aibook-ios-nested-git-backup` for recovery.
- `ios/AiBook/AiBook/AiBook/Item.swift`: unused Xcode template model.
- Empty directories left below `ios/AiBook/AiBook` after migration.

---

### Task 1: Normalize Repository and Xcode Project Layout

**Files:**
- Move: `ios/AiBook/AiBook/AiBook.xcodeproj` → `ios/AiBook.xcodeproj`
- Move: `ios/AiBook/AiBook/AiBook/Assets.xcassets` → `ios/AiBook/Resources/Assets.xcassets`
- Move: `ios/AiBook/AiBook/AiBook/Preview Content` → `ios/AiBook/Resources/Preview Content`
- Move: `ios/AiBook/AiBook/AiBookTests` → `ios/AiBookTests`
- Move: `ios/AiBook/AiBook/AiBookUITests` → `ios/AiBookUITests`
- Modify: `ios/AiBook.xcodeproj/project.pbxproj`
- Remove: `ios/AiBook/AiBook/AiBook/Item.swift`

**Interfaces:**
- Consumes: the modified nested `project.pbxproj` that already references the business source tree.
- Produces: one canonical project at `ios/AiBook.xcodeproj` whose App target reads source from `ios/AiBook`.

- [ ] **Step 1: Capture the exact nested state before moving files**

Run:

```bash
git status --short
git -C ios/AiBook/AiBook status --short
git -C ios/AiBook/AiBook diff -- AiBook.xcodeproj/project.pbxproj > /tmp/aibook-ios-project-before-migration.diff
cp ios/AiBook/AiBook/AiBook.xcodeproj/project.pbxproj /tmp/aibook-ios-project-before-migration.pbxproj
```

Expected: the root reports `m ios/AiBook/AiBook`; the nested repository reports its modified project and deleted template app files; both backup files exist under `/tmp`.

- [ ] **Step 2: Make the structural assertion fail before migration**

Run:

```bash
test -d ios/AiBook.xcodeproj
```

Expected: FAIL because the project still lives at `ios/AiBook/AiBook/AiBook.xcodeproj`.

- [ ] **Step 3: Detach the Gitlink and preserve nested Git metadata outside the workspace**

Run:

```bash
git rm --cached ios/AiBook/AiBook
mv ios/AiBook/AiBook/.git /tmp/aibook-ios-nested-git-backup
```

Expected: `git ls-files -s ios/AiBook/AiBook` returns no `160000` entry and `/tmp/aibook-ios-nested-git-backup/HEAD` exists.

- [ ] **Step 4: Move the project, assets, and test sources into canonical locations**

Run:

```bash
mkdir -p ios/AiBook/Resources
mv ios/AiBook/AiBook/AiBook.xcodeproj ios/AiBook.xcodeproj
mv ios/AiBook/AiBook/AiBook/Assets.xcassets ios/AiBook/Resources/Assets.xcassets
mv "ios/AiBook/AiBook/AiBook/Preview Content" "ios/AiBook/Resources/Preview Content"
mv ios/AiBook/AiBook/AiBookTests ios/AiBookTests
mv ios/AiBook/AiBook/AiBookUITests ios/AiBookUITests
```

Expected layout:

```text
ios/AiBook.xcodeproj/project.pbxproj
ios/AiBook/App/AiBookApp.swift
ios/AiBook/Resources/Assets.xcassets
ios/AiBookTests/AiBookTests.swift
ios/AiBookUITests/AiBookUITests.swift
```

- [ ] **Step 5: Rewrite Xcode paths and remove `Item.swift` membership**

In `project.pbxproj`:

```text
App/Core/Feature/Navigation/UI groups: paths relative to ios/AiBook
Assets.xcassets: ios/AiBook/Resources/Assets.xcassets
Preview Content: ios/AiBook/Resources/Preview Content
AiBookTests group: ios/AiBookTests
AiBookUITests group: ios/AiBookUITests
DEVELOPMENT_ASSET_PATHS: "AiBook/Resources/Preview Content"
```

Delete the `PBXBuildFile`, `PBXFileReference`, group child, and Sources build-phase entries for `Item.swift`, then remove the physical file.

Verify every old relative escape has been removed:

```bash
rg -n '\.\./\.\./|Item\.swift|AiBook/Preview Content' ios/AiBook.xcodeproj/project.pbxproj
```

Expected: no output.

- [ ] **Step 6: Verify project structure**

Run:

```bash
test -d ios/AiBook.xcodeproj
test -f ios/AiBook/App/AiBookApp.swift
test -f ios/AiBookTests/AiBookTests.swift
test -z "$(git ls-files -s ios | awk '$1 == 160000 { print $4 }')"
xcodebuild -project ios/AiBook.xcodeproj -list
```

Expected: all `test` commands pass; no Gitlink path is printed; Xcode lists `AiBook`, `AiBookTests`, and `AiBookUITests`.

- [ ] **Step 7: Commit the normalized layout**

```bash
git add ios
git commit -m "refactor: 统一iOS工程目录结构"
```

Expected: only iOS migration files are committed; `/tmp` backups and unrelated workspace changes are absent.

---

### Task 2: Add Deterministic Domain and Import Policy Tests

**Files:**
- Replace: `ios/AiBookTests/AiBookTests.swift`
- Create: `ios/AiBookTests/BookFormatTests.swift`
- Create: `ios/AiBookTests/ImportPolicyTests.swift`
- Create: `ios/AiBookTests/ShelfBookSorterTests.swift`
- Modify: `ios/AiBook/Core/Model/ShelfBookSorter.swift`

**Interfaces:**
- Consumes: `BookFormat.fromFileName(_:)`, `ImportPolicy`, `ShelfBookSorter.sort(_:by:)`.
- Produces: deterministic sorting when primary keys are equal; the secondary key is `LocalBook.id` ascending.

- [ ] **Step 1: Replace the template test with BookFormat and ImportPolicy failures**

Add XCTest cases that assert:

```swift
XCTAssertEqual(BookFormat.fromFileName("Novel.EPUB"), .epub)
XCTAssertEqual(BookFormat.fromFileName("notes.md"), .markdown)
XCTAssertNil(BookFormat.fromFileName("archive.zip"))
XCTAssertTrue(ImportPolicy.isSupported(fileName: "manual.pdf"))
XCTAssertFalse(ImportPolicy.isSupported(fileName: "manual.docx"))
XCTAssertEqual(ImportPolicy.normalizedTitle(from: "  汗牛充栋.epub"), "汗牛充栋")
```

- [ ] **Step 2: Add a stable-order failing test**

Create two books with the same `importedAt`, no `lastReadAt`, and ids `b` then `a`. Assert every sort option returns `a` before `b` whenever its business comparison keys are equal.

Run:

```bash
xcodebuild test -project ios/AiBook.xcodeproj -scheme AiBook -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:AiBookTests -derivedDataPath /tmp/aibook-ios-tests CODE_SIGNING_ALLOWED=NO
```

Expected: BookFormat and ImportPolicy assertions pass; stable-order assertions fail because the current sorter has no deterministic tie-breaker.

- [ ] **Step 3: Add the minimal deterministic tie-breaker**

Implement one private comparator helper:

```swift
private static func ordered(_ result: ComparisonResult, lhsID: String, rhsID: String) -> Bool {
    result == .orderedSame ? lhsID < rhsID : result == .orderedAscending
}
```

For descending date sorts, compare the primary date first and fall back to `id`; for title sort, pass the localized title comparison to `ordered`.

- [ ] **Step 4: Run unit tests**

Run the Task 2 test command again.

Expected: all domain and policy tests pass with zero failures.

- [ ] **Step 5: Commit**

```bash
git add ios/AiBookTests ios/AiBook/Core/Model/ShelfBookSorter.swift
git commit -m "test: 补充iOS领域模型测试"
```

---

### Task 3: Enable Swift 6 and Define Actor Boundaries

**Files:**
- Modify: `ios/AiBook.xcodeproj/project.pbxproj`
- Create: `ios/AiBookTests/SendableConformanceTests.swift`
- Modify: `ios/AiBook/Core/Model/BookModels.swift`
- Modify: `ios/AiBook/Core/Model/OpdsModels.swift`
- Modify: `ios/AiBook/Core/Model/StoreModels.swift`
- Modify: `ios/AiBook/Core/Reader/ReaderModels.swift`
- Modify: `ios/AiBook/Core/Data/Prefs/ReaderSettingsStore.swift`
- Modify: `ios/AiBook/Core/Data/Prefs/ServerConfigStore.swift`
- Modify: `ios/AiBook/Core/Network/ApiClient.swift`
- Modify: all files matching `ios/AiBook/Feature/*/*ViewModel.swift`
- Modify: `ios/AiBook/Feature/Importer/LocalBookImporter.swift`
- Modify: `ios/AiBook/Core/Network/Opds/OpdsCatalogService.swift`

**Interfaces:**
- Consumes: existing domain models and actor-isolated repositories.
- Produces: `Sendable` domain values and main-actor UI/configuration services that compile in Swift 6 strict mode.

- [ ] **Step 1: Add compile-time Sendable assertions**

Create this helper and call it for `LocalBook`, `ReadingProgress`, `ReaderSettings`, `ShelfFolder`, `OpdsFeed`, `OpdsEntry`, `OpdsLink`, `OpdsConnection`, `StoreBook`, `StoreCatalogFilter`, `ReaderChapter`, `ReaderPage`, `ReaderBookmark`, and `ReaderHighlight`:

```swift
private func requireSendable<T: Sendable>(_: T.Type) {}

func testCrossActorModelsAreSendable() {
    requireSendable(LocalBook.self)
    requireSendable(ReadingProgress.self)
}
```

- [ ] **Step 2: Turn on Swift 6 strict concurrency**

Set these values in Debug and Release for App, unit-test, and UI-test targets:

```text
IPHONEOS_DEPLOYMENT_TARGET = 17.0;
SWIFT_VERSION = 6.0;
SWIFT_STRICT_CONCURRENCY = complete;
```

Run:

```bash
xcodebuild build-for-testing -project ios/AiBook.xcodeproj -scheme AiBook -sdk iphonesimulator -configuration Debug -derivedDataPath /tmp/aibook-ios-swift6 CODE_SIGNING_ALLOWED=NO
```

Expected: FAIL with Sendable and actor-isolation diagnostics from existing declarations.

- [ ] **Step 3: Make cross-actor value models Sendable**

Add `Sendable` to the structs and enums named in Step 1 and to every stored-property enum they contain. Keep SwiftData `@Model` classes non-Sendable and main-actor isolated through their repositories.

- [ ] **Step 4: Isolate UI-owned reference types**

Place `@MainActor` before `@Observable` on `ShelfViewModel`, `StoreViewModel`, `OpdsViewModel`, `ReaderViewModel`, `SettingsViewModel`, and `LocalBookImporter`. Add `@MainActor` to `ReaderSettingsStore`, `ServerConfigStore`, `ApiClient`, and `OpdsCatalogService` because they are owned by the main-actor dependency graph.

Use this ordering:

```swift
@MainActor
@Observable
final class ShelfViewModel {
```

- [ ] **Step 5: Require sendable network payloads**

Change generic response constraints to:

```swift
func get<T: Decodable & Sendable>(_ path: String) async throws -> T
func post<T: Decodable & Sendable>(_ path: String, body: (any Encodable & Sendable)? = nil) async throws -> T
func put<T: Decodable & Sendable>(_ path: String, body: (any Encodable & Sendable)? = nil) async throws -> T
```

Add `Sendable` to the Codable request and response DTO structs used by `AuthApi` and `BookApi`.

- [ ] **Step 6: Re-run Swift 6 build-for-testing**

Run the Step 2 command.

Expected: the actor-isolation and Sendable diagnostics addressed by this task are absent. Any remaining compile errors must identify concrete call sites handled in Task 4 or Task 5, not project layout errors.

- [ ] **Step 7: Commit**

```bash
git add ios/AiBook.xcodeproj ios/AiBook ios/AiBookTests/SendableConformanceTests.swift
git commit -m "refactor: 启用Swift 6并发检查"
```

---

### Task 4: Move Import Preparation Off the Main Actor

**Files:**
- Create: `ios/AiBook/Core/Import/BookImportPreparer.swift`
- Modify: `ios/AiBook/Core/Data/Repository/BookRepository.swift`
- Modify: `ios/AiBook/Feature/Importer/LocalBookImporter.swift`
- Modify: `ios/AiBook/Feature/Shelf/ShelfViewModel.swift`
- Test: `ios/AiBookTests/ImportPolicyTests.swift`

**Interfaces:**
- Consumes: file URLs selected by SwiftUI and `BookRepository` on `@MainActor`.
- Produces: `BookImportPreparer.prepare(url:fileName:) async -> PreparedBookImportResult` and `BookRepository.importPrepared(_:) -> ImportResult`.

- [ ] **Step 1: Write preparation-result tests**

Define and test these values without SwiftData:

```swift
struct PreparedBookImport: Sendable {
    let sourceURL: URL
    let fileName: String
    let format: BookFormat
    let data: Data
    let sha256: String
    let normalizedTitle: String
}

enum PreparedBookImportResult: Sendable {
    case prepared(PreparedBookImport)
    case unsupported
    case failed
}
```

Create a temporary `sample.TXT` containing `hello`, call `await prepare`, and assert format `.txt`, normalized title `sample`, nonempty SHA-256, and identical data. Pass `sample.docx` and assert `.unsupported`.

- [ ] **Step 2: Run the focused test and verify failure**

```bash
xcodebuild test -project ios/AiBook.xcodeproj -scheme AiBook -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:AiBookTests/ImportPolicyTests -derivedDataPath /tmp/aibook-ios-tests CODE_SIGNING_ALLOWED=NO
```

Expected: FAIL because `BookImportPreparer` and result types do not exist.

- [ ] **Step 3: Implement the actor-backed preparer**

Implement:

```swift
actor BookImportPreparer {
    func prepare(url: URL, fileName: String) -> PreparedBookImportResult {
        guard let format = BookFormat.fromFileName(fileName) else { return .unsupported }
        guard let data = try? Data(contentsOf: url) else { return .failed }
        let sha256 = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        return .prepared(PreparedBookImport(
            sourceURL: url,
            fileName: fileName,
            format: format,
            data: data,
            sha256: sha256,
            normalizedTitle: ImportPolicy.normalizedTitle(from: fileName)
        ))
    }
}
```

- [ ] **Step 4: Split preparation from SwiftData persistence**

Change `BookRepository` so `importPrepared(_:)` performs duplicate lookup, sandbox file writes, EPUB metadata application, entity insertion, and save using the prepared bytes and hash. Keep the entire repository `@MainActor`; remove `Data(contentsOf:)` and SHA-256 calculation from it.

- [ ] **Step 5: Make the UI import flow asynchronous**

Inject one `BookImportPreparer`, change `importBooks(from:)` to start a `Task`, await each preparation result, persist prepared results on the main actor, update counters, and set `isImporting = false` with `defer`. Update `ShelfViewModel.importBooks(from:)` to call the asynchronous importer interface without blocking.

- [ ] **Step 6: Run focused tests and build-for-testing**

Run the Task 4 test command followed by Task 3's build-for-testing command.

Expected: preparation tests pass; no warning reports file reading or hashing as a main-actor synchronous operation.

- [ ] **Step 7: Commit**

```bash
git add ios/AiBook/Core/Import ios/AiBook/Core/Data/Repository/BookRepository.swift ios/AiBook/Feature/Importer/LocalBookImporter.swift ios/AiBook/Feature/Shelf/ShelfViewModel.swift ios/AiBookTests/ImportPolicyTests.swift
git commit -m "refactor: 异步准备iOS导入文件"
```

---

### Task 5: Add Recoverable Persistence Bootstrap Diagnostics

**Files:**
- Create: `ios/AiBook/Core/Data/Database/PersistenceBootstrap.swift`
- Modify: `ios/AiBook/Core/Data/Database/AiBookContainer.swift`
- Modify: `ios/AiBook/Core/DI/ServiceLocator.swift`
- Modify: `ios/AiBook/App/AiBookApp.swift`
- Create: `ios/AiBookTests/PersistenceBootstrapTests.swift`

**Interfaces:**
- Consumes: throwing persistent and in-memory ModelContainer factory closures.
- Produces: `PersistenceBootstrapResult { container: ModelContainer, issue: PersistenceStartupIssue? }` and `ServiceLocator.startupIssue`.

- [ ] **Step 1: Write fallback behavior tests**

Use injectable closures and assert:

```swift
let result = try PersistenceBootstrap.bootstrap(
    persistent: { throw StubError.persistentStoreUnavailable },
    inMemory: { try AiBookContainer.createInMemory() }
)
XCTAssertNotNil(result.issue)
XCTAssertTrue(result.issue?.message.contains("临时内存数据库") == true)
```

Also assert that a successful persistent closure returns `issue == nil`.

- [ ] **Step 2: Run the focused test and verify failure**

```bash
xcodebuild test -project ios/AiBook.xcodeproj -scheme AiBook -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:AiBookTests/PersistenceBootstrapTests -derivedDataPath /tmp/aibook-ios-tests CODE_SIGNING_ALLOWED=NO
```

Expected: FAIL because bootstrap types do not exist.

- [ ] **Step 3: Make container creation throwing**

Change the factory signatures to:

```swift
static func createPersistent() throws -> ModelContainer
static func createInMemory() throws -> ModelContainer
```

Both functions build the same Schema. Remove `fatalError` from recoverable persistent initialization.

- [ ] **Step 4: Implement bootstrap result and fallback**

Define `PersistenceStartupIssue: Identifiable, Sendable` with a UUID id and Chinese message. `bootstrap` first calls the persistent closure; on error it calls the in-memory closure and returns this message:

```text
本地数据库无法打开，当前已使用临时内存数据库。退出应用后，本次数据不会保留。
```

If both closures fail, throw the in-memory creation error because the dependency graph cannot be constructed.

- [ ] **Step 5: Surface the diagnostic in the app shell**

`ServiceLocator` stores the result's container and issue. `AiBookApp` presents an `.alert(item:)` titled `存储初始化失败` with the issue message and one `知道了` button.

- [ ] **Step 6: Run persistence tests and build-for-testing**

Run the Task 5 test command followed by Task 3's build-for-testing command.

Expected: fallback tests pass; the app target compiles without `fatalError` in persistent bootstrap code.

- [ ] **Step 7: Commit**

```bash
git add ios/AiBook/Core/Data/Database ios/AiBook/Core/DI/ServiceLocator.swift ios/AiBook/App/AiBookApp.swift ios/AiBookTests/PersistenceBootstrapTests.swift
git commit -m "fix: 增加iOS存储降级诊断"
```

---

### Task 6: Add Four-Tab UI Smoke Test and Developer Documentation

**Files:**
- Modify: `ios/AiBook/App/ContentView.swift`
- Replace: `ios/AiBookUITests/AiBookUITests.swift`
- Remove: `ios/AiBookUITests/AiBookUITestsLaunchTests.swift`
- Modify: `ios/README.md`

**Interfaces:**
- Consumes: the four existing TabView labels.
- Produces: stable identifiers `tab.shelf`, `tab.store`, `tab.opds`, and `tab.settings` for launch verification.

- [ ] **Step 1: Write the failing UI smoke test**

Replace the template test with:

```swift
func testLaunchShowsAllPrimaryTabs() {
    let app = XCUIApplication()
    app.launchArguments = ["-ui-testing"]
    app.launch()

    XCTAssertTrue(app.buttons["tab.shelf"].waitForExistence(timeout: 5))
    XCTAssertTrue(app.buttons["tab.store"].exists)
    XCTAssertTrue(app.buttons["tab.opds"].exists)
    XCTAssertTrue(app.buttons["tab.settings"].exists)
}
```

- [ ] **Step 2: Run the smoke test and verify failure**

```bash
xcodebuild test -project ios/AiBook.xcodeproj -scheme AiBook -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:AiBookUITests/AiBookUITests/testLaunchShowsAllPrimaryTabs -derivedDataPath /tmp/aibook-ios-ui-tests CODE_SIGNING_ALLOWED=NO
```

Expected: FAIL because the tab accessibility identifiers are absent.

- [ ] **Step 3: Add stable accessibility identifiers**

Add `.accessibilityIdentifier("tab.shelf")`, `.accessibilityIdentifier("tab.store")`, `.accessibilityIdentifier("tab.opds")`, and `.accessibilityIdentifier("tab.settings")` to the corresponding tab labels or tab content controls so XCTest exposes them as buttons.

- [ ] **Step 4: Document the canonical workflow**

Update `ios/README.md` with:

```text
Requirements: Xcode 16+, Swift 6, iOS 17+
Open: open ios/AiBook.xcodeproj
Build: xcodebuild build -project ios/AiBook.xcodeproj -scheme AiBook -sdk iphonesimulator -derivedDataPath /tmp/aibook-ios-derived CODE_SIGNING_ALLOWED=NO
Unit tests: xcodebuild test -project ios/AiBook.xcodeproj -scheme AiBook -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:AiBookTests -derivedDataPath /tmp/aibook-ios-tests CODE_SIGNING_ALLOWED=NO
UI smoke test: xcodebuild test -project ios/AiBook.xcodeproj -scheme AiBook -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:AiBookUITests/AiBookUITests/testLaunchShowsAllPrimaryTabs -derivedDataPath /tmp/aibook-ios-ui-tests CODE_SIGNING_ALLOWED=NO
```

Also explain that server credentials must not be committed and that App Store signing is outside this milestone.

- [ ] **Step 5: Run complete acceptance verification**

Run:

```bash
xcodebuild -version
xcodebuild clean build-for-testing -project ios/AiBook.xcodeproj -scheme AiBook -sdk iphonesimulator -configuration Debug -derivedDataPath /tmp/aibook-ios-final CODE_SIGNING_ALLOWED=NO
xcodebuild test-without-building -project ios/AiBook.xcodeproj -scheme AiBook -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath /tmp/aibook-ios-final CODE_SIGNING_ALLOWED=NO
git ls-files -s ios | awk '$1 == 160000 { print $4 }'
find ios -type d -name .git -print
rg -n 'Authorization:|Bearer [A-Za-z0-9._-]+|password\s*=\s*"[^"$]+' ios --glob '*.swift' --glob '*.md'
git status --short
```

Expected:

```text
Xcode 16.x or newer
** BUILD SUCCEEDED **
** TEST SUCCEEDED **
no Gitlink output
no nested .git output
no hard-coded credential output
only intentional iOS and plan changes in git status
```

If the machine reports Xcode 15.x, record the Xcode version and the exact unexecuted Xcode 16 commands in the handoff. Do not claim Swift 6 acceptance until Step 5 succeeds under Xcode 16+.

- [ ] **Step 6: Commit**

```bash
git add ios/AiBook/App/ContentView.swift ios/AiBookUITests ios/README.md
git commit -m "test: 添加iOS启动冒烟测试"
```

---

## Final Review Checklist

- [ ] The canonical project is `ios/AiBook.xcodeproj` and opens without broken file references.
- [ ] Root Git contains no iOS Gitlink and `ios/` contains no nested `.git`.
- [ ] The App, unit-test, and UI-test targets use Swift 6 strict concurrency and iOS 17.0.
- [ ] Domain models crossing actors are explicitly `Sendable`.
- [ ] UI state, preferences, ServiceLocator, and SwiftData access are explicitly `@MainActor`.
- [ ] File reading and SHA-256 preparation execute outside the main actor.
- [ ] Persistent-store failure produces an in-memory fallback and visible diagnostic.
- [ ] Unit tests cover format parsing, import policy, deterministic sorting, Sendable declarations, and persistence fallback.
- [ ] UI smoke test verifies all four primary tabs.
- [ ] README commands match the actual canonical project path.
- [ ] Xcode 16 verification evidence is reported honestly.
