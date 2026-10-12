// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.domain.usecase

import com.pilcrowmd.domain.markdown.AdapterBlocks
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ParseMarkdownHeadingsUseCase.readerBlockForOffset]: the reader adapter position of the block the
 * editor caret sat in, which the reader reveals above the keyboard after an Edit-to-Read switch.
 * The expected positions are cross-checked against [AdapterBlocks.of] of the full parse, the list the
 * adapter is numbered by.
 */
class ReaderBlockForOffsetTest {
    private val useCase = ParseMarkdownHeadingsUseCase()

    private val content = """
        # Title

        First paragraph.

        ```kotlin
        val x = 1
        ```

        - item one
        - item two

        [ref]: https://example.com

        Text with a note[^1].

        [^1]: The note body.

        ### Last Line!
    """.trimIndent()

    /** Each block's marker text and the adapter position it has (the link definition has none). */
    private val markers = listOf(
        "# Title" to 0,
        "First paragraph." to 1,
        "val x = 1" to 2,
        "item two" to 3,
        "[ref]: https://example.com" to 3, // takes no position: the block before it
        "Text with a note" to 4,
        "The note body." to 5,
        "### Last Line!" to 6,
    )

    @Test
    fun `the fixture has the blocks the expectations assume`() {
        val blocks = AdapterBlocks.of(useCase.parseDocument(content)!!)
        assertEquals(7, blocks.size)
        assertEquals(true, blocks[0] is Heading)
        assertEquals(true, blocks[1] is Paragraph)
        assertEquals(true, blocks[2] is FencedCodeBlock)
        assertEquals(true, blocks[6] is Heading)
    }

    @Test
    fun `an offset inside each block maps to that block's adapter position`() {
        for ((marker, expected) in markers) {
            val start = content.indexOf(marker)
            // First character, a middle one and the last one of the marker text.
            for (offset in listOf(start, start + marker.length / 2, start + marker.length - 1)) {
                assertEquals("offset $offset in \"$marker\"", expected, useCase.readerBlockForOffset(content, offset))
            }
        }
    }

    @Test
    fun `an offset at the very end maps to the last block`() {
        assertEquals(6, useCase.readerBlockForOffset(content, content.length))
    }

    @Test
    fun `an out-of-range offset is clamped`() {
        assertEquals(0, useCase.readerBlockForOffset(content, -5))
        assertEquals(6, useCase.readerBlockForOffset(content, content.length + 99))
    }

    @Test
    fun `an offset inside frontmatter maps to the frontmatter block`() {
        val doc = "---\ntitle: x\n---\n\n# Body\n"
        assertEquals(0, useCase.readerBlockForOffset(doc, doc.indexOf("title")))
        assertEquals(1, useCase.readerBlockForOffset(doc, doc.indexOf("Body")))
    }

    @Test
    fun `an empty document has no block`() {
        assertNull(useCase.readerBlockForOffset("", 0))
    }

    @Test
    fun `a document the reader shows as plain text has no block`() {
        val tooDeep = ">".repeat(150) + " deep"
        assertNull(useCase.readerBlockForOffset(tooDeep, tooDeep.length))
    }
}
