package com.aibook.android.core.data

import kotlin.test.Test
import kotlin.test.assertEquals

class BookFingerprintTest {
    @Test
    fun `creates stable sha256 hex for book bytes`() {
        val fingerprint = BookFingerprint.sha256("hello book".toByteArray())

        assertEquals(
            "f372843c1c5888b2ee9b50e2569e6f8989b7b3833178ece510e593a0439bd828",
            fingerprint
        )
    }
}
