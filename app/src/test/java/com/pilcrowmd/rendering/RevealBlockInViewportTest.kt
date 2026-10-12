// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.rendering

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [revealBlockInViewport]: after an Edit to Read switch with the keyboard open, the reader list is
 * shorter than it was when its scroll anchor was saved, so the block the caret sat in can be below
 * the new bottom edge. The list below is the reader's shape in a fixed viewport: 100 px blocks and
 * the two-body-line tail after the last one.
 *
 * "Keyboard" = the same list laid out again at a smaller height, its first visible block put back
 * where it was, which is what the reader's restore does.
 */
@RunWith(RobolectricTestRunner::class)
class RevealBlockInViewportTest {

    private val fullHeight = 900
    private val keyboardHeight = 600
    private val blockPx = 100
    private val blockCount = 30
    private val lastBlock = blockCount - 1
    private val bodyLinePx = 40

    /** [tallAt] is [tallPx] tall instead of [blockPx]. */
    private fun list(tallAt: Int = -1, tallPx: Int = 0): RecyclerView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rv = RecyclerView(context)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = blockCount
            override fun getItemViewType(position: Int) = if (position == tallAt) 1 else 0
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val text = TextView(parent.context)
                text.layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, if (viewType == 1) tallPx else blockPx)
                return object : RecyclerView.ViewHolder(text) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as TextView).text = "block $position"
            }
        }
        applyReaderBottomSpacer(rv, bodyLinePx)
        layOut(rv, fullHeight)
        return rv
    }

    private fun layOut(rv: RecyclerView, height: Int) {
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        rv.layout(0, 0, 600, height)
    }

    private fun RecyclerView.lm() = layoutManager as LinearLayoutManager

    private fun RecyclerView.block(position: Int): View? = lm().findViewByPosition(position)

    /** The keyboard opens: shorter list, first visible block kept where it was (the reader's restore). */
    private fun RecyclerView.openKeyboard() {
        val first = lm().findFirstVisibleItemPosition()
        val offset = lm().findViewByPosition(first)!!.top
        layOut(this, keyboardHeight)
        lm().scrollToPositionWithOffset(first, offset)
        layOut(this, keyboardHeight)
    }

    private fun RecyclerView.scrollToEndThenOpenKeyboard() {
        scrollBy(0, blockCount * blockPx + fullHeight)
        layOut(this, fullHeight)
        openKeyboard()
    }

    @Test
    fun `a block cut off by the new bottom edge is bottom-aligned against it`() {
        val rv = list()
        rv.scrollToEndThenOpenKeyboard()
        val cutOff = 27
        assertTrue(
            "guard: the restore leaves block $cutOff laid out but below the keyboard, or the test proves nothing",
            rv.block(cutOff)!!.bottom > keyboardHeight,
        )

        revealBlockInViewport(rv, cutOff)

        assertEquals(keyboardHeight, rv.block(cutOff)!!.bottom)
    }

    @Test
    fun `a block already inside does not move`() {
        val rv = list()
        rv.scrollToEndThenOpenKeyboard()
        val position = rv.lm().findFirstVisibleItemPosition() + 2
        val before = rv.block(position)!!.top

        revealBlockInViewport(rv, position)

        assertEquals(before, rv.block(position)!!.top)
    }

    @Test
    fun `a block cut off at the top is top-aligned`() {
        val rv = list()
        rv.scrollBy(0, 250) // block 2 now has its top at -50
        layOut(rv, fullHeight)
        assertEquals("guard: block 2 is cut off at the top", -50, rv.block(2)!!.top)

        revealBlockInViewport(rv, 2)

        assertEquals(0, rv.block(2)!!.top)
    }

    @Test
    fun `the last block, left unlaid out below the keyboard, ends bottom-aligned once it has a height`() {
        val rv = list()
        rv.scrollToEndThenOpenKeyboard()
        assertNull("guard: the last block is not laid out under the keyboard", rv.block(lastBlock))

        revealBlockInViewport(rv, lastBlock)
        layOut(rv, keyboardHeight)

        assertNotNull(rv.block(lastBlock))
        assertEquals(keyboardHeight, rv.block(lastBlock)!!.bottom)
    }

    @Test
    fun `a block taller than the viewport is bottom-aligned`() {
        val rv = list(tallAt = 10, tallPx = 1_000)
        rv.scrollBy(0, 10 * blockPx + 300) // the tall block's top is at -300; its end is below the edge
        layOut(rv, fullHeight)
        rv.openKeyboard()
        assertTrue("guard: the block is taller than the keyboard viewport", rv.block(10)!!.height > keyboardHeight)

        revealBlockInViewport(rv, 10)

        assertEquals(keyboardHeight, rv.block(10)!!.bottom)
    }

    @Test
    fun `a position the adapter does not have is ignored`() {
        val rv = list()
        val before = rv.block(0)!!.top

        revealBlockInViewport(rv, blockCount + 5)
        revealBlockInViewport(rv, -1)

        assertEquals(before, rv.block(0)!!.top)
    }

    private companion object {
        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
