package com.aibook.android.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aibook.android.core.model.PageTurnMode
import com.aibook.android.core.model.ParagraphSpacing
import com.aibook.android.core.model.ReaderFontType
import com.aibook.android.core.model.ReaderTheme
import com.aibook.android.core.model.TextAlignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

class ReaderSettingsStore(private val context: Context) {

    private object Keys {
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val FONT_TYPE = stringPreferencesKey("font_type")
        val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
        val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val THEME = stringPreferencesKey("reader_theme")
        val PARAGRAPH_SPACING = stringPreferencesKey("paragraph_spacing")
        val TEXT_ALIGNMENT = stringPreferencesKey("text_alignment")
        val PAGE_TURN_MODE = stringPreferencesKey("page_turn_mode")
        val AUTO_BRIGHTNESS = booleanPreferencesKey("auto_brightness")
        val SCREEN_ALWAYS_ON = booleanPreferencesKey("screen_always_on")
    }

    val fontScale: Flow<Float> =
        context.readerSettingsStore.data.map { it[Keys.FONT_SCALE] ?: 1.0f }

    val fontType: Flow<ReaderFontType> = context.readerSettingsStore.data.map {
        val name = it[Keys.FONT_TYPE] ?: ReaderFontType.SYSTEM.name
        runCatching { ReaderFontType.valueOf(name) }.getOrDefault(ReaderFontType.SYSTEM)
    }

    val customFontName: Flow<String?> =
        context.readerSettingsStore.data.map { it[Keys.CUSTOM_FONT_NAME] }

    val customFontPath: Flow<String?> =
        context.readerSettingsStore.data.map { it[Keys.CUSTOM_FONT_PATH] }

    val lineHeight: Flow<Float> =
        context.readerSettingsStore.data.map { it[Keys.LINE_HEIGHT] ?: 1.45f }

    val theme: Flow<ReaderTheme> = context.readerSettingsStore.data.map {
        val name = it[Keys.THEME] ?: ReaderTheme.PAPER.name
        runCatching { ReaderTheme.valueOf(name) }.getOrDefault(ReaderTheme.PAPER)
    }

    val paragraphSpacing: Flow<ParagraphSpacing> = context.readerSettingsStore.data.map {
        val name = it[Keys.PARAGRAPH_SPACING] ?: ParagraphSpacing.SMALL.name
        runCatching { ParagraphSpacing.valueOf(name) }.getOrDefault(ParagraphSpacing.SMALL)
    }

    val textAlignment: Flow<TextAlignment> = context.readerSettingsStore.data.map {
        val name = it[Keys.TEXT_ALIGNMENT] ?: TextAlignment.LEFT.name
        runCatching { TextAlignment.valueOf(name) }.getOrDefault(TextAlignment.LEFT)
    }

    val pageTurnMode: Flow<PageTurnMode> = context.readerSettingsStore.data.map {
        val name = it[Keys.PAGE_TURN_MODE] ?: PageTurnMode.SIMULATION.name
        runCatching { PageTurnMode.valueOf(name) }.getOrDefault(PageTurnMode.SIMULATION)
    }

    val autoBrightness: Flow<Boolean> =
        context.readerSettingsStore.data.map { it[Keys.AUTO_BRIGHTNESS] ?: true }

    val screenAlwaysOn: Flow<Boolean> =
        context.readerSettingsStore.data.map { it[Keys.SCREEN_ALWAYS_ON] ?: false }

    suspend fun setFontScale(value: Float) {
        context.readerSettingsStore.edit { it[Keys.FONT_SCALE] = value }
    }

    suspend fun setFontType(type: ReaderFontType) {
        context.readerSettingsStore.edit { it[Keys.FONT_TYPE] = type.name }
    }

    suspend fun setCustomFont(name: String, path: String) {
        context.readerSettingsStore.edit {
            it[Keys.FONT_TYPE] = ReaderFontType.CUSTOM.name
            it[Keys.CUSTOM_FONT_NAME] = name
            it[Keys.CUSTOM_FONT_PATH] = path
        }
    }

    suspend fun setLineHeight(value: Float) {
        context.readerSettingsStore.edit { it[Keys.LINE_HEIGHT] = value }
    }

    suspend fun setTheme(theme: ReaderTheme) {
        context.readerSettingsStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setParagraphSpacing(spacing: ParagraphSpacing) {
        context.readerSettingsStore.edit { it[Keys.PARAGRAPH_SPACING] = spacing.name }
    }

    suspend fun setTextAlignment(alignment: TextAlignment) {
        context.readerSettingsStore.edit { it[Keys.TEXT_ALIGNMENT] = alignment.name }
    }

    suspend fun setPageTurnMode(mode: PageTurnMode) {
        context.readerSettingsStore.edit { it[Keys.PAGE_TURN_MODE] = mode.name }
    }

    suspend fun setAutoBrightness(enabled: Boolean) {
        context.readerSettingsStore.edit { it[Keys.AUTO_BRIGHTNESS] = enabled }
    }

    suspend fun setScreenAlwaysOn(enabled: Boolean) {
        context.readerSettingsStore.edit { it[Keys.SCREEN_ALWAYS_ON] = enabled }
    }
}
