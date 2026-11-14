package com.antigravity.deck.hardware

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.antigravity.deck.i18n.AppLanguage
import com.antigravity.deck.i18n.DeckI18n
import com.antigravity.deck.i18n.DeckStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class SpeechUiState(
    val isListening: Boolean = false,
    val isFinalizing: Boolean = false,
    val partialText: String = "",
    val statusHint: String? = null
)

// Android標準SpeechRecognizerを使ったPush-to-Talk音声認識マネージャー
class PushToTalkManager(
    context: Context,
    private val languageProvider: () -> AppLanguage = { AppLanguage.SYSTEM },
    private val onTranscriptReady: (String) -> Unit
) {
    private fun strings(): DeckStrings = DeckI18n.strings(languageProvider())
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(SpeechUiState())
    val state: StateFlow<SpeechUiState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var isPressed = false
    private var pendingCommitOnResult = false
    private var latestPartial = ""
    private var bufferedFinal: String? = null
    private var commitTimeoutRunnable: Runnable? = null

    fun onPressStart() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _state.update {
                    it.copy(
                        isListening = false,
                        isFinalizing = false,
                        statusHint = strings().speechUnavailable
                    )
                }
                return@post
            }

            cancelPendingTimeout()
            isPressed = true
            pendingCommitOnResult = false
            latestPartial = ""
            bufferedFinal = null

            ensureRecognizer().apply {
                val speechTag = languageProvider().speechLocaleTag()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                _state.value = SpeechUiState(
                    isListening = true,
                    isFinalizing = false,
                    partialText = "",
                    statusHint = strings().speechListeningHint
                )
                runCatching { startListening(intent) }.onFailure { err ->
                    isPressed = false
                    _state.value = SpeechUiState(
                        isListening = false,
                        statusHint = strings().speechStartFailed(err.message ?: "")
                    )
                }
            }
        }
    }

    fun onPressRelease() {
        mainHandler.post {
            if (!isPressed) {
                return@post
            }
            isPressed = false

            // 押している最中に確定結果がすでに届いていた場合は即座に送信する
            val ready = bufferedFinal?.trim().orEmpty()
            if (ready.isNotEmpty()) {
                bufferedFinal = null
                commitAndReset(ready)
                return@post
            }

            pendingCommitOnResult = true
            _state.update {
                it.copy(
                    isListening = false,
                    isFinalizing = true,
                    statusHint = strings().speechFinalizing
                )
            }

            runCatching { recognizer?.stopListening() }

            // 認識サービスの応答が遅延した場合のタイムアウト救済処理
            val timeoutTask = Runnable {
                if (pendingCommitOnResult) {
                    val fallback = latestPartial.trim()
                    pendingCommitOnResult = false
                    if (fallback.isNotEmpty()) {
                        commitAndReset(fallback)
                    } else {
                        runCatching { recognizer?.cancel() }
                        _state.value = SpeechUiState(statusHint = strings().speechNoAudio)
                    }
                }
            }
            commitTimeoutRunnable = timeoutTask
            mainHandler.postDelayed(timeoutTask, 2200L)
        }
    }

    fun release() {
        mainHandler.post {
            cancelPendingTimeout()
            isPressed = false
            pendingCommitOnResult = false
            runCatching {
                recognizer?.cancel()
                recognizer?.destroy()
            }
            recognizer = null
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer {
        val current = recognizer
        if (current != null) {
            return current
        }

        val created = SpeechRecognizer.createSpeechRecognizer(appContext)
        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val text = extractTopTranscript(partialResults)
                if (text.isNotEmpty()) {
                    latestPartial = text
                    _state.update { it.copy(partialText = text) }
                }
            }

            override fun onResults(results: Bundle?) {
                cancelPendingTimeout()
                val text = extractTopTranscript(results).ifEmpty { latestPartial.trim() }
                if (pendingCommitOnResult) {
                    pendingCommitOnResult = false
                    if (text.isNotEmpty()) {
                        commitAndReset(text)
                    } else {
                        _state.value = SpeechUiState(statusHint = strings().speechNotRecognized)
                    }
                } else if (isPressed) {
                    bufferedFinal = text
                    _state.update { it.copy(partialText = text) }
                }
            }

            override fun onError(error: Int) {
                cancelPendingTimeout()
                val fallback = latestPartial.trim()
                if (pendingCommitOnResult && fallback.isNotEmpty()) {
                    pendingCommitOnResult = false
                    commitAndReset(fallback)
                    return
                }
                isPressed = false
                pendingCommitOnResult = false
                val s = strings()
                val msg = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> s.speechPermissionMissing
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> s.speechNotRecognized
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> s.speechBusy
                    else -> s.speechErrorCode(error)
                }
                _state.value = SpeechUiState(statusHint = msg)
            }
        })
        recognizer = created
        return created
    }

    private fun extractTopTranscript(bundle: Bundle?): String {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull()?.trim().orEmpty()
    }

    private fun commitAndReset(transcript: String) {
        cancelPendingTimeout()
        latestPartial = ""
        bufferedFinal = null
        _state.value = SpeechUiState(
            isListening = false,
            isFinalizing = false,
            partialText = "",
            statusHint = strings().speechSent(transcript)
        )
        onTranscriptReady(transcript)
    }

    private fun cancelPendingTimeout() {
        commitTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        commitTimeoutRunnable = null
    }
}
