// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.rendering

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

/**
 * A jump to a heading, an `#anchor` or a footnote MOVES the list instead of teleporting it.
 *
 * The list runs in a real window and time is advanced explicitly, so each assertion names the
 * instant it is about: "after one frame the target has not arrived" is what tells a glide from the
 * old `scrollToPositionWithOffset`, which puts the target at the top in the same breath.
 *
 * Fixture: 100 px blocks in a 900 px viewport, so "near" is up to 1,800 px (18 blocks) and the
 * far cases are well beyond it. Every case that ends somewhere is compared with where the INSTANT
 * jump ends on a second, identical list, because "the same place, including when the end of the
 * document clamps it" is the contract, not any particular pixel count.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderSmoothJumpTest {

    private class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

    /** [count] blocks of [BLOCK_PX], except the one at [tallAt], which is [tallPx] tall. */
    private class Blocks(private val count: Int, private val tallAt: Int = -1, private val tallPx: Int = 0) :
        RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = count

        override fun getItemViewType(position: Int) = if (position == tallAt) TALL else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            TextView(parent.context).apply {
                val height = if (viewType == TALL) tallPx else BLOCK_PX
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            },
        )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.text.text = "block $position"
        }
    }

    /**
     * Robolectric's Choreographer normally RUNS AHEAD: the first idle after an animation starts
     * advances the clock to the next frame and the next, until it ends, so every animation finishes
     * inside one `idle()` and "where is it after 50 ms" cannot be asked. Paused, a frame runs only
     * when the test advances the clock past it. (Measured: unpaused, a 300 ms scroll was over after
     * `idleFor(1 ms)` and the clock had moved 210 ms.)
     */
    @Before
    fun choreographerRunsOnTheTestsClock() {
        ShadowChoreographer.setPaused(true)
    }

    @After
    fun animationsBackOn() {
        setAnimationsEnabled(true)
        ShadowChoreographer.setPaused(false)
    }

    private fun setAnimationsEnabled(enabled: Boolean) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, if (enabled) 1f else 0f)
    }

    /**
     * Frame by frame, because one big `idleFor` does not run the frames in it as separate frames:
     * measured, a 500 ms advance left a far jump half way, with the layout it was waiting on never run.
     */
    private fun advance(millis: Long) {
        var left = millis
        while (left > 0) {
            val step = minOf(left, FRAME_MS)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
            left -= step
        }
    }

    /** One synchronous layout pass, for the case where NO time may pass (animations off). */
    private fun layOut(rv: RecyclerView) {
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_PX, View.MeasureSpec.EXACTLY),
        )
        rv.layout(0, 0, WIDTH_PX, VIEWPORT_PX)
    }

    private fun showList(blocks: Int, tallAt: Int = -1, tallPx: Int = 0): RecyclerView {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val rv = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            itemAnimator = null
            adapter = Blocks(blocks, tallAt, tallPx)
        }
        applyReaderBottomSpacer(rv, BODY_LINE_PX)
        val root = FrameLayout(activity).apply { addView(rv, FrameLayout.LayoutParams(WIDTH_PX, VIEWPORT_PX)) }
        activity.setContentView(root)
        advance(FRAME_MS)
        return rv
    }

    private fun RecyclerView.layoutManagerOf() = layoutManager as LinearLayoutManager

    /** The target's top relative to the viewport, or null while it is not laid out at all. */
    private fun RecyclerView.topOf(position: Int): Int? = layoutManagerOf().findViewByPosition(position)?.top

    /** Where the list rests: the first visible block and how far its top is from the viewport's. */
    private fun RecyclerView.rest(): Pair<Int, Int?> {
        val first = layoutManagerOf().findFirstVisibleItemPosition()
        return first to topOf(first)
    }

    /** Where the INSTANT jump ends on an identical list. */
    private fun instantRest(blocks: Int, from: Int, target: Int): Pair<Int, Int?> {
        val rv = showList(blocks)
        rv.layoutManagerOf().scrollToPositionWithOffset(from, 0)
        advance(FRAME_MS)
        rv.layoutManagerOf().scrollToPositionWithOffset(target, 0)
        advance(FRAME_MS)
        return rv.rest()
    }

    private fun jumpFrom(rv: RecyclerView, from: Int) {
        rv.layoutManagerOf().scrollToPositionWithOffset(from, 0)
        advance(FRAME_MS)
    }

    // --- near ------------------------------------------------------------------------------

    @Test
    fun `T1 a near jump to a block not yet laid out moves over time and does not teleport`() {
        val rv = showList(100)
        val target = 14 // 1,400 px: past the screen, inside two of them
        assertEquals("guard: the target must not be laid out yet", null, rv.topOf(target))

        smoothJumpTo(rv, target)
        advance(2 * FRAME_MS)

        assertTrue("the list must have started moving", rv.computeVerticalScrollOffset() > 0)
        assertNotEquals("one or two frames in, the target must not have arrived", 0, rv.topOf(target))

        advance(500)
        assertEquals("and half a second later it has", 0, rv.topOf(target))
    }

    @Test
    fun `T1b a near jump to a laid-out block eases onto it`() {
        val rv = showList(100)
        val target = 7
        assertTrue("guard: the target must be laid out", rv.topOf(target) != null)

        smoothJumpTo(rv, target)
        advance(2 * FRAME_MS)

        assertTrue("the list must have started moving", rv.computeVerticalScrollOffset() > 0)
        assertTrue("but not arrived", (rv.topOf(target) ?: 0) > 0)

        advance(500)
        assertEquals(0, rv.topOf(target))
    }

    @Test
    fun `a near jump upward lands on the block too`() {
        val rv = showList(100)
        jumpFrom(rv, 50)
        smoothJumpTo(rv, 40)
        advance(2 * FRAME_MS)
        val first = rv.layoutManagerOf().findFirstVisibleItemPosition()
        assertNotEquals("guard: it must be moving, not teleported", 50, first)
        advance(500)
        assertEquals(0, rv.topOf(40))
    }

    // --- a near estimate that is wrong -----------------------------------------------------

    /**
     * The estimate multiplies the block count by the visible blocks' average height, so one huge
     * block the list cannot see makes a 16-screen move look like 1,000 px. Blocks 0-8 are on screen
     * at 100 px, block 9 (just off the bottom) is 15 viewports tall, and the target is the block
     * after it: estimated 1,000 px (near, an unlaid-out target, so the seek scroller runs), really
     * 14,500 px. A seek at 175 ms a screen would take 2.8 s; it must give up and finish like a far jump.
     */
    @Test
    fun `a near seek that hits a huge hidden block finishes like a far jump`() {
        val tallAt = 9
        val target = 10
        val rv = showList(60, tallAt = tallAt, tallPx = 15 * VIEWPORT_PX)
        assertEquals("guard: the huge block must be off screen, or the estimate sees it", null, rv.topOf(tallAt))
        assertEquals("guard: so must the target, or this is the laid-out path", null, rv.topOf(target))

        smoothJumpTo(rv, target)
        advance(2 * FRAME_MS)
        assertNotEquals("it moves meanwhile", 0, rv.computeVerticalScrollOffset())

        // Worst case here by arithmetic: seek 1,450 px (282 ms) + teleport frame + 250 ms glide + frames.
        advance(700)
        assertEquals(0, rv.topOf(target))
    }

    // --- far -------------------------------------------------------------------------------

    @Test
    fun `T2 a far jump down arrives, and not before the run-up is over`() {
        val rv = showList(400)
        val target = 180 // 18,000 px: twenty screens
        smoothJumpTo(rv, target)

        advance(50)
        assertNotEquals("50 ms in, the run-up is still running", 0, rv.topOf(target))
        assertTrue("and the list has moved", rv.computeVerticalScrollOffset() > 0)

        advance(500)
        assertEquals(0, rv.topOf(target))
        assertEquals(instantRest(400, 0, target), rv.rest())
    }

    @Test
    fun `T2 a far jump up arrives too`() {
        val rv = showList(400)
        jumpFrom(rv, 300)
        val target = 40
        val before = rv.computeVerticalScrollOffset()

        smoothJumpTo(rv, target)
        advance(50)
        assertNotEquals("50 ms in, the run-up is still running", 0, rv.topOf(target))
        assertTrue("and it ran UP, the way the jump goes", rv.computeVerticalScrollOffset() < before)

        advance(500)
        assertEquals(0, rv.topOf(target))
        assertEquals(instantRest(400, 300, target), rv.rest())
    }

    // --- animations off --------------------------------------------------------------------

    @Test
    fun `T3 with animations off the jump is today's instant one`() {
        setAnimationsEnabled(false)
        val rv = showList(400)
        smoothJumpTo(rv, 180)
        layOut(rv) // the layout pass the jump asked for; no clock is advanced
        assertEquals("no time has passed and the target is already at the top", 0, rv.topOf(180))
    }

    @Test
    fun `T3 with animations off a near jump is instant too`() {
        setAnimationsEnabled(false)
        val rv = showList(100)
        smoothJumpTo(rv, 14)
        layOut(rv)
        assertEquals(0, rv.topOf(14))
    }

    // --- the end of the document -----------------------------------------------------------

    @Test
    fun `T4 a far jump to the last block rests where the instant jump does, clamp included`() {
        val rv = showList(400)
        smoothJumpTo(rv, 399)
        advance(500)
        val instant = instantRest(400, 0, 399)
        assertEquals(instant, rv.rest())
        assertNotEquals("guard: the end really clamps, so the block is not at the top", 0, rv.topOf(399))
    }

    @Test
    fun `T4 a near jump to the last block rests where the instant jump does, clamp included`() {
        // 1,800 px + the tail: the last block starts 1,700 px down (near), but the document ends first.
        val rv = showList(18)
        smoothJumpTo(rv, 17)
        advance(500)
        assertEquals(instantRest(18, 0, 17), rv.rest())
        assertNotEquals("guard: the end really clamps, so the block is not at the top", 0, rv.topOf(17))
    }

    // --- a second jump ---------------------------------------------------------------------

    @Test
    fun `T5 a second jump during the first wins`() {
        val rv = showList(400)
        smoothJumpTo(rv, 300)
        advance(30) // still in the run-up
        smoothJumpTo(rv, 20)
        advance(600)
        assertEquals(0, rv.topOf(20))
        assertEquals(instantRest(400, 0, 20), rv.rest())
    }

    @Test
    fun `T5 a second jump after the teleport wins`() {
        val rv = showList(400)
        smoothJumpTo(rv, 300)
        advance(150) // past the run-up: the list has teleported and is gliding in
        smoothJumpTo(rv, 20)
        advance(600)
        assertEquals(0, rv.topOf(20))
    }

    // --- something else positions the list -------------------------------------------------

    @Test
    fun `cancelReaderJump during the run-up lets the caller's own position stand`() {
        val rv = showList(400)
        smoothJumpTo(rv, 300)
        advance(30) // still in the run-up
        cancelReaderJump(rv)
        rv.layoutManagerOf().scrollToPositionWithOffset(50, 0)
        advance(500)
        assertEquals(0, rv.topOf(50))
    }

    @Test
    fun `cancelReaderJump after the teleport lets the caller's own position stand`() {
        val rv = showList(400)
        smoothJumpTo(rv, 300)
        advance(160) // past the run-up: the list has teleported and is gliding in
        cancelReaderJump(rv)
        rv.layoutManagerOf().scrollToPositionWithOffset(50, 0)
        advance(500)
        assertEquals(0, rv.topOf(50))
    }

    @Test
    fun `cancelReaderJump with no jump ever started does nothing`() {
        val rv = showList(100)
        cancelReaderJump(rv)
        assertEquals(0, rv.computeVerticalScrollOffset())
    }

    // --- the user takes over ---------------------------------------------------------------

    @Test
    fun `a touch during the run-up abandons the jump`() {
        val rv = showList(400)
        smoothJumpTo(rv, 300)
        advance(30)
        val now = SystemClock.uptimeMillis()
        rv.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 300f, 600f, 0))
        rv.dispatchTouchEvent(MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_MOVE, 300f, 500f, 0))
        rv.dispatchTouchEvent(MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_MOVE, 300f, 400f, 0))
        assertEquals("guard: the finger is dragging", RecyclerView.SCROLL_STATE_DRAGGING, rv.scrollState)
        // The gesture ending without a fling (a cancel) makes the list report IDLE, the signal the
        // jump waits for: a jump that had not abandoned itself would teleport here, under the
        // reader's hand. (A real lift would fling, and the IDLE would come only when that ends.)
        rv.dispatchTouchEvent(MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_CANCEL, 300f, 400f, 0))
        assertEquals("guard: the list is at rest", RecyclerView.SCROLL_STATE_IDLE, rv.scrollState)

        advance(600)
        assertNotEquals("the jump must not carry on and take the list from the finger", 0, rv.topOf(300))
        assertTrue("it is nowhere near the target", rv.layoutManagerOf().findFirstVisibleItemPosition() < 50)
    }

    private companion object {
        const val BLOCK_PX = 100
        const val VIEWPORT_PX = 900
        const val WIDTH_PX = 600
        const val BODY_LINE_PX = 40
        const val FRAME_MS = 16L
        const val TALL = 1
    }
}
