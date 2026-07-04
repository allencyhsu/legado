package io.legado.server.routes

import org.apache.commons.text.StringEscapeUtils

private val HtmlTagPattern = Regex("<[^>]*>")
private val ControlCharacterPattern = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u200B\\u200C\\u200D\\uFEFF]")
private val QuoteAndBracketPattern = Regex("[「」『』“”‘’《》〈〉（）()\\[\\]{}【】]")
private val EllipsisPattern = Regex("(?:\\.{3,}|…+|⋯+)")
private val DoubleDotPattern = Regex("\\.{2}")
private val DashPattern = Regex("(?:[-‐‑‒–—―]{2,}|[—―])")
private val DecorativeSymbolPattern = Regex("[*_#~^|\\\\<>`]")
private val WhitespacePattern = Regex("[\\s\\u00A0]+")
private val PunctuationSpacingPattern = Regex("\\s*([，。！？、；：])\\s*")
private val PauseBeforeTerminalPunctuationPattern = Regex("[，、；：]+([。！？])")
private val LeadingPausePattern = Regex("^[，。！？、；：\\s]+")
private val TrailingSoftPausePattern = Regex("[，、；：\\s]+$")

internal fun normalizeTtsInput(rawText: String): String {
    val unescapedText = StringEscapeUtils.unescapeHtml4(rawText)
        .replace(HtmlTagPattern, "")
        .replace(ControlCharacterPattern, "")
        .replace(QuoteAndBracketPattern, "")

    if (unescapedText.none { it.isLetterOrDigit() }) {
        return ""
    }

    return unescapedText
        .replace(EllipsisPattern, "，")
        .replace(DoubleDotPattern, "，")
        .replace(DashPattern, "，")
        .replace(DecorativeSymbolPattern, "")
        .replace(Regex("[!！]+"), "！")
        .replace(Regex("[?？]+"), "？")
        .replace(Regex("[。．]+"), "。")
        .replace(Regex("[,，、]+"), "，")
        .replace(Regex("[;；:：]+"), "，")
        .replace(WhitespacePattern, " ")
        .replace(PunctuationSpacingPattern, "$1")
        .replace(PauseBeforeTerminalPunctuationPattern, "$1")
        .replace(Regex("[，、；：]{2,}"), "，")
        .replace(LeadingPausePattern, "")
        .replace(TrailingSoftPausePattern, "。")
        .trim()
}
