package com.unitrix.sentinela

import android.app.KeyguardManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.pm.PackageManager

class SentinelaAdminReceiver : DeviceAdminReceiver() {
    override fun onDisabled(context: Context, intent: Intent) {
        SecurityStore(context).apply { if (state !in setOf(ProtectionState.EMERGENCY, ProtectionState.LOST, ProtectionState.RECOVERING)) state = ProtectionState.DISABLED; log("Administração removida. Bloqueio nativo indisponível; emergência em andamento preservada.") }
        runCatching { ApiClient(context).event("ADMIN_DISABLED", "Administração removida; bloqueio indisponível. Estado de emergência preservado.") }
    }
}

data class LockOutcome(val apiAccepted: Boolean, val lockObserved: Boolean, val code: String, val message: String)

class ProtectionController(private val context: Context) {
    private val manager = context.getSystemService(DevicePolicyManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val store = SecurityStore(context)
    val admin: ComponentName get() = ComponentName(context, SentinelaAdminReceiver::class.java)
    fun authorized(): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN) && manager.isAdminActive(admin)
    fun screenSecure(): Boolean = keyguard.isDeviceSecure
    fun authorizationIntent(): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Sentinela solicita apenas bloquear a tela quando você acionar proteção ou autorizar comandos remotos. Não solicita apagar dados ou alterar sua senha.")
    /** Run off the main thread. API completion and observed lock are deliberately separate outcomes. */
    fun lock(type: String): LockOutcome {
        if (store.demoActive) return LockOutcome(false, false, "DEMO_ISOLATED", "Demonstração isolada: bloqueio nativo não executado.")
        // Detecting an emergency and actually locking Android are separate persisted facts.
        if (type == "LOST" || store.state == ProtectionState.LOST) store.state = ProtectionState.LOST
        else if (store.state != ProtectionState.RECOVERING) store.state = ProtectionState.EMERGENCY
        if (!authorized()) return failure(type, "ADMIN_REQUIRED", "Emergência registrada. Autorize a administração do dispositivo para bloquear a tela.")
        if (!screenSecure()) return failure(type, "SECURE_SCREEN_REQUIRED", "Emergência registrada. Configure PIN, padrão ou senha no Android antes de testar o bloqueio.")
        return try {
            manager.lockNow()
            Thread.sleep(700)
            val observed = keyguard.isDeviceLocked
            val outcome = LockOutcome(true, observed, if (observed) "LOCK_OBSERVED" else "API_ACCEPTED_UNCONFIRMED", if (observed) "Android informou bloqueio da tela." else "Android aceitou lockNow; bloqueio ainda não confirmado. Verifique o aparelho.")
            if (store.state != ProtectionState.LOST && store.state != ProtectionState.RECOVERING) store.state = ProtectionState.EMERGENCY
            store.log(outcome.message)
            runCatching { ApiClient(context).event(type, outcome.message, outcome.code) }.onFailure { store.log("Evento aguardando espaço na fila local.") }
            outcome
        } catch (e: Exception) {
            failure(type, "LOCK_FAILED", "O Android recusou o bloqueio: ${e.javaClass.simpleName}. Emergência preservada.")
        }
    }
    private fun failure(type: String, code: String, message: String): LockOutcome {
        store.log(message)
        runCatching { ApiClient(context).event(type, message, code) }.onFailure { store.log("Falha ao enfileirar evento de emergência; sincronize a fila local.") }
        return LockOutcome(false, false, code, message)
    }
}

class LockActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.unitrix.sentinela.LOCK") return
        val pending = goAsync()
        Thread {
            try { ProtectionController(context).lock("NOTIFICATION_TRIGGER"); runCatching { ApiClient(context).flush() } }
            finally { pending.finish() }
        }.start()
    }
}
