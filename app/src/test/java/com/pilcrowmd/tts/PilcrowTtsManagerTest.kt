// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PilcrowTtsManagerTest {

    @Test
    fun `cleanMarkdownForSpeech strips frontmatter`() {
        val input = """
            ---
            title: My Document
            author: Test
            ---
            # Hello World
            This is content.
        """.trimIndent()

        val result = PilcrowTtsManager.cleanMarkdownForSpeech(input)
        assertFalse("Frontmatter should be removed", result.contains("title: My Document"))
        assertTrue("Content should remain", result.contains("Hello World"))
        assertTrue("Content should remain", result.contains("This is content."))
    }

    @Test
    fun `cleanMarkdownForSpeech strips formatting symbols and preserves text`() {
        val input = """
            # Heading 1
            ## Heading 2
            
            This is **bold text**, *italic text*, and ~~strikethrough~~.
            Here is a `code snippet`.
            
            > A blockquote here.
            
            - Bullet item 1
            * Bullet item 2
            1. Numbered item
            
            ---
            
            Visit [Google](https://google.com) and check ![My Photo](photo.png).
        """.trimIndent()

        val result = PilcrowTtsManager.cleanMarkdownForSpeech(input)

        assertFalse("Markdown headers '#' should be stripped", result.contains("#"))
        assertFalse("Bold '**' should be stripped", result.contains("**"))
        assertFalse("Strikethrough '~~' should be stripped", result.contains("~~"))
        assertFalse("Inline code ticks '`' should be stripped", result.contains("`"))
        assertFalse("Blockquote '>' should be stripped", result.contains(">"))
        assertFalse("URLs in markdown links should not be read", result.contains("https://google.com"))
        assertFalse("Image URLs should not be read", result.contains("photo.png"))

        assertTrue("Heading text should remain", result.contains("Heading 1"))
        assertTrue("Bold text content should remain", result.contains("bold text"))
        assertTrue("Italic text content should remain", result.contains("italic text"))
        assertTrue("Strikethrough text content should remain", result.contains("strikethrough"))
        assertTrue("Code content should remain", result.contains("code snippet"))
        assertTrue("Blockquote text should remain", result.contains("A blockquote here."))
        assertTrue("Link text should remain", result.contains("Google"))
        assertTrue("Image alt text should remain", result.contains("My Photo"))
    }

    @Test
    fun `cleanMarkdownForSpeech strips fenced code blocks`() {
        val input = """
            Introductory text.
            
            ```kotlin
            fun test() {
                val x = 10
            }
            ```
            
            Concluding text.
        """.trimIndent()

        val result = PilcrowTtsManager.cleanMarkdownForSpeech(input)
        assertTrue(result.contains("Introductory text."))
        assertTrue(result.contains("Concluding text."))
        assertFalse("Fenced code body should not be read character-by-character", result.contains("val x = 10"))
    }

    @Test
    fun `splitIntoChunks preserves small texts as single chunk`() {
        val text = "Short text under the limit."
        val chunks = PilcrowTtsManager.splitIntoChunks(text, maxChunkSize = 100)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0])
    }

    @Test
    fun `splitIntoChunks splits long paragraphs properly`() {
        val p1 = "First paragraph that has some words."
        val p2 = "Second paragraph that also has words."
        val p3 = "Third paragraph with even more words."
        val text = "$p1\n\n$p2\n\n$p3"

        // Force maxChunkSize small enough that each paragraph is separate
        val chunks = PilcrowTtsManager.splitIntoChunks(text, maxChunkSize = 45)
        assertEquals(3, chunks.size)
        assertEquals(p1, chunks[0])
        assertEquals(p2, chunks[1])
        assertEquals(p3, chunks[2])
    }
}
