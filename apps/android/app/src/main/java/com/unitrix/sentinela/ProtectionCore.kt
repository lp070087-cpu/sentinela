package com.unitrix.sentinela

import java.text.Normalizer
import java.util.Locale

enum class ProtectionState { DISABLED, ACTIVE, EMERGENCY, LOST, RECOVERING, RESOLVED }

object VoiceMatcher {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}".toRegex(), "").lowercase(Locale.ROOT)
        .replace("[^\\p{L}\\p{N} ]".toRegex(), " ").trim().replace("\\s+".toRegex(), " ")

    /** Strict matches avoid a short trigger inside an unrelated sentence. Phrase mode matches whole words. */
    fun matches(transcript: String, triggers: List<String>, phraseMode: Boolean): Boolean {
        val heard = normalize(transcript)
        if (heard.isBlank()) return false
        return triggers.any {
            val trigger = normalize(it)
            trigger.isNotBlank() && if (phraseMode) " $heard ".contains(" $trigger ") else heard == trigger
        }
    }
}

object ApiAddress {
    fun validate(value: String, debug: Boolean): String {
        val uri = java.net.URI(value.trim().trimEnd('/'))
        require(uri.userInfo == null && uri.query == null && uri.fragment == null && (uri.path.isNullOrEmpty() || uri.path == "/")) { "Informe somente a origem do servidor, sem credenciais ou caminho." }
        val host = uri.host ?: throw IllegalArgumentException("Endereço de servidor inválido.")
        val local = host in setOf("10.0.2.2", "localhost", "127.0.0.1")
        require(uri.scheme == "https" || (debug && uri.scheme == "http" && local)) { "Use HTTPS. HTTP debug apenas localhost/10.0.2.2 via adb reverse." }
        return value.trim().trimEnd('/')
    }
}

object VoiceGate {
    fun canTrigger(transcript: String, phrases: List<String>, phraseMode: Boolean, confidence: Float?, minimum: Float, userAuthorized: Boolean, protectionActive: Boolean): Boolean =
        userAuthorized && protectionActive && minimum in .7f..1f && confidence != null && confidence in minimum..1f && VoiceMatcher.matches(transcript, phrases, phraseMode)
}
