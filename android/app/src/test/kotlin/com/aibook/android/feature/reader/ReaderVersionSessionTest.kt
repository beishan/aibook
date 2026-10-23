package com.aibook.android.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderVersionSessionTest {
    private class Probe : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Test
    fun keepsSameVersionButClearsOldReaderWhenSwitching() {
        val session = ReaderVersionSession()
        session.activate(1)
        val reader = Probe()
        session.viewModelStore.put("reader", reader)
        session.activate(1)
        assertFalse(reader.cleared)
        session.activate(2)
        assertTrue(reader.cleared)
        assertTrue(session.viewModelStore.keys().isEmpty())
    }

    @Test
    fun leavingTheRouteClearsTheActiveReader() {
        val parent = ViewModelStore()
        val session = ReaderVersionSession()
        session.activate(1)
        parent.put("session", session)
        val reader = Probe()
        session.viewModelStore.put("reader", reader)
        parent.clear()
        assertTrue(reader.cleared)
    }
}
