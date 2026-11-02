package com.unitrix.sentinela

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** One local recognition session, while Activity is visible. Never restarts in the background. */
class VoiceController(private val context: Context, private val status: (String, Boolean) -> Unit, private val result: (String, Float?) -> Unit) : RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    fun available(): Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    fun start() {
        stop()
        if (!available()) { status("Reconhecimento local indisponível neste aparelho. Nenhum áudio será enviado para nuvem.", false); return }
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply { setRecognitionListener(this@VoiceController) }
            status("Preparando reconhecimento local…", true)
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (e: Exception) { stop(); status("Não foi possível iniciar o reconhecimento local (${e.javaClass.simpleName}).", false) }
    }
    fun stop() { recognizer?.cancel(); recognizer?.destroy(); recognizer = null; status("Microfone inativo.", false) }
    override fun onReadyForSpeech(params: Bundle?) { status("Microfone ativo · sessão local única. Diga a frase.", true) }
    override fun onBeginningOfSpeech() { status("Ouvindo nesta sessão…", true) }
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { status("Processando localmente; microfone encerrou a captura.", false) }
    override fun onError(error: Int) { status("Monitoramento inativo. Serviço encerrou a sessão (código $error).", false) }
    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()?.takeIf { it >= 0 }
        status("Sessão encerrada; microfone inativo.", false)
        result(text, confidence)
    }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
