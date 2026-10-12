// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.testing

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M-396: the unit-test JVM must have real headroom. On Gradle's 512 MB default the suite already
 * held 509 MB after GC on main (measured 2026-10-10, `-Xlog:gc`), so which test hit
 * `OutOfMemoryError` depended only on the order Linux listed the classes in: PR #381's CI failed
 * twice in `SettingsLargeFontScreenshotTest`, a test it does not touch. `app/build.gradle.kts` sets
 * the heap; this fails if that setting is lost.
 */
class UnitTestHeapTest {

    @Test
    fun `the unit-test JVM has at least a gigabyte of heap`() {
        val maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        // Runtime.maxMemory reports a little under -Xmx (one survivor space is not counted).
        assertTrue("unit-test heap is $maxMb MB, expected about 1024", maxMb >= MIN_HEAP_MB)
    }

    private companion object {
        const val MIN_HEAP_MB = 900L
    }
}
