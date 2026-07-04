package io.legado.server.model

/**
 * Book chapter entity - simplified version of Legado's BookChapter.kt
 * Compatible with the Vue frontend API
 */
data class BookChapter(
    val url: String,                        // Chapter URL/identifier
    val title: String,                      // Chapter title
    val bookUrl: String,                    // Parent book URL
    val index: Int,                         // Chapter index (0-based)
    val isVolume: Boolean = false,          // Is this a volume header
    val isVip: Boolean = false,             // VIP chapter (always false for local)
    val isPay: Boolean = false,             // Paid chapter (always false for local)
    val start: Long? = null,                // Start position in file (for TXT)
    val end: Long? = null,                  // End position in file (for TXT)
    val startFragmentId: String? = null,    // EPUB fragment ID
    val endFragmentId: String? = null,      // EPUB next fragment ID
    val tag: String? = null,                // Extra info
    val variable: String? = null            // Custom variables (JSON)
)
