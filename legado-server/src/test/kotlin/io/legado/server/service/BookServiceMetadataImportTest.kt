package io.legado.server.service

import io.legado.server.data.BookRepository
import io.legado.server.data.Database
import io.legado.server.model.Book
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BookServiceMetadataImportTest {
    @Test
    fun `scan stores parsed title author and category`() {
        initTempDatabase()
        val root = Files.createTempDirectory("legado-metadata-import-root")
        val file = root
            .resolve("白金作者合集")
            .resolve("12.愛潛水的烏賊合集")
            .resolve("《詭秘之主》作者：愛潛水的烏賊.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")

        BookService(root.toString()).scanBooksDirectory()

        val book = requireNotNull(BookRepository.getBook(file.toFile().absolutePath))
        assertEquals("詭秘之主", book.name)
        assertEquals("愛潛水的烏賊", book.author)
        assertEquals("白金作者合集", book.kind)
        assertEquals(file.fileName.toString(), book.originName)
    }

    @Test
    fun `scan refreshes metadata for unchanged existing rows`() {
        initTempDatabase()
        val root = Files.createTempDirectory("legado-metadata-import-root")
        val file = root
            .resolve("其他人氣作者合集")
            .resolve("161.西瓜是水果")
            .resolve("人生重啟二十年.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")
        val oldBook = Book.fromFile(file.toFile()).copy(
            name = "人生重啟二十年",
            author = "",
            kind = null,
            totalChapterNum = 7,
            latestChapterTitle = "舊章節",
            lastCheckTime = file.toFile().lastModified()
        )
        BookRepository.upsertBook(oldBook)

        BookService(root.toString()).scanBooksDirectory()

        val book = requireNotNull(BookRepository.getBook(file.toFile().absolutePath))
        assertEquals("人生重啟二十年", book.name)
        assertEquals("西瓜是水果", book.author)
        assertEquals("其他人氣作者合集", book.kind)
        assertEquals(7, book.totalChapterNum)
        assertEquals("舊章節", book.latestChapterTitle)
    }

    @Test
    fun `scan keeps books importable when metadata has no author`() {
        initTempDatabase()
        val root = Files.createTempDirectory("legado-metadata-import-root")
        val file = root
            .resolve("其他人氣作者合集")
            .resolve("162")
            .resolve("翁媳乱情.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")

        BookService(root.toString()).scanBooksDirectory()

        val book = requireNotNull(BookRepository.getBook(file.toFile().absolutePath))
        assertEquals("翁媳乱情", book.name)
        assertEquals("", book.author)
        assertEquals("其他人氣作者合集", book.kind)
        assertNotNull(BookRepository.getBook(file.toFile().absolutePath))
    }

    private fun initTempDatabase() {
        val dbPath = Files.createTempDirectory("legado-metadata-import-db").resolve("legado.db")
        Database.init(dbPath.toString())
    }
}
