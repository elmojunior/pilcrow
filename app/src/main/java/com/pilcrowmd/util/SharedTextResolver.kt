// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.util

/**
 * Resolves incoming shared intent content into clean Markdown text.
 * Handles both rich text / HTML (from web browsers and formatted apps)
 * and plain text, preserving external source URLs if attached.
 */
object SharedTextResolver {

    private val urlRegex = Regex("""https?://[^\s<>"']+""")

    fun resolve(htmlText: String?, plainText: String?, subject: String? = null): String? {
        val converted = if (!htmlText.isNullOrBlank()) {
            HtmlToMarkdownConverter.convert(htmlText).trim()
        } else {
            ""
        }

        val plain = plainText?.replace("\r\n", "\n")?.replace("\r", "\n")?.trim().orEmpty()

        val text = when {
            converted.isNotBlank() && plain.isNotBlank() -> {
                val plainUrls = urlRegex.findAll(plain).map { it.value }.distinct().toList()
                val missingUrls = plainUrls.filter { url -> !converted.contains(url) }
                if (missingUrls.isNotEmpty()) {
                    converted + "\n\n" + missingUrls.joinToString("\n")
                } else {
                    converted
                }
            }
            converted.isNotBlank() -> converted
            plain.isNotBlank() -> plain
            else -> ""
        }

        if (text.isBlank()) return null

        return if (!subject.isNullOrBlank() &&
            (text.startsWith("http://") || text.startsWith("https://")) &&
            !text.contains('\n')
        ) {
            "# $subject\n\n$text"
        } else {
            text
        }
    }
}
