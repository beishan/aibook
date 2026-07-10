# Compact Store Row Labels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Shorten format, source, and local shelf-action labels in the Android book store compact list without changing other layouts.

**Architecture:** Add an internal pure formatter object in the store feature so label rules can be unit-tested without Compose. Wire only `StoreCompactListItem` to those rules and a compact-only local shelf action, leaving the shared action used by other layouts unchanged.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, kotlin.test, Gradle

## Global Constraints

- Every non-blank format is represented by its lowercase first character; blank stays blank.
- Local source is `本`; OPDS source is `O`.
- Local unshelved action is `入架`; local shelved state is `✓`.
- Accessibility descriptions remain `加入书架` and `已在书架`.
- Only the compact store list changes.
- Click feedback uses no shadow or press-projection effect.

---

### Task 1: Compact Label Rules

**Files:**
- Create: `app/src/main/kotlin/com/aibook/android/feature/store/CompactStoreRowLabels.kt`
- Create: `app/src/test/kotlin/com/aibook/android/feature/store/CompactStoreRowLabelsTest.kt`

**Interfaces:**
- Consumes: `StoreItemKind` from the store feature.
- Produces: `CompactStoreRowLabels.format(String): String`, `source(StoreItemKind): String`, `localShelf(shelved: Boolean): CompactShelfLabel`, and `CompactShelfLabel(text: String, contentDescription: String)`.

- [x] **Step 1: Write the failing formatter tests**

```kotlin
package com.aibook.android.feature.store

import kotlin.test.Test
import kotlin.test.assertEquals

class CompactStoreRowLabelsTest {
    @Test fun formatUsesLowercaseFirstCharacter() {
        assertEquals("e", CompactStoreRowLabels.format("EPUB"))
        assertEquals("t", CompactStoreRowLabels.format("txt"))
        assertEquals("p", CompactStoreRowLabels.format("PDF"))
        assertEquals("m", CompactStoreRowLabels.format("Mobi"))
    }

    @Test fun blankFormatStaysBlank() {
        assertEquals("", CompactStoreRowLabels.format("  "))
    }

    @Test fun sourceUsesCompactKindMarker() {
        assertEquals("本", CompactStoreRowLabels.source(StoreItemKind.LOCAL))
        assertEquals("O", CompactStoreRowLabels.source(StoreItemKind.OPDS))
    }

    @Test fun localShelfLabelsKeepFullAccessibilityDescription() {
        assertEquals(CompactShelfLabel("入架", "加入书架"), CompactStoreRowLabels.localShelf(false))
        assertEquals(CompactShelfLabel("✓", "已在书架"), CompactStoreRowLabels.localShelf(true))
    }
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests com.aibook.android.feature.store.CompactStoreRowLabelsTest`

Expected: compilation fails because `CompactStoreRowLabels` and `CompactShelfLabel` do not exist.

- [x] **Step 3: Add the minimal pure implementation**

```kotlin
package com.aibook.android.feature.store

internal data class CompactShelfLabel(val text: String, val contentDescription: String)

internal object CompactStoreRowLabels {
    fun format(format: String): String =
        format.trim().firstOrNull()?.lowercaseChar()?.toString().orEmpty()

    fun source(kind: StoreItemKind): String = when (kind) {
        StoreItemKind.LOCAL -> "本"
        StoreItemKind.OPDS -> "O"
    }

    fun localShelf(shelved: Boolean): CompactShelfLabel = if (shelved) {
        CompactShelfLabel("✓", "已在书架")
    } else {
        CompactShelfLabel("入架", "加入书架")
    }
}
```

- [x] **Step 4: Run the focused test and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests com.aibook.android.feature.store.CompactStoreRowLabelsTest`

Expected: `BUILD SUCCESSFUL` and all four tests pass.

### Task 2: Compact Row UI Wiring

**Files:**
- Modify: `app/src/main/kotlin/com/aibook/android/feature/store/BookStoreScreen.kt` in `StoreCompactListItem` and a new adjacent compact action composable.

**Interfaces:**
- Consumes: all APIs produced by Task 1.
- Produces: compact rows showing short format/source/local-shelf labels while preserving existing navigation, download, management, and click behavior.

- [x] **Step 1: Replace compact metadata rendering**

In `StoreCompactListItem`, replace the full format and `SourceBadge` calls with:

```kotlin
Text(
    CompactStoreRowLabels.format(book.format),
    color = DesignTokens.Accent,
    style = MaterialTheme.typography.bodySmall
)
Text(
    CompactStoreRowLabels.source(book.kind),
    color = DesignTokens.SoftText,
    style = MaterialTheme.typography.bodySmall
)
CompactStoreBookAction(
    book = book,
    downloading = downloading,
    managementMode = managementMode,
    onDownloadClick = onDownloadClick,
    onLocalShelfClick = onLocalShelfClick
)
```

- [x] **Step 2: Add the compact-only action**

Add next to `StoreCompactListItem`:

```kotlin
@Composable
private fun CompactStoreBookAction(
    book: StoreBook,
    downloading: Boolean,
    managementMode: Boolean,
    onDownloadClick: (StoreBook) -> Unit,
    onLocalShelfClick: (StoreBook) -> Unit
) {
    if (book.kind != StoreItemKind.LOCAL) {
        StoreBookAction(book, downloading, managementMode, onDownloadClick, onLocalShelfClick)
        return
    }
    if (managementMode) return

    val label = CompactStoreRowLabels.localShelf(book.shelved)
    Text(
        text = label.text,
        modifier = Modifier
            .semantics { contentDescription = label.contentDescription }
            .background(DesignTokens.Accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(
                enabled = !book.shelved,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onLocalShelfClick(book) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (book.shelved) DesignTokens.SoftText else DesignTokens.Accent,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium
    )
}
```

Add the Compose semantics imports if they are not already present:

```kotlin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
```

- [x] **Step 3: Run focused and full app unit tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [x] **Step 4: Compile the debug app**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` with no Kotlin or Compose compilation errors.

- [x] **Step 5: Review the final diff**

Run: `git diff --check && git diff -- app/src/main/kotlin/com/aibook/android/feature/store/CompactStoreRowLabels.kt app/src/main/kotlin/com/aibook/android/feature/store/BookStoreScreen.kt app/src/test/kotlin/com/aibook/android/feature/store/CompactStoreRowLabelsTest.kt`

Expected: no whitespace errors; the diff changes only compact-row behavior and its formatter tests.
