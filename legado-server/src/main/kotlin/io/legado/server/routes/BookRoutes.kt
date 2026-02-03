package io.legado.server.routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.legado.server.model.Book
import io.legado.server.model.ReturnData
import io.legado.server.service.BookService

/**
 * Book-related API routes
 * Compatible with Legado's web API
 */
fun Route.bookRoutes(bookService: BookService) {

    /**
     * GET /getBookshelf
     * Returns all books on the bookshelf
     */
    get("/getBookshelf") {
        val books = bookService.getAllBooks()
        call.respond(ReturnData.success(books))
    }

    /**
     * GET /getChapterList?url={bookUrl}
     * Returns chapter list for a book
     */
    get("/getChapterList") {
        val bookUrl = call.parameters["url"]
        if (bookUrl.isNullOrBlank()) {
            call.respond(ReturnData.error("Missing url parameter"))
            return@get
        }

        val chapters = bookService.getChapterList(bookUrl)
        call.respond(ReturnData.success(chapters))
    }

    /**
     * GET /getBookContent?url={bookUrl}&index={chapterIndex}
     * Returns the content of a specific chapter
     */
    get("/getBookContent") {
        val bookUrl = call.parameters["url"]
        val index = call.parameters["index"]?.toIntOrNull() ?: 0

        if (bookUrl.isNullOrBlank()) {
            call.respond(ReturnData.error("Missing url parameter"))
            return@get
        }

        val content = bookService.getChapterContent(bookUrl, index)
        call.respond(ReturnData.success(content))
    }

    /**
     * GET /refreshToc?url={bookUrl}
     * Refreshes the table of contents for a book
     */
    get("/refreshToc") {
        val bookUrl = call.parameters["url"]
        if (bookUrl.isNullOrBlank()) {
            call.respond(ReturnData.error("Missing url parameter"))
            return@get
        }

        val chapters = bookService.refreshChapterList(bookUrl)
        call.respond(ReturnData.success(chapters))
    }

    /**
     * POST /saveBook
     * Saves/updates a book
     */
    post("/saveBook") {
        try {
            val book = call.receive<Book>()
            bookService.saveBook(book)
            call.respond(ReturnData.success(""))
        } catch (e: Exception) {
            call.respond(ReturnData.error(e.message ?: "Failed to save book"))
        }
    }

    /**
     * POST /deleteBook
     * Deletes a book
     */
    post("/deleteBook") {
        try {
            val book = call.receive<Book>()
            bookService.deleteBook(book.bookUrl)
            call.respond(ReturnData.success(""))
        } catch (e: Exception) {
            call.respond(ReturnData.error(e.message ?: "Failed to delete book"))
        }
    }

    /**
     * GET /cover?path={coverPath}
     * Returns book cover image
     */
    get("/cover") {
        val path = call.parameters["path"]
        if (path.isNullOrBlank()) {
            call.respond(ReturnData.error("Missing path parameter"))
            return@get
        }

        val coverBytes = bookService.getCover(path)
        if (coverBytes != null) {
            call.respondBytes(coverBytes, io.ktor.http.ContentType.Image.PNG)
        } else {
            call.respond(ReturnData.error("Cover not found"))
        }
    }
}
