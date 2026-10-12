// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 pleree

package com.pilcrowmd.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Controller for system Text-To-Speech (TTS) integration.
 *
 * Connects to the device's default TTS engine, sanitizes markdown into smooth, natural spoken text,
 * splits long documents into manageable chunks (honoring Android's speech input length limit),
 * and provides state flow to toggle between reading and stopping.
 */
class PilcrowTtsManager(private val context: Context) {

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null
    private var lastUtteranceId: String? = null

    private fun ensureTts(onReady: (TextToSpeech) -> Unit) {
        val existing = tts
        if (existing != null && isInitialized) {
            onReady(existing)
            return
        }

        if (existing == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    tts?.let { engine ->
                        try {
                            engine.language = Locale.getDefault()
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not set default locale for TTS: ${e.message}")
                        }
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _isSpeaking.value = true
                            }

                            override fun onDone(utteranceId: String?) {
                                if (utteranceId == lastUtteranceId) {
                                    _isSpeaking.value = false
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                if (utteranceId == lastUtteranceId) {
                                    _isSpeaking.value = false
                                }
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                if (utteranceId == lastUtteranceId) {
                                    _isSpeaking.value = false
                                }
                            }
                        })
                        pendingText?.let { text ->
                            pendingText = null
                            speakInternal(engine, text)
                        }
                    }
                } else {
                    Log.w(TAG, "TextToSpeech init failed with status: $status")
                    isInitialized = false
                    _isSpeaking.value = false
                    pendingText = null
                }
            }
        }
    }

    /**
     * Start reading the given raw Markdown content aloud using the device's default TTS player.
     * Sanitizes markdown into clean spoken text and queues chunks.
     */
    fun speak(rawMarkdown: String) {
        val clean = cleanMarkdownForSpeech(rawMarkdown)
        if (clean.isBlank()) return

        val existing = tts
        if (existing != null && isInitialized) {
            speakInternal(existing, clean)
        } else {
            pendingText = clean
            ensureTts { engine ->
                speakInternal(engine, clean)
            }
        }
    }

    private fun speakInternal(engine: TextToSpeech, text: String) {
        engine.stop()

        val chunks = splitIntoChunks(text)
        if (chunks.isEmpty()) {
            _isSpeaking.value = false
            return
        }

        val batchId = System.currentTimeMillis()
        lastUtteranceId = "pilcrow_tts_${batchId}_${chunks.lastIndex}"
        _isSpeaking.value = true

        chunks.forEachIndexed { index, chunk ->
            val utteranceId = "pilcrow_tts_${batchId}_$index"
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            engine.speak(chunk, queueMode, params, utteranceId)
        }
    }

    /**
     * Immediately stop speech playback.
     */
    fun stop() {
        pendingText = null
        lastUtteranceId = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS: ${e.message}")
        }
        _isSpeaking.value = false
    }

    /**
     * Release TTS resources when activity or view is disposed.
     */
    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "PilcrowTtsManager"
        private const val MAX_CHUNK_LENGTH = 2000

        /**
         * Cleans Markdown syntax so the TTS engine speaks natural sentences rather than symbols.
         */
        fun cleanMarkdownForSpeech(markdown: String): String {
            var text = markdown.replace("\r\n", "\n")

            // 1. Remove YAML / TOML frontmatter
            text = text.replace(Regex("^(---[\\s\\S]*?---\\s*|\\+\\+\\+[\\s\\S]*?\\+\\+\\+\\s*)"), "")

            // 2. Remove HTML comments
            text = text.replace(Regex("<!--[\\s\\S]*?-->"), "")

            // 3. Remove fenced code blocks
            text = text.replace(Regex("```[\\w-]*\\n[\\s\\S]*?```"), "\n")

            // 4. Remove HTML tags
            text = text.replace(Regex("<[^>]+>"), "")

            // 5. Replace markdown links [anchor](url) -> anchor
            text = text.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")

            // 6. Replace images ![alt](url) -> alt
            text = text.replace(Regex("!\\[([^\\]]*)\\]\\([^)]+\\)"), "$1")

            // 7. Strip table delimiter lines |---|---| and convert remaining pipes to pauses/commas
            text = text.replace(Regex("(?m)^\\|?\\s*[-:]+[-| :]*\\|?\\s*$"), "")
            text = text.replace("|", ", ")

            // 8. Math expressions
            text = text.replace(Regex("\\$\\$([^$]+)\\$\\$"), "$1")
            text = text.replace(Regex("(?<!\\$)\\$([^$]+)\\$(?!\\$)"), "$1")

            // 9. Strip headings markers: ### Title -> Title
            text = text.replace(Regex("(?m)^#{1,6}\\s+"), "")

            // 10. Strip blockquote markers: > Quote -> Quote
            text = text.replace(Regex("(?m)^>+\\s*"), "")

            // 11. Strip bullet points and list markers: - item, * item, + item
            text = text.replace(Regex("(?m)^[\\*\\-\\+]\\s+"), "")
            text = text.replace(Regex("(?m)^\\d+\\.\\s+"), "")

            // 12. Strip bold, italic, strikethrough, inline code
            text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            text = text.replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)"), "$1")
            text = text.replace(Regex("__([^_]+)__"), "$1")
            text = text.replace(Regex("(?<!_)_([^_]+)_(?!_)"), "$1")
            text = text.replace(Regex("~~([^~]+)~~"), "$1")
            text = text.replace(Regex("`([^`]+)`"), "$1")

            // 13. Remove horizontal rules
            text = text.replace(Regex("(?m)^[-*_]{3,}\\s*$"), "")

            // 14. Collapse extra whitespace and empty lines
            text = text.replace(Regex("[ \\t]+"), " ")
            text = text.replace(Regex("\\n{3,}"), "\n\n")

            return text.trim()
        }

        /**
         * Splits text into chunks smaller than [maxChunkSize], respecting paragraph and sentence boundaries.
         */
        fun splitIntoChunks(text: String, maxChunkSize: Int = MAX_CHUNK_LENGTH): List<String> {
            if (text.length <= maxChunkSize) return listOf(text)
            val chunks = mutableListOf<String>()
            val paragraphs = text.split("\n\n")
            val currentChunk = StringBuilder()

            for (para in paragraphs) {
                val trimmedPara = para.trim()
                if (trimmedPara.isEmpty()) continue

                if (currentChunk.length + trimmedPara.length + 2 <= maxChunkSize) {
                    if (currentChunk.isNotEmpty()) currentChunk.append("\n\n")
                    currentChunk.append(trimmedPara)
                } else {
                    if (currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString())
                        currentChunk.clear()
                    }
                    if (trimmedPara.length > maxChunkSize) {
                        val sentences = trimmedPara.split(Regex("(?<=[.!?])\\s+"))
                        for (sentence in sentences) {
                            if (currentChunk.length + sentence.length + 1 <= maxChunkSize) {
                                if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                                currentChunk.append(sentence)
                            } else {
                                if (currentChunk.isNotEmpty()) {
                                    chunks.add(currentChunk.toString())
                                    currentChunk.clear()
                                }
                                var remaining = sentence
                                while (remaining.length > maxChunkSize) {
                                    chunks.add(remaining.take(maxChunkSize))
                                    remaining = remaining.drop(maxChunkSize)
                                }
                                currentChunk.append(remaining)
                            }
                        }
                    } else {
                        currentChunk.append(trimmedPara)
                    }
                }
            }
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
            }
            return chunks.filter { it.isNotBlank() }
        }
    }
}
