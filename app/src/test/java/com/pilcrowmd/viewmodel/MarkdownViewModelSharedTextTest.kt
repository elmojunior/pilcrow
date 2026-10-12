// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.viewmodel

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.pilcrowmd.di.AppInfo
import com.pilcrowmd.domain.model.ViewMode
import com.pilcrowmd.domain.usecase.ParseMarkdownHeadingsUseCase
import com.pilcrowmd.domain.usecase.SearchMarkdownUseCase
import com.pilcrowmd.repository.FileRepository
import com.pilcrowmd.repository.FileText
import com.pilcrowmd.repository.StrandedSlot
import com.pilcrowmd.storage.LocalStorageManager
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MarkdownViewModelSharedTextTest {

    private val vmStore = ViewModelStore()

    private fun track(vm: MarkdownViewModel): MarkdownViewModel {
        vmStore.put("vm_${System.identityHashCode(vm)}", vm)
        return vm
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var storage: LocalStorageManager
    private lateinit var storageScope: CoroutineScope

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        storageScope = CoroutineScope(Dispatchers.IO + Job())
        val dataStore = PreferenceDataStoreFactory.create(scope = storageScope) {
            tempFolder.newFile("shared_text_test.preferences_pb")
        }
        storage = LocalStorageManager(context, dataStore)
    }

    @After
    fun tearDown() {
        vmStore.clear()
        storageScope.cancel()
        Dispatchers.resetMain()
    }

    private class FakeRepo(
        private val readContent: String = "",
        val writableUris: MutableSet<Uri> = mutableSetOf(),
    ) : FileRepository {
        val capturedSaves = linkedMapOf<Uri, String>()

        override suspend fun readFile(uri: Uri) = Result.success(FileText(readContent, isUtf8 = true))
        override suspend fun saveFile(uri: Uri, content: String): Result<Unit> {
            capturedSaves[uri] = content
            return Result.success(Unit)
        }
        override suspend fun recoverPendingSaves() = Result.success(0)
        override suspend fun takePersistableUriPermission(uri: Uri): Result<Unit> {
            writableUris.add(uri)
            return Result.success(Unit)
        }
        override suspend fun displayName(uri: Uri): String = uri.lastPathSegment ?: "doc.md"
        override fun hasPersistedPermission(uri: Uri): Boolean = true
        override fun hasPersistedWritePermission(uri: Uri): Boolean = uri in writableUris
        override suspend fun strandedSlots() = Result.success(emptyList<StrandedSlot>())
        override suspend fun saveStrandedSlotToTarget(slotKey: String, targetUri: Uri) = Result.success(Unit)
        override suspend fun discardSlot(key: String) = Result.success(Unit)
    }

    private fun vmWith(repo: FileRepository): MarkdownViewModel {
        val parseHeadings = ParseMarkdownHeadingsUseCase()
        return track(
            MarkdownViewModel(
                repository = repo,
                storage = storage,
                parseHeadingsUseCase = parseHeadings,
                searchUseCase = SearchMarkdownUseCase(parseHeadings),
                pdfExporter = mockk(relaxed = true),
                appInfo = object : AppInfo {
                    override val versionName = "test"
                },
            ),
        )
    }

    @Test
    fun `openSharedText loads content into new document in editor mode when clean`() = runTest {
        val vm = vmWith(FakeRepo())
        vm.openSharedText("# Shared from web\n\nContent here")

        val doc = vm.currentDocument.value
        assertNotNull(doc)
        assertEquals("# Shared from web\n\nContent here", doc!!.content)
        assertNull(doc.uri)
        assertTrue(doc.dirty)
        assertEquals(ViewMode.EDITOR, vm.mode.value)
    }

    @Test
    fun `openSharedText defers behind pending prompt when current document is dirty`() = runTest {
        val uri = Uri.parse("content://test/original.md")
        val repo = FakeRepo("Original content\n", writableUris = mutableSetOf(uri))
        val vm = vmWith(repo)
        vm.loadFile(uri)

        // Make original dirty
        vm.updateContent("Original edited")

        // Try opening shared text
        vm.openSharedText("New shared text")

        // Content must still be original edited (Safeguard 1: no data loss)
        assertEquals("Original edited", vm.currentDocument.value?.content)
        assertEquals("New shared text", vm.pendingSharedText.value)

        // Cancel dismisses prompt
        vm.cancelPendingSharedText()
        assertNull(vm.pendingSharedText.value)
        assertEquals("Original edited", vm.currentDocument.value?.content)
    }

    @Test
    fun `discardAndOpenPendingSharedText replaces dirty document`() = runTest {
        val uri = Uri.parse("content://test/original.md")
        val repo = FakeRepo("Original content\n", writableUris = mutableSetOf(uri))
        val vm = vmWith(repo)
        vm.loadFile(uri)

        vm.updateContent("Original edited")
        vm.openSharedText("New shared text")
        assertEquals("New shared text", vm.pendingSharedText.value)

        vm.discardAndOpenPendingSharedText()

        assertNull(vm.pendingSharedText.value)
        assertEquals("New shared text", vm.currentDocument.value?.content)
        assertEquals(ViewMode.EDITOR, vm.mode.value)
    }

    @Test
    fun `saveAndOpenPendingSharedText saves current doc and opens shared text`() = runTest {
        val uri = Uri.parse("content://test/original.md")
        val repo = FakeRepo("Original content\n", writableUris = mutableSetOf(uri))
        val vm = vmWith(repo)
        vm.loadFile(uri)

        vm.updateContent("Original edited")
        vm.openSharedText("New shared text")

        vm.saveAndOpenPendingSharedText()

        // Original was saved to disk
        assertEquals("Original edited\n", repo.capturedSaves[uri])
        assertNull(vm.pendingSharedText.value)
        assertEquals("New shared text", vm.currentDocument.value?.content)
        assertEquals(ViewMode.EDITOR, vm.mode.value)
    }
}
