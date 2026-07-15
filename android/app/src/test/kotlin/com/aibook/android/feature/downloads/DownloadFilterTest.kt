package com.aibook.android.feature.downloads

import com.aibook.android.core.data.repository.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadFilterTest {
    @Test
    fun activeIncludesQueuedAndRunningOnly() {
        assertTrue(DownloadFilter.ACTIVE.matchesStatus(DownloadStatus.QUEUED))
        assertTrue(DownloadFilter.ACTIVE.matchesStatus(DownloadStatus.RUNNING))
        assertFalse(DownloadFilter.ACTIVE.matchesStatus(DownloadStatus.PAUSED))
    }

    @Test
    fun terminalFiltersMatchTheirOwnState() {
        assertTrue(DownloadFilter.FAILED.matchesStatus(DownloadStatus.FAILED))
        assertTrue(DownloadFilter.COMPLETED.matchesStatus(DownloadStatus.COMPLETED))
        assertFalse(DownloadFilter.COMPLETED.matchesStatus(DownloadStatus.CANCELLED))
    }
}
