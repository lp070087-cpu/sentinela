package com.unitrix.sentinela

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.time.Instant

class LocationController(private val context: Context) {
    private var cancellation: CancellationSignal? = null
    private val handler = Handler(Looper.getMainLooper())
    fun permitted(): Boolean = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun capture(callback: (Location?, String) -> Unit) {
        cancel()
        val store = SecurityStore(context)
        if (store.demoActive || !permitted() || !store.locationConsent) { callback(null, "Localização não autorizada."); return }
        val manager = context.getSystemService(LocationManager::class.java)
        val precise = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val provider = when {
            precise && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) { callback(null, "Ative os serviços de localização do Android."); return }
        val signal = CancellationSignal(); cancellation = signal
        var completed = false
        handler.postDelayed({ if (!completed) { completed = true; signal.cancel(); callback(null, "Sem posição atual após 20 segundos. Última posição permanece identificada como antiga.") } }, 20000)
        try {
            manager.getCurrentLocation(provider, signal, context.mainExecutor) { position ->
                if (!completed) {
                    completed = true
                    if (store.demoActive || !store.locationConsent) { callback(null, "Captura cancelada: demonstração ou consentimento retirado."); return@getCurrentLocation }
                    if (position == null) callback(null, "Não foi possível obter posição atual. Tente ao ar livre.")
                    else {
                        store.lastLocation = "${position.latitude}, ${position.longitude} · ±${position.accuracy.toInt()} m · ${Instant.ofEpochMilli(position.time)}"
                        callback(position, store.lastLocation)
                    }
                }
            }
        } catch (e: SecurityException) { completed = true; callback(null, "Permissão de localização removida.") }
    }
    fun cancel() { cancellation?.cancel(); cancellation = null }
    companion object {
        fun payload(position: Location): JSONObject = JSONObject().put("latitude", position.latitude).put("longitude", position.longitude).put("accuracy", position.accuracy).put("capturedAt", Instant.ofEpochMilli(position.time).toString())
    }
}
