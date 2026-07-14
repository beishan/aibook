package com.aibook.android.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiBookDatabaseMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AiBookDatabase::class.java
    )

    @After
    fun deleteDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migration12To13_preservesBookAndAddsNullablePdfZoom() {
        migrationHelper.createDatabase(TEST_DATABASE, 12).apply {
            insertExistingBook()
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            13,
            true,
            AiBookDatabase.MIGRATION_12_13
        ).use { migrated ->
            migrated.query(
                "SELECT title, progressPercent, progressChapterIndex, progressPdfZoom FROM books WHERE id = ?",
                arrayOf(EXISTING_BOOK_ID)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Existing PDF", cursor.getString(0))
                assertEquals(0.5f, cursor.getFloat(1))
                assertEquals(4, cursor.getInt(2))
                assertTrue(cursor.isNull(3))
            }
        }
    }

    private fun SupportSQLiteDatabase.insertExistingBook() {
        execSQL(
            """
            INSERT INTO books (
                id, title, format, uri, status, favorite, importedAt,
                readingDurationSeconds, progressPercent, progressChapterIndex,
                progressScrollOffset, source, shelved, visibleInStore
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                EXISTING_BOOK_ID,
                "Existing PDF",
                "PDF",
                "file:///existing.pdf",
                "READING",
                0,
                1_700_000_000_000L,
                120L,
                0.5f,
                4,
                32,
                "LOCAL",
                1,
                1
            )
        )
    }

    private companion object {
        const val TEST_DATABASE = "migration-12-13-test"
        const val EXISTING_BOOK_ID = "existing-pdf"
    }
}
