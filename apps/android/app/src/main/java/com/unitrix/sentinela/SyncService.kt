package com.unitrix.sentinela

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Explicit user-started foreground data sync. No microphone or continuous/background GPS. */
class SyncService : Service() {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private lateinit var store: SecurityStore
    private var audio: MediaPlayer? = null
    private var started = false
    override fun onCreate() { super.onCreate(); store = SecurityStore(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (store.demoActive || !store.remoteEnabled || store.token.isBlank()) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("sentinela_sync", "Sincronização autorizada", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val lock = PendingIntent.getBroadcast(this, 1, Intent(this, LockActionReceiver::class.java).setAction("com.unitrix.sentinela.LOCK"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(42, Notification.Builder(this, "sentinela_sync").setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Sentinela · sincronização ativa").setContentText("Comandos autorizados a cada 15 s. Microfone inativo.")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Bloquear agora", lock).build()).build())
        if (!started) { started = true; scheduler.scheduleWithFixedDelay({ sync() }, 0, 15, TimeUnit.SECONDS) }
        return START_NOT_STICKY
    }
    private fun sync() {
        if (store.demoActive || !store.remoteEnabled || store.token.isBlank()) { stopSelf(); return }
        try {
            val api = ApiClient(this)
            runCatching { api.flush() }
            val response = api.request("/api/device/commands")
            val commands = response.optJSONArray("commands") ?: return
            for (i in 0 until commands.length()) {
                if (store.demoActive || !store.remoteEnabled) break
                val command = commands.getJSONObject(i); val id = command.getString("id")
                val previousResult = store.commandResult(id)
                if (previousResult != null) {
                    api.request("/api/device/commands/$id/ack", "POST", previousResult)
                    continue
                }
                val type = command.getString("type")
                val outcome = when (type) {
                    "LOCK" -> { val result = ProtectionController(this).lock("REMOTE_LOCK"); result.lockObserved to result.message }
                    "LOST" -> {
                        store.state = ProtectionState.LOST
                        command.optJSONObject("payload")?.optString("message")?.takeIf { it.isNotBlank() }?.let { store.lostMessage = it }
                        val result = ProtectionController(this).lock("LOST")
                        api.event("LOST", "Modo perdido aplicado. Mensagem disponível no aplicativo; tela nativa preservada.", result.code)
                        true to ("Estado perdido aplicado. " + result.message + " Localização requer app aberto e consentimento.")
                    }
                    "RECOVER" -> {
                        if (store.state == ProtectionState.RESOLVED) true to "Emergência já encerrada. Bloqueio Android preservado."
                        else if (store.state !in setOf(ProtectionState.EMERGENCY, ProtectionState.LOST, ProtectionState.RECOVERING)) false to "Não há emergência Sentinela para encerrar; estado local preservado."
                        else {
                            store.state = ProtectionState.RECOVERING; api.event("RECOVERING", "Comando autenticado de recuperação recebido.")
                            store.state = ProtectionState.RESOLVED; stopAlarm()
                            api.event("RESOLVED", "Emergência Sentinela encerrada; PIN/bloqueio do Android preservado.")
                            true to "Proteção Sentinela encerrada. O bloqueio de segurança do Android não foi removido."
                        }
                    }
                    "ALARM" -> { val playing = startAlarm(); playing to if (playing) "Reprodução de alarme iniciada por comando autenticado; volume e DND dependem do sistema." else "O sistema não iniciou a reprodução do alarme." }
                    "STOP_ALARM" -> { stopAlarm(); true to "Alarme interrompido." }
                    else -> false to "Comando não suportado."
                }
                store.log("Remoto $type · ${outcome.second}")
                // Mark before ACK to avoid repeating physical actions on connection failure.
                val acknowledgement = JSONObject().put("status", if (outcome.first) "SUCCEEDED" else "FAILED").put("result", outcome.second)
                store.markCommandDone(id, acknowledgement)
                api.request("/api/device/commands/$id/ack", "POST", acknowledgement)
            }
            api.flush()
        } catch (e: Exception) { store.syncStatus = "Offline/erro: ${e.message?.take(150)}. Tentaremos novamente enquanto o serviço estiver ativo." }
    }
    private fun startAlarm(): Boolean {
        stopAlarm()
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        audio = MediaPlayer.create(this, sound)?.apply { isLooping = true; setVolume(.35f, .35f); start() }
        android.os.Handler(mainLooper).postDelayed({ stopAlarm() }, 30000)
        return audio?.isPlaying == true
    }
    private fun stopAlarm() { runCatching { audio?.stop() }; audio?.release(); audio = null }
    override fun onTimeout(startId: Int, fgsType: Int) { store.syncStatus = "Android encerrou a janela de sincronização em primeiro plano."; store.remoteEnabled = false; stopSelf() }
    override fun onDestroy() { store.remoteEnabled = false; store.syncStatus = "Serviço de sincronização inativo."; scheduler.shutdownNow(); stopAlarm(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
