package io.legado.server.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.legado.server.model.ReturnData
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TtsRoutes")

/**
 * TTS request from frontend
 */
data class TtsSpeechRequest(
    val text: String,
    val voice: String = "zf_xiaoxiao",
    val speed: Double = 1.0,
    val instruct: String? = null
)

/**
 * TTS proxy routes
 * Forwards requests to Kokoro-FastAPI OpenAI-compatible API server
 */
fun Route.ttsRoutes(httpClient: HttpClient, ttsBaseUrl: String) {

    /**
     * POST /tts/speech
     * Proxy to TTS server's /v1/audio/speech endpoint
     * Returns audio/mpeg stream
     */
    post("/tts/speech") {
        val request = try {
            call.receive<TtsSpeechRequest>()
        } catch (e: Exception) {
            call.respond(ReturnData.error("Invalid request: ${e.message}"))
            return@post
        }

        val normalizedText = normalizeTtsInput(request.text)

        if (normalizedText.isBlank()) {
            call.respond(ReturnData.error("Text cannot be empty"))
            return@post
        }

        logger.info("TTS request: voice=${request.voice}, speed=${request.speed}, text=${normalizedText.take(50)}...")

        try {
            logger.info("Proxying to $ttsBaseUrl/v1/audio/speech ...")
            val ttsResponse = httpClient.post("$ttsBaseUrl/v1/audio/speech") {
                contentType(ContentType.Application.Json)
                setBody(buildString {
                    append("""{"model":"kokoro","voice":"${request.voice}"""")
                    append(""","input":${com.google.gson.Gson().toJson(normalizedText)}""")
                    append(""","response_format":"mp3","speed":${request.speed}""")
                    append("}")
                })
            }

            if (ttsResponse.status != HttpStatusCode.OK) {
                val errorBody = ttsResponse.bodyAsText()
                logger.error("TTS server error: ${ttsResponse.status} - $errorBody")
                call.respond(ReturnData.error("TTS server error: ${ttsResponse.status}"))
                return@post
            }

            // Forward the Content-Type from TTS server (audio/mpeg, audio/wav, etc.)
            val contentType = ttsResponse.contentType()
                ?: ContentType.Audio.MPEG
            logger.info("TTS response Content-Type: $contentType, Content-Length: ${ttsResponse.headers[HttpHeaders.ContentLength]}")

            call.respondOutputStream(contentType = contentType) {
                val channel = ttsResponse.bodyAsChannel()
                val buffer = ByteArray(8192)
                var totalBytes = 0L
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read > 0) {
                        write(buffer, 0, read)
                        flush()
                        totalBytes += read
                    }
                }
                logger.info("TTS proxy streamed $totalBytes bytes")
            }
        } catch (e: Exception) {
            logger.error("TTS proxy error", e)
            call.respond(ReturnData.error("TTS service unavailable: ${e.message}"))
        }
    }

    /**
     * GET /tts/voices
     * Proxy to TTS server's /v1/voices endpoint
     */
    get("/tts/voices") {
        try {
            val response = httpClient.get("$ttsBaseUrl/v1/audio/voices")
            val body = response.bodyAsText()
            call.respondText(body, ContentType.Application.Json, response.status)
        } catch (e: Exception) {
            logger.error("TTS voices proxy error", e)
            call.respond(ReturnData.error("TTS service unavailable: ${e.message}"))
        }
    }

    /**
     * GET /tts/health
     * Check TTS server availability
     */
    get("/tts/health") {
        try {
            val response = httpClient.get("$ttsBaseUrl/health")
            val body = response.bodyAsText()
            call.respondText(body, ContentType.Application.Json, response.status)
        } catch (e: Exception) {
            call.respond(ReturnData.error("TTS service unavailable: ${e.message}"))
        }
    }
}
