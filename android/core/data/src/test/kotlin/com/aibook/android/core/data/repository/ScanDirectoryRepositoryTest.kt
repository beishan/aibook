package com.aibook.android.core.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class ScanDirectoryRepositoryTest {

    @Test
    fun mergeStatsAddsEveryOutcome() {
        val result = ScanImportStats()
            .plus(ScanImportStats(scanned = 3, added = 1, restored = 1, unsupported = 1))
            .plus(ScanImportStats(scanned = 2, added = 1, duplicate = 1, failed = 1))

        assertEquals(5, result.scanned)
        assertEquals(2, result.added)
        assertEquals(1, result.restored)
        assertEquals(1, result.duplicate)
        assertEquals(1, result.unsupported)
        assertEquals(1, result.failed)
    }
}
