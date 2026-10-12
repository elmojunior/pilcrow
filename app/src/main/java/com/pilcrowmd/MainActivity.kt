// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pilcrowmd.ui.screen.MainScreen
import com.pilcrowmd.viewmodel.ImageFolderViewModel
import com.pilcrowmd.viewmodel.MarkdownViewModel

/** Bounded minimum the branded splash stays up so its lines→¶ morph plays. */
private const val SPLASH_MIN_DISPLAY_MS = 900L

/** Saved-state key: the Activity's current intent has already been handed to the app (M-139). */
private const val KEY_INTENT_HANDLED = "com.pilcrowmd.INTENT_HANDLED"

/**
 * Pilcrow main activity.
 * Single-activity Compose app. No Hilt — manual ViewModel construction (simplicity-first).
 *
 * Dependency injection: contentResolver → LocalFileRepository → MarkdownViewModel, LocalStorageManager
 * Essential safeguards and hard constraints:
 * - No data loss: LocalFileRepository uses atomic write via sync()
 * - Content fidelity: UTF-8 round-trip without normalization
 * - Render never crashes: MarkwonRenderer handles unsupported syntax gracefully
 * - Design fidelity: All colors from token layer, no hardcoded hex
 */
class MainActivity : ComponentActivity() {
    // The most recent intent-provided file URI, held as Compose *state* so the app
    // reacts both at launch (cold) and while already running (warm). The earlier version stored
    // a plain field read once by a LaunchedEffect(Unit), so a file opened while running never
    // loaded. The composable observes this state and loads on each change.
    private val _intentFileUri = mutableStateOf<Uri?>(null)
    val intentFileUri: State<Uri?> get() = _intentFileUri

    // Shared text received via ACTION_SEND (share sheet).
    private val _intentSharedText = mutableStateOf<String?>(null)
    val intentSharedText: State<String?> get() = _intentSharedText

    // True once the current intent's file or shared text has been consumed by the app. Saved across recreation,
    // because getIntent() survives recreation too: without this, every rotation re-published the
    // launch file and loaded it over whatever the user had opened since (M-139). Set only on
    // consumption, not on publish, so a recreation that lands before the first composition still
    // opens the file.
    private var intentHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the system splash before super.onCreate so the OS draws the branded launch frame
        // (Theme.Pilcrow.Splash) and then hands back to Theme.Pilcrow. Presentation chrome only —
        // no save-path contact.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash for a brief, BOUNDED minimum so the lines→¶ morph actually plays before it
        // dismisses. On a fast device first-frame-ready is near-instant, so without this the animation
        // was imperceptible ("too quick / unfinished"). This is a fixed *upper bound*, not an
        // indefinite gate: the condition becomes false after SPLASH_MIN_DISPLAY_MS regardless of app
        // state, so the splash can never hang.
        val splashStart = SystemClock.uptimeMillis()
        splashScreen.setKeepOnScreenCondition {
            SystemClock.uptimeMillis() - splashStart < SPLASH_MIN_DISPLAY_MS
        }
        // Whether this process was launched to open a specific file or text (vs a plain launcher start).
        // Gates the stranded-WAL-slot dialog: auto-pop only on a launcher start, never on a file/text open.
        val launchedWithContent = isContentIntent(intent)
        setContent {
            PilcrowApp(
                context = this,
                intentUri = intentFileUri,
                onIntentConsumed = ::consumeIntentFileUri,
                intentSharedText = intentSharedText,
                onSharedTextConsumed = ::consumeIntentSharedText,
                launchedToOpenFile = launchedWithContent,
            )
        }

