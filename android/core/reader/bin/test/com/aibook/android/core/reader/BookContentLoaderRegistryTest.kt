package com.aibook.android.core.reader

import com.aibook.android.core.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class BookContentLoaderRegistryTest {

    @Test
    fun registryReturnsLoaderForSupportedFormat() {
        val loader = FakeLoader(setOf(BookFormat.MARKDOWN))

        assertSame(
            loader,
            BookContentLoaderRegistry(listOf(loader)).loaderFor(BookFormat.MARKDOWN)
        )
    }

    @Test
    fun registryReturnsNullForUnsupportedFormat() {
        assertNull(BookContentLoaderRegistry(emptyList()).loaderFor(BookFormat.MOBI))
    }

    private class FakeLoader(
        override val supportedFormats: Set<BookFormat>
    ) : BookContentLoader {
        override suspend fun load(request: BookContentRequest): BookContentResult =
            BookContentResult.Success(ReaderBookContent(chapters = emptyList()))
    }
}
