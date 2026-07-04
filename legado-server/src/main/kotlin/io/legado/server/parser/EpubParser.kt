package io.legado.server.parser

import io.legado.server.model.BookChapter
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

/**
 * Parser for EPUB files
 * Uses nl.siegmann.epublib (pure Java library)
 */
object EpubParser {

    private val logger = LoggerFactory.getLogger(EpubParser::class.java)

    /**
     * Parse chapters from an EPUB file
     */
    fun parseChapters(file: File, bookUrl: String): List<BookChapter> {
        if (!file.exists()) return emptyList()

        return try {
            FileInputStream(file).use { fis ->
                val epubReader = EpubReader()
                val book = epubReader.readEpub(fis)

                val chapters = mutableListOf<BookChapter>()

                // Get table of contents
                val toc = book.tableOfContents
                if (toc.tocReferences.isNotEmpty()) {
                    // Use TOC structure
                    toc.tocReferences.forEachIndexed { index, tocRef ->
                        val href = tocRef.resource?.href ?: return@forEachIndexed
                        chapters.add(
                            BookChapter(
                                url = "$bookUrl!$href",
                                title = tocRef.title ?: "Chapter ${index + 1}",
                                bookUrl = bookUrl,
                                index = index,
                                startFragmentId = href
                            )
                        )

                        // Add children (sub-chapters)
                        tocRef.children?.forEachIndexed { childIndex, childRef ->
                            val childHref = childRef.resource?.href ?: return@forEachIndexed
                            chapters.add(
                                BookChapter(
                                    url = "$bookUrl!$childHref",
                                    title = "  " + (childRef.title ?: "Section ${childIndex + 1}"),
                                    bookUrl = bookUrl,
                                    index = chapters.size,
                                    startFragmentId = childHref
                                )
                            )
                        }
                    }
                } else {
                    // No TOC, use spine order
                    book.spine.spineReferences.forEachIndexed { index, spineRef ->
                        val resource = spineRef.resource ?: return@forEachIndexed
                        val href = resource.href

                        // Try to extract title from content
                        val title = try {
                            val content = String(resource.data, Charsets.UTF_8)
                            val doc = Jsoup.parse(content)
                            doc.select("h1, h2, h3, title").firstOrNull()?.text()
                                ?: "Chapter ${index + 1}"
                        } catch (e: Exception) {
                            "Chapter ${index + 1}"
                        }

                        chapters.add(
                            BookChapter(
                                url = "$bookUrl!$href",
                                title = title,
                                bookUrl = bookUrl,
                                index = index,
                                startFragmentId = href
                            )
                        )
                    }
                }

                logger.info("Found ${chapters.size} chapters in ${file.name}")
                chapters
            }
        } catch (e: Exception) {
            logger.error("Failed to parse EPUB: ${file.name}", e)
            emptyList()
        }
    }

    /**
     * Get content of a specific chapter
     */
    fun getChapterContent(file: File, chapter: BookChapter): String {
        if (!file.exists()) return ""

        val href = chapter.startFragmentId ?: return ""

        return try {
            FileInputStream(file).use { fis ->
                val epubReader = EpubReader()
                val book = epubReader.readEpub(fis)

                // Find the resource by href
                val resource = book.resources.getByHref(href)
                    ?: book.resources.all.find { it.href.endsWith(href) }
                    ?: return ""

                val rawContent = String(resource.data, Charsets.UTF_8)

                // Parse and clean HTML
                val doc = Jsoup.parse(rawContent)

                // Remove scripts and styles
                doc.select("script, style, link").remove()

                // Get body content
                val body = doc.body()

                // Convert relative image paths to data URIs or remove
                body.select("img").forEach { img ->
                    val src = img.attr("src")
                    if (src.isNotBlank() && !src.startsWith("data:")) {
                        // Try to load image from EPUB
                        val imgData = getImageAsDataUri(file, src, href)
                        if (imgData != null) {
                            img.attr("src", imgData)
                        } else {
                            img.remove()
                        }
                    }
                }

                body.html()
            }
        } catch (e: Exception) {
            logger.error("Failed to get chapter content: ${chapter.title}", e)
            ""
        }
    }

    /**
     * Get cover image from EPUB
     */
    fun getCover(file: File): ByteArray? {
        if (!file.exists()) return null

        return try {
            FileInputStream(file).use { fis ->
                val epubReader = EpubReader()
                val book = epubReader.readEpub(fis)

                // Try to get cover image
                book.coverImage?.data
            }
        } catch (e: Exception) {
            logger.error("Failed to get cover: ${file.name}", e)
            null
        }
    }

    /**
     * Convert an image resource to data URI
     */
    private fun getImageAsDataUri(epubFile: File, imgSrc: String, baseHref: String): String? {
        return try {
            FileInputStream(epubFile).use { fis ->
                val epubReader = EpubReader()
                val book = epubReader.readEpub(fis)

                // Resolve relative path
                val basePath = baseHref.substringBeforeLast("/", "")
                val resolvedPath = if (imgSrc.startsWith("/")) {
                    imgSrc.substring(1)
                } else if (basePath.isNotEmpty()) {
                    "$basePath/$imgSrc"
                } else {
                    imgSrc
                }.replace("../", "")

                val imgResource = book.resources.getByHref(resolvedPath)
                    ?: book.resources.all.find { it.href.endsWith(imgSrc) }
                    ?: return null

                val mediaType = imgResource.mediaType?.name ?: "image/jpeg"
                val base64 = java.util.Base64.getEncoder().encodeToString(imgResource.data)
                "data:$mediaType;base64,$base64"
            }
        } catch (e: Exception) {
            null
        }
    }
}
