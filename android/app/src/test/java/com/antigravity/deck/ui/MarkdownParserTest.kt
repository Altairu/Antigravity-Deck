package com.antigravity.deck.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun parseMarkdownBlocks_extractsHeadingsListsCodeAndQuotes() {
        val sample = """
            # タイトル
            本文の段落です。

            * 項目1
              - ネスト項目
            1. 番号付き項目

            > 引用文

            ```kotlin
            fun hello() = println("ok")
            ```

            ---
        """.trimIndent()

        val blocks = parseMarkdownBlocks(sample)
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertEquals("タイトル", (blocks[0] as MarkdownBlock.Heading).text)

        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
        assertTrue(blocks[2] is MarkdownBlock.ListItem)
        assertEquals("•", (blocks[2] as MarkdownBlock.ListItem).marker)
        assertTrue(blocks[3] is MarkdownBlock.ListItem)
        assertEquals(1, (blocks[3] as MarkdownBlock.ListItem).indentLevel)
        assertTrue(blocks[4] is MarkdownBlock.ListItem)
        assertEquals("1.", (blocks[4] as MarkdownBlock.ListItem).marker)

        assertTrue(blocks[5] is MarkdownBlock.BlockQuote)
        assertEquals("引用文", (blocks[5] as MarkdownBlock.BlockQuote).text)

        assertTrue(blocks[6] is MarkdownBlock.CodeBlock)
        assertEquals("kotlin", (blocks[6] as MarkdownBlock.CodeBlock).language)
        assertEquals("""fun hello() = println("ok")""", (blocks[6] as MarkdownBlock.CodeBlock).code)

        assertTrue(blocks[7] is MarkdownBlock.HorizontalRule)
    }

    @Test
    fun buildMarkdownInlineText_stripsMarkersAndPreservesText() {
        val annotated = buildMarkdownInlineText("**強調** と `code` と [リンク](https://example.com)")
        assertEquals("強調 と  code  と リンク", annotated.text)
    }
}
