package com.aibook.android.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aibook.android.core.model.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

class ReaderSettingsStore(private val context: Context) {

    private object Keys {
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val THEME = stringPreferencesKey("reader_theme")
    }

    val fontScale: Flow<Float> = context.readerSettingsStore.data.map { it[Keys.FONT_SCALE] ?: 1.0f }
    val lineHeight: Flow<Float> = context.readerSettingsStore.data.map { it[Keys.LINE_HEIGHT] ?: 1.45f }
    val theme: Flow<ReaderTheme> = context.readerSettingsStore.data.map {
        val name = it[Keys.THEME] ?: ReaderTheme.PAPER.name
        runCatching { ReaderTheme.valueOf(name) }.getOrDefault(ReaderTheme.PAPER)
    }

    suspend fun setFontScale(value: Float) {
        context.readerSettingsStore.edit { it[Keys.FONT_SCALE] = value }
    }

    suspend fun setLineHeight(value: Float) {
        context.readerSettingsStore.edit { it[Keys.LINE_HEIGHT] = value }
    }

    suspend fun setTheme(theme: ReaderTheme) {
        context.readerSettingsStore.edit { it[Keys.THEME] = theme.name }
    }
}
