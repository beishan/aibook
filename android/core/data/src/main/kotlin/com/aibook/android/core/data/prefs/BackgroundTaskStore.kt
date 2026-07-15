package com.aibook.android.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.backgroundTaskStore: DataStore<Preferences> by preferencesDataStore(name = "background_tasks")

class BackgroundTaskStore(private val context: Context) {
    private object Keys {
        val AUTO_SCAN_ON_START = booleanPreferencesKey("auto_scan_on_start")
        val SCAN_INTERVAL_HOURS = intPreferencesKey("scan_interval_hours")
        val OPDS_INTERVAL_HOURS = intPreferencesKey("opds_interval_hours")
    }

    val autoScanOnStart: Flow<Boolean> = context.backgroundTaskStore.data.map { it[Keys.AUTO_SCAN_ON_START] ?: false }
    val scanIntervalHours: Flow<Int> = context.backgroundTaskStore.data.map { it[Keys.SCAN_INTERVAL_HOURS] ?: 0 }
    val opdsIntervalHours: Flow<Int> = context.backgroundTaskStore.data.map { it[Keys.OPDS_INTERVAL_HOURS] ?: 0 }

    suspend fun setAutoScanOnStart(enabled: Boolean) { context.backgroundTaskStore.edit { it[Keys.AUTO_SCAN_ON_START] = enabled } }
    suspend fun setScanIntervalHours(hours: Int) { context.backgroundTaskStore.edit { it[Keys.SCAN_INTERVAL_HOURS] = hours.coerceAtLeast(0) } }
    suspend fun setOpdsIntervalHours(hours: Int) { context.backgroundTaskStore.edit { it[Keys.OPDS_INTERVAL_HOURS] = hours.coerceAtLeast(0) } }
}
