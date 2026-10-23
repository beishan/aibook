package com.aibook.android.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Retain the reader across rotation, but cancel all old work when changing versions. */
internal class ReaderVersionSession : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var activeVersionId: Long? = null

    fun activate(versionId: Long?) {
        if (activeVersionId != versionId) {
            viewModelStore.clear()
            activeVersionId = versionId
        }
    }

    override fun onCleared() {
        viewModelStore.clear()
    }
}
