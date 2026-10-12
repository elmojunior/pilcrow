// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.largefile

/**
 * Large Markdown fixtures for M-172 / M-179, generated in memory. The twin of
 * `docs/tools/gen_large_markdown.py`: the same block cycle, the same padding rule, so
 * `generate(n)` is byte-for-byte the file the script writes for `n` bytes. Keep the two in step.
 *
 * ASCII only, so one char is one byte and the String is Latin-1 compact on HotSpot and ART.
 */
internal object LargeMarkdownFixture {

    const val MB = 1_000_000

    /** Exactly [sizeChars] chars of the M-179 fixture shape. */
    fun generate(sizeChars: Int): String {
        val out = StringBuilder(sizeChars)
        var i = 0
        while (true) {
            val b = block(i)
            if (out.length + b.length > sizeChars) break
            out.append(b)
            i++
        }
        val remaining = sizeChars - out.length
        if (remaining > 1) {
            val pad = "pad ".repeat(remaining / 4 + 1)
            out.append(pad, 0, remaining - 1).append('\n')
        } else if (remaining == 1) {
            out.append('\n')
        }
        check(out.length == sizeChars)
        return out.toString()
    }

    /** The i-th block of the cycle; every block ends with a blank line. */
    @Suppress("MaxLineLength")
    private fun block(i: Int): String {
        val n = i / 8
        return when (i % 8) {
            0 -> "## Section $n\n\n"
            1 ->
                "Paragraph $n has **bold text**, *italic text*, `inline code` and a " +
                    "[link](https://example.com/$n). It keeps going with **more bold**, " +
                    "*more italic* and `more.code()` so the inline parser has work to do. " +
                    "Inline maths too: \$x_{${n % 10}}^2 + y^2 = r^2\$.\n\n"
            2 ->
                "| Column A | Column B | Column C |\n|---|---|---|\n" +
                    "| a$n | **b$n** | `c$n` |\n| d$n | *e$n* | f$n |\n| g$n | h$n | i$n |\n\n"
            3 -> "```kotlin\nfun section$n(x: Int): Int {\n    val y = x * ${n % 97}\n    return y + 1\n}\n```\n\n"
            4 -> "\$\$\n\\int_0^{${n % 9 + 1}} x^2\\,dx = \\frac{1}{3}\n\$\$\n\n"
            5 -> "- item one of $n with **bold**\n- item two with *italic*\n- item three with `code`\n\n"
            6 -> "### Subsection $n.1\n\n"
            else -> "> Quote $n: *emphasis* and **strong** inside a blockquote.\n\n"
        }
    }
}
