// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.largefile

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 * Pins the in-memory fixture to `docs/tools/gen_large_markdown.py`, so a size quoted from a phone run
 * with the script's file and a size used in a test describe the same bytes.
 */
class LargeMarkdownFixtureTest {

    @Test
    fun `the 1 MB fixture is byte-identical to the script's output`() {
        // `python3 docs/tools/gen_large_markdown.py 1 out.md && md5sum out.md`
        val expected = "b49450d72f8e36caa226e029d254e5d5"
        val bytes = LargeMarkdownFixture.generate(LargeMarkdownFixture.MB).toByteArray(Charsets.US_ASCII)
        val md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(LargeMarkdownFixture.MB, bytes.size)
        assertEquals(expected, md5)
    }
}
