package com.chuishui.katago

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Parses a subset of Markdown into [AnnotatedString] for display in AI chat
 * bubbles. Supports: headers, bold, italic, inline code, code blocks, lists,
 * and links.
 */
fun parseMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    var inCodeBlock = false
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        // Fenced code block toggle
        if (line.trimStart().startsWith("```")) {
            inCodeBlock = !inCodeBlock
            if (!inCodeBlock && i + 1 < lines.size) i++ else i++
            continue
        }
        if (inCodeBlock) {
            if (line.isNotEmpty()) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified, background = Color(0x22000000), color = Color(0xFFE0E0E0))) {
                    append(line)
                }
            }
            append("\n")
            i++
            continue
        }
        // Headers
        when {
            line.startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)) {
                    appendInlineMarkdown(line.removePrefix("### "))
                }
                append("\n"); i++; continue
            }
            line.startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)) {
                    appendInlineMarkdown(line.removePrefix("## "))
                }
                append("\n"); i++; continue
            }
            line.startsWith("# ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)) {
                    appendInlineMarkdown(line.removePrefix("# "))
                }
                append("\n"); i++; continue
            }
        }
        // Unordered list
        if (line.trimStart().startsWith("- ")) {
            val indent = line.length - line.trimStart().length
            val spaces = " ".repeat(indent)
            append("${spaces}• ")
            appendInlineMarkdown(line.trimStart().removePrefix("- "))
            append("\n")
            i++; continue
        }
        // Ordered list
        val olMatch = Regex("""^(\s*)(\d+)\.\s+(.*)""").matchEntire(line)
        if (olMatch != null) {
            val (_, spaces, num, content) = olMatch.groupValues
            append("${spaces}${num}. ")
            appendInlineMarkdown(content)
            append("\n")
            i++; continue
        }
        // Regular line
        appendInlineMarkdown(line)
        append("\n")
        i++
    }
}

/**
 * Appends text with inline Markdown spans: **bold**, *italic*, `code`, [link](url).
 */
private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    val pattern = Regex("""(\*\*(.+?)\*\*)|(\*(.+?)\*)|(`(.+?)`)|(\[([^\]]+)\]\(([^)]+)\))""")
    var lastEnd = 0
    for (m in pattern.findAll(text)) {
        // Append text before this match
        if (m.range.first > lastEnd) {
            append(text.substring(lastEnd, m.range.first))
        }
        when {
            m.groupValues[2].isNotEmpty() -> {
                // Bold **text**
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(m.groupValues[2])
                }
            }
            m.groupValues[4].isNotEmpty() -> {
                // Italic *text*
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(m.groupValues[4])
                }
            }
            m.groupValues[6].isNotEmpty() -> {
                // Inline code `code`
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x22000000),
                    color = Color(0xFFE0E0E0),
                )) {
                    append(m.groupValues[6])
                }
            }
            m.groupValues[8].isNotEmpty() -> {
                // Link [text](url)
                withStyle(SpanStyle(color = Color(0xFF1E88E5))) {
                    append(m.groupValues[8])
                }
            }
        }
        lastEnd = m.range.last + 1
    }
    if (lastEnd < text.length) {
        append(text.substring(lastEnd))
    }
}
