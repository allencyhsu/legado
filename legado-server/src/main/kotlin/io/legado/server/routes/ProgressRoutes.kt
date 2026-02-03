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
