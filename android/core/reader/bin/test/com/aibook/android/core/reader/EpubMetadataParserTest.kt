package com.aibook.android.core.reader

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

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

    @Test
    fun `parses cover image declared by metadata cover meta`() {
        val coverBytes = byteArrayOf(1, 2, 3, 4)
        val bytes = epubBytes(
            opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>有封面的书</dc:title>
                    <meta name="cover" content="cover-image"/>
                  </metadata>
                  <manifest>
                    <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg"/>
                  </manifest>
                </package>
            """.trimIndent(),
            files = mapOf("OEBPS/images/cover.jpg" to coverBytes)
        )

        val metadata = EpubMetadataParser.parse(bytes)

        assertEquals("images/cover.jpg", metadata.coverImage?.href)
        assertEquals("image/jpeg", metadata.coverImage?.mediaType)
        assertContentEquals(coverBytes, metadata.coverImage?.bytes)
    }

    @Test
    fun `ignores remote cover href without failing metadata parse`() {
        val bytes = epubBytes(
            opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>远程封面的书</dc:title>
                    <meta name="cover" content="cover-image"/>
                  </metadata>
                  <manifest>
                    <item id="cover-image" href="http://example.com/cover.jpg" media-type="image/jpeg"/>
                  </manifest>
                </package>
            """.trimIndent()
        )

        val metadata = EpubMetadataParser.parse(bytes)

        assertEquals("远程封面的书", metadata.title)
        assertEquals(null, metadata.coverImage)
    }

    private fun epubBytes(opf: String, files: Map<String, ByteArray> = emptyMap()): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(opf.toByteArray())
            zip.closeEntry()

            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
