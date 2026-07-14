package com.aibook.android.core.mobi

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativeMobiDocumentParser : MobiDocumentParser {

    override suspend fun parse(filePath: String, outputDirectory: String): MobiParseResult =
        withContext(Dispatchers.IO) {
            val source = File(filePath)
            if (!source.exists()) return@withContext MobiParseResult.Failure(MobiParseError.FILE_MISSING)
            val output = File(outputDirectory).apply { mkdirs() }
            if (!output.isDirectory) return@withContext MobiParseResult.Failure(MobiParseError.INSUFFICIENT_STORAGE)

            val status = nativeParse(source.canonicalPath, output.canonicalPath)
            if (status != NativeMobiStatus.SUCCESS) {
                return@withContext MobiParseResult.Failure(NativeMobiStatus.toError(status))
            }

            val manifest = File(output, "chapters.manifest")
            val chapters = runCatching {
                manifest.readLines().mapIndexed { index, line ->
                    val fileName = line.substringAfter('\t').trim()
                    require(fileName.matches(Regex("chapter[0-9]{5}\\.[A-Za-z0-9]+")))
                    val chapterFile = File(output, fileName).canonicalFile
                    require(chapterFile.path.startsWith(output.canonicalPath + File.separator))
                    require(chapterFile.isFile)
                    MobiChapter(
                        title = null,
                        href = "mobi:$index",
                        htmlPath = chapterFile.path
                    )
                }
            }.getOrElse { return@withContext MobiParseResult.Failure(MobiParseError.CORRUPTED_FILE) }

            if (chapters.isEmpty()) {
                MobiParseResult.Failure(MobiParseError.UNSUPPORTED_VARIANT)
            } else {
                MobiParseResult.Success(
                    MobiDocument(
                        title = File(output, "title.txt").readOptionalText(),
                        author = File(output, "author.txt").readOptionalText(),
                        description = File(output, "description.txt").readOptionalText(),
                        coverPath = output.listFiles()
                            ?.firstOrNull { it.isFile && it.name.matches(Regex("cover\\.(jpg|gif|png|bmp|bin)")) }
                            ?.path,
                        chapters = chapters
                    )
                )
            }
        }

    private fun File.readOptionalText(): String? =
        takeIf(File::isFile)?.readText()?.trim()?.takeIf(String::isNotBlank)

    private external fun nativeParse(sourcePath: String, outputDirectory: String): Int

    companion object {
        init {
            System.loadLibrary("aibook_mobi")
        }
    }
}
