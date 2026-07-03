package com.aibook.android.core.reader

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class EpubMetadataParserTest {
    @Test
    fun `parses title author and language from opf metadata`() {
        val bytes = epubBytes(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>三体</dc:title>
                    <dc:creator>刘慈欣</dc:creator>
                    <dc:language>zh-CN</dc:language>
                  </metadata>
                </package>
            """.trimIndent()
        )

        val metadata = EpubMetadataParser.parse(bytes)

        assertEquals("三体", metadata.title)
        assertEquals("刘慈欣", metadata.author)
        assertEquals("zh-CN", metadata.language)
    }

    @Test
    fun `returns empty metadata when opf is absent`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("chapter.xhtml"))
            zip.write("<p>正文</p>".toByteArray())
            zip.closeEntry()
        }

        assertEquals(EpubMetadata(), EpubMetadataParser.parse(output.toByteArray()))
    }

    private fun epubBytes(opf: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(opf.toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
