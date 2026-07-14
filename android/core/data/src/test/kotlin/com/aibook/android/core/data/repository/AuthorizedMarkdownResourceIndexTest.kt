package com.aibook.android.core.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthorizedMarkdownResourceIndexTest {

    private val markdown = SelectedDocument(
        value = "book-uri",
        documentId = "primary:Books/Novel/book.md",
        displayName = "book.md"
    )

    @Test
    fun resolvesNestedResourceOnlyWhenExactDocumentWasSelected() {
        val selectedImage = SelectedDocument(
            value = "selected-image-uri",
            documentId = "primary:Books/Novel/images/a.png",
            displayName = "a.png"
        )
        val index = AuthorizedMarkdownResourceIndex(listOf(markdown, selectedImage))

        assertEquals("selected-image-uri", index.resolve(markdown, "images/a.png"))
        assertNull(index.resolve(markdown, "images/not-selected.png"))
        assertNull(index.resolve(markdown, "../private.png"))
    }

    @Test
    fun openerIsInvokedOnlyWithExplicitlySelectedUri() {
        val selectedImage = SelectedDocument(
            value = "selected-image-uri",
            documentId = "primary:Books/Novel/images/a.png",
            displayName = "a.png"
        )
        val index = AuthorizedMarkdownResourceIndex(listOf(markdown, selectedImage))
        val opened = mutableListOf<String>()

        index.withResolved(markdown, "images/not-selected.png") { opened += it }
        index.withResolved(markdown, "images/a.png") { opened += it }

        assertEquals(listOf("selected-image-uri"), opened)
    }

    @Test
    fun identicalDocumentIdFromAnotherProviderIsNotMatched() {
        val source = markdown.copy(providerId = "books.provider")
        val wrongProviderImage = SelectedDocument(
            value = "wrong-provider-uri",
            documentId = "primary:Books/Novel/images/a.png",
            displayName = "a.png",
            providerId = "other.provider"
        )
        val index = AuthorizedMarkdownResourceIndex(listOf(source, wrongProviderImage))

        assertNull(index.resolve(source, "images/a.png"))
    }

    @Test
    fun flatNameDoesNotAssociateUnrelatedOrOpaqueDocuments() {
        val unrelatedCover = SelectedDocument(
            value = "cover-uri",
            documentId = null,
            displayName = "cover.jpg",
            providerId = "other.provider"
        )
        val index = AuthorizedMarkdownResourceIndex(listOf(markdown, unrelatedCover))

        assertNull(index.resolve(markdown, "cover.jpg"))
    }

    @Test
    fun treeIndexUsesQueriedRelativePathsInsteadOfSynthesizingDocumentIds() {
        val index = AuthorizedTreeResourceIndex(
            listOf(
                TreeDocument(value = "opaque-book-uri", relativePath = "Novel/book.md"),
                TreeDocument(value = "opaque-image-uri", relativePath = "Novel/images/a.png")
            )
        )

        assertEquals("opaque-image-uri", index.resolve("Novel/book.md", "images/a.png"))
        assertNull(index.resolve("Novel/book.md", "images/not-selected.png"))
        assertNull(index.resolve("Novel/book.md", "../private.png"))
    }
}
