// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.domain.usecase

import com.pilcrowmd.domain.markdown.PlainReason
import com.pilcrowmd.domain.markdown.ReaderModel
import com.pilcrowmd.domain.model.RenderMode
import com.pilcrowmd.testing.bigParagraph
import com.pilcrowmd.ui.screen.plainFallbackNoticeText
import org.commonmark.node.Heading
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one whole-document parse behind the reader (M-172, M-179): when it runs, what it yields, and
 * how it degrades. The parse is the parity parser behind a counter, so each test can say how many
 * parses happened — the property the memory fix rests on.
 */
class PrepareReaderModelUseCaseTest {

    private val headings = ParseMarkdownHeadingsUseCase()
    private var parses = 0
    private val countingParse: (String) -> Node = {
        parses++
        headings.parseTree(it)
    }

    private fun useCase(
        ceiling: Int = PrepareReaderModelUseCase.MARKDOWN_CEILING_CHARS,
        parse: (String) -> Node = countingParse,
    ) = PrepareReaderModelUseCase(parse, headings, ceiling)

    private val doc = "# One\n\nText.\n\n## Two\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n### Three\n"

    @Test
    fun `the blocks, details and TOC all come from ONE parse`() {
        val model = useCase().prepare(doc, RenderMode.MARKDOWN) as ReaderModel.Markdown

        assertEquals("exactly one whole-document parse", 1, parses)
        // The TOC positions ARE the reader's block indices — by construction, not by a parity test.
        model.headings.forEach { assertTrue(model.blocks[it.adapterPosition] is Heading) }
        // …and the TOC is the one the separate heading parse produced before this change.
        assertEquals(headings.extractHeadings(doc), model.headings)
    }

    private fun childCount(node: Node) = generateSequence(node.firstChild) { it.next }.count()

    @Test
    fun `a huge paragraph is split into pieces, and the TOC and anchors after it are numbered by the pieces`() {
        val source = "# One\n\n" + bigParagraph(450) + "\n\n## Two\n\nTail.\n"

        val model = useCase().prepare(source, RenderMode.MARKDOWN) as ReaderModel.Markdown

        model.blocks.forEachIndexed { i, block ->
            assertTrue(
                "block $i has ${childCount(block)} children, at most 150 allowed",
                childCount(block) <= 150,
            )
        }
        assertTrue("the paragraph became more than two pieces, got ${model.blocks.size} blocks", model.blocks.size > 5)
        val two = model.headings.single { it.text == "Two" }
        assertEquals("Two sits after every piece, before Tail", model.blocks.size - 2, two.adapterPosition)
        assertTrue(model.blocks[two.adapterPosition] is Heading)
        assertEquals(two.adapterPosition, model.anchors["two"])
        assertTrue("the second piece continues the first", model.seams.continuesPrevious(model.blocks[2]))
        assertFalse("the first piece continues nothing", model.seams.continuesPrevious(model.blocks[1]))
        assertTrue(model.seams.continuesNext(model.blocks[1]))
        // Search and the drawer parse on their own: they must have taken the same split.
        assertEquals(headings.extractHeadings(source), model.headings)
    }

    @Test
    fun `a document of short paragraphs has no seam anywhere`() {
        val model = useCase().prepare(doc, RenderMode.MARKDOWN) as ReaderModel.Markdown

        model.blocks.forEach {
            assertFalse(model.seams.continuesPrevious(it))
            assertFalse(model.seams.continuesNext(it))
        }
    }

    @Test
    fun `a link reference definition takes no block, so TOC and anchor positions are the adapter's`() {
        // The reader hands these blocks to the adapter as a list, past Markwon's reducer, so the
        // model must drop the definition itself, as the reducer would (M-214).
        val withDefinition = "[r]: https://example.com\n\n# One\n\nSee [r].\n\n## Two\n"
        val model = useCase().prepare(withDefinition, RenderMode.MARKDOWN) as ReaderModel.Markdown

        assertFalse("no definition among the blocks", model.blocks.any { it is LinkReferenceDefinition })
        assertEquals(listOf(0, 2), model.headings.map { it.adapterPosition })
        assertEquals(mapOf("one" to 0, "two" to 2), model.anchors)
        model.anchors.values.forEach { assertTrue(model.blocks[it] is Heading) }
        assertEquals(headings.extractHeadings(withDefinition), model.headings)
    }

    @Test
    fun `a document nested past the depth limit is shown plain, verbatim, with no notice, after one parse`() {
        val deep = ">".repeat(200) + " deep"
        val model = useCase().prepare(deep, RenderMode.MARKDOWN) as ReaderModel.Plain

        assertEquals(PlainReason.TOO_DEEP, model.reason)
        // Silent, as the reader has always shown such a document (NEW-11): no "could not be formatted".
        assertNull("no notice for a too-deep document", plainFallbackNoticeText(model.reason))
        assertEquals(1, parses)
        assertEquals(deep, model.chunkLiterals.joinToString("\n"))
    }

    @Test
    fun `a document at the ceiling is still parsed, one char over is shown plain without parsing`() {
        val atCeiling = useCase(ceiling = doc.length).prepare(doc, RenderMode.MARKDOWN)
        assertTrue("at the ceiling: Markdown", atCeiling is ReaderModel.Markdown)
        assertEquals(1, parses)

        val over = useCase(ceiling = doc.length - 1).prepare(doc, RenderMode.MARKDOWN) as ReaderModel.Plain
        assertEquals(PlainReason.TOO_LARGE, over.reason)
        assertEquals("no parse over the ceiling", 1, parses)
        assertEquals("the plain chunks are the text, verbatim", doc, over.chunkLiterals.joinToString("\n"))
    }

    @Test
    fun `an OutOfMemoryError in the parse degrades to plain text instead of escaping`() {
        val model = useCase(parse = { throw OutOfMemoryError("simulated") }).prepare(doc, RenderMode.MARKDOWN)

        model as ReaderModel.Plain
        assertEquals(PlainReason.UNRENDERABLE, model.reason)
        assertEquals(doc, model.chunkLiterals.joinToString("\n"))
        assertTrue("no TOC for a document shown plain", model.tocHeadings.isEmpty())
    }

    @Test
    fun `a parser exception degrades to plain text too`() {
        val model = useCase(parse = { throw IllegalStateException("simulated") }).prepare(doc, RenderMode.MARKDOWN)
        assertEquals(PlainReason.UNRENDERABLE, (model as ReaderModel.Plain).reason)
    }

    @Test
    fun `plain mode never parses`() {
        val model = useCase().prepare(doc, RenderMode.PLAIN) as ReaderModel.Plain
        assertEquals(PlainReason.PLAIN_TEXT, model.reason)
        assertEquals(0, parses)
        assertEquals(model.chunkLiterals.size, model.blocks.size)
    }

    @Test
    fun `showsAsPlain agrees with prepare`() {
        val small = useCase(ceiling = doc.length)
        val tiny = useCase(ceiling = doc.length - 1)
        assertEquals(false, small.showsAsPlain(doc, RenderMode.MARKDOWN))
        assertEquals(true, tiny.showsAsPlain(doc, RenderMode.MARKDOWN))
        assertEquals(true, small.showsAsPlain(doc, RenderMode.PLAIN))
    }
}
