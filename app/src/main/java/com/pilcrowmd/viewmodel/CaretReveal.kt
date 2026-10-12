// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.viewmodel

/**
 * A one-shot request to bring reader block [blockIndex] (the one the editor caret sat in) into view
 * above an open keyboard, published when the user switches Edit to Read. seq tells one switch from
 * the next, as [HeadingJump] does, and the reader acknowledges it so a reader composed later never
 * replays it.
 */
data class CaretReveal(val blockIndex: Int, val seq: Int)
