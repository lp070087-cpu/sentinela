package com.unitrix.sentinela

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int, message: String) : Exception(message)
class ApiClient(context: Context) {
    val store = SecurityStore(context)
    private var cookie: String = ""
    private var csrf: String = ""
    fun request(path: String, method: String = "GET", payload: JSONObject? = null, deviceAuth: Boolean = true, tokenOverride: String? = null): JSONObject {
        check(!store.demoActive) { "Demonstração isolada: comunicação com APIs reais pausada." }
        val base = ApiAddress.validate(store.server, BuildConfig.DEBUG)
        require(path.startsWith("/api/"))
        val conn = URL(base + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 10000; conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Origin", base)
        if (deviceAuth) { require((tokenOverride ?: store.token).isNotBlank()) { "Cadastre um dispositivo antes de sincronizar." }; conn.setRequestProperty("Authorization", "Bearer ${tokenOverride ?: store.token}") }
        else if (cookie.isNotBlank()) { conn.setRequestProperty("Cookie", cookie); if (csrf.isNotBlank()) conn.setRequestProperty("X-CSRF-Token", csrf) }
        try {
            if (payload != null) { conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json"); conn.outputStream.use { it.write(payload.toString().toByteArray()) } }
            val status = conn.responseCode
            conn.headerFields.filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }.values.flatten()
                .map { it.substringBefore(';') }.filter { it.startsWith("sentinela") }.takeIf { it.isNotEmpty() }?.let { cookie = it.joinToString("; ") }
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            val result = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                if (deviceAuth && tokenOverride == null && status == 401) { store.clearConnection(); store.syncStatus = "Token revogado. Cadastre o aparelho novamente." }
                throw ApiException(status, result.optString("error", "Falha HTTP $status").take(180))
            }
            return result
        } finally { conn.disconnect() }
    }
    fun connect(email: String, password: String, deviceName: String) {
        cookie = ""; csrf = ""
        try {
            val login = request("/api/auth/login", "POST", JSONObject().put("email", email).put("password", password), false)
            csrf = login.optString("csrfToken")
            if (csrf.isBlank()) csrf = request("/api/auth/me", deviceAuth = false).getString("csrfToken")
            val response = request("/api/devices/register", "POST", JSONObject().put("name", deviceName).put("platform", "ANDROID").put("model", android.os.Build.MODEL).put("osVersion", android.os.Build.VERSION.RELEASE), false)
            val token = response.getString("deviceToken")
            val id = response.getJSONObject("device").getString("id")
            store.token = token; store.deviceId = id
            store.log("Dispositivo cadastrado no servidor.")
            event("STATE_REGISTERED", "Estado local após cadastro autenticado do Android.")
            runCatching { flush() }
        } finally {
            if (cookie.isNotBlank() && csrf.isNotBlank()) runCatching { request("/api/auth/logout", "POST", JSONObject(), false) }
                .onFailure { store.log("Sessão temporária de cadastro não pôde ser encerrada no servidor. Revogue pelo painel de sessões.") }
            cookie = ""; csrf = "" // Session cookie is never persisted on the phone.
        }
    }
    fun pair(candidateServer: String, candidateToken: String) {
        val origin = ApiAddress.validate(candidateServer, BuildConfig.DEBUG)
        val previousOrigin = store.server
        // The origin is non-secret; candidate token stays in memory until validation succeeds.
        store.server = origin
        try {
            val device = request("/api/device/me", tokenOverride = candidateToken).getJSONObject("device")
            val id = device.getString("id")
            store.token = candidateToken; store.deviceId = id
            if (store.state !in setOf(ProtectionState.EMERGENCY, ProtectionState.LOST, ProtectionState.RECOVERING)) {
                runCatching { ProtectionState.valueOf(device.optString("state")) }.getOrNull()?.let { store.state = it }
            }
            store.log("Dispositivo existente pareado após validação autenticada do token.")
            event("STATE_REGISTERED", "Estado local após pareamento autenticado do Android.")
            runCatching { flush() }
        } catch (e: Exception) { store.server = previousOrigin; throw e }
    }
    fun event(type: String, details: String, lockResult: String? = null) {
        val payload = JSONObject().put("state", store.state.name).put("type", type).put("details", details.take(500)).put("eventId", java.util.UUID.randomUUID().toString())
        lockResult?.let { payload.put("lockResult", it) }
        store.enqueue("/api/device/events", payload)
    }
    fun flush() {
        if (store.token.isBlank()) return
        val queue = store.pending()
        for (i in 0 until queue.length()) {
            val item = queue.getJSONObject(i)
            if (item.getString("path") == "/api/device/location" && !store.locationConsent) {
                store.removePending(item.getString("id")); continue
            }
            try {
                request(item.getString("path"), "POST", item.getJSONObject("payload"))
                store.removePending(item.getString("id"))
            } catch (e: ApiException) {
                if (e.status in setOf(400, 404, 409, 422)) {
                    // Permanent validation/state errors must not poison the rest of the queue.
                    // Keep only redacted metadata locally; never log coordinates or full payload.
                    store.log("Registro local descartado da fila após rejeição permanente HTTP ${e.status}; endpoint ${item.getString("path")}. Verifique histórico do painel.")
                    store.removePending(item.getString("id"))
                } else throw e
            }
        }
        store.syncStatus = "Sincronizado em ${java.time.Instant.now()}"
    }
}
