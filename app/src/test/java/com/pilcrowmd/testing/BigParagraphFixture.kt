// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.testing

/**
 * The ON-BIG fixture for M-328: one Markdown paragraph made of [repetitions] repeats of a sentence
 * with text, strong, code, emphasis and a link — about nine inline children each — joined by spaces,
 * so 450 repetitions give a paragraph of about 4,000 inline children. [tail] is appended to the
 * repetition numbered [tailAt], to plant a word in a chosen piece once the paragraph is cut.
 */
fun bigParagraph(repetitions: Int, tailAt: Int = -1, tail: String = ""): String =
    (0 until repetitions).joinToString(" ") { i ->
        val sentence = "word$i more **b$i** tail `c$i` and *e$i* then [l$i](https://example.com/$i)"
        if (i == tailAt) "$sentence$tail" else sentence
    }

/**
 * [bigParagraph] with the hard inline content between the spans: a dollar pair that is currency, not
 * a formula, brackets, parentheses, punctuation, an entity, a backslash escape and, every 40th
 * repetition, a real soft line break inside the paragraph. About 10 inline children per repetition.
 */
fun hardInlineParagraph(repetitions: Int): String = (0 until repetitions).joinToString("") { i ->
    val separator = if (i > 0 && i % 40 == 0) "\n" else " "
    val sentence = "word$i costs \$5 and 10\$ a [bracket] b **b$i** c (paren); d! e? f: g; &amp; " +
        "\\*not em\\* `c$i` and *e$i* then [l$i](https://example.com/$i)"
    if (i == 0) sentence else separator + sentence
}
