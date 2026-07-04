package io.legado.server.service

import io.legado.server.model.Book
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

data class LocalBookMetadata(
    val name: String,
    val author: String = "",
    val kind: String? = null
)

object LocalBookMetadataParser {
    private val logger = LoggerFactory.getLogger(LocalBookMetadataParser::class.java)
    private val bracketedAuthorPattern = Regex("""^《(.+?)》\s*作者[:：]\s*(.+)$""")
    private val plainAuthorPattern = Regex("""^(.+?)\s+作者[:：]\s*(.+)$""")
    private val leadingAuthorNumberPattern = Regex("""^\s*\d+[\.\-_、\s]*""")
    private val trailingCollectionPattern = Regex("""合集$""")

    fun parse(file: File, booksRoot: File): LocalBookMetadata {
        val fallbackName = file.nameWithoutExtension.trim()
        return try {
            val directoryParts = relativeDirectoryParts(file.toPath(), booksRoot.toPath())
            val kind = directoryParts.getOrNull(0)?.cleanSegment()
            val authorFromDirectory = directoryParts.getOrNull(1)?.cleanAuthorDirectory().orEmpty()
            val filenameMetadata = parseFilename(fallbackName)

            LocalBookMetadata(
                name = filenameMetadata.name.ifBlank { fallbackName },
                author = filenameMetadata.author.ifBlank { authorFromDirectory },
                kind = kind?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            logger.debug(
                "Falling back to filename metadata for {} ({}: {})",
                file.absolutePath,
                e::class.java.simpleName,
                e.message
            )
            LocalBookMetadata(name = fallbackName)
        }
    }

    fun toBook(file: File, booksRoot: File): Book {
        val fallback = Book.fromFile(file)
        val metadata = parse(file, booksRoot)
        return fallback.copy(
            name = metadata.name.ifBlank { fallback.name },
            author = metadata.author,
            kind = metadata.kind
        )
    }

    private fun relativeDirectoryParts(filePath: Path, rootPath: Path): List<String> {
        val root = rootPath.toAbsolutePath().normalize()
        val file = filePath.toAbsolutePath().normalize()
        require(file.startsWith(root)) {
            "File is outside books root: $file"
        }
        val parent = file.parent ?: return emptyList()
        if (!parent.startsWith(root) || parent == root) return emptyList()
        return root.relativize(parent).map { it.toString() }
    }

    private fun parseFilename(nameWithoutExtension: String): LocalBookMetadata {
        val trimmed = nameWithoutExtension.trim()
        val match = bracketedAuthorPattern.matchEntire(trimmed)
            ?: plainAuthorPattern.matchEntire(trimmed)
        return if (match == null) {
            LocalBookMetadata(name = trimmed)
        } else {
            LocalBookMetadata(
                name = match.groupValues[1].trim(),
                author = match.groupValues[2].trim()
            )
        }
    }

    private fun String.cleanSegment(): String = trim()

    private fun String.cleanAuthorDirectory(): String {
        return trim()
            .replace(leadingAuthorNumberPattern, "")
            .replace(trailingCollectionPattern, "")
            .trim()
    }
}
