package io.legado.server.service

import io.legado.server.data.BookRepository
import io.legado.server.data.Database
import io.legado.server.data.ReadProgressData
import io.legado.server.model.Book
import io.legado.server.model.BookChapter
import java.nio.file.Files
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookServiceCleanupTest {
    @Test
    fun `delete book removes cached chapters and read progress`() {
        initTempDatabase()
        val book = Book(bookUrl = "/missing/book.txt", name = "Missing Book")

        BookRepository.upsertBook(book)
        BookRepository.saveChapters(
            book.bookUrl,
            listOf(
                BookChapter(
                    url = "${book.bookUrl}#0",
                    title = "第一章",
                    bookUrl = book.bookUrl,
                    index = 0,
                    start = 0,
                    end = 10
                )
            )
        )
        BookRepository.saveReadProgress(
            ReadProgressData(
                bookUrl = book.bookUrl,
                durChapterIndex = 0,
                durChapterPos = 5,
                durChapterTime = 123,
                durChapterTitle = "第一章"
            )
        )

        BookRepository.deleteBook(book.bookUrl)

        assertNull(BookRepository.getBook(book.bookUrl))
        assertTrue(BookRepository.getChapters(book.bookUrl).isEmpty())
        assertNull(BookRepository.getReadProgress(book.bookUrl))
    }

    @Test
    fun `scan books directory prunes database records for deleted local files`() {
        initTempDatabase()
        val booksDir = Files.createTempDirectory("legado-cleanup-books")
        val otherDir = Files.createTempDirectory("legado-cleanup-other-books")
        val keptBook = booksDir.resolve("kept.txt")
        val deletedBook = booksDir.resolve("deleted.txt")
        val outsideBook = otherDir.resolve("outside.txt")
        keptBook.writeText("kept content")
        deletedBook.writeText("deleted content")
        outsideBook.writeText("outside content")
        val service = BookService(booksDir.toString())
        service.scanBooksDirectory()
        val deletedBookUrl = deletedBook.toFile().absolutePath
        val outsideBookModel = Book.fromFile(outsideBook.toFile())
        val remoteBook = Book(
            bookUrl = "https://example.test/book",
            name = "Remote Book",
            origin = "https://example.test/source",
            type = Book.TYPE_TEXT
        )
        BookRepository.upsertBook(outsideBookModel)
        BookRepository.upsertBook(remoteBook)

        deletedBook.deleteExisting()
        service.scanBooksDirectory()

        assertEquals(
            listOf(
                keptBook.toFile().absolutePath,
                outsideBook.toFile().absolutePath,
                remoteBook.bookUrl
            ).sorted(),
            BookRepository.getAllBooks().map { it.bookUrl }.sorted()
        )
        assertNull(BookRepository.getBook(deletedBookUrl))
        assertTrue(BookRepository.getChapters(deletedBookUrl).isEmpty())
    }

    private fun initTempDatabase() {
        val dbPath = Files.createTempDirectory("legado-cleanup-db").resolve("legado.db")
        Database.init(dbPath.toString())
    }
}
