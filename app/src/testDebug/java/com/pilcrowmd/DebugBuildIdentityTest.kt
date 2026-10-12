// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A debug build is a separate app on the phone (NEW-110): its own id, so it installs beside the Play
 * app instead of replacing it, and its own launcher name, so the two can be told apart. Debug-only
 * source set: the release id and name are checked on the built bundle, not here.
 */
@RunWith(RobolectricTestRunner::class)
class DebugBuildIdentityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the debug build has its own application id`() {
        assertEquals("com.pilcrowmd.debug", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `the debug build has its own launcher name`() {
        assertEquals("PilcrowMD debug", context.getString(R.string.app_name))
    }
}
