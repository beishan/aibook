package com.aibook.android.core.mobi

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeMobiDocumentParserInstrumentedTest {

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun parsesOfficialKf7Fixture() = withFixture("kf7-textread.mobi") { input, output ->
        val result = runBlocking { NativeMobiDocumentParser().parse(input.path, output.path) }

        assertTrue(result is MobiParseResult.Success)
        assertTrue((result as MobiParseResult.Success).document.chapters.isNotEmpty())
    }

    @Test
    fun parsesOfficialKf8FixtureWithAzw3Extension() = withFixture("kf8-ncx.azw3") { input, output ->
        val result = runBlocking { NativeMobiDocumentParser().parse(input.path, output.path) }

        assertTrue(result is MobiParseResult.Success)
        assertTrue((result as MobiParseResult.Success).document.chapters.isNotEmpty())
    }

    @Test
    fun exportsImageFromOfficialMultimediaFixture() = withFixture("multimedia.mobi") { input, output ->
        val result = runBlocking { NativeMobiDocumentParser().parse(input.path, output.path) }

        assertTrue(result is MobiParseResult.Success)
        assertTrue((result as MobiParseResult.Success).document.chapters.isNotEmpty())
        assertTrue(
            "expected an exported image resource",
            output.listFiles().orEmpty().any {
                it.isFile && it.name.matches(Regex("resource[0-9]+\\.(gif|jpe?g|png|bmp)", RegexOption.IGNORE_CASE))
            }
        )
    }

    @Test
    fun rejectsOfficialDrmFixture() = withFixture("drm-v1.mobi") { input, output ->
        val result = runBlocking { NativeMobiDocumentParser().parse(input.path, output.path) }

        assertEquals(MobiParseResult.Failure(MobiParseError.DRM_PROTECTED), result)
    }

    @Test
    fun corruptFixtureReturnsFailureWithoutCrashing() = withFixture("invalid-indx.fail") { input, output ->
        val result = runBlocking { NativeMobiDocumentParser().parse(input.path, output.path) }

        assertTrue(result is MobiParseResult.Failure)
    }

    private fun withFixture(
        fixtureName: String,
        block: (input: File, output: File) -> Unit
    ) {
        val root = File(targetContext.cacheDir, "mobi-parser-test-${System.nanoTime()}")
        val input = File(root, fixtureName)
        val output = File(root, "parsed")
        try {
            assertTrue(root.mkdirs())
            testContext.assets.open("fixtures/$fixtureName").use { source ->
                input.outputStream().use(source::copyTo)
            }
            assertTrue(output.mkdirs())
            block(input, output)
        } finally {
            root.deleteRecursively()
        }
    }
}
