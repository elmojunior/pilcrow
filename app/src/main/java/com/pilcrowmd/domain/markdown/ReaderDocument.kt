// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.domain.markdown

import org.commonmark.node.Node

/**
 * The post-parse passes every reader parse site applies (the renderer, the PDF export, search and
 * the headings drawer), so all four walk the same tree and adapter positions stay 1:1. The one
 * exception is the long-paragraph split, which the PDF skips (M-328; see [transform]).
 */
object ReaderDocument {
    /**
     * [splitLongParagraphs]: a top-level paragraph with more than
     * [ParagraphSplitter.MAX_INLINE_CHILDREN] inline children becomes several paragraphs, so the
     * reader binds, lays out and records a bounded block per frame (M-328). It runs first, because
     * [Footnotes.transform] numbers the blocks. The PDF export passes false: it lays its pages out
     * off the main thread and prints the paragraph whole.
     */
    fun transform(document: Node, splitLongParagraphs: Boolean = true): Node {
        if (splitLongParagraphs) ParagraphSplitter.split(document)
        return Callouts.transform(Footnotes.transform(document))
    }
}
