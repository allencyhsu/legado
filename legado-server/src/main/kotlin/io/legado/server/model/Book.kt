package io.legado.server.model

/**
 * Book entity - simplified version of Legado's Book.kt
 * Compatible with the Vue frontend API
 */
data class Book(
    val bookUrl: String,                    // File path as unique ID
    val name: String,                       // Book title
    val author: String = "",                // Author
    val kind: String? = null,               // Category
    val coverUrl: String? = null,           // Cover URL
    val intro: String? = null,              // Introduction
    val charset: String? = null,            // Character encoding (for TXT)
    val type: Int = TYPE_LOCAL,             // Book type
    val origin: String = "local",           // Source (always "local" for this service)
    val originName: String = "",            // Source name (filename)
    val totalChapterNum: Int = 0,           // Total chapters
    val latestChapterTitle: String? = null, // Latest chapter title
    val durChapterIndex: Int = 0,           // Current reading chapter index
    val durChapterPos: Int = 0,             // Current position in chapter
    val durChapterTime: Long = 0,           // Last read timestamp
    val durChapterTitle: String? = null,    // Current chapter title
    val lastCheckTime: Long = 0,            // Last update check
    val order: Int = 0,                     // Sort order
    val group: Long = 0,                    // Group ID
    val tocUrl: String = ""                 // Table of contents URL (for local: same as bookUrl)
) {
    companion object {
        const val TYPE_TEXT = 0
        const val TYPE_AUDIO = 1
        const val TYPE_IMAGE = 2
        const val TYPE_LOCAL = 4

        fun fromFile(file: java.io.File): Book {
            val fileName = file.nameWithoutExtension
            val ext = file.extension.lowercase()

            return Book(
                bookUrl = file.absolutePath,
                name = fileName,
                originName = file.name,
                type = TYPE_LOCAL,
                tocUrl = file.absolutePath,
                lastCheckTime = file.lastModified()
            )
        }
    }

    val isLocal: Boolean get() = type == TYPE_LOCAL || origin == "local"
    val isEpub: Boolean get() = bookUrl.endsWith(".epub", ignoreCase = true)
    val isTxt: Boolean get() = bookUrl.endsWith(".txt", ignoreCase = true)
}
