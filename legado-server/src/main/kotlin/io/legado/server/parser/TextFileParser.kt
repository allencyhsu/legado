package io.legado.server.parser

import io.legado.server.model.BookChapter
import io.legado.server.util.EncodingDetect
import org.slf4j.LoggerFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Parser for TXT files
 * Ported from Legado's TextFile.kt with Android dependencies removed
 */
object TextFileParser {

    private val logger = LoggerFactory.getLogger(TextFileParser::class.java)

    // Common chapter title patterns (Chinese novels)
    // These patterns are restrictive - chapter titles should be short lines
    private val defaultChapterPatterns = listOf(
        // 第X章 / 第X节 / 第X回 (with optional short title)
        """^\s*第[0-9一二三四五六七八九十百千万零〇]+[章节回]\s*.{0,30}$""",
        // Chapter X / CHAPTER X
        """^\s*[Cc]hapter\s*[0-9]+.{0,50}$""",
        // 数字. 标题 (numbered chapters)
        """^\s*[0-9]+[.、]\s*.{1,30}$""",
        // 卷X / 篇X
        """^\s*[卷篇][0-9一二三四五六七八九十百千万零〇]+.{0,30}$""",
        // 引子 / 序章 / 楔子 / 尾声
        """^\s*(引子|序章|序|楔子|前言|正文|终章|尾声|后记|番外).{0,20}$"""
    )

    private val combinedPattern by lazy {
        defaultChapterPatterns.joinToString("|") { "($it)" }.toRegex(RegexOption.MULTILINE)
    }

    // Maximum title length (characters)
    private const val MAX_TITLE_LENGTH = 50

    /**
     * Parse chapters from a TXT file
     */
    fun parseChapters(file: File, bookUrl: String): List<BookChapter> {
        if (!file.exists()) return emptyList()

        val charset = detectCharset(file)
        logger.info("Detected charset: $charset for ${file.name}")

        val chapters = mutableListOf<BookChapter>()
        val content = file.readText(charset)

        // Find all chapter matches
        val matches = combinedPattern.findAll(content).toList()

        if (matches.isEmpty()) {
            // No chapters found, treat the whole file as one chapter
            chapters.add(
                BookChapter(
                    url = "$bookUrl#0",
                    title = file.nameWithoutExtension,
                    bookUrl = bookUrl,
                    index = 0,
                    start = 0,
                    end = content.length.toLong()
                )
            )
        } else {
            // Filter out matches that are too long (likely content lines, not titles)
            val filteredMatches = matches.filter { match ->
                val title = match.value.trim()
                title.length <= MAX_TITLE_LENGTH && !title.contains("。") && !title.contains("？")
            }

            if (filteredMatches.isEmpty()) {
                // No valid chapters found, treat whole file as one chapter
                chapters.add(
                    BookChapter(
                        url = "$bookUrl#0",
                        title = file.nameWithoutExtension,
                        bookUrl = bookUrl,
                        index = 0,
                        start = 0,
                        end = content.length.toLong()
                    )
                )
                logger.info("Found ${chapters.size} chapters in ${file.name}")
                return chapters
            }

            // Add chapters based on filtered matches
            filteredMatches.forEachIndexed { index, match ->
                val start = match.range.first.toLong()
                val end = if (index < filteredMatches.size - 1) {
                    filteredMatches[index + 1].range.first.toLong()
                } else {
                    content.length.toLong()
                }

                val title = match.value.trim()
                    .replace("\n", " ")
                    .replace("\r", "")
                    .take(MAX_TITLE_LENGTH)

                chapters.add(
                    BookChapter(
                        url = "$bookUrl#$index",
                        title = title,
                        bookUrl = bookUrl,
                        index = index,
                        start = start,
                        end = end
                    )
                )
            }

            // If first chapter doesn't start at beginning, add a "preface" chapter
            if (matches.first().range.first > 100) {
                val preface = BookChapter(
                    url = "$bookUrl#preface",
                    title = "前言",
                    bookUrl = bookUrl,
                    index = -1,
                    start = 0,
                    end = matches.first().range.first.toLong()
                )
                chapters.add(0, preface)
                // Re-index chapters
                chapters.forEachIndexed { idx, ch ->
                    if (idx > 0) {
                        chapters[idx] = ch.copy(index = idx)
                    } else {
                        chapters[idx] = ch.copy(index = 0)
                    }
                }
            }
        }

        logger.info("Found ${chapters.size} chapters in ${file.name}")
        return chapters
    }

    /**
     * Get content of a specific chapter
     */
    fun getChapterContent(file: File, chapter: BookChapter): String {
        if (!file.exists()) return ""

        val start = chapter.start ?: 0
        val end = chapter.end ?: file.length()

        val charset = detectCharset(file)
        val content = file.readText(charset)

        val startIndex = start.toInt().coerceIn(0, content.length)
        val endIndex = end.toInt().coerceIn(startIndex, content.length)

        val chapterContent = content.substring(startIndex, endIndex)

        // Format content as HTML paragraphs
        return formatAsHtml(chapterContent)
    }

    /**
     * Detect file encoding
     */
    private fun detectCharset(file: File): Charset {
        return try {
            val detected = EncodingDetect.detect(file)
            Charset.forName(detected)
        } catch (e: Exception) {
            Charsets.UTF_8
        }
    }

    /**
     * Format plain text as HTML with paragraphs
     */
    private fun formatAsHtml(text: String): String {
        val lines = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return buildString {
            lines.forEach { line ->
                append("<p>")
                append(escapeHtml(line))
                append("</p>\n")
            }
        }
    }

    /**
     * Escape HTML special characters
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
