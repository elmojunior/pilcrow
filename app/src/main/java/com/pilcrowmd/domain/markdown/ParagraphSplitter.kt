// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.domain.markdown

import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlInline
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Where the pieces of a paragraph cut by [ParagraphSplitter] meet. A piece that continues the
 * previous one and a piece that continues into the next are drawn with no gap at that seam, so the
 * cut reads as a line break, not a paragraph break. Keyed by node identity: a block that is not a
 * piece answers false to both, so a document with no split paragraph needs nothing from this.
 */
class ParagraphSeams internal constructor(continuesPrevious: Collection<Node>, continuesNext: Collection<Node>) {
    private val continuesPrevious: Set<Node> = identitySet(continuesPrevious)
    private val continuesNext: Set<Node> = identitySet(continuesNext)

    fun continuesPrevious(block: Node): Boolean = block in continuesPrevious

    fun continuesNext(block: Node): Boolean = block in continuesNext

    companion object {
        val NONE = ParagraphSeams(emptyList(), emptyList())

        private fun identitySet(nodes: Collection<Node>): Set<Node> =
            Collections.newSetFromMap(IdentityHashMap<Node, Boolean>()).also { it.addAll(nodes) }
    }
}

/**
 * Cuts a top-level paragraph with more than [MAX_INLINE_CHILDREN] inline children into several
 * ordinary [Paragraph] blocks, so no single reader TextView has to render, lay out and record
 * thousands of spans in one frame (M-328; a paragraph of 4,000 spans or 2,000 formulas froze the
 * main thread for over a second per phase, the second Play Vitals ANR). Display only: the text in
 * memory and the saved bytes are untouched (Safeguard 2), and the PDF export keeps the paragraph
 * whole.
 *
 * The pieces are plain paragraphs, not a node class of their own, so every tree walker (search, the
 * headings drawer, footnotes, anchors, Markwon's own paragraph visitor) sees ordinary paragraphs and
 * nothing has to learn a new node. Only the reader needs to know where the seams are, and it gets
 * them from the returned [ParagraphSeams].
 *
 * Span-safe: only the paragraph's DIRECT children are partitioned, and a link, emphasis, code span
 * or formula is one child, so a cut between two children never splits one. A cut prefers a line
 * break in the back half of the window (the break itself is dropped: the seam stands in for it),
 * else the last text child there, cut at its last space so the next piece starts on a word.
 *
 * Nothing here throws on a tree commonmark built; the plan is computed first and the tree is only
 * touched once the whole plan exists. Ordinary prose never comes near the limit: 150 inline children
 * is about 75 formatted words or 75 formulas in one paragraph.
 */
object ParagraphSplitter {
    /** Direct inline children a top-level paragraph may keep as one reader block. */
    const val MAX_INLINE_CHILDREN = 150

    /** Cut every oversized top-level paragraph of [document] in place and say where the seams are. */
    fun split(document: Node): ParagraphSeams {
        val continuesPrevious = ArrayList<Node>()
        val continuesNext = ArrayList<Node>()
        var block = document.firstChild
        while (block != null) {
            val next = block.next
            if (block is Paragraph) splitParagraph(block, continuesPrevious, continuesNext)
            block = next
        }
        return ParagraphSeams(continuesPrevious, continuesNext)
    }

    private fun splitParagraph(
        paragraph: Paragraph,
        continuesPrevious: MutableList<Node>,
        continuesNext: MutableList<Node>,
    ) {
        val children = generateSequence(paragraph.firstChild) { it.next }.toList()
        if (children.size <= MAX_INLINE_CHILDREN) return
        val textCuts = ArrayList<Pair<Text, String>>()
        val groups = partition(children, textCuts)
        // Planned above, applied from here on: appendChild moves each child out of the paragraph.
        textCuts.forEach { (text, head) -> text.literal = head }
        var anchor: Node = paragraph
        groups.forEachIndexed { index, group ->
            val piece = Paragraph()
            group.forEach { piece.appendChild(it) }
            anchor.insertAfter(piece)
            anchor = piece
            if (index > 0) continuesPrevious += piece
            if (index < groups.lastIndex) continuesNext += piece
        }
        paragraph.unlink()
    }

