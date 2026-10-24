// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

interface DictationSession { fun stop(); fun cancel() }
fun interface DictationProvider {
    fun start(language: String, result: (String) -> Unit, error: () -> Unit): DictationSession
}

/** RecognitionService fallback for devices without a recognition Activity. Main-thread API. */
class SystemDictation(private val context: Context) : DictationProvider {
    override fun start(language: String, result: (String) -> Unit, error: () -> Unit): DictationSession {
        check(SpeechRecognizer.isRecognitionAvailable(context))
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var closed = false
        fun close() { if (!closed) { closed = true; recognizer.destroy() } }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onError(code: Int) { if (!closed) { close(); error() } }
            override fun onResults(results: Bundle?) {
                if (closed) return
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                close()
                if (text.isBlank()) error() else result(text)
            }
        })
        try {
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        } catch (failure: Exception) { close(); throw failure }
        return object : DictationSession {
            override fun stop() { if (!closed) recognizer.stopListening() }
            override fun cancel() { if (!closed) { recognizer.cancel(); close() } }
        }
    }
}
