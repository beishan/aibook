# Markdown companion resources and import I/O report

## Root cause

- `BookRepository.importBook()` copied only the Markdown file into `files/books`; the reader correctly resolved relative images beside that private copy, so every original sibling image became unreachable after import.
- picker imports retained only the selected document URI, while scan imports knew the tree/document ID but did not expose it to the book importer.
- file copy, SHA-256, metadata parsing, Markdown `readText`, and CommonMark AST construction inherited the caller context. `LocalBookImportViewModel` launches on Main, so these operations could run on the UI thread.

## RED evidence

- `MarkdownBookContentLoaderTest.markdownReadAndParseRunOnInjectedIoDispatcher` initially failed to compile because the loader had no injectable I/O dispatcher/read boundary.
- `MarkdownBookContentLoaderTest.resourceReferencesIncludeOnlySafeRelativeRasterImages` initially failed to compile because no safe companion-reference extractor existed.
- `MarkdownCompanionStorageTest` initially failed to compile because no private companion copier existed.
- `percentEncodedRelativeImageResolvesToCopiedPrivateFile` ran and failed with `imageUri == null`, proving copied decoded paths and reader lookup were inconsistent.

## Implementation

- Markdown imports now use an isolated `files/books/markdown-*/book.md` directory and copy only raster images explicitly referenced by Markdown image AST nodes into the same relative location.
- Resource paths reject schemes, authority/query/fragment, absolute paths, backslashes, empty/dot/`..` segments, and non-raster extensions. Every destination is canonicalized under the private book directory; each resource is capped at 20 MiB and written through a temporary file.
- scan imports resolve companions with the already-authorized tree URI. Picker imports attempt the sibling document URI only during the active grant and copy immediately, so reading never depends on a content URI or future directory permission.
- `BookRepository.importBook()` and `importDownloadedBook()` run all copy/hash/metadata work on an injectable `Dispatchers.IO` boundary.
- `MarkdownBookContentLoader` runs file reading and AST parsing on an injectable I/O dispatcher. Percent-encoded safe paths now use the same normalization for copy and reading.
- deleting a Markdown book removes its private companion directory.

## GREEN evidence

- `./gradlew --no-daemon :core:reader:test --tests '*MarkdownBookContentLoaderTest' :core:data:testDebugUnitTest --tests '*MarkdownCompanionStorageTest'`
  - `BUILD SUCCESSFUL in 24s`
- Targeted encoded-path regression after the shared normalization fix:
  - `./gradlew --no-daemon :core:reader:test --tests '*percentEncodedRelativeImageResolvesToCopiedPrivateFile'`
  - `BUILD SUCCESSFUL in 11s`

No files were staged or committed. A device/runtime SAF-provider test was not run; final full-module verification remains with the parent task.

## Review round 2: URI authorization and aggregate limits

The first implementation attempted to derive a sibling document URI from the selected Markdown document ID. Review correctly rejected that approach because `ACTION_OPEN_DOCUMENT` grants only selected URIs and document IDs may be opaque.

### New RED coverage

- `AuthorizedMarkdownResourceIndexTest` requires nested resources to resolve only when their exact URI was selected, rejects unselected/traversal/cross-provider matches, and proves the open callback is invoked only with an authorized value.
- The same test verifies a flat, unique display-name fallback and rejects ambiguous names.
- `AuthorizedTreeResourceIndex` coverage uses opaque URI values and relative paths returned by tree traversal, proving scan resolution does not synthesize provider document IDs.
- `MarkdownCompanionStorageTest` now requires a resource-count cap and aggregate-byte cap, removal of partial files, and no further source opens after the aggregate limit is reached.

### Revised implementation

- Picker import uses `BookRepository.importSelectedBooks`: it indexes only URIs returned by `OpenMultipleDocuments`, matches document IDs only within the same provider authority, and opens a resource only after a whitelist match. Image MIME types were added to the picker and UI copy tells users to select Markdown images together with the `.md` file.
- The unauthorized `openMarkdownSibling` fallback was removed. A Markdown imported alone never probes a sibling URI.
- Directory scanning first queries the complete authorized tree and records each returned document's relative path and actual URI. Imports then resolve resources through that index; opaque IDs are never concatenated.
- Companion copy defaults are now 128 resources, 64 MiB aggregate, and 20 MiB per resource. Count overflow is skipped; aggregate overflow removes the `.part` file and stops opening further resources; per-resource overflow removes its partial file.

Per parent coordination, no second-round Gradle command was run locally to avoid collision with concurrent PDF verification. The requested serial focused command is:

```text
./gradlew --no-daemon :core:reader:test --tests '*MarkdownBookContentLoaderTest' :core:data:testDebugUnitTest --tests '*AuthorizedMarkdownResourceIndexTest' --tests '*MarkdownCompanionStorageTest' :app:compileDebugKotlin
```
