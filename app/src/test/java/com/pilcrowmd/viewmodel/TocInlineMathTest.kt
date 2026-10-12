// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.viewmodel

import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.pilcrowmd.largefile.ReaderHarness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * A heading with inline maths keeps the formula's SOURCE in the TOC, without
 * its delimiters. The TOC comes from the reader's own tree (M-179), where each inline formula is one
 * node with no text children — so without the ruling's reading the formula would vanish from the
 * entry. Built through `provideFactory`, so the wiring that supplies that reading is under test too.
 */
@RunWith(RobolectricTestRunner::class)
class TocInlineMathTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var harness: ReaderHarness

    @After
    fun tearDown() = harness.close()

    @Test
    fun `inline maths in a heading lists as its source, without the dollar signs`() {
        harness = ReaderHarness(composeRule, tempFolder)
        val vm = harness.viewModel(
            "## The \$O(n)\$ bound\n\ntext\n\n# Energy \$\$E=mc^2\$\$ again\n\n## Plain heading\n",
        )

        vm.loadFile(Uri.parse("content://test/toc.md"))
        // The TOC starts empty and is filled once, from the prepared model: a real barrier.
        val deadline = System.currentTimeMillis() + ReaderHarness.AWAIT_TIMEOUT_MS
        while (vm.headings.value.size < 3 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }

        assertEquals(
            listOf("The O(n) bound", "Energy E=mc^2 again", "Plain heading"),
            vm.headings.value.map { it.text },
        )
    }

    private companion object {
        const val POLL_MS = 10L
    }
}
