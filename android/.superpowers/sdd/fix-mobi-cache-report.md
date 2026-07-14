# Final review follow-up — MOBI source and cache integrity

Status: **COMPLETE**

## Scope

- `BookContentRequest.contentHash` is now treated only as a verified hint. The loader always computes the current source file SHA-256 and accepts the supplied hash only when it equals the current bytes. Cache directory selection and the parser fingerprint therefore cannot remain pinned to a valid-looking but stale hash.
- Bumped the normalized reader cache from `v2` to `v3`.
- Chapter manifest entries retain and verify the normalized text SHA-256 and now also record and verify the chapter image SHA-256.
- Cover metadata now records and verifies the cover SHA-256.
- Cached image and cover validation preserves canonical cache-directory containment, requires a regular non-empty file, and checks the recorded SHA-256 before reuse.
- Missing, replaced, truncated, or zero-byte resources invalidate the cache. The existing repair path deletes the invalid cache, parses once, writes fresh integrity metadata, then reuses it on the following load.

## Files changed

- `core/mobi/src/main/kotlin/com/aibook/android/core/mobi/MobiBookContentLoader.kt`
- `core/mobi/src/test/kotlin/com/aibook/android/core/mobi/MobiBookContentLoaderTest.kt`
- `.superpowers/sdd/fix-mobi-cache-report.md`

No files were staged or committed.

## TDD RED evidence

Command:

```text
./gradlew :core:mobi:testDebugUnitTest --tests '*MobiBookContentLoaderTest'
```

Observed before production changes:

```text
MobiBookContentLoaderTest > stale supplied content hash cannot hide source replacement or truncation FAILED
MobiBookContentLoaderTest > stale supplied content hash cannot serve old cache after source becomes drm protected FAILED
MobiBookContentLoaderTest > replaced cached image invalidates cache and reparses only once FAILED
MobiBookContentLoaderTest > zero byte cached image invalidates cache and reparses only once FAILED
MobiBookContentLoaderTest > truncated cached cover invalidates cache and reparses only once FAILED
15 tests completed, 5 failed
BUILD FAILED
```

This demonstrated both defects: a syntactically valid stale request hash served old normalized content without calling the parser, and resource files were trusted using only path/existence checks.

## Added regression coverage

- Valid but stale supplied hash after source replacement, then source truncation: three distinct byte states require three parses.
- Valid but stale supplied hash after the source becomes DRM-protected: the second load must return `DrmProtected` instead of cached readable content.
- Replaced cached image, zero-byte cached image, and truncated cached cover: each must cause exactly one repair parse and the next load must reuse the repair.
- Existing chapter missing/truncation tests continue to cover normalized text files.

## GREEN verification

The immediate GREEN attempts were intentionally not counted because other agents were running Gradle against the same build directories. One attempt encountered a concurrent `core:reader` dependency update; another reported Kotlin incremental lookup storage already registered and cascaded unresolved-class errors. The coordinator then ran the suite serially. The first serial run exposed a `/var` versus canonical `/private/var` cover-path representation difference; normalizing the cover path at the parser boundary fixed it. The rerun completed successfully with all 15 loader tests passing, and the final full Gradle verification also completed successfully.

Static inspection completed: source-hash selection is based on freshly computed bytes, all resource cache reads validate containment/type/non-empty/hash, and `git diff --check`-equivalent whitespace inspection found no issue (the whole new `core/mobi` module is currently untracked, so standard `git diff` emits no module diff).
