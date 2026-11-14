package com.antigravity.deck.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.deck.ui.theme.DeckColors

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
    data class ListItem(val marker: String, val indentLevel: Int, val text: String) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
}

private val HEADING_REGEX = Regex("""^(#{1,4})\s+(.+)$""")
private val UNORDERED_LIST_REGEX = Regex("""^(\s*)[*+-]\s+(.+)$""")
private val ORDERED_LIST_REGEX = Regex("""^(\s*)(\d+\.)\s+(.+)$""")
private val HR_REGEX = Regex("""^([-*_])\1{2,}\s*$""")

// マークダウン文字列をブロック要素のリストへ分解する
internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphBuffer.joinToString("\n")))
            paragraphBuffer.clear()
        }
    }

    var idx = 0
    while (idx < lines.size) {
        val line = lines[idx]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
            val codeLines = mutableListOf<String>()
            idx += 1
            while (idx < lines.size && !lines[idx].trim().startsWith("```")) {
                codeLines.add(lines[idx])
                idx += 1
            }
            blocks.add(MarkdownBlock.CodeBlock(language = lang, code = codeLines.joinToString("\n")))
            idx += 1
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            idx += 1
            continue
        }

        if (HR_REGEX.matches(trimmed)) {
            flushParagraph()
            blocks.add(MarkdownBlock.HorizontalRule)
            idx += 1
            continue
        }

        val headingMatch = HEADING_REGEX.matchEntire(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks.add(
                MarkdownBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim()
                )
            )
            idx += 1
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quoteLines = mutableListOf<String>()
            while (idx < lines.size && lines[idx].trim().startsWith(">")) {
                quoteLines.add(lines[idx].trim().removePrefix(">").trimStart())
                idx += 1
            }
            blocks.add(MarkdownBlock.BlockQuote(quoteLines.joinToString("\n")))
            continue
        }

        val ulMatch = UNORDERED_LIST_REGEX.matchEntire(line)
        if (ulMatch != null) {
            flushParagraph()
            val indent = ulMatch.groupValues[1].length / 2
            blocks.add(
                MarkdownBlock.ListItem(
                    marker = "•",
                    indentLevel = indent.coerceAtMost(4),
                    text = ulMatch.groupValues[2].trim()
                )
            )
            idx += 1
            continue
        }

        val olMatch = ORDERED_LIST_REGEX.matchEntire(line)
        if (olMatch != null) {
            flushParagraph()
            val indent = olMatch.groupValues[1].length / 2
            blocks.add(
                MarkdownBlock.ListItem(
                    marker = olMatch.groupValues[2],
                    indentLevel = indent.coerceAtMost(4),
                    text = olMatch.groupValues[3].trim()
                )
            )
            idx += 1
            continue
        }

        paragraphBuffer.add(line)
        idx += 1
    }

    flushParagraph()
    return blocks
}

private val INLINE_TOKEN_REGEX = Regex(
    """(`[^`\n]+`)|(\[([^\]\n]+)\]\(([^)\s]+)\))|(\*\*([^*\n]+)\*\*)|(__([^_\n]+)__)|(~~([^~\n]+)~~)|(\*([^*\n]+)\*)|(_([^_\n]+)_)|(https?://[^\s<>()]+)"""
)

// インライン装飾とリンクをAnnotatedStringへ変換する
internal fun buildMarkdownInlineText(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        for (match in INLINE_TOKEN_REGEX.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            val codeGroup = match.groups[1]?.value
            val linkLabel = match.groups[3]?.value
            val linkUrl = match.groups[4]?.value
            val boldStar = match.groups[6]?.value
            val boldUnder = match.groups[8]?.value
            val strike = match.groups[10]?.value
            val italicStar = match.groups[12]?.value
            val italicUnder = match.groups[14]?.value
            val rawUrl = match.groups[15]?.value

            when {
                codeGroup != null -> {
                    val inner = codeGroup.substring(1, codeGroup.length - 1)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF7DD3FC),
                            background = Color(0xFF1E293B),
                            fontSize = 12.sp
                        )
                    )
                    append(" $inner ")
                    pop()
                }

                linkLabel != null && linkUrl != null -> {
                    val linkStyle = SpanStyle(
                        color = DeckColors.StatusThinking,
                        textDecoration = TextDecoration.Underline
                    )
                    if (linkUrl.startsWith("http://") || linkUrl.startsWith("https://")) {
                        pushLink(
                            LinkAnnotation.Url(
                                url = linkUrl,
                                styles = TextLinkStyles(style = linkStyle)
                            )
                        )
                        append(linkLabel)
                        pop()
                    } else {
                        pushStyle(linkStyle)
                        append(linkLabel)
                        pop()
                    }
                }

                boldStar != null || boldUnder != null -> {
                    val inner = boldStar ?: boldUnder.orEmpty()
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White))
                    append(inner)
                    pop()
                }

                strike != null -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(strike)
                    pop()
                }

                italicStar != null || italicUnder != null -> {
                    val inner = italicStar ?: italicUnder.orEmpty()
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(inner)
                    pop()
                }

                rawUrl != null -> {
                    pushLink(
                        LinkAnnotation.Url(
                            url = rawUrl,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = DeckColors.StatusThinking,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    )
                    append(rawUrl)
                    pop()
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

@Composable
fun MarkdownResponseView(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    SelectionContainer(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val (fontSize, color) = when (block.level) {
                            1 -> 17.sp to Color.White
                            2 -> 15.sp to DeckColors.StatusThinking
                            3 -> 14.sp to Color(0xFFE2E8F0)
                            else -> 13.sp to Color(0xFFCBD5E1)
                        }
                        Text(
                            text = remember(block.text) { buildMarkdownInlineText(block.text) },
                            color = color,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            lineHeight = (fontSize.value + 5).sp
                        )
                    }

                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = remember(block.text) { buildMarkdownInlineText(block.text) },
                            color = DeckColors.TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    is MarkdownBlock.ListItem -> {
                        val startPadding = (block.indentLevel * 12).dp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = startPadding),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = block.marker,
                                color = DeckColors.StatusThinking,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 19.sp
                            )
                            Text(
                                text = remember(block.text) { buildMarkdownInlineText(block.text) },
                                color = DeckColors.TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    is MarkdownBlock.BlockQuote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(Color(0xFF0E1726), RoundedCornerShape(6.dp))
                                .padding(end = 8.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(DeckColors.StatusThinking)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = remember(block.text) { buildMarkdownInlineText(block.text) },
                                color = DeckColors.TextSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    is MarkdownBlock.CodeBlock -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF0B1220),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DeckColors.BorderMuted)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                if (!block.language.isNullOrBlank()) {
                                    Text(
                                        text = block.language,
                                        color = DeckColors.TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = block.code,
                                        color = Color(0xFFE2E8F0),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    MarkdownBlock.HorizontalRule -> {
                        HorizontalDivider(
                            color = DeckColors.BorderMuted,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
