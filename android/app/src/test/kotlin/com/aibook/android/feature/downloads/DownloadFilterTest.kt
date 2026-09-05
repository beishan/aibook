package com.aibook.android.feature.downloads

import com.aibook.android.core.data.repository.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadFilterTest {
    @Test
    fun activeIncludesAllNonTerminalDownloads() {
        assertTrue(DownloadFilter.ACTIVE.matchesStatus(DownloadStatus.QUEUED))
        assertTrue(DownloadFilter.ACTIVE.matchesStatus(DownloadStatus.RUNNING))
        assertTrue(DownloadFilter.ACTIVE.matchesStatus(DownloadStatus.PAUSED))
        assertTrue(DownloadFilter.ACTIVE.matchesStatus(DownloadStatus.FAILED))
        assertFalse(DownloadFilter.ACTIVE.matchesStatus(DownloadStatus.COMPLETED))
    }

    @Test
    fun terminalFiltersMatchTheirOwnState() {
        assertTrue(DownloadFilter.FAILED.matchesStatus(DownloadStatus.FAILED))
        assertTrue(DownloadFilter.COMPLETED.matchesStatus(DownloadStatus.COMPLETED))
        assertFalse(DownloadFilter.COMPLETED.matchesStatus(DownloadStatus.CANCELLED))
    }
}
