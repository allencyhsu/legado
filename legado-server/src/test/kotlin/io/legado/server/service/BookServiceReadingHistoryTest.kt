package io.legado.server.service

import io.legado.server.data.BookRepository
import io.legado.server.data.Database
import io.legado.server.data.ReadProgressData
import io.legado.server.model.Book
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BookServiceReadingHistoryTest {
    @Test
    fun `reading history returns books ordered by newest progress first`() {
        initTempDatabase()
        val oldBook = Book(bookUrl = "/books/old.txt", name = "Old Book")
        val newBook = Book(bookUrl = "/books/new.txt", name = "New Book")
        val unreadBook = Book(bookUrl = "/books/unread.txt", name = "Unread Book")
        BookRepository.upsertBook(oldBook)
        BookRepository.upsertBook(newBook)
        BookRepository.upsertBook(unreadBook)
        BookRepository.saveReadProgress(progress(oldBook, chapterTime = 100))
        BookRepository.saveReadProgress(progress(newBook, chapterTime = 200))

        val history = BookService("/books").getReadingHistory()

        assertEquals(listOf("New Book", "Old Book"), history.map { it.name })
        assertEquals(listOf(200L, 100L), history.map { it.durChapterTime })
        assertEquals(listOf(2, 1), history.map { it.durChapterIndex })
    }

    @Test
    fun `deleting one reading history item removes progress but keeps the book`() {
        initTempDatabase()
        val book = Book(bookUrl = "/books/history.txt", name = "History Book")
        BookRepository.upsertBook(book)
        BookRepository.saveReadProgress(progress(book, chapterTime = 300))

        val deletedCount = BookService("/books").deleteReadingHistory(book.bookUrl)

        assertEquals(1, deletedCount)
        assertNotNull(BookRepository.getBook(book.bookUrl))
        assertNull(BookRepository.getReadProgress(book.bookUrl))
    }

    @Test
    fun `clearing reading history removes all progress but keeps books`() {
        initTempDatabase()
        val firstBook = Book(bookUrl = "/books/first.txt", name = "First Book")
        val secondBook = Book(bookUrl = "/books/second.txt", name = "Second Book")
        BookRepository.upsertBook(firstBook)
        BookRepository.upsertBook(secondBook)
        BookRepository.saveReadProgress(progress(firstBook, chapterTime = 400))
        BookRepository.saveReadProgress(progress(secondBook, chapterTime = 500))

        val deletedCount = BookService("/books").clearReadingHistory()

        assertEquals(2, deletedCount)
        assertNotNull(BookRepository.getBook(firstBook.bookUrl))
        assertNotNull(BookRepository.getBook(secondBook.bookUrl))
        assertNull(BookRepository.getReadProgress(firstBook.bookUrl))
        assertNull(BookRepository.getReadProgress(secondBook.bookUrl))
    }

    private fun initTempDatabase() {
        val dbPath = Files.createTempDirectory("legado-reading-history-db").resolve("legado.db")
        Database.init(dbPath.toString())
    }

    private fun progress(book: Book, chapterTime: Long) = ReadProgressData(
        bookUrl = book.bookUrl,
        durChapterIndex = if (chapterTime >= 200) 2 else 1,
        durChapterPos = 12,
        durChapterTime = chapterTime,
        durChapterTitle = "Chapter $chapterTime"
    )
}
