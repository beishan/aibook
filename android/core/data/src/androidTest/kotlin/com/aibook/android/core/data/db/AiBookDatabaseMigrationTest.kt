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

    @Test
    fun migration13To14_addsPagingIndexes() {
        migrationHelper.createDatabase(TEST_DATABASE, 13).close()

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            14,
            true,
            AiBookDatabase.MIGRATION_13_14
        ).use { migrated ->
            val expected = setOf(
                "index_books_shelved_lastReadAt_title",
                "index_books_shelved_folderId_lastReadAt",
                "index_books_sha256",
                "index_opds_catalog_entries_syncedAt_title"
            )
            migrated.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { cursor ->
                val actual = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
                assertTrue(actual.containsAll(expected))
            }
        }
    }

    @Test
    fun migration14To15_addsEditableRatingAndTags() {
        migrationHelper.createDatabase(TEST_DATABASE, 14).apply {
            insertExistingBook()
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            15,
            true,
            AiBookDatabase.MIGRATION_14_15
        ).use { migrated ->
            migrated.query("SELECT rating, tags FROM books WHERE id = ?", arrayOf(EXISTING_BOOK_ID)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals("", cursor.getString(1))
            }
        }
    }

    @Test
    fun migration15To16_encryptsOpdsPasswordAndAddsSyncMode() {
        migrationHelper.createDatabase(TEST_DATABASE, 15).apply {
            execSQL(
                """
                INSERT INTO opds_connections (
                    id, name, baseUrl, username, password, enabled, bookCount,
                    syncState, createdAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>("source-1", "测试源", "https://example.com/opds", "reader", "plain-secret", 1, 0, "IDLE", 1L)
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            16,
            true,
            AiBookDatabase.MIGRATION_15_16
        ).use { migrated ->
            migrated.query("SELECT passwordCiphertext, syncMode FROM opds_connections WHERE id = 'source-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0).startsWith("enc:v1:"))
                assertEquals("FULL", cursor.getString(1))
            }
            migrated.query("PRAGMA table_info(opds_connections)").use { cursor ->
                val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertTrue("password" !in columns)
            }
        }
    }

    @Test
    fun migration16To17_addsPersistentDownloadQueue() {
        migrationHelper.createDatabase(TEST_DATABASE, 16).close()

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            17,
            true,
            AiBookDatabase.MIGRATION_16_17
        ).use { migrated ->
            migrated.execSQL(
                """
                INSERT INTO download_tasks (
                    id, remoteEntryId, connectionId, title, href, fileName, status,
                    progress, downloadedBytes, createdAt, updatedAt
                ) VALUES ('task-1', 'book-1', 'source-1', '三体', '/1.epub', '三体.epub', 'PAUSED', 42, 1024, 1, 1)
                """.trimIndent()
            )
            migrated.query("SELECT status, progress FROM download_tasks WHERE id = 'task-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("PAUSED", cursor.getString(0))
                assertEquals(42, cursor.getInt(1))
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
