package io.legado.server.routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.legado.server.model.ReturnData
import io.legado.server.service.BookService

/**
 * Reading progress API routes
 */
fun Route.progressRoutes(bookService: BookService) {

    /**
     * POST /saveBookProgress
     * Saves reading progress for a book
     * Request body: { name, author, durChapterIndex, durChapterPos, durChapterTime, durChapterTitle }
     */
    post("/saveBookProgress") {
        try {
            val progress = call.receive<BookProgressRequest>()
            bookService.saveProgress(progress)
            call.respond(ReturnData.success(""))
        } catch (e: Exception) {
            call.respond(ReturnData.error(e.message ?: "Failed to save progress"))
        }
    }

    /**
     * GET /getReadingHistory
     * Returns books with saved reading progress, newest first.
     */
    get("/getReadingHistory") {
        try {
            call.respond(ReturnData.success(bookService.getReadingHistory()))
        } catch (e: Exception) {
            call.respond(ReturnData.error(e.message ?: "Failed to get reading history"))
        }
    }

    /**
     * POST /deleteReadingHistory
     * Deletes one reading history item without deleting the book.
     * Request body: { bookUrl }
     */
    post("/deleteReadingHistory") {
        try {
            val request = call.receive<ReadingHistoryDeleteRequest>()
            if (request.bookUrl.isNullOrBlank()) {
                call.respond(ReturnData.error("Missing bookUrl"))
                return@post
            }
            bookService.deleteReadingHistory(request.bookUrl)
            call.respond(ReturnData.success(""))
        } catch (e: Exception) {
            call.respond(ReturnData.error(e.message ?: "Failed to delete reading history"))
        }
    }

    /**
     * POST /clearReadingHistory
     * Deletes all reading history items without deleting books.
     */
    post("/clearReadingHistory") {
        try {
            val deletedCount = bookService.clearReadingHistory()
            call.respond(ReturnData.success(deletedCount))
        } catch (e: Exception) {
            call.respond(ReturnData.error(e.message ?: "Failed to clear reading history"))
        }
    }

    /**
     * GET /getReadConfig
     * Returns reading configuration
     */
    get("/getReadConfig") {
        val config = bookService.getReadConfig()
        call.respond(ReturnData.success(config))
    }

    /**
     * POST /saveReadConfig
     * Saves reading configuration
     */
    post("/saveReadConfig") {
        try {
            val configJson = call.receiveText()
            bookService.saveReadConfig(configJson)
            call.respond(ReturnData.success(""))
        } catch (e: Exception) {
            call.respond(ReturnData.error(e.message ?: "Failed to save config"))
        }
    }
}

/**
 * Progress request matching Legado's BookProgress entity
 */
data class BookProgressRequest(
    val name: String,
    val author: String,
    val durChapterIndex: Int,
    val durChapterPos: Int,
    val durChapterTime: Long,
    val durChapterTitle: String?
)

data class ReadingHistoryDeleteRequest(
    val bookUrl: String? = null
)
