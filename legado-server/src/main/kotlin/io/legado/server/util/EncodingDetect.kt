package io.legado.server.util

import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset

/**
 * Utility for detecting file encoding
 * Simplified version of Legado's EncodingDetect.kt
 */
object EncodingDetect {

    private const val BUFFER_SIZE = 4096

    /**
     * Detect the encoding of a file
     */
    fun detect(file: File): String {
        if (!file.exists()) return "UTF-8"

        return try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                val bytesRead = fis.read(buffer)
                if (bytesRead <= 0) return "UTF-8"

                val bytes = buffer.copyOf(bytesRead)
                detectFromBytes(bytes)
            }
        } catch (e: Exception) {
            "UTF-8"
        }
    }

    /**
     * Detect encoding from byte array
     */
    fun detectFromBytes(bytes: ByteArray): String {
        // Check for BOM markers
        if (bytes.size >= 3) {
            // UTF-8 BOM
            if (bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return "UTF-8"
            }
        }
        if (bytes.size >= 2) {
            // UTF-16 LE BOM
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return "UTF-16LE"
            }
            // UTF-16 BE BOM
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return "UTF-16BE"
            }
        }

        // Try to detect UTF-8 by checking byte patterns
        if (isValidUtf8(bytes)) {
            // Check if it contains multi-byte UTF-8 sequences
            if (containsMultiByteUtf8(bytes)) {
                return "UTF-8"
            }
        }

        // Try to detect GBK/GB2312/GB18030 (Chinese encodings)
        val gbkScore = calculateGbkScore(bytes)
        val utf8Score = calculateUtf8Score(bytes)

        return if (gbkScore > utf8Score && gbkScore > 0.5) {
            "GBK"
        } else {
            "UTF-8"
        }
    }

    /**
     * Check if bytes are valid UTF-8
     */
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b <= 0x7F -> i++ // ASCII
                b in 0xC0..0xDF -> {
                    if (i + 1 >= bytes.size) return false
                    if ((bytes[i + 1].toInt() and 0xFF) !in 0x80..0xBF) return false
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 >= bytes.size) return false
                    if ((bytes[i + 1].toInt() and 0xFF) !in 0x80..0xBF) return false
                    if ((bytes[i + 2].toInt() and 0xFF) !in 0x80..0xBF) return false
                    i += 3
                }
                b in 0xF0..0xF7 -> {
                    if (i + 3 >= bytes.size) return false
                    if ((bytes[i + 1].toInt() and 0xFF) !in 0x80..0xBF) return false
                    if ((bytes[i + 2].toInt() and 0xFF) !in 0x80..0xBF) return false
                    if ((bytes[i + 3].toInt() and 0xFF) !in 0x80..0xBF) return false
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }

    /**
     * Check if bytes contain multi-byte UTF-8 sequences
     */
    private fun containsMultiByteUtf8(bytes: ByteArray): Boolean {
        for (b in bytes) {
            val value = b.toInt() and 0xFF
            if (value >= 0xC0) return true
        }
        return false
    }

    /**
     * Calculate GBK probability score
     */
    private fun calculateGbkScore(bytes: ByteArray): Double {
        var validPairs = 0
        var totalPairs = 0

        var i = 0
        while (i < bytes.size - 1) {
            val b1 = bytes[i].toInt() and 0xFF
            val b2 = bytes[i + 1].toInt() and 0xFF

            // Check if this could be a GBK character
            if (b1 in 0x81..0xFE && b2 in 0x40..0xFE && b2 != 0x7F) {
                validPairs++
                i += 2
            } else if (b1 <= 0x7F) {
                i++
            } else {
                i++
            }
            totalPairs++
        }

        return if (totalPairs > 0) validPairs.toDouble() / totalPairs else 0.0
    }

    /**
     * Calculate UTF-8 probability score
     */
    private fun calculateUtf8Score(bytes: ByteArray): Double {
        var validSequences = 0
        var totalSequences = 0

        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b <= 0x7F -> {
                    validSequences++
                    i++
                }
                b in 0xC0..0xDF && i + 1 < bytes.size -> {
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    if (b2 in 0x80..0xBF) {
                        validSequences++
                        i += 2
                    } else {
                        i++
                    }
                }
                b in 0xE0..0xEF && i + 2 < bytes.size -> {
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    val b3 = bytes[i + 2].toInt() and 0xFF
                    if (b2 in 0x80..0xBF && b3 in 0x80..0xBF) {
                        validSequences++
                        i += 3
                    } else {
                        i++
                    }
                }
                else -> i++
            }
            totalSequences++
        }

        return if (totalSequences > 0) validSequences.toDouble() / totalSequences else 0.0
    }
}