    /**
     * The children of each piece, in order; a dropped line break belongs to no piece. [children] is
     * never changed, so [openInlineTags] stays in step with it: a word carried over a cut is held
     * aside as [carried] and counts as the first child of the next piece.
     */
    private fun partition(children: List<Node>, textCuts: MutableList<Pair<Text, String>>): List<List<Node>> {
        val groups = ArrayList<List<Node>>()
        val openTags = openInlineTags(children)
        var start = 0
        var carried: Text? = null
        // Where the piece starts if the carried word were a child: the window is measured from here.
        var first = 0
        while (children.size - first > MAX_INLINE_CHILDREN) {
            val half = first + MAX_INLINE_CHILDREN / 2
            val limit = first + MAX_INLINE_CHILDREN
            val lineBreak = (half..limit).lastOrNull { children[it].isLineBreak() && openTags[it] == 0 }
            if (lineBreak != null) {
                groups += listOfNotNull(carried) + children.subList(start, lineBreak)
                carried = null
                start = lineBreak + 1
            } else {
                val lastText = (half until limit).lastOrNull { children[it] is Text && openTags[it] == 0 }
                val end = if (lastText != null) lastText + 1 else limit
                groups += listOfNotNull(carried) + children.subList(start, end)
                carried = cutAtWordBoundary(children, end, textCuts)
                start = end
            }
            first = if (carried == null) start else start - 1
        }
        if (first < children.size) groups += listOfNotNull(carried) + children.subList(start, children.size)
        return groups
    }

    /**
     * How many inline HTML tags are still open after each child. `<u>text</u>` parses as three
     * siblings (two [HtmlInline] tags around the text), and a piece rendered on its own would lose
     * the styling past a cut between them, so a cut is only taken where nothing is open. A window
     * with no such place is cut at its limit anyway: a bounded block matters more than one tag.
     */
    private fun openInlineTags(children: List<Node>): IntArray {
        var depth = 0
        return IntArray(children.size) { index ->
            val literal = (children[index] as? HtmlInline)?.literal
            when {
                literal == null || literal.isNotHalfOfATagPair() -> Unit
                literal.startsWith("</") -> depth = maxOf(0, depth - 1)
                else -> depth++
            }
            depth
        }
    }

    /**
     * The cut falls after `children[end - 1]`. If that is a text that runs straight into the next
     * child mid-word, its last word goes to the next piece: a new [Text] for it is returned, and the
     * shortened literal of the old one is queued in [textCuts]. Null when nothing is carried.
     */
    private fun cutAtWordBoundary(children: List<Node>, end: Int, textCuts: MutableList<Pair<Text, String>>): Text? {
        val text = children[end - 1] as? Text
        if (text == null || end >= children.size) return null
        val literal = text.literal
        val space = literal.indexOfLast { it.isWhitespace() }
        // No space also covers an empty literal, so last() is only read on a non-empty one.
        if (space < 0 || literal.last().isWhitespace()) return null
        textCuts += text to literal.substring(0, space + 1)
        return Text(literal.substring(space + 1))
    }

    private fun Node.isLineBreak() = this is SoftLineBreak || this is HardLineBreak

    /** A comment, a processing instruction, a self-closing tag or a void element opens nothing. */
    private fun String.isNotHalfOfATagPair() =
        startsWith("<!") || startsWith("<?") || endsWith("/>") || tagName() in VOID_ELEMENTS

    /** The element name, with the `<` or `</` and anything after the name dropped. */
    private fun String.tagName() =
        removePrefix("</").removePrefix("<").takeWhile { it.isLetterOrDigit() || it == '-' }.lowercase()

    /** The HTML elements that never have a closing tag, so `<br>` is whole without a slash. */
    private val VOID_ELEMENTS =
        setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr")
}
