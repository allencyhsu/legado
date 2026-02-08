package io.legado.server

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.http.*
import io.legado.server.data.Database
import io.legado.server.model.ReturnData
import io.legado.server.routes.bookRoutes
import io.legado.server.routes.progressRoutes
import io.legado.server.routes.ttsRoutes
import io.legado.server.service.BookService
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("Application")

fun main(args: Array<String>) {
    val port = System.getenv("PORT")?.toIntOrNull()
        ?: args.getOrNull(0)?.toIntOrNull()
        ?: 8080
    val booksDir = System.getenv("BOOKS_DIR")
        ?: args.getOrNull(1)
        ?: "/mnt/d/Books"
    val dbPath = System.getenv("DB_PATH")
        ?: args.getOrNull(2)
        ?: "./data/legado.db"
    val ttsUrl = System.getenv("TTS_URL")
        ?: args.getOrNull(3)
        ?: "http://10.243.2.5:8880"

    logger.info("Starting Legado Server...")
    logger.info("  Port: $port")
    logger.info("  Books directory: $booksDir")
    logger.info("  Database: $dbPath")
    logger.info("  TTS URL: $ttsUrl")

    // Ensure data directory exists
    File(dbPath).parentFile?.mkdirs()

    // Initialize database
    Database.init(dbPath)

    // Create HTTP client for TTS proxy
    val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 0 // Disable engine-level timeout
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000   // 5 min overall
            connectTimeoutMillis = 10_000    // 10s connect
            socketTimeoutMillis = 300_000    // 5 min socket idle
        }
    }

    // Create book service
    val bookService = BookService(booksDir)

    // Scan books directory on startup
    bookService.scanBooksDirectory()

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
                disableHtmlEscaping()
            }
        }

        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
        }

        install(CallLogging)

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                logger.error("Unhandled exception", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ReturnData.error(cause.message ?: "Unknown error")
                )
            }
        }

        routing {
            // API routes
            bookRoutes(bookService)
            progressRoutes(bookService)
            ttsRoutes(httpClient, ttsUrl)

            // Static Vue frontend
            staticResources("/", "web") {
                default("index.html")
            }
        }
    }.start(wait = true)
}
