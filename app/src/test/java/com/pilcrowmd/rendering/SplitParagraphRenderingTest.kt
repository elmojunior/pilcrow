// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.rendering

import android.text.Spanned
import androidx.test.core.app.ApplicationProvider
import com.pilcrowmd.domain.markdown.ParagraphSplitter
import com.pilcrowmd.testing.bigParagraph
import io.noties.markwon.Markwon
import org.commonmark.node.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The split must change where a paragraph is cut, never what is drawn (M-328): rendered through the
 * real Markwon, the pieces of a split paragraph add up to the whole paragraph's text and to its
 * spans, class by class.
 */
@RunWith(RobolectricTestRunner::class)
class SplitParagraphRenderingTest {

    private val markwon: Markwon by lazy { buildPilcrowMarkwon(ApplicationProvider.getApplicationContext()) }

    private fun spanCounts(rendered: CharSequence): Map<String, Int> =
        (rendered as Spanned).getSpans(0, rendered.length, Any::class.java)
            .groupingBy { it.javaClass.name }
            .eachCount()

    @Test
    fun theRenderedPiecesAddUpToTheWholeParagraph() {
        val source = bigParagraph(100)
        val whole = markwon.render(markwon.parse(source))

        val document = markwon.parse(source)
        ParagraphSplitter.split(document)
        val pieces: List<Node> = generateSequence(document.firstChild) { it.next }.toList()
        pieces.forEach { it.unlink() }
        val rendered = pieces.map { markwon.render(it) }

        assertTrue("the paragraph was cut into ${pieces.size} pieces", pieces.size > 1)
        assertEquals(whole.toString(), rendered.joinToString("") { it.toString() })
        val summed = rendered.map { spanCounts(it) }
            .fold(emptyMap<String, Int>()) { acc, counts ->
                (acc.keys + counts.keys).associateWith { (acc[it] ?: 0) + (counts[it] ?: 0) }
            }
        assertEquals(spanCounts(whole), summed)
        assertTrue("spans were counted", spanCounts(whole).values.sum() > 100)
    }
}
