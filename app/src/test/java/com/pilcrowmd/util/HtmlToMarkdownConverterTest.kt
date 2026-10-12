// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlToMarkdownConverterTest {

    @Test
    fun `converts headings from h1 to h6`() {
        val html = "<h1>Heading 1</h1><h2>Heading 2</h2><h3>Heading 3</h3>"
        val md = HtmlToMarkdownConverter.convert(html)
        assertTrue(md.contains("# Heading 1"))
        assertTrue(md.contains("## Heading 2"))
        assertTrue(md.contains("### Heading 3"))
    }

    @Test
    fun `converts bold, italic, and strikethrough`() {
        val html = "<p>This is <b>bold</b>, <strong>strong</strong>, <i>italic</i>, <em>em</em>, and <del>strike</del>.</p>"
        val md = HtmlToMarkdownConverter.convert(html)
        assertEquals("This is **bold**, **strong**, *italic*, *em*, and ~~strike~~.", md)
    }

    @Test
    fun `converts links with text and bare links`() {
        val html = "<p><a href=\"https://example.com\">Example</a> and <a href=\"https://test.org\">https://test.org</a></p>"
        val md = HtmlToMarkdownConverter.convert(html)
        assertEquals("[Example](https://example.com) and https://test.org", md)
    }

    @Test
    fun `converts inline code and preserves symbols`() {
        val html = "<p>Use <code>val x = 1 &lt; 2</code> for comparison.</p>"
        val md = HtmlToMarkdownConverter.convert(html)
        assertEquals("Use `val x = 1 < 2` for comparison.", md)
    }

    @Test
    fun `converts code blocks and preserves internal tags and entities without modifying them`() {
        val html = "<p>Here is code:</p><pre><code>fun test() {\n    val tag = \"<b>hello</b>\"\n    val cmp = 1 &lt; 2\n}</code></pre>"
        val md = HtmlToMarkdownConverter.convert(html)
        assertTrue(md.contains("```\nfun test() {\n    val tag = \"<b>hello</b>\"\n    val cmp = 1 < 2\n}\n```"))
    }

    @Test
    fun `converts lists`() {
        val html = "<ul><li>First</li><li>Second</li><li>Third</li></ul>"
        val md = HtmlToMarkdownConverter.convert(html)
        assertEquals("- First\n- Second\n- Third", md)
    }

    @Test
    fun `converts blockquotes`() {
        val html = "<blockquote>Quote line 1<br>Quote line 2</blockquote>"
        val md = HtmlToMarkdownConverter.convert(html)
        assertEquals("> Quote line 1\n> Quote line 2", md)
    }

    @Test
    fun `strips script, style, and head tags entirely`() {
        val html = "<head><title>Title</title></head><script>alert('xss');</script><style>body { color: red; }</style><p>Real content</p>"
        val md = HtmlToMarkdownConverter.convert(html)
        assertEquals("Real content", md)
    }

    @Test
    fun `decodes html entities`() {
        val html = "<p>A &amp; B &lt; C &gt; D &quot;quotes&quot; &#39;apostrophe&#39; &copy; 2026</p>"
        val md = HtmlToMarkdownConverter.convert(html)
        assertEquals("A & B < C > D \"quotes\" 'apostrophe' © 2026", md)
    }

    @Test
    fun `handles empty, null, or whitespace gracefully`() {
        assertEquals("", HtmlToMarkdownConverter.convert(null))
        assertEquals("", HtmlToMarkdownConverter.convert(""))
        assertEquals("", HtmlToMarkdownConverter.convert("   "))
    }

    @Test
    fun `handles malformed html without crashing`() {
        val html = "<b>unclosed bold <p>paragraph without end"
        val md = HtmlToMarkdownConverter.convert(html)
        assertTrue(md.contains("unclosed bold") || md.contains("paragraph without end"))
    }
}
