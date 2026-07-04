package io.legado.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.legado.server.service.BookService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationRoutingTest {
    @Test
    fun `chapter route returns vue app for direct browser history loads`() = withTestServer {
        val response = client.get("/chapter?token=abc&bookUrl=file%3A%2F%2Fbook.txt&chapterIndex=54")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().contains("text/html"))
    }

    @Test
    fun `source routes return vue app for direct browser history loads`() = withTestServer {
        assertEquals(HttpStatusCode.OK, client.get("/bookSource").status)
        assertEquals(HttpStatusCode.OK, client.get("/rssSource").status)
    }

    @Test
    fun `api routes are not handled by spa fallback`() = withTestServer {
        val response = client.get("/getChapterList")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().contains("application/json"))
    }

    private fun withTestServer(testBlock: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val booksDir = Files.createTempDirectory("legado-routing-test")
        val upstreamClient = HttpClient(CIO)
        try {
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
}
