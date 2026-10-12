// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedTextResolverTest {

    @Test
    fun `resolves plain text when html is absent`() {
        val plain = "Simple note with\r\nmultiple lines"
        val resolved = SharedTextResolver.resolve(null, plain)
        assertEquals("Simple note with\nmultiple lines", resolved)
    }

    @Test
    fun `resolves formatted html when plain text is absent`() {
        val html = "<p>Selected <b>bold</b> text.</p>"
        val resolved = SharedTextResolver.resolve(html, null)
        assertEquals("Selected **bold** text.", resolved)
    }

    @Test
    fun `appends external source URL from plain text when absent from html`() {
        val html = "<p>Excerpt from web article</p>"
        val plain = "Excerpt from web article\nhttps://example.com/article"
        val resolved = SharedTextResolver.resolve(html, plain)
        assertTrue(resolved!!.contains("Excerpt from web article"))
        assertTrue(resolved.contains("https://example.com/article"))
    }

    @Test
    fun `does not duplicate URL if already inside html link`() {
        val html = "<p>Check out <a href=\"https://example.com\">example</a></p>"
        val plain = "Check out example https://example.com"
        val resolved = SharedTextResolver.resolve(html, plain)
        assertEquals("Check out [example](https://example.com)", resolved)
    }

    @Test
    fun `formats subject as heading when text is a bare URL`() {
        val subject = "Great Article"
        val plain = "https://example.com/great-article"
        val resolved = SharedTextResolver.resolve(null, plain, subject)
        assertEquals("# Great Article\n\nhttps://example.com/great-article", resolved)
    }

    @Test
    fun `returns null when both html and plain text are empty`() {
        assertNull(SharedTextResolver.resolve(null, null))
        assertNull(SharedTextResolver.resolve("", "   "))
    }
}
