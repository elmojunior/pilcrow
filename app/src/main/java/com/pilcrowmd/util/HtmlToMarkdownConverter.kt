// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.util

/**
 * Converts rich/HTML text (shared from web browsers, note apps, etc.) into clean Markdown.
 * Preserves essential Markdown formatting: headings, bold, italic, strikethrough, inline code,
 * code blocks, blockquotes, unordered/ordered lists, links, images, and paragraph breaks.
 *
 * Designed to be safe, resilient against malformed HTML, and offline-only without third-party web parsers.
 */
object HtmlToMarkdownConverter {

    private val scriptStyleRegex = Regex("""(?is)<(script|style|head)\b[^>]*>.*?</\1>""")
    private val commentRegex = Regex("""(?s)<!--.*?-->""")
    private val preCodeRegex = Regex("""(?is)<pre\b[^>]*>\s*<code\b[^>]*>(.*?)</code>\s*</pre>""")
    private val preRegex = Regex("""(?is)<pre\b[^>]*>(.*?)</pre>""")
    private val codeRegex = Regex("""(?is)<code\b[^>]*>(.*?)</code>""")
    private val headingRegex = Regex("""(?is)<h([1-6])\b[^>]*>(.*?)</h\1>""")
    private val hrRegex = Regex("""(?i)<hr\s*/?>""")
    private val blockquoteRegex = Regex("""(?is)<blockquote\b[^>]*>(.*?)</blockquote>""")
    private val liRegex = Regex("""(?is)<li\b[^>]*>(.*?)</li>""")
    private val aRegex = Regex("""(?is)<a\b[^>]*\bhref=["']([^"']*)["'][^>]*>(.*?)</a>""")
    private val imgRegex = Regex("""(?is)<img\b[^>]*\bsrc=["']([^"']*)["'][^>]*(?:alt=["']([^"']*)["'])?[^>]*>""")
    private val imgAltFirstRegex = Regex("""(?is)<img\b[^>]*\balt=["']([^"']*)["'][^>]*(?:src=["']([^"']*)["'])?[^>]*>""")
    private val boldRegex = Regex("""(?is)<(b|strong)\b[^>]*>(.*?)</\1>""")
    private val italicRegex = Regex("""(?is)<(i|em)\b[^>]*>(.*?)</\1>""")
    private val strikeRegex = Regex("""(?is)<(s|del|strike)\b[^>]*>(.*?)</\1>""")
    private val pRegex = Regex("""(?is)<p\b[^>]*>(.*?)</p>""")
    private val divRegex = Regex("""(?is)<div\b[^>]*>(.*?)</div>""")
    private val brRegex = Regex("""(?i)<br\s*/?>""")
    private val allTagsRegex = Regex("""<[^>]+>""")
    private val decimalEntityRegex = Regex("""&#(\d+);""")
    private val hexEntityRegex = Regex("""&#x([0-9a-fA-F]+);""")
    private val multipleNewlinesRegex = Regex("""\n{3,}""")

    private const val CODE_BLOCK_PREFIX = "@@@PILCROW_CB_"
    private const val CODE_BLOCK_SUFFIX = "@@@"
    private const val INLINE_CODE_PREFIX = "@@@PILCROW_IC_"
    private const val INLINE_CODE_SUFFIX = "@@@"

