// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.ui.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pilcrowmd.ui.theme.mdColors
import kotlinx.coroutines.delay

/**
 * The reader's "still preparing" indicator (M-172): the model is prepared off Main, and until it lands
 * [MarkdownPreview] shows its background and this, which appears only once the wait is long enough to
 * notice — no flash on small files. Token colour only (Safeguard 4).
 */
@Composable
internal fun PreparingIndicator(modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PREPARING_INDICATOR_DELAY_MS)
        visible = true
    }
    if (visible) CircularProgressIndicator(modifier = modifier, color = mdColors().secondaryText)
}

private const val PREPARING_INDICATOR_DELAY_MS = 300L
