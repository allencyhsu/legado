package io.legado.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.legado.server.data.BookRepository
import io.legado.server.data.Database
import io.legado.server.data.ReadProgressData
import io.legado.server.model.Book
import io.legado.server.service.BookService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingHistoryRoutingTest {
    @Test
    fun `reading history routes list delete and clear progress`() = withReadingHistoryServer {
        val listResponse = client.get("/getReadingHistory")
        val listBody = listResponse.bodyAsText()

        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertTrue(listBody.contains("\"isSuccess\": true"))
        assertTrue(listBody.indexOf("New Route Book") < listBody.indexOf("Old Route Book"))

        val deleteResponse = client.post("/deleteReadingHistory") {
            contentType(ContentType.Application.Json)
            setBody("""{"bookUrl":"/books/old-route.txt"}""")
        }
        val deleteBody = deleteResponse.bodyAsText()

        assertEquals(HttpStatusCode.OK, deleteResponse.status)
        assertTrue(deleteBody.contains("\"isSuccess\": true"))
        assertNotNull(BookRepository.getBook("/books/old-route.txt"))
        assertNull(BookRepository.getReadProgress("/books/old-route.txt"))
        assertNotNull(BookRepository.getReadProgress("/books/new-route.txt"))

        val clearResponse = client.post("/clearReadingHistory")
        val clearBody = clearResponse.bodyAsText()

        assertEquals(HttpStatusCode.OK, clearResponse.status)
        assertTrue(clearBody.contains("\"isSuccess\": true"))
        assertTrue(clearBody.contains("\"data\": 1"))
        assertNotNull(BookRepository.getBook("/books/new-route.txt"))
        assertNull(BookRepository.getReadProgress("/books/new-route.txt"))
    }

    @Test
    fun `delete reading history rejects missing bookUrl`() = withReadingHistoryServer {
        val response = client.post("/deleteReadingHistory") {
            contentType(ContentType.Application.Json)
            setBody("""{"bookUrl":""}""")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"isSuccess\": false"))
        assertTrue(body.contains("Missing bookUrl"))
    }

    private fun withReadingHistoryServer(testBlock: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val dbPath = Files.createTempDirectory("legado-reading-history-routing-db").resolve("legado.db")
        val booksDir = Files.createTempDirectory("legado-reading-history-routing-books")
        val upstreamClient = HttpClient(CIO)
        try {
            Database.init(dbPath.toString())
            seedHistory()
            application {
                configureLegadoServer(
                    BookService(booksDir.toString()),
                    upstreamClient,
                    "http://127.0.0.1:65535"
                )
            }
            testBlock()
        } finally {
            upstreamClient.close()
            booksDir.toFile().deleteRecursively()
        }
    }

    private fun seedHistory() {
        val oldBook = Book(bookUrl = "/books/old-route.txt", name = "Old Route Book")
        val newBook = Book(bookUrl = "/books/new-route.txt", name = "New Route Book")
        BookRepository.upsertBook(oldBook)
        BookRepository.upsertBook(newBook)
        BookRepository.saveReadProgress(progress(oldBook, chapterTime = 1000))
        BookRepository.saveReadProgress(progress(newBook, chapterTime = 2000))
    }

    private fun progress(book: Book, chapterTime: Long) = ReadProgressData(
        bookUrl = book.bookUrl,
        durChapterIndex = 3,
        durChapterPos = 15,
        durChapterTime = chapterTime,
        durChapterTitle = "Route Chapter $chapterTime"
    )
}
