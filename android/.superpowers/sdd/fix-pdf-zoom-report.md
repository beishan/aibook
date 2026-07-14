# PDF zoom, page-offset, and render-request fixes

Status: **IMPLEMENTED — GREEN pending shared-workspace stabilization**

## Scope

- Zoom now requests a bitmap width proportional to the 1x viewport width, capped at 4096 px.
- `AndroidPdfDocumentController` additionally limits each rendered ARGB bitmap to one third of the heap-derived total cache budget, adjusted for the source-page aspect ratio.
- A zoomed page reserves its scaled vertical layout height, so its drawing does not occupy the unscaled slot and overlap adjacent pages.
- Horizontal pan is stored per page in the Compose screen instead of one offset being applied to every page.
- Existing `progressScrollOffset` remains the persisted current-page vertical position. `PdfPageOffsetStore` keeps in-session offsets page-scoped, restores the persisted offset for the restored page, and passes the visible page's own offset to the coordinator.
- In-flight rendering is keyed by `(pageIndex, targetWidthPx)`. A new width for a page is allowed while an older width is rendering, and only the latest width may publish into the visible page map.

## TDD evidence

Tests were added first for:

- zoom-aware target width and the 4096 px cap;
- scaled page layout height;
- the heap-derived single-bitmap aspect-aware cap;
- page-scoped restored/updated offsets;
- concurrent old/new widths and stale-result suppression;
- restored PDF scroll offset in the coordinator.

Attempted RED command:

```text
./gradlew :app:testDebugUnitTest --tests 'com.aibook.android.feature.reader.pdf.*'
```

The first sandboxed attempt was blocked by the existing Gradle wrapper lock. The approved rerun reached compilation but was stopped by concurrent shared-workspace changes in `core:reader`: `MarkdownBookContentLoader` referenced `kotlinx.coroutines` before that module's dependency update had landed. The failure occurred in `:core:reader:compileKotlin`, before the new PDF tests could compile, so it is recorded as an infrastructure/dependency-blocked RED rather than a valid PDF RED.

Per root-agent coordination, no more Gradle processes were started while the shared dependency work was active. Production implementation was completed after the tests, and `git diff --check` passed for the PDF source/test files. A fresh GREEN run is still required once `core:reader` is stable.

Recommended serial verification:

```text
./gradlew :app:testDebugUnitTest --tests 'com.aibook.android.feature.reader.pdf.*'
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## Files

- `app/src/main/kotlin/com/aibook/android/feature/reader/pdf/PdfDocumentController.kt`
- `app/src/main/kotlin/com/aibook/android/feature/reader/pdf/PdfPagePresentationState.kt`
- `app/src/main/kotlin/com/aibook/android/feature/reader/pdf/PdfReaderScreen.kt`
- `app/src/main/kotlin/com/aibook/android/feature/reader/pdf/PdfReaderViewModel.kt`
- `app/src/main/kotlin/com/aibook/android/feature/reader/pdf/PdfRenderSizing.kt`
- `app/src/test/kotlin/com/aibook/android/feature/reader/pdf/PdfPageOffsetStoreTest.kt`
- `app/src/test/kotlin/com/aibook/android/feature/reader/pdf/PdfReaderCoordinatorTest.kt`
- `app/src/test/kotlin/com/aibook/android/feature/reader/pdf/PdfRenderRequestTrackerTest.kt`
- `app/src/test/kotlin/com/aibook/android/feature/reader/pdf/PdfRenderSizingTest.kt`

No files were staged or committed.

## Reviewer follow-up: navigation wiring and shared bitmap budget

The first review found that the page-offset store was not yet used by toolbar/slider navigation and that a fixed 16 MiB bitmap cap could allow the visible map to retain too much memory independently of the LRU budget.

Tests were changed first to require:

- a `PdfPageNavigationTarget(pageIndex, scrollOffset)` built from the requested target page's own saved offset, including page clamping;
- a maximum three-page render window;
- a per-bitmap budget derived from the existing total heap-based cache budget;
- an 8 MiB total budget yielding a 2,796,202-byte per-bitmap budget and an aspect-aware reduced render width.

Implementation follow-up:

- `PdfReaderViewModel.navigationTarget(pageIndex)` is now the UI navigation API.
- initial database restore, previous page, next page, and slider navigation all call that API and pass both page and target-page offset to `scrollToItem`/`animateScrollToItem`;
- `PdfRenderWindow` is shared by Compose prefetch and ViewModel retention, limiting the visible window to current ±1 page;
- `PdfPageBitmapCache.retainPages` removes cached pages outside that same visible window;
- `visibleCenterPage` updates synchronously with list visibility, so render completion does not depend on the coordinator's later state update;
- render completion rechecks both the current window and latest `(page,width)` request, recycling stale/off-window results instead of caching them;
- the cache keeps only the latest width variant for each retained page, preventing rapid zoom changes from filling the total budget with obsolete sizes while the visible map holds newer ones;
- the controller's bitmap limit is `cacheBudget(maxHeap) / PdfRenderWindow.MAX_PAGE_COUNT`, never a fixed amount larger than the device-derived total cache budget;
- the factory passes that derived per-bitmap budget to `AndroidPdfDocumentController` while the LRU retains the original total budget.

No Gradle process was started for this follow-up, per root-agent coordination. Static `git diff --check` remains the local verification step; the serial GREEN commands above are still required.
