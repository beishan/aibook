package com.aibook.android.feature.store

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.BookRepository
import com.aibook.android.core.model.LocalBook
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StoreViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {

    val books: StateFlow<List<LocalBook>> = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                StoreViewModel(ServiceLocator.get(app).bookRepository)
            }
        }
    }
}