    fun convert(html: String?): String {
        if (html.isNullOrBlank()) return ""

        val codeBlocks = mutableListOf<String>()
        val inlineCodes = mutableListOf<String>()

        var text = html
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        // 1. Strip script, style, and comments
        text = scriptStyleRegex.replace(text, "")
        text = commentRegex.replace(text, "")

        // 2. Protect code blocks with placeholders
        text = preCodeRegex.replace(text) { match ->
            val code = decodeEntities(match.groupValues[1])
            val index = codeBlocks.size
            codeBlocks.add(code)
            "\n\n$CODE_BLOCK_PREFIX$index$CODE_BLOCK_SUFFIX\n\n"
        }
        text = preRegex.replace(text) { match ->
            val code = decodeEntities(match.groupValues[1])
            val index = codeBlocks.size
            codeBlocks.add(code)
            "\n\n$CODE_BLOCK_PREFIX$index$CODE_BLOCK_SUFFIX\n\n"
        }

        // 3. Protect inline code with placeholders
        text = codeRegex.replace(text) { match ->
            val code = decodeEntities(match.groupValues[1])
            val index = inlineCodes.size
            inlineCodes.add(code)
            "$INLINE_CODE_PREFIX$index$INLINE_CODE_SUFFIX"
        }

        // 4. Headings
        text = headingRegex.replace(text) { match ->
            val level = match.groupValues[1].toIntOrNull() ?: 1
            val prefix = "#".repeat(level)
            val content = match.groupValues[2].trim()
            "\n\n$prefix $content\n\n"
        }

        // 5. Horizontal rules
        text = hrRegex.replace(text, "\n\n---\n\n")

        // 6. Blockquotes
        text = blockquoteRegex.replace(text) { match ->
            val inner = match.groupValues[1].trim()
            val cleanInner = inner.replace(brRegex, "\n")
            val lines = cleanInner.lines()
            val quoted = lines.joinToString("\n") { line -> "> ${line.trim()}" }
            "\n\n$quoted\n\n"
        }

        // 7. List items
        text = liRegex.replace(text) { match ->
            val item = match.groupValues[1].trim()
            "- $item\n"
        }

        // 8. Links
        text = aRegex.replace(text) { match ->
            val href = match.groupValues[1].trim()
            val linkText = match.groupValues[2].trim()
            when {
                linkText.isBlank() -> href
                linkText == href -> href
                else -> "[$linkText]($href)"
            }
        }

        // 9. Images
        text = imgRegex.replace(text) { match ->
            val src = match.groupValues[1].trim()
            val alt = match.groupValues[2].trim()
            "![$alt]($src)"
        }
        text = imgAltFirstRegex.replace(text) { match ->
            val alt = match.groupValues[1].trim()
            val src = match.groupValues[2].trim()
            if (src.isNotBlank()) "![$alt]($src)" else ""
        }

        // 10. Text styles
        text = boldRegex.replace(text) { match ->
            val inner = match.groupValues[2].trim()
            if (inner.isNotEmpty()) "**$inner**" else ""
        }
        text = italicRegex.replace(text) { match ->
            val inner = match.groupValues[2].trim()
            if (inner.isNotEmpty()) "*$inner*" else ""
        }
        text = strikeRegex.replace(text) { match ->
            val inner = match.groupValues[2].trim()
            if (inner.isNotEmpty()) "~~$inner~~" else ""
        }

        // 11. Paragraphs and divs
        text = pRegex.replace(text) { match ->
            val inner = match.groupValues[1].trim()
            "\n\n$inner\n\n"
        }
        text = divRegex.replace(text) { match ->
            val inner = match.groupValues[1].trim()
            "\n$inner\n"
        }
        text = brRegex.replace(text, "\n")

        // 12. Strip remaining HTML tags
        text = allTagsRegex.replace(text, "")

        // 13. Decode HTML entities in regular text
        text = decodeEntities(text)

        // 14. Restore protected inline code
        inlineCodes.forEachIndexed { index, code ->
            val placeholder = "$INLINE_CODE_PREFIX$index$INLINE_CODE_SUFFIX"
            text = text.replace(placeholder, "`$code`")
        }

        // 15. Restore protected code blocks
        codeBlocks.forEachIndexed { index, code ->
            val placeholder = "$CODE_BLOCK_PREFIX$index$CODE_BLOCK_SUFFIX"
            text = text.replace(placeholder, "```\n$code\n```")
        }

        // 16. Normalize newlines
        text = multipleNewlinesRegex.replace(text, "\n\n").trim()

        return text
    }

    private fun decodeEntities(input: String): String {
        var result = input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace("&copy;", "©")
            .replace("&reg;", "®")

        result = decimalEntityRegex.replace(result) { match ->
            val codePoint = match.groupValues[1].toIntOrNull()
            if (codePoint != null && codePoint in 1..0x10FFFF) {
                String(Character.toChars(codePoint))
            } else {
                match.value
            }
        }

        result = hexEntityRegex.replace(result) { match ->
            val codePoint = match.groupValues[1].toIntOrNull(16)
            if (codePoint != null && codePoint in 1..0x10FFFF) {
                String(Character.toChars(codePoint))
            } else {
                match.value
            }
        }

        return result
    }
}
