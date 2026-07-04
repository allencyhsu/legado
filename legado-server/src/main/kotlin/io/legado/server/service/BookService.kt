package io.legado.server.service

import io.legado.server.data.BookRepository
import io.legado.server.data.ReadProgressData
import io.legado.server.model.Book
import io.legado.server.model.BookChapter
import io.legado.server.parser.EpubParser
import io.legado.server.parser.TextFileParser
import io.legado.server.routes.BookProgressRequest
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Main service for book management
 */
class BookService(private val booksDir: String) {

    private val logger = LoggerFactory.getLogger(BookService::class.java)
    private var readConfig: String = DEFAULT_READ_CONFIG

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("txt", "epub")
        private val SUPPORTED_COVER_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
        private const val DEFAULT_READ_CONFIG = """{"theme":0,"font":0,"fontSize":18,"readWidth":800}"""
    }

    /**
     * Scan the books directory and import all books
     */
    fun scanBooksDirectory() {
        val dir = File(booksDir)
        if (!dir.exists() || !dir.isDirectory) {
            logger.warn("Books directory does not exist: $booksDir")
            return
        }

        logger.info("Scanning books directory: $booksDir")
        var count = 0

        dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            .forEach { file ->
                try {
                    importBook(file)
                    count++
                } catch (e: Exception) {
                    logger.error("Failed to import book: ${file.absolutePath}", e)
                }
            }

        val staleBookUrls = BookRepository.getAllBooks()
            .filter { it.isLocal && (it.isTxt || it.isEpub) && !File(it.bookUrl).exists() }
            .map { it.bookUrl }
            .toSet()
        val prunedCount = BookRepository.deleteBooks(staleBookUrls)
        if (prunedCount > 0) {
            logger.info("Pruned $prunedCount stale books")
        }

        logger.info("Imported $count books")
    }

    /**
     * Import a single book file
     */
    private fun importBook(file: File) {
        val existing = BookRepository.getBook(file.absolutePath)
        if (existing != null && existing.lastCheckTime >= file.lastModified()) {
            // Book already up to date
            return
        }

        logger.info("Importing: ${file.name}")

        val book = Book.fromFile(file)
        val chapters = parseChapters(book)

        val updatedBook = book.copy(
            totalChapterNum = chapters.size,
            latestChapterTitle = chapters.lastOrNull()?.title,
            lastCheckTime = file.lastModified()
        )

        BookRepository.upsertBook(updatedBook)
        BookRepository.saveChapters(book.bookUrl, chapters)
    }

    /**
     * Parse chapters from a book file
     */
    private fun parseChapters(book: Book): List<BookChapter> {
        val file = File(book.bookUrl)
        if (!file.exists()) return emptyList()

        return when {
            book.isEpub -> EpubParser.parseChapters(file, book.bookUrl)
            book.isTxt -> TextFileParser.parseChapters(file, book.bookUrl)
            else -> emptyList()
        }
    }

    /**
     * Get all books
     */
    fun getAllBooks(): List<Book> {
        return BookRepository.getAllBooks()
    }

    /**
     * Get chapter list for a book
     */
    fun getChapterList(bookUrl: String): List<BookChapter> {
        var chapters = BookRepository.getChapters(bookUrl)

        // If no chapters in DB, try parsing the file
        if (chapters.isEmpty()) {
            val book = BookRepository.getBook(bookUrl)
            if (book != null) {
                chapters = parseChapters(book)
                if (chapters.isNotEmpty()) {
                    BookRepository.saveChapters(bookUrl, chapters)
                }
            }
        }

        return chapters
    }

    /**
     * Refresh chapter list (re-parse from file)
     */
    fun refreshChapterList(bookUrl: String): List<BookChapter> {
        val book = BookRepository.getBook(bookUrl) ?: return emptyList()
        val chapters = parseChapters(book)

        if (chapters.isNotEmpty()) {
            BookRepository.saveChapters(bookUrl, chapters)

            // Update book info
            val updatedBook = book.copy(
                totalChapterNum = chapters.size,
                latestChapterTitle = chapters.lastOrNull()?.title,
                lastCheckTime = System.currentTimeMillis()
            )
            BookRepository.upsertBook(updatedBook)
        }

        return chapters
    }

    /**
     * Get chapter content
     */
    fun getChapterContent(bookUrl: String, chapterIndex: Int): String {
        val chapters = getChapterList(bookUrl)
        if (chapterIndex < 0 || chapterIndex >= chapters.size) {
            return ""
        }

        val chapter = chapters[chapterIndex]
        val file = File(bookUrl)
        if (!file.exists()) return ""

        return when {
            bookUrl.endsWith(".epub", true) -> EpubParser.getChapterContent(file, chapter)
            bookUrl.endsWith(".txt", true) -> TextFileParser.getChapterContent(file, chapter)
            else -> ""
        }
    }

    /**
     * Save a book
     */
    fun saveBook(book: Book) {
        BookRepository.upsertBook(book)
    }

    /**
     * Delete a book
     */
    fun deleteBook(bookUrl: String) {
        BookRepository.deleteBook(bookUrl)
    }

    /**
     * Save reading progress
     */
    fun saveProgress(progress: BookProgressRequest) {
        // Find book by name and author
        val books = getAllBooks()
        val book = books.find { it.name == progress.name && it.author == progress.author }
            ?: books.find { it.name == progress.name }

        if (book != null) {
            BookRepository.saveReadProgress(
                ReadProgressData(
                    bookUrl = book.bookUrl,
                    durChapterIndex = progress.durChapterIndex,
                    durChapterPos = progress.durChapterPos,
                    durChapterTime = progress.durChapterTime,
                    durChapterTitle = progress.durChapterTitle
                )
            )
        }
    }

    /**
     * Get server-backed reading history.
     */
    fun getReadingHistory(): List<Book> {
        return BookRepository.getReadingHistory()
    }

    /**
     * Delete one reading history item without deleting the book.
     */
    fun deleteReadingHistory(bookUrl: String): Int {
        return BookRepository.deleteReadProgress(bookUrl)
    }

    /**
     * Clear all reading history without deleting books.
     */
    fun clearReadingHistory(): Int {
        return BookRepository.clearReadProgress()
    }

    /**
     * Get reading configuration
     */
    fun getReadConfig(): String = readConfig

    /**
     * Save reading configuration
     */
    fun saveReadConfig(config: String) {
        readConfig = config
    }

    /**
     * Get book cover
     */
    fun getCover(path: String): ByteArray? {
        // For EPUB, extract cover from the file
        if (path.contains(".epub", true)) {
            val file = File(path.substringBefore("!"))
            if (file.exists() && file.extension.equals("epub", true)) {
                return EpubParser.getCover(file)
            }
        }

        // For local image files
        val file = File(path)
        if (
            file.exists() &&
            file.isFile &&
            file.extension.lowercase() in SUPPORTED_COVER_EXTENSIONS
        ) {
            return file.readBytes()
        }

        return null
    }
}