        // Handle intent-opened file (ACTION_VIEW from file manager, ACTION_SEND from share sheet),
        // unless a previous instance of this Activity already consumed it (M-139).
        if (savedInstanceState?.getBoolean(KEY_INTENT_HANDLED) == true) {
            intentHandled = true
        } else {
            handleIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_INTENT_HANDLED, intentHandled)
    }

    /** True if [intent] launched the app to open specific content (file or shared text). */
    private fun isContentIntent(intent: Intent?): Boolean = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data != null
        Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) != null ||
            intent.hasExtra(Intent.EXTRA_TEXT) || intent.hasExtra(Intent.EXTRA_HTML_TEXT)
        else -> false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // keep getIntent() current for the running Activity
        // Handle intent when app is already running
        // (e.g., user taps a .md file or shares text while Pilcrow is open).
        handleIntent(intent)
    }

    /**
     * Extract URI or shared text from intent (ACTION_VIEW or ACTION_SEND), take persistent permission,
     * and store for the app to load.
     */
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    takePermission(uri)
                    intentHandled = false
                    _intentFileUri.value = uri
                }
            }
            Intent.ACTION_SEND -> {
                val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (streamUri != null) {
                    takePermission(streamUri)
                    intentHandled = false
                    _intentFileUri.value = streamUri
                } else {
                    val html = intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
                    val plain = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    val shared = com.pilcrowmd.util.SharedTextResolver.resolve(html, plain, subject)
                    if (!shared.isNullOrBlank()) {
                        intentHandled = false
                        _intentSharedText.value = shared
                    }
                }
            }
        }
    }

    /**
     * Take persistent read/write permission for the given URI so it remains accessible and writable after restart.
     * Some content providers only grant read or don't support persistable permissions; fall back gracefully.
     */
    private fun takePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (e: Exception) {
                // Some file managers don't support persistent permissions; fail gracefully.
                // File still loads in this session but won't be re-openable after app restart.
                Log.w("MainActivity", "Could not take persistent permission for $uri", e)
            }
        }
    }

    /**
     * Mark the intent's file as handled. Clearing the Compose mirror stops a re-load on the next
     * recomposition; [intentHandled], saved in [onSaveInstanceState], stops one on the next
     * Activity *recreation*, which is the case that mattered (M-139).
     */
    fun consumeIntentFileUri() {
        _intentFileUri.value = null
        intentHandled = true
    }

    /** Mark the intent's shared text as handled. */
    fun consumeIntentSharedText() {
        _intentSharedText.value = null
        intentHandled = true
    }
}

/**
 * Root composable for Pilcrow app.
 * Constructs ViewModel with repository and storage implementations.
 * Wires up MainScreen with full state management and SAF integration.
 * Handles intent-opened files and shared text via PilcrowApp initialization.
 */
@Composable
fun PilcrowApp(
    context: android.content.Context,
    intentUri: androidx.compose.runtime.State<android.net.Uri?>,
    onIntentConsumed: () -> Unit,
    intentSharedText: androidx.compose.runtime.State<String?> = androidx.compose.runtime.mutableStateOf(null),
    onSharedTextConsumed: () -> Unit = {},
    launchedToOpenFile: Boolean = false,
) {
    MaterialTheme {
        // Obtain the composition root (AppContainer) from the Application instance.
        // applicationContext is always available and never destroyed until process death,
        // so this is safe for retained ViewModels (Safeguard: ViewModel never holds
        // an Activity reference, only applicationContext-derived dependencies).
        val appContext = context.applicationContext
        val container = (appContext as PilcrowApplication).container

        // Obtain the ViewModel from the factory, which injects dependencies from the container.
        // viewModel() retains the instance across recomposition and Activity recreation
        // (device rotation, etc.), so open file/mode/scroll survive.
        val viewModel: MarkdownViewModel = viewModel(
            factory = MarkdownViewModel.provideFactory(container),
        )

        // Evaluate stranded WAL slots once per process start. Detection always
        // runs; the dialog auto-pops only on a launcher start with slots — never on a file open.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            viewModel.evaluateStrandedSlotsOnStart(openingFileFromIntent = launchedToOpenFile)
        }

        // Intent-opened files (cold start + warm intent while running).
        // The Activity owns intentUri as Compose state and passes it down. The app observes
        // it without needing to downcast the context to MainActivity.
        androidx.compose.runtime.LaunchedEffect(intentUri.value) {
            if (intentUri.value != null) {
                val uri = intentUri.value!!
                // NOTE: takePermission and openFromIntent on the ViewModel are the
                // public interface: takePermission persists the SAF read/write grant
                // for the URI so the file stays accessible across restarts, and
                // openFromIntent guards against losing unsaved edits in the current
                // file (Safeguard 1).
                viewModel.takePermission(uri)
                viewModel.openFromIntent(uri)
                onIntentConsumed()
            }
        }

        // Shared text (cold start + warm share sheet while running).
        androidx.compose.runtime.LaunchedEffect(intentSharedText.value) {
            val text = intentSharedText.value
            if (text != null) {
                viewModel.openSharedText(text)
                onSharedTextConsumed()
            }
        }

        // Main screen: toolbar + welcome/preview/editor content.
        // Thread the MarkwonRenderer to MainScreen so it doesn't construct one in the UI layer.
        MainScreen(
            viewModel = viewModel,
            context = context,
            renderer = container.markwonRenderer,
            // M-93: the banner and folder picker for a note's pictures.
            imageFolderViewModel = viewModel(factory = ImageFolderViewModel.provideFactory(container)),
        )
    }
}
