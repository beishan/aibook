package com.aibook.android.core.mobi

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialFixtureInventoryTest {

    private val fixtures = File("src/androidTest/assets/fixtures")

    @Test
    fun `official libmobi fixtures are complete and unmodified`() {
        val expected = mapOf(
            "kf7-textread.mobi" to "151c8169b2933b1aafa60df3fd4a50c222952e891adf58d5a797a27270c23371",
            "kf8-ncx.azw3" to "bb33217f3369d8ca6c5a373ca8e21a151be5494f5de6623aa9e7f42fb5fed743",
            "multimedia.mobi" to "a301cce8187f1f1ea9562196c46328ca75585da280d4b742827cebd4698697c9",
            "drm-v1.mobi" to "631e7afe719c04a91744c22f3021a2af1cafef541f93612a27d629ab74645494",
            "invalid-indx.fail" to "ba9b8727f9b71d67aa52f7e3c6aa6c49b35845bace9bf5e0b758797de3b484eb"
        )

        assertTrue(File(fixtures, "PROVENANCE.md").isFile, "missing fixture provenance")
        assertTrue(File(fixtures, "SHA256SUMS").isFile, "missing checksum manifest")
        assertEquals(
            expected.keys,
            fixtures.listFiles()
                .orEmpty()
                .filter { it.isFile && it.name !in setOf("PROVENANCE.md", "SHA256SUMS") }
                .mapTo(linkedSetOf(), File::getName)
        )
        expected.forEach { (name, hash) ->
            val fixture = File(fixtures, name)
            assertTrue(fixture.isFile, "missing fixture $name")
            assertEquals(hash, fixture.sha256(), "fixture changed: $name")
        }
    }

    private fun File.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(readBytes())
            .joinToString("") { "%02x".format(it) }
}
