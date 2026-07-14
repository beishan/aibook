package com.aibook.android.core.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.aibook.android.core.model.ReaderContentsStyle
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSettingsStoreTest {
    @Test
    fun `contents style defaults to classic and persists grouped`() = runTest {
        val file = Files.createTempDirectory("reader-settings")
            .resolve("prefs.preferences_pb")
            .toFile()
        val store = ReaderSettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { file }
            )
        )

        assertEquals(ReaderContentsStyle.CLASSIC, store.contentsStyle.first())
        store.setContentsStyle(ReaderContentsStyle.GROUPED)
        assertEquals(ReaderContentsStyle.GROUPED, store.contentsStyle.first())
    }

    @Test
    fun `contents progress defaults to visible and persists hidden`() = runTest {
        val file = Files.createTempDirectory("reader-progress-visibility")
            .resolve("prefs.preferences_pb")
            .toFile()
        val store = ReaderSettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { file }
            )
        )

        assertEquals(true, store.showContentsProgress.first())
        store.setShowContentsProgress(false)
        assertEquals(false, store.showContentsProgress.first())
    }
}
