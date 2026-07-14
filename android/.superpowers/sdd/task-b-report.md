# Task B — MOBI cache/list normalization and Markdown local images

Status: **DONE**

## Scope completed

- Added a MOBI cache regression test that removes a normalized cached chapter, then verifies the loader invalidates the cache, reparses once, rewrites it, and reuses the repaired cache without a third parse.
- Added a MOBI nested-list regression test and changed normalization so paragraphs owned by list items are emitted once instead of being duplicated through both `p` and ancestor `li` selections.
- Added Markdown chapter-image tests and implementation:
  - records only the first valid local image in each chapter;
  - resolves relative paths from the Markdown file's parent directory;
  - uses canonical paths and requires the target to remain inside that parent;
  - requires the target to be an existing file;
  - rejects remote/schemed URLs, absolute paths, `../` traversal, and missing files;
  - preserves the existing rendered text/alt-text behavior.
- Kept the existing single `ReaderChapter.imageUri` model; no broad chapter/media redesign and no network access were introduced.

## Files changed

- `core/mobi/src/main/kotlin/com/aibook/android/core/mobi/MobiBookContentLoader.kt`
- `core/mobi/src/test/kotlin/com/aibook/android/core/mobi/MobiBookContentLoaderTest.kt`
- `core/reader/src/main/kotlin/com/aibook/android/core/reader/MarkdownBookContentLoader.kt`
- `core/reader/src/test/kotlin/com/aibook/android/core/reader/MarkdownBookContentLoaderTest.kt`
- `.superpowers/sdd/task-b-report.md`

No files were staged or committed. Task A schema/migration files and PDF code were not touched by this task.

## TDD evidence

### RED — Markdown images

Command:

```text
./gradlew :core:mobi:testDebugUnitTest --tests '*MobiBookContentLoaderTest' \
  :core:reader:test --tests '*MarkdownBookContentLoaderTest'
```

Observed expected failures before production changes:

```text
MarkdownBookContentLoaderTest > unsafeAndMissingImagesAreRejectedWhileLaterSafeImageIsUsed FAILED
MarkdownBookContentLoaderTest > firstSafeLocalImageIsResolvedPerChapter FAILED
5 tests completed, 2 failed
BUILD FAILED
```

Both failures were `imageUri` assertions because the loader did not yet extract chapter images.

### RED — MOBI nested lists

Command:

```text
./gradlew :core:mobi:testDebugUnitTest --tests '*MobiBookContentLoaderTest'
```

Observed expected failure before production changes:

```text
MobiBookContentLoaderTest > nested list paragraphs are normalized without duplicate text FAILED
7 tests completed, 1 failed
BUILD FAILED
```

The cache-repair regression added in the same test-first change already passed against the existing cache loader; therefore no cache production change was necessary. It now permanently proves a missing cached chapter causes exactly one repair parse and subsequent cache reuse.

### GREEN — focused tests

Command:

```text
./gradlew :core:mobi:testDebugUnitTest --tests '*MobiBookContentLoaderTest' \
  :core:reader:test --tests '*MarkdownBookContentLoaderTest'
```

Result:

```text
> Task :core:reader:test
> Task :core:mobi:testDebugUnitTest
BUILD SUCCESSFUL in 2s
```

Focused results: Markdown 5/5 and MOBI loader 7/7 passed.

### GREEN — complete module tests and integration compile

Command:

```text
./gradlew :core:reader:test :core:mobi:testDebugUnitTest :app:compileDebugKotlin
```

Result:

```text
> Task :core:reader:test
> Task :core:mobi:testDebugUnitTest
> Task :app:compileDebugKotlin
BUILD SUCCESSFUL in 4s
```

Complete results: `core:reader` 29/29 and `core:mobi` 9/9 tests passed; the Android app Kotlin integration compiled successfully.

## Notes

- The first sandboxed Gradle invocation could not open the existing user-level Gradle wrapper lock. It was rerun with approved access to the existing Gradle cache; all RED/GREEN evidence above comes from actual Gradle test executions.
- Image destinations containing query strings or fragments are treated as literal file names and will normally be rejected as missing. Supporting URL-style suffix stripping was outside this focused offline path-safety scope.

## Reviewer follow-up — cached content integrity and wrapped lists

### Additional scope completed

- Added a regression test for a normalized chapter file that still exists but has been truncated to empty content.
- Extended each cache manifest chapter entry with the normalized file's SHA-256 and validate it before reading the cached chapter.
- Bumped the reader normalization cache version from `v1` to `v2`; old five-column manifests are invalidated rather than trusted without integrity metadata.
- Added the requested wrapped-list fixture: `<li><div><p>父项</p></div><ul><li><p>子项</p></li></ul></li>`.
- Changed list normalization so an `li` with a paragraph belonging to that same list item defers to its paragraph blocks. Paragraph ownership is based on the nearest `li`, so nested items neither duplicate ancestor text nor suppress the parent's wrapped paragraph.
- List items without paragraph blocks still collect only text nodes whose nearest list-item owner is themselves, preserving simple/inline list text while excluding nested child items.

### Additional RED

Command:

```text
./gradlew :core:mobi:testDebugUnitTest --tests '*MobiBookContentLoaderTest'
```

Observed before the reviewer fixes:

```text
MobiBookContentLoaderTest > truncated cached chapter invalidates cache and reparses only once FAILED
MobiBookContentLoaderTest > wrapped nested list paragraphs keep parent without duplicate text FAILED
9 tests completed, 2 failed
BUILD FAILED
```

The failures respectively showed that an existing-but-empty cache file was trusted, and that the parent's wrapped `父项` text was lost.

### Additional GREEN — focused MOBI loader tests

Command:

```text
./gradlew :core:mobi:testDebugUnitTest --tests '*MobiBookContentLoaderTest'
```

Result:

```text
> Task :core:mobi:testDebugUnitTest
BUILD SUCCESSFUL in 2s
```

MOBI loader tests: 9/9 passed. The repaired cache is parsed once, receives a new valid hash, and is reused on the following load.

### Additional GREEN — modules and App compile

Command:

```text
./gradlew :core:reader:test :core:mobi:testDebugUnitTest :app:compileDebugKotlin
```

Result:

```text
> Task :core:mobi:testDebugUnitTest
> Task :app:compileDebugKotlin
BUILD SUCCESSFUL in 3s
```

Current complete results: `core:reader` 29/29 and `core:mobi` 11/11 tests passed; App debug Kotlin compilation succeeded.

## Final reviewer follow-up — list prefix plus wrapped paragraph

Added the regression fixture `<li>前缀<div><p>正文</p></div></li>` with the required normalized content `前缀\n正文`.

### RED

Command:

```text
./gradlew :core:mobi:testDebugUnitTest --tests '*MobiBookContentLoaderTest'
```

Observed before the fix:

```text
MobiBookContentLoaderTest > list item prefix and wrapped paragraph are both kept exactly once FAILED
10 tests completed, 1 failed
BUILD FAILED
```

The old ownership filter skipped the whole `li` whenever it owned a paragraph, losing `前缀`.

### GREEN

Command:

```text
./gradlew :core:mobi:testDebugUnitTest --tests '*MobiBookContentLoaderTest'
```

Result:

```text
> Task :core:mobi:testDebugUnitTest
BUILD SUCCESSFUL in 2s
```

MOBI loader tests: 10/10 passed. An `li` now emits only its own non-paragraph text nodes; its paragraph blocks are emitted separately, while nearest-`li` ownership still excludes nested child-list text. The complete `core:mobi` suite now contains 12 tests.
