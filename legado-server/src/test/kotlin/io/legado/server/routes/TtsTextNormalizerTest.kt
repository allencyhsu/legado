package io.legado.server.routes

import kotlin.test.Test
import kotlin.test.assertEquals

class TtsTextNormalizerTest {
    @Test
    fun `punctuation-only text is skipped`() {
        assertEquals("", normalizeTtsInput("……！？——！！"))
    }

    @Test
    fun `disruptive punctuation is normalized for speech`() {
        assertEquals(
            "你好，這是測試！下一句。",
            normalizeTtsInput("「你好」……——這是測試！！！&nbsp;下一句&hellip;")
        )
    }

    @Test
    fun `pause punctuation before terminal punctuation is collapsed`() {
        assertEquals(
            "他愣住了！下一句？",
            normalizeTtsInput("他愣住了……！！！下一句——？？")
        )
    }

    @Test
    fun `semantic punctuation is preserved`() {
        assertEquals(
            "日期 2026/06/14，版本 v1.2，A+B=C",
            normalizeTtsInput("日期 2026/06/14，版本 v1.2，A+B=C")
        )
    }
}
