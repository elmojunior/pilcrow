// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pilcrowmd.ui.theme.DarkColorScheme
import com.pilcrowmd.ui.theme.LocalMDColors
import com.pilcrowmd.viewmodel.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w384dp-h832dp-night-xxhdpi")
class ToolbarOverflowTtsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `overflow shows Ler em voz alta when not speaking and triggers callback`() {
        var ttsToggled = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalMDColors provides DarkColorScheme) {
                PilcrowToolbar(
                    currentMode = ViewMode.READER,
                    isTtsSpeaking = false,
                    onToggleTts = { ttsToggled++ },
                )
            }
        }

        // Open overflow menu
        composeRule.onNodeWithContentDescription("More actions").performClick()

        // "Ler em voz alta" should be displayed
        val ttsItem = composeRule.onNodeWithText("Ler em voz alta")
        ttsItem.assertIsDisplayed()

        // Tap it
        ttsItem.performClick()
        assertEquals(1, ttsToggled)
    }

    @Test
    fun `overflow shows Parar when speaking and triggers callback`() {
        var ttsToggled = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalMDColors provides DarkColorScheme) {
                PilcrowToolbar(
                    currentMode = ViewMode.READER,
                    isTtsSpeaking = true,
                    onToggleTts = { ttsToggled++ },
                )
            }
        }

        // Open overflow menu
        composeRule.onNodeWithContentDescription("More actions").performClick()

        // "Parar" should be displayed
        val stopItem = composeRule.onNodeWithText("Parar")
        stopItem.assertIsDisplayed()

        // Tap it
        stopItem.performClick()
        assertEquals(1, ttsToggled)
    }
}
