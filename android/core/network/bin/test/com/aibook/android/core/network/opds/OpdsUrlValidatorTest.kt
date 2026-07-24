package com.aibook.android.core.network.opds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpdsUrlValidatorTest {
    @Test
    fun `normalizes valid http url`() {
        assertEquals("https://example.com/opds?q=1", OpdsUrlValidator.normalize(" HTTPS://Example.COM/opds?q=1 ").getOrThrow())
    }

    @Test
    fun `rejects credentials fragments and non-http protocols`() {
        assertTrue(OpdsUrlValidator.normalize("https://user:pass@example.com/opds").isFailure)
        assertTrue(OpdsUrlValidator.normalize("https://example.com/opds#secret").isFailure)
        assertTrue(OpdsUrlValidator.normalize("file:///tmp/feed.xml").isFailure)
    }
}
