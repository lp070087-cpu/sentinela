package com.unitrix.sentinela

import org.junit.Assert.*
import org.junit.Test

class ProtectionCoreTest {
    @Test fun exactModeRejectsIncidentalSentence() {
        assertFalse(VoiceMatcher.matches("li uma notícia sobre assalto ontem", listOf("assalto"), false))
        assertTrue(VoiceMatcher.matches("ASSALTO!", listOf("assalto"), false))
    }
    @Test fun phraseModeUsesWholeWordBoundaries() {
        assertFalse(VoiceMatcher.matches("assaltou", listOf("assalto"), true))
        assertTrue(VoiceMatcher.matches("agora é minha frase secreta aqui", listOf("minha frase secreta"), true))
    }
    @Test fun whitespaceAccentsAndEmptyAreHandled() {
        assertTrue(VoiceMatcher.matches("  emergência   agora! ", listOf("emergencia agora"), false))
        assertFalse(VoiceMatcher.matches("", listOf(""), true))
    }
    @Test fun confidenceAndConsentGateNeverDefaultsToBlocking() {
        for (threshold in listOf(.7f, .8f, .9f, 1f)) {
            assertTrue(VoiceGate.canTrigger("assalto", listOf("assalto"), false, threshold, threshold, true, true))
            assertFalse(VoiceGate.canTrigger("assalto", listOf("assalto"), false, threshold - .01f, threshold, true, true))
            assertFalse(VoiceGate.canTrigger("assalto", listOf("assalto"), false, null, threshold, true, true))
            assertFalse(VoiceGate.canTrigger("assalto", listOf("assalto"), false, 1f, threshold, false, true))
            assertFalse(VoiceGate.canTrigger("assalto", listOf("assalto"), false, 1f, threshold, true, false))
            assertFalse(VoiceGate.canTrigger("conversando sobre assalto", listOf("assalto"), false, 1f, threshold, true, true))
        }
        assertFalse(VoiceGate.canTrigger("assalto", listOf("assalto"), false, Float.NaN, .8f, true, true))
    }
    @Test fun productionRejectsCleartextAndCredentials() {
        assertThrows(IllegalArgumentException::class.java) { ApiAddress.validate("http://10.0.2.2:4173", false) }
        assertThrows(IllegalArgumentException::class.java) { ApiAddress.validate("https://user:pass@example.com", true) }
        assertThrows(IllegalArgumentException::class.java) { ApiAddress.validate("https://example.com/path", true) }
        assertEquals("https://example.com", ApiAddress.validate("https://example.com/", false))
    }
    @Test fun debugAllowsOnlyLocalCleartext() {
        assertEquals("http://10.0.2.2:4173", ApiAddress.validate("http://10.0.2.2:4173", true))
        assertThrows(IllegalArgumentException::class.java) { ApiAddress.validate("http://192.168.1.20:4173", true) }
    }
}
