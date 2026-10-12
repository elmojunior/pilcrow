// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.largefile

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.commonmark.node.Node
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A file opened while a large parse is still running may wait for it —
 * parses are serialised so two trees never share the heap (M-179) — PROVIDED a loading indicator is
 * visible the whole time. The first file's parse is held open by a latch, so the wait is as long as
 * the test says, not as long as a workstation happens to take.
 *
 * "The whole time" is read as: from the indicator's 300 ms grace (which keeps small files from
 * flashing it) until the second file's blocks appear, sampled repeatedly — never absent in between,
 * and never with a stale document on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ReaderWaitIndicatorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var harness: ReaderHarness

    @After
    fun tearDown() = harness.close()

    private val bigUri = Uri.parse("content://test/big.md")
    private val nextUri = Uri.parse("content://test/next.md")
    private val big = "# Big\n\nheld open by the test\n"
    private val next = "# Next\n\nthe second file\n"

    private val bigEntered = CountDownLatch(1)
    private val bigRelease = CountDownLatch(1)

    private val indicator = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate,
    )

    private fun indicatorShown() = composeRule.onAllNodes(indicator).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a file opened during a large parse shows the loading indicator until it appears`() {
        harness = ReaderHarness(composeRule, tempFolder)
        val real = com.pilcrowmd.domain.usecase.ParseMarkdownHeadingsUseCase()
        val vm = harness.viewModel(mapOf(bigUri to big, nextUri to next)) { content: String ->
            if (content == big) {
                bigEntered.countDown()
                bigRelease.await(ReaderHarness.AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            real.parseTree(content) as Node
        }
        harness.show(vm)

        vm.loadFile(bigUri)
        harness.awaitReader("the big file's parse is running") { bigEntered.count == 0L }
        vm.loadFile(nextUri)
        harness.awaitReader("the second file is published") { vm.currentDocument.value?.uri == nextUri }
        // Past the grace, the indicator must be up…
        harness.awaitReader("the indicator appears", timeoutMs = INDICATOR_TIMEOUT_MS) { indicatorShown() }

        // …and STAY up for as long as the second file is waiting, with nothing stale in the reader.
        repeat(SAMPLES) {
            harness.awaitReader("sample") { true }
            assertTrue("the indicator disappeared while the second file was waiting", indicatorShown())
            assertEquals("a document was painted while the second file was waiting", 0, blocks())
            Thread.sleep(SAMPLE_GAP_MS)
        }

        bigRelease.countDown()
        harness.awaitReader("the second file appears") { blocks() > 0 }
        assertEquals("the reader shows the second file", next, vm.readerModel.value?.content)
        harness.awaitReader("the indicator goes away") { !indicatorShown() }
    }

    private fun blocks() = harness.readerList()?.adapter?.itemCount ?: 0

    private companion object {
        const val INDICATOR_TIMEOUT_MS = 5_000L
        const val SAMPLES = 15
        const val SAMPLE_GAP_MS = 50L
    }
}
