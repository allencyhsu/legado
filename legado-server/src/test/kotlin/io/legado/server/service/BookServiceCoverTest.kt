package io.legado.server.service

import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class BookServiceCoverTest {
    @Test
    fun `txt book files are not served as covers`() {
        val tempDir = Files.createTempDirectory("legado-cover-test")
        val txtBook = tempDir.resolve("book.txt")
        txtBook.writeText("chapter content should never be returned as cover bytes")

        val service = BookService(tempDir.toString())

        assertNull(service.getCover(txtBook.toString()))
    }

    @Test
    fun `local image files can still be served as covers`() {
        val tempDir = Files.createTempDirectory("legado-cover-test")
        val cover = tempDir.resolve("cover.png")
        val coverBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
        cover.writeBytes(coverBytes)

        val service = BookService(tempDir.toString())

        assertContentEquals(coverBytes, service.getCover(cover.toString()))
    }
}
