// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.testing

/**
 * JUnit category for the M-179 memory tests. **They run in `testLargeDocumentDebug`, a JVM of their
 * own with a phone-sized heap, and are excluded from `testDebugUnitTest`** (see `app/build.gradle.kts`).
 *
 * Why a separate JVM: an out-of-memory test is only a test when the heap ceiling is known. The
 * default test worker's heap is whatever Gradle picks, and a failing run can leave that shared JVM
 * short of memory for every suite after it. Each class also gets a fresh JVM (`forkEvery = 1`), so
 * one class's allocation pattern cannot decide another's outcome.
 *
 * Forgetting the annotation does not fail loudly: the test then runs under the default heap, where
 * a regression may no longer run out of memory. If you add an M-179 memory test, add the category.
 */
interface LargeDocumentSuite
