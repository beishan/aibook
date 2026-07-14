package com.aibook.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderFontCatalogTest {

    @Test
    fun builtInFontsExposeReadableLabels() {
        assertEquals(
            listOf("系统字体", "衬线字体", "无衬线字体", "等宽字体"),
            ReaderFontCatalog.builtInFonts.map { it.label }
        )
    }

    @Test
    fun importedFontNamesAreAcceptedOnlyForTtfAndOtf() {
        assertTrue(ReaderFontCatalog.isSupportedFontFile("霞鹜文楷.ttf"))
        assertTrue(ReaderFontCatalog.isSupportedFontFile("source-serif.OTF"))
        assertFalse(ReaderFontCatalog.isSupportedFontFile("font.zip"))
        assertFalse(ReaderFontCatalog.isSupportedFontFile("font"))
    }

    @Test
    fun selectedLabelFallsBackWhenCustomNameIsMissing() {
        assertEquals(
            "本地导入字体",
            ReaderFontCatalog.selectedLabel(
                ReaderSettings(fontType = ReaderFontType.CUSTOM, customFontName = null)
            )
        )
    }
}
