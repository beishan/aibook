# Task C report: real MOBI/AZW3 and PDF fixtures

Status: **DONE_WITH_CONCERNS**

## Fixture provenance

Five minimal fixtures were copied byte-for-byte from the official libmobi
v0.12 test suite at `/private/tmp/libmobi-v0.12/tests/samples` into
`core/mobi/src/androidTest/assets/fixtures`.

- Upstream: <https://github.com/bfabiszewski/libmobi/tree/v0.12/tests/samples>
- Tag: `v0.12`
- Commit: `85dcfe803fc2a21020ddcf15c3eb66b93d388add`
- License: LGPL-3.0-or-later; the vendored license is
  `third_party/libmobi/COPYING`.

| Local fixture | Upstream fixture | SHA-256 |
|---|---|---|
| `kf7-textread.mobi` | `sample-textread.mobi` | `151c8169b2933b1aafa60df3fd4a50c222952e891adf58d5a797a27270c23371` |
| `kf8-ncx.azw3` | `sample-ncx.mobi` | `bb33217f3369d8ca6c5a373ca8e21a151be5494f5de6623aa9e7f42fb5fed743` |
| `multimedia.mobi` | `sample-multimedia.mobi` | `a301cce8187f1f1ea9562196c46328ca75585da280d4b742827cebd4698697c9` |
| `drm-v1.mobi` | `sample-drm-v1.mobi` | `631e7afe719c04a91744c22f3021a2af1cafef541f93612a27d629ab74645494` |
| `invalid-indx.fail` | `sample-invalid-indx.fail` | `ba9b8727f9b71d67aa52f7e3c6aa6c49b35845bace9bf5e0b758797de3b484eb` |

`PROVENANCE.md` and `SHA256SUMS` are stored beside the fixtures.

## Files added or changed

- Added `core/mobi/src/test/kotlin/com/aibook/android/core/mobi/OfficialFixtureInventoryTest.kt`.
- Added five official fixtures, `PROVENANCE.md`, and `SHA256SUMS` under
  `core/mobi/src/androidTest/assets/fixtures`.
- Added `core/mobi/src/androidTest/kotlin/com/aibook/android/core/mobi/NativeMobiDocumentParserInstrumentedTest.kt`.
- Added `app/src/androidTest/kotlin/com/aibook/android/feature/reader/pdf/AndroidPdfDocumentControllerInstrumentedTest.kt`.
- Added the minimal AndroidX test runner/dependencies in `core/mobi/build.gradle.kts`
  and AndroidX test dependencies in `app/build.gradle.kts`.

No Task A/B production reader or parser behavior was changed.

## TDD and build evidence

### Fixture inventory RED

Command:

```text
./gradlew :core:mobi:testDebugUnitTest --tests com.aibook.android.core.mobi.OfficialFixtureInventoryTest
```

Before copying the fixtures it failed as intended at
`OfficialFixtureInventoryTest.kt:23` with one failed test because the fixture
provenance/file inventory did not exist.

### Fixture inventory GREEN

The same command after adding the official files completed with:

```text
BUILD SUCCESSFUL in 1s
20 actionable tasks: 1 executed, 19 up-to-date
```

### Instrumentation compilation RED

Before adding AndroidX test dependencies,
`:core:mobi:compileDebugAndroidTestKotlin` failed with unresolved
`androidx.test`, JUnit, and assertion references. After dependencies were
added, the first combined compile found one test-code issue: Kotlin's
`Closeable.use` did not accept `PdfDocument`. The test now closes the document
explicitly in `finally`; library assets are also read from the instrumentation
test context rather than the target app context.

### Final unit/build verification

Command:

```text
./gradlew :core:mobi:testDebugUnitTest :core:mobi:assembleDebugAndroidTest :app:assembleDebugAndroidTest
```

Result:

```text
BUILD SUCCESSFUL in 7s
137 actionable tasks: 48 executed, 89 up-to-date
```

Generated test APKs:

- `core/mobi/build/outputs/apk/androidTest/debug/mobi-debug-androidTest.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## Instrumentation coverage and unrun status

The compiled MOBI/AZW3 instrumentation suite covers:

- KF7 parse succeeds with non-empty chapters.
- KF8 input renamed to `.azw3` succeeds with non-empty chapters.
- Multimedia input succeeds and exports an image resource.
- DRM input maps exactly to `DRM_PROTECTED`.
- Invalid input returns a failure without crashing.
- Every case deletes copied input and generated output in `finally`.

The compiled PDF instrumentation suite creates a one-page `PdfDocument` at
runtime, verifies open/page count/render dimensions at a 360 px target width,
and verifies corrupt bytes return a failure. Controller and temporary files
are closed/removed in `finally`.

**Neither instrumentation suite was executed.** A later `adb devices` check
found device `1516fabe`, but the combined connected-test run stopped before
starting any test because the device rejected the first test APK with
`INSTALL_FAILED_USER_RESTRICTED` (`Install canceled by user`). Therefore native
runtime behavior—including the exact multimedia resource extension—and
platform `PdfRenderer` behavior remain concerns until USB installation is
allowed on the device and connected tests are retried.
