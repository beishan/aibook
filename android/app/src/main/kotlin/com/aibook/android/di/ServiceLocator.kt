package com.aibook.android.di

import android.content.Context
import com.aibook.android.core.data.db.AiBookDatabase
import com.aibook.android.core.data.prefs.ReaderSettingsStore
import com.aibook.android.core.data.prefs.ServerConfigStore
import com.aibook.android.core.data.repository.BookRepository
import com.aibook.android.core.data.repository.OpdsCatalogCacheRepository
import com.aibook.android.core.data.repository.OpdsConnectionRepository
import com.aibook.android.core.data.repository.ScanDirectoryRepository
import com.aibook.android.core.data.repository.ServerRepository
import com.aibook.android.core.data.repository.ReaderBookmarkRepository
import com.aibook.android.core.data.repository.ReaderHighlightRepository
import com.aibook.android.core.network.opds.OkHttpOpdsTransport
import com.aibook.android.core.network.opds.OpdsCatalogService
import com.aibook.android.core.network.opds.OpdsFeedParser
import okhttp3.OkHttpClient

class ServiceLocator(private val context: Context) {

    private val database: AiBookDatabase by lazy { AiBookDatabase.get(context) }

    val readerSettingsStore: ReaderSettingsStore by lazy { ReaderSettingsStore(context) }

    val serverConfigStore: ServerConfigStore by lazy { ServerConfigStore(context) }

    val bookRepository: BookRepository by lazy {
        BookRepository(context, database.bookDao(), database.shelfFolderDao())
    }

    val opdsConnectionRepository: OpdsConnectionRepository by lazy {
        OpdsConnectionRepository(database.opdsConnectionDao())
    }

    val opdsCatalogCacheRepository: OpdsCatalogCacheRepository by lazy {
        OpdsCatalogCacheRepository(database.opdsCatalogEntryDao())
    }

    val scanDirectoryRepository: ScanDirectoryRepository by lazy {
        ScanDirectoryRepository(context, database.scanDirectoryDao(), bookRepository)
    }

    val serverRepository: ServerRepository by lazy {
        ServerRepository(serverConfigStore)
    }

    val readerBookmarkRepository: ReaderBookmarkRepository by lazy {
        ReaderBookmarkRepository(database.readerBookmarkDao())
    }

    val readerHighlightRepository: ReaderHighlightRepository by lazy {
        ReaderHighlightRepository(database.readerHighlightDao())
    }

    val opdsCatalogService: OpdsCatalogService by lazy {
        OpdsCatalogService(
            transport = OkHttpOpdsTransport(OkHttpClient()),
            parser = OpdsFeedParser()
        )
    }

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun get(context: Context): ServiceLocator {
            return instance ?: synchronized(this) {
                ServiceLocator(context.applicationContext).also { instance = it }
            }
        }
    }
}
