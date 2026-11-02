package com.unitrix.sentinela

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityStore(context: Context) {
    private val preferences = context.getSharedPreferences("sentinela_private", Context.MODE_PRIVATE)
    private val lock = Companion
    var server: String
        get() = preferences.getString("server", if (BuildConfig.DEBUG) "http://10.0.2.2:4173" else "") ?: ""
        set(value) { preferences.edit().putString("server", value).apply() }
    var deviceId: String
        get() = preferences.getString("deviceId", "") ?: ""
        set(value) { preferences.edit().putString("deviceId", value).apply() }
    var state: ProtectionState
        get() = runCatching { ProtectionState.valueOf(preferences.getString("state", "DISABLED")!!) }.getOrDefault(ProtectionState.DISABLED)
        set(value) { preferences.edit().putString("state", value.name).apply() }
    var demoActive: Boolean
        get() = preferences.getBoolean("demoActive", false)
        set(value) { preferences.edit().putBoolean("demoActive", value).commit() }
    var remoteEnabled: Boolean
        get() = preferences.getBoolean("remoteEnabled", false)
        set(value) { preferences.edit().putBoolean("remoteEnabled", value).apply() }
    var locationConsent: Boolean
        get() = preferences.getBoolean("locationConsent", false)
        set(value) { preferences.edit().putBoolean("locationConsent", value).apply() }
    var phraseMode: Boolean
        get() = preferences.getBoolean("phraseMode", false)
        set(value) { preferences.edit().putBoolean("phraseMode", value).apply() }
    var voiceConfidence: Float
        get() = preferences.getFloat("voiceConfidence", .8f).let { if (it.isFinite()) it.coerceIn(.7f, 1f) else .8f }
        set(value) { if (value.isFinite()) preferences.edit().putFloat("voiceConfidence", value.coerceIn(.7f, 1f)).apply() }
    var phrases: List<String>
        get() = runCatching { val arr = JSONArray(preferences.getString("phrases", "[\"assalto\",\"socorro\"]") ?: "[]"); (0 until arr.length()).map(arr::getString) }.getOrDefault(listOf("assalto", "socorro"))
        set(value) { preferences.edit().putString("phrases", JSONArray(value).toString()).apply() }
    var lastLocation: String
        get() = preferences.getString("location", "Nenhuma posição capturada.") ?: ""
        set(value) { preferences.edit().putString("location", value).apply() }
    var lostMessage: String
        get() = preferences.getString("lostMessage", "Este celular foi marcado como perdido. Use o contato informado pelo proprietário no painel.") ?: ""
        set(value) { preferences.edit().putString("lostMessage", value.take(300)).apply() }
    var syncStatus: String
        get() = preferences.getString("syncStatus", "Sem sincronização.") ?: ""
        set(value) { preferences.edit().putString("syncStatus", value).apply() }
    var token: String
        get() = runCatching {
            val value = preferences.getString("token", null) ?: return ""
            val arr = value.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(arr[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(arr[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrElse {
            // A lost/replaced Keystore key must never let old queued telemetry follow a new account.
            preferences.edit().remove("token").remove("deviceId").remove("queue").putBoolean("remoteEnabled", false).apply()
            ""
        }
        set(value) {
            if (value.isEmpty()) { preferences.edit().remove("token").apply(); return }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            preferences.edit().putString("token", encoded).apply()
        }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("sentinela_device_token", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("sentinela_device_token", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun clearConnection() { token = ""; deviceId = ""; remoteEnabled = false; locationConsent = false; preferences.edit().remove("queue").apply() }
    fun log(message: String) = synchronized(lock) {
        val arr = JSONArray(preferences.getString("logs", "[]") ?: "[]")
        val new = JSONArray().put("${Instant.now()} · ${message.take(300)}")
        for (i in 0 until minOf(arr.length(), 49)) new.put(arr.get(i))
        preferences.edit().putString("logs", new.toString()).apply()
    }
    fun logs(): List<String> = synchronized(lock) {
        val arr = JSONArray(preferences.getString("logs", "[]") ?: "[]"); (0 until arr.length()).map(arr::getString)
    }
    fun enqueue(path: String, payload: JSONObject) = synchronized(lock) {
        val arr = pending(); require(arr.length() < 200) { "Fila local cheia. Sincronize antes de registrar novos dados." }
        arr.put(JSONObject().put("id", UUID.randomUUID().toString()).put("path", path).put("payload", payload))
        preferences.edit().putString("queue", arr.toString()).commit()
    }
    fun pending(): JSONArray = synchronized(lock) { JSONArray(preferences.getString("queue", "[]") ?: "[]") }
    fun removePending(id: String) = synchronized(lock) {
        val current = pending(); val next = JSONArray()
        for (i in 0 until current.length()) if (current.getJSONObject(i).getString("id") != id) next.put(current.get(i))
        preferences.edit().putString("queue", next.toString()).commit()
    }
    fun purgePendingLocations() = synchronized(lock) {
        val next = JSONArray(); val queue = pending()
        for (i in 0 until queue.length()) if (queue.getJSONObject(i).getString("path") != "/api/device/location") next.put(queue.get(i))
        preferences.edit().putString("queue", next.toString()).commit()
    }
    fun clearLocalHistory() = synchronized(lock) { preferences.edit().remove("queue").remove("logs").remove("location").apply() }
    fun commandResult(id: String): JSONObject? = synchronized(lock) {
        val done = JSONObject(preferences.getString("commandResults", "{}") ?: "{}")
        done.optJSONObject(id)
    }
    fun markCommandDone(id: String, result: JSONObject) = synchronized(lock) {
        val done = JSONObject(preferences.getString("commandResults", "{}") ?: "{}")
        done.put(id, result)
        if (done.length() > 100) done.keys().asSequence().firstOrNull()?.let { done.remove(it) }
        preferences.edit().putString("commandResults", done.toString()).commit()
    }
    companion object {}
}
