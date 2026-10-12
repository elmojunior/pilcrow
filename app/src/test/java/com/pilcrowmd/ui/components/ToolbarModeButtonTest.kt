// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.pilcrowmd.ui.theme.DarkColorScheme
import com.pilcrowmd.ui.theme.LocalMDColors
import com.pilcrowmd.viewmodel.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The toolbar's left control is ONE mode button showing the action (Edit in Read mode, Read in Edit
 * mode); Save shows in Edit mode only; every action keeps a 42dp target and fits a 384dp (S24+) bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w384dp-h832dp-night-xxhdpi")
class ToolbarModeButtonTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val modes = mutableListOf<ViewMode>()
    private var saves = 0

    private fun show(mode: ViewMode) {
        val editing = mode == ViewMode.EDITOR
        composeRule.setContent {
            CompositionLocalProvider(LocalMDColors provides DarkColorScheme) {
                PilcrowToolbar(
                    currentMode = mode,
                    onModeSelected = { modes += it },
                    onSave = { saves++ },
                    onUndo = if (editing) ({}) else null,
                    onRedo = if (editing) ({}) else null,
                )
            }
        }
    }

    private fun button(label: String): SemanticsNodeInteraction = composeRule.onNodeWithContentDescription(label)

    @Test
    fun readModeShowsEditAndSwitchesToTheEditor() {
        show(ViewMode.READER)
        button("Edit").assertExists()
        button("Read").assertDoesNotExist()
        button("Save").assertDoesNotExist()
        button("Export PDF").assertExists()
        button("Table of contents").assertExists()

        button("Edit").performClick()
        assertEquals(listOf(ViewMode.EDITOR), modes)
    }

    @Test
    fun editModeShowsReadAndSwitchesToTheReader() {
        show(ViewMode.EDITOR)
        button("Read").assertExists()
        button("Edit").assertDoesNotExist()
        button("Export PDF").assertDoesNotExist()
        button("Undo").assertExists()
        button("Redo").assertExists()

        button("Read").performClick()
        assertEquals(listOf(ViewMode.READER), modes)

        button("Save").assertExists().performClick()
        assertEquals(1, saves)
    }

    @Test
    fun theOldSegmentLabelsAreGoneInReadMode() = assertNoSegmentLabels(ViewMode.READER)

    @Test
    fun theOldSegmentLabelsAreGoneInEditMode() = assertNoSegmentLabels(ViewMode.EDITOR)

    private fun assertNoSegmentLabels(mode: ViewMode) {
        show(mode)
        listOf("Reader", "Editor", "Reader, selected", "Editor, selected").forEach {
            button(it).assertDoesNotExist()
        }
    }

    @Test
    fun readModeActionsFitTheBar() = assertFits(ViewMode.READER, listOf("Edit", "Export PDF"))

    @Test
    fun editModeActionsFitTheBar() = assertFits(ViewMode.EDITOR, listOf("Read", "Save", "Undo", "Redo"))

    private fun assertFits(mode: ViewMode, modeSpecific: List<String>) {
        show(mode)
        val labels = modeSpecific + listOf("Search", "Table of contents", "More actions", "Close file")
        labels.forEach { label ->
            button(label).assertIsDisplayed().assertWidthIsAtLeast(42.dp).assertHeightIsAtLeast(42.dp)
        }
        val right = button("Close file").getUnclippedBoundsInRoot().right
        assertTrue("Close file right edge $right must be <= 384dp", right <= 384.dp)
    }

    @Test
    fun longPressOnTheModeButtonShowsItsWord() {
        show(ViewMode.READER)
        button("Edit").performTouchInput { longClick() }
        composeRule.onNodeWithText("Edit").assertIsDisplayed()
    }
}
