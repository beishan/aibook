package com.aibook.android.core.reader

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class EpubContentParserTest {
    @Test
    fun `parses chapters in spine order`() {
        val bytes = epubBytes(
            opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>测试书</dc:title>
                  </metadata>
                  <manifest>
                    <item id="chap1" href="chapters/one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="chap2" href="chapters/two.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="chap1"/>
                    <itemref idref="chap2"/>
                  </spine>
                </package>
            """.trimIndent(),
            files = mapOf(
                "OEBPS/chapters/one.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body><h1>第一章 科学边界</h1><p>汪淼看见了倒计时。</p></body>
                    </html>
                """.trimIndent(),
                "OEBPS/chapters/two.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body><h2>第二章 台球</h2><p>宇宙不是一张台球桌。</p></body>
                    </html>
                """.trimIndent()
            )
        )

        val book = EpubContentParser.parse(bytes)

        assertEquals("测试书", book.metadata.title)
        assertEquals(2, book.chapters.size)
        assertEquals("第一章 科学边界", book.chapters[0].title)
        assertEquals("汪淼看见了倒计时。", book.chapters[0].content)
        assertEquals("第二章 台球", book.chapters[1].title)
        assertEquals("宇宙不是一张台球桌。", book.chapters[1].content)
    }

    @Test
    fun `falls back to manifest documents when spine is absent`() {
        val bytes = epubBytes(
            opf = """
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <manifest>
                    <item id="body" href="body.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                </package>
            """.trimIndent(),
            files = mapOf("OEBPS/body.xhtml" to "<html><body><p>正文内容</p></body></html>")
        )

        val book = EpubContentParser.parse(bytes)

        assertEquals(1, book.chapters.size)
        assertEquals("正文内容", book.chapters.single().content)
    }

    @Test
    fun `parses common xhtml with doctype and nbsp entity`() {
        val bytes = epubBytes(
            opf = """
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <manifest>
                    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="chapter"/>
                  </spine>
                </package>
            """.trimIndent(),
            files = mapOf(
                "OEBPS/chapter.xhtml" to """
                    <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"
                      "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body>
                        <h1>第一章</h1>
                        <p>第一段&nbsp;有空格。</p>
                      </body>
                    </html>
                """.trimIndent()
            )
        )

        val book = EpubContentParser.parse(bytes)

        assertEquals("第一段 有空格。", book.chapters.single().content)
    }

    private fun epubBytes(opf: String, files: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(opf.toByteArray())
            zip.closeEntry()

            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
