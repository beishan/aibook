package com.aibook.android.core.data.repository

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

class ParsedBookStorage(filesDirectory: File) {

    private val root = File(filesDirectory, "parsed-books").canonicalFile

    fun directoryFor(bookId: String): File {
        require(BOOK_ID.matches(bookId)) { "Invalid book ID" }
        val directory = File(root, bookId).canonicalFile
        require(directory.path.startsWith(root.path + File.separator)) { "Book cache escaped root" }
        return directory
    }

    fun deleteForBook(bookId: String) {
        deleteWithoutFollowingLinks(directoryFor(bookId).toPath())
    }

    private fun deleteWithoutFollowingLinks(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) {
            Files.newDirectoryStream(path).use { children ->
                children.forEach(::deleteWithoutFollowingLinks)
            }
        }
        Files.deleteIfExists(path)
    }

    private companion object {
        val BOOK_ID = Regex("[A-Za-z0-9_-]+")
    }
}
