package com.aibook.android.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aibook.android.core.model.AccentColor
import com.aibook.android.core.model.AppThemeMode
import com.aibook.android.core.model.PageTurnMode
import com.aibook.android.core.model.ParagraphSpacing
import com.aibook.android.core.model.ReaderContentsStyle
import com.aibook.android.core.model.ReaderAutoScrollSpeed
import com.aibook.android.core.model.ReaderFontType
import com.aibook.android.core.model.ReaderOrientationMode
import com.aibook.android.core.model.ReaderTheme
import com.aibook.android.core.model.TextAlignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

class ReaderSettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.readerSettingsStore)

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
        val BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val ORIENTATION_MODE = stringPreferencesKey("reader_orientation_mode")
        val AUTO_PAGE_INTERVAL_SECONDS = intPreferencesKey("auto_page_interval_seconds")
        val AUTO_SCROLL_SPEED = stringPreferencesKey("auto_scroll_speed")
        val SCREEN_ALWAYS_ON = booleanPreferencesKey("screen_always_on")
        val COMPRESS_TXT_BLANK_LINES = booleanPreferencesKey("compress_txt_blank_lines")
        val MERGE_TXT_SHORT_LINES = booleanPreferencesKey("merge_txt_short_lines")
        val INDENT_TXT_PARAGRAPHS = booleanPreferencesKey("indent_txt_paragraphs")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val CONTENTS_STYLE = stringPreferencesKey("contents_style")
        val SHOW_CONTENTS_PROGRESS = booleanPreferencesKey("show_contents_progress")
    }

    val fontScale: Flow<Float> =
        dataStore.data.map { it[Keys.FONT_SCALE] ?: 1.0f }

    val fontType: Flow<ReaderFontType> = dataStore.data.map {
        val name = it[Keys.FONT_TYPE] ?: ReaderFontType.SYSTEM.name
        runCatching { ReaderFontType.valueOf(name) }.getOrDefault(ReaderFontType.SYSTEM)
    }

    val customFontName: Flow<String?> =
        dataStore.data.map { it[Keys.CUSTOM_FONT_NAME] }

    val customFontPath: Flow<String?> =
        dataStore.data.map { it[Keys.CUSTOM_FONT_PATH] }

    val lineHeight: Flow<Float> =
        dataStore.data.map { it[Keys.LINE_HEIGHT] ?: 1.45f }

    val theme: Flow<ReaderTheme> = dataStore.data.map {
        val name = it[Keys.THEME] ?: ReaderTheme.PAPER.name
        runCatching { ReaderTheme.valueOf(name) }.getOrDefault(ReaderTheme.PAPER)
    }

    val paragraphSpacing: Flow<ParagraphSpacing> = dataStore.data.map {
        val name = it[Keys.PARAGRAPH_SPACING] ?: ParagraphSpacing.SMALL.name
        runCatching { ParagraphSpacing.valueOf(name) }.getOrDefault(ParagraphSpacing.SMALL)
    }

    val textAlignment: Flow<TextAlignment> = dataStore.data.map {
        val name = it[Keys.TEXT_ALIGNMENT] ?: TextAlignment.LEFT.name
        runCatching { TextAlignment.valueOf(name) }.getOrDefault(TextAlignment.LEFT)
    }

    val pageTurnMode: Flow<PageTurnMode> = dataStore.data.map {
        val name = it[Keys.PAGE_TURN_MODE] ?: PageTurnMode.SIMULATION.name
        runCatching { PageTurnMode.valueOf(name) }.getOrDefault(PageTurnMode.SIMULATION)
    }

    val autoBrightness: Flow<Boolean> =
        dataStore.data.map { it[Keys.AUTO_BRIGHTNESS] ?: true }

    val brightness: Flow<Float> =
        dataStore.data.map { (it[Keys.BRIGHTNESS] ?: 0.6f).coerceIn(0.1f, 1f) }

    val orientationMode: Flow<ReaderOrientationMode> = dataStore.data.map {
        val name = it[Keys.ORIENTATION_MODE] ?: ReaderOrientationMode.SYSTEM.name
        runCatching { ReaderOrientationMode.valueOf(name) }.getOrDefault(ReaderOrientationMode.SYSTEM)
    }

    val autoPageIntervalSeconds: Flow<Int> =
        dataStore.data.map { (it[Keys.AUTO_PAGE_INTERVAL_SECONDS] ?: 8).coerceIn(3, 30) }

    val autoScrollSpeed: Flow<ReaderAutoScrollSpeed> = dataStore.data.map {
        val name = it[Keys.AUTO_SCROLL_SPEED] ?: ReaderAutoScrollSpeed.MEDIUM.name
        runCatching { ReaderAutoScrollSpeed.valueOf(name) }.getOrDefault(ReaderAutoScrollSpeed.MEDIUM)
    }

    val screenAlwaysOn: Flow<Boolean> =
        dataStore.data.map { it[Keys.SCREEN_ALWAYS_ON] ?: false }

    val compressTxtBlankLines: Flow<Boolean> =
        dataStore.data.map { it[Keys.COMPRESS_TXT_BLANK_LINES] ?: true }
    val mergeTxtShortLines: Flow<Boolean> = dataStore.data.map { it[Keys.MERGE_TXT_SHORT_LINES] ?: false }
    val indentTxtParagraphs: Flow<Boolean> = dataStore.data.map { it[Keys.INDENT_TXT_PARAGRAPHS] ?: false }

    val appThemeMode: Flow<AppThemeMode> = dataStore.data.map {
        val name = it[Keys.APP_THEME_MODE] ?: AppThemeMode.SYSTEM.name
        runCatching { AppThemeMode.valueOf(name) }.getOrDefault(AppThemeMode.SYSTEM)
    }

    val accentColor: Flow<AccentColor> = dataStore.data.map {
        val name = it[Keys.ACCENT_COLOR] ?: AccentColor.ORANGE.name
        runCatching { AccentColor.valueOf(name) }.getOrDefault(AccentColor.ORANGE)
    }

    val contentsStyle: Flow<ReaderContentsStyle> = dataStore.data.map {
        ReaderContentsStyle.fromStoredValue(it[Keys.CONTENTS_STYLE])
    }

    val showContentsProgress: Flow<Boolean> =
        dataStore.data.map { it[Keys.SHOW_CONTENTS_PROGRESS] ?: true }

    suspend fun setFontScale(value: Float) {
        dataStore.edit { it[Keys.FONT_SCALE] = value }
    }

    suspend fun setFontType(type: ReaderFontType) {
        dataStore.edit { it[Keys.FONT_TYPE] = type.name }
    }

    suspend fun setCustomFont(name: String, path: String) {
        dataStore.edit {
            it[Keys.FONT_TYPE] = ReaderFontType.CUSTOM.name
            it[Keys.CUSTOM_FONT_NAME] = name
            it[Keys.CUSTOM_FONT_PATH] = path
        }
    }

    suspend fun setLineHeight(value: Float) {
        dataStore.edit { it[Keys.LINE_HEIGHT] = value }
    }

    suspend fun setTheme(theme: ReaderTheme) {
        dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setParagraphSpacing(spacing: ParagraphSpacing) {
        dataStore.edit { it[Keys.PARAGRAPH_SPACING] = spacing.name }
    }

    suspend fun setTextAlignment(alignment: TextAlignment) {
        dataStore.edit { it[Keys.TEXT_ALIGNMENT] = alignment.name }
    }

    suspend fun setPageTurnMode(mode: PageTurnMode) {
        dataStore.edit { it[Keys.PAGE_TURN_MODE] = mode.name }
    }

    suspend fun setAutoBrightness(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_BRIGHTNESS] = enabled }
    }

    suspend fun setBrightness(value: Float) {
        dataStore.edit { it[Keys.BRIGHTNESS] = value.coerceIn(0.1f, 1f) }
    }

    suspend fun setOrientationMode(mode: ReaderOrientationMode) {
        dataStore.edit { it[Keys.ORIENTATION_MODE] = mode.name }
    }

    suspend fun setAutoPageIntervalSeconds(seconds: Int) {
        dataStore.edit { it[Keys.AUTO_PAGE_INTERVAL_SECONDS] = seconds.coerceIn(3, 30) }
    }

    suspend fun setAutoScrollSpeed(speed: ReaderAutoScrollSpeed) {
        dataStore.edit { it[Keys.AUTO_SCROLL_SPEED] = speed.name }
    }

    suspend fun setScreenAlwaysOn(enabled: Boolean) {
        dataStore.edit { it[Keys.SCREEN_ALWAYS_ON] = enabled }
    }

    suspend fun setCompressTxtBlankLines(enabled: Boolean) {
        dataStore.edit { it[Keys.COMPRESS_TXT_BLANK_LINES] = enabled }
    }
    suspend fun setMergeTxtShortLines(enabled: Boolean) { dataStore.edit { it[Keys.MERGE_TXT_SHORT_LINES] = enabled } }
    suspend fun setIndentTxtParagraphs(enabled: Boolean) { dataStore.edit { it[Keys.INDENT_TXT_PARAGRAPHS] = enabled } }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        dataStore.edit { it[Keys.APP_THEME_MODE] = mode.name }
    }

    suspend fun setAccentColor(color: AccentColor) {
        dataStore.edit { it[Keys.ACCENT_COLOR] = color.name }
    }

    suspend fun setContentsStyle(style: ReaderContentsStyle) {
        dataStore.edit { it[Keys.CONTENTS_STYLE] = style.name }
    }

    suspend fun setShowContentsProgress(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_CONTENTS_PROGRESS] = show }
    }
}
