package com.aibook.android.feature.reader

import com.aibook.android.core.network.api.dto.BookVersionDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ReaderVersionSelectionTest {
    private val primary = BookVersionDTO(1, "原版", "epub", primaryVersion = true)
    private val selected = BookVersionDTO(2, "修订版", "txt", selected = true)

    @Test
    fun explicitChoiceWinsOverSavedAndPrimaryVersions() {
        assertEquals(primary, resolveReaderVersion(listOf(selected, primary), 1))
        assertEquals(selected, resolveReaderVersion(listOf(primary, selected), 2))
    }

    @Test
    fun restoresSavedVersionThenPrimaryThenFirst() {
        assertEquals(selected, resolveReaderVersion(listOf(primary, selected)))
        assertEquals(primary, resolveReaderVersion(listOf(selected.copy(selected = false), primary)))
        assertEquals(3L, resolveReaderVersion(listOf(BookVersionDTO(3, "正文", "structured"))).id)
    }

    @Test
    fun multipleVersionsPromptOnEntryButNotOnRotation() {
        assertTrue(requiresReaderVersionSelection(listOf(primary, selected), null))
        assertFalse(requiresReaderVersionSelection(listOf(primary, selected), selected.id))
        assertFalse(requiresReaderVersionSelection(listOf(primary), null))
    }

    @Test
    fun removedOpenedVersionRequiresConfirmationEvenIfOnlyOneVersionRemains() {
        assertTrue(requiresReaderVersionSelection(listOf(primary), selected.id))
        assertFalse(requiresReaderVersionSelection(listOf(primary), primary.id))
    }

    @Test
    fun missingExplicitChoiceCannotSilentlyOpenAnotherVersion() {
        assertFailsWith<IllegalStateException> { resolveReaderVersion(listOf(primary, selected), 99) }
        assertFailsWith<IllegalStateException> { resolveReaderVersion(emptyList()) }
    }
}
