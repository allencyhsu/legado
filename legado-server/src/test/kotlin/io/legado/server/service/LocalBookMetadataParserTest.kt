package io.legado.server.service

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalBookMetadataParserTest {
    @Test
    fun `parses bracketed title author and top-level category`() {
        val root = Files.createTempDirectory("legado-metadata-root")
        val file = root
            .resolve("白金作者合集")
            .resolve("12.愛潛水的烏賊")
            .resolve("《詭秘之主》作者：愛潛水的烏賊.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")

        val metadata = LocalBookMetadataParser.parse(file.toFile(), root.toFile())

        assertEquals("詭秘之主", metadata.name)
        assertEquals("愛潛水的烏賊", metadata.author)
        assertEquals("白金作者合集", metadata.kind)
    }

    @Test
    fun `uses cleaned author directory for plain filenames`() {
        val root = Files.createTempDirectory("legado-metadata-root")
        val file = root
            .resolve("其他人氣作者合集")
            .resolve("161.西瓜是水果")
            .resolve("人生重啟二十年.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")

        val metadata = LocalBookMetadataParser.parse(file.toFile(), root.toFile())

        assertEquals("人生重啟二十年", metadata.name)
        assertEquals("西瓜是水果", metadata.author)
        assertEquals("其他人氣作者合集", metadata.kind)
    }

    @Test
    fun `does not treat numeric-only author directories as authors`() {
        val root = Files.createTempDirectory("legado-metadata-root")
        val file = root
            .resolve("其他人氣作者合集")
            .resolve("162")
            .resolve("翁媳乱情.txt")
        file.parent.createDirectories()
        file.writeText("第一章 開始\n內容")

        val metadata = LocalBookMetadataParser.parse(file.toFile(), root.toFile())

        assertEquals("翁媳乱情", metadata.name)
        assertEquals("", metadata.author)
        assertEquals("其他人氣作者合集", metadata.kind)
    }

    @Test
    fun `falls back when file is outside configured books root`() {
        val root = Files.createTempDirectory("legado-metadata-root")
        val outside = Files.createTempDirectory("legado-outside-root")
            .resolve("《外部書》作者：路人.txt")
        outside.writeText("第一章 開始\n內容")

        val metadata = LocalBookMetadataParser.parse(outside.toFile(), root.toFile())
        val book = LocalBookMetadataParser.toBook(outside.toFile(), root.toFile())

        assertEquals("《外部書》作者：路人", metadata.name)
        assertEquals("", metadata.author)
        assertNull(metadata.kind)
        assertEquals("《外部書》作者：路人", book.name)
        assertEquals("", book.author)
        assertNull(book.kind)
    }

    @Test
    fun `does not treat root-level filenames as categories`() {
        val root = Files.createTempDirectory("legado-metadata-root")
        val file = root.resolve("根目錄小說.txt")
        file.writeText("第一章 開始\n內容")

        val metadata = LocalBookMetadataParser.parse(file.toFile(), root.toFile())

        assertEquals("根目錄小說", metadata.name)
        assertEquals("", metadata.author)
        assertNull(metadata.kind)
    }
}
