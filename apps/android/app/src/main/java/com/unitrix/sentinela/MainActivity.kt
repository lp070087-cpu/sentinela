package com.unitrix.sentinela

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executors

private val Paper = Color(0xFFFAFAF8)
private val Green = Color(0xFF426E60)
private val Ink = Color(0xFF182A30)
private val Muted = Color(0xFF68767A)

class MainActivity : FragmentActivity() {
    private lateinit var store: SecurityStore
    private lateinit var protector: ProtectionController
    private lateinit var location: LocationController
    private lateinit var voice: VoiceController
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var update by mutableIntStateOf(0)
    private var feedback by mutableStateOf("")
    private var listening by mutableStateOf(false)
    private var voiceStatus by mutableStateOf("Microfone inativo.")
    private var transcript by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var pendingPermission = ""
    private var allowVoiceLock by mutableStateOf(false)
    private var minimumConfidence by mutableFloatStateOf(.8f)
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        when (pendingPermission) {
            "voice" -> if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) voice.start() else feedback = "Microfone não autorizado."
            "location" -> if (location.permitted()) captureLocation() else feedback = "Localização não autorizada."
            "notifications" -> startSync()
        }
        pendingPermission = ""; refresh()
    }
    private val authorizeAdmin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh(); feedback = if (protector.authorized()) "Administração autorizada. Apenas force-lock foi solicitado." else "Administração não autorizada." }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecurityStore(this); protector = ProtectionController(this); location = LocationController(this)
        voice = VoiceController(this, { message, active -> voiceStatus = message; listening = active }, { text, confidence ->
            transcript = if (text.isBlank()) "Nenhuma frase reconhecida." else "Reconhecido localmente: $text"
            val match = VoiceMatcher.matches(text, store.phrases, store.phraseMode)
            val confidencePassed = confidence != null && confidence >= minimumConfidence
            if (VoiceGate.canTrigger(text, store.phrases, store.phraseMode, confidence, minimumConfidence, allowVoiceLock, store.state == ProtectionState.ACTIVE)) {
                runTask { protector.lock("VOICE_TRIGGER").message.also { runCatching { ApiClient(this).flush() } } }
            } else if (match) {
                feedback = when { !allowVoiceLock -> "Frase corresponde. Modo de teste: nenhum bloqueio foi acionado."
                    !confidencePassed -> "Frase corresponde, mas confiança ausente/baixa. Bloqueio não acionado."
                    else -> "Frase corresponde. Ative a proteção antes do bloqueio por voz." }
                store.log("Teste de frase reconhecida localmente; nenhum áudio ou transcrição enviado.")
            } else feedback = "A frase não corresponde aos comandos configurados. Nenhuma ação."
        })
        setContent { SentinelaApp() }
    }
    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) {
            store.demoActive = false
            @Suppress("DEPRECATION")
            val running = getSystemService(android.app.ActivityManager::class.java).getRunningServices(100).any { it.service.className == SyncService::class.java.name }
            if (store.remoteEnabled && !running) { store.remoteEnabled = false; store.syncStatus = "Serviço inativo. O Android pode tê-lo encerrado; reative explicitamente." }
            refresh()
        }
    }
    override fun onStop() { if (::voice.isInitialized) voice.stop(); if (::location.isInitialized) location.cancel(); allowVoiceLock = false; super.onStop() }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
    private fun refresh() { update++ }
    private fun runTask(task: () -> String) {
        if (busy) return
        busy = true
        worker.execute {
            val result = runCatching(task).getOrElse { it.message ?: "Operação falhou." }
            handler.post { feedback = result; busy = false; refresh() }
        }
    }
    private fun captureLocation() {
        location.capture { position, message ->
            feedback = message; refresh()
            if (position != null) runTask {
                val api = ApiClient(this)
                api.store.enqueue("/api/device/location", LocationController.payload(position))
                if (store.token.isNotBlank()) { api.flush(); "Posição sincronizada com o servidor autorizado." } else "Posição capturada. Cadastre o aparelho para sincronizar."
            }
        }
    }
    private fun startSync() {
        if (store.token.isBlank()) { feedback = "Cadastre o dispositivo antes de ativar comandos remotos."; return }
        store.remoteEnabled = true
        runCatching { startForegroundService(Intent(this, SyncService::class.java)) }.onFailure { store.remoteEnabled = false; feedback = "Android recusou iniciar o serviço. Abra o app e tente novamente." }
        refresh()
    }
    private fun recoverWithBiometrics() {
        if (store.state !in setOf(ProtectionState.EMERGENCY, ProtectionState.LOST, ProtectionState.RECOVERING)) {
            feedback = if (store.state == ProtectionState.RESOLVED) "Emergência já encerrada." else "Não há emergência Sentinela para encerrar."
            return
        }
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            feedback = "Configure biometria forte ou credencial de bloqueio no Android."; return
        }
        BiometricPrompt(this, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                store.state = ProtectionState.RESOLVED
                runTask {
                    val api = ApiClient(this@MainActivity)
                    api.event("RESOLVED", "Emergência Sentinela encerrada após autenticação nativa. Bloqueio Android preservado.")
                    runCatching { api.flush() }
                    "Estado de emergência encerrado. PIN e bloqueio do Android permanecem intactos."
                }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { feedback = "Recuperação não concluída: $errString" }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Encerrar emergência Sentinela")
            .setSubtitle("Não remove PIN, senha ou bloqueio do Android.").setAllowedAuthenticators(authenticators).build())
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun SentinelaApp() {
        val tick = update
        var tab by remember { mutableStateOf("Proteção") }
        var server by remember { mutableStateOf(store.server) }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var pairingToken by remember { mutableStateOf("") }
        var deviceName by remember { mutableStateOf("Meu Android") }
        var phrases by remember { mutableStateOf(store.phrases.joinToString("\n")) }
        var consentLocation by remember(tick) { mutableStateOf(store.locationConsent) }
        var phraseMode by remember(tick) { mutableStateOf(store.phraseMode) }
        var confirmLock by remember { mutableStateOf(false) }
        var confirmForget by remember { mutableStateOf(false) }
        val colors = lightColorScheme(primary = Green, onPrimary = Color.White, background = Paper, surface = Color.White, onSurface = Ink, secondary = Muted)
        MaterialTheme(colorScheme = colors, typography = sentinelaTypography()) {
            Scaffold(containerColor = Paper, topBar = { TopAppBar(title = { Text("SENTINELA", style = MaterialTheme.typography.titleMedium) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper), actions = { Text("DEV · NATIVO", color = Green, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp)) }) }) { padding ->
                Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Text("Seu celular.\nSeu controle.", fontFamily = SentinelaEditorialFont, style = MaterialTheme.typography.headlineLarge, color = Ink)
                    Text("Aplicativo nativo de desenvolvimento. As ações abaixo podem operar neste Android após sua autorização.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Button(enabled = !busy, onClick = {
                        store.demoActive = true
                        voice.stop(); location.cancel(); allowVoiceLock = false
                        store.remoteEnabled = false
                        stopService(Intent(this@MainActivity, SyncService::class.java))
                        feedback = "Área real pausada para explorar a demonstração. Emergência existente preservada."
                        startActivity(Intent(this@MainActivity, DemoActivity::class.java))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Entrar na demonstração") }
                    Text("Sem conta ou permissões: dados fictícios, sem GPS, microfone, bloqueio ou alertas reais. Sua área nativa permanece separada.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Proteção", "Voz", "Conexão").forEach { title -> FilterChip(selected = tab == title, onClick = { tab = title }, label = { Text(title) }) }
                    }
                    if (feedback.isNotBlank()) Surface(color = Color(0xFFE4F0E9), shape = RoundedCornerShape(10.dp)) { Text(feedback, color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    when (tab) {
                        "Proteção" -> {
                            Section("Estado do aparelho") {
                                Text(stateLabel(store.state), style = MaterialTheme.typography.headlineSmall, color = Green)
                                Text("Administração: ${if (protector.authorized()) "autorizada" else "ausente"}\nBloqueio seguro Android: ${if (protector.screenSecure()) "configurado" else "configure PIN, senha ou padrão"}", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { authorizeAdmin.launch(protector.authorizationIntent()) }, modifier = Modifier.fillMaxWidth()) { Text("Autorizar bloqueio no Android") }
                                Button(enabled = protector.authorized() && protector.screenSecure() && !busy, onClick = { confirmLock = true }, modifier = Modifier.fillMaxWidth()) { Text("Bloquear este Android agora") }
                                OutlinedButton(onClick = {
                                    if (store.state in setOf(ProtectionState.EMERGENCY, ProtectionState.LOST, ProtectionState.RECOVERING)) recoverWithBiometrics()
                                    else { store.state = if (store.state == ProtectionState.ACTIVE) ProtectionState.DISABLED else ProtectionState.ACTIVE; store.log("Estado local: ${store.state}"); refresh(); runTask { val api = ApiClient(this@MainActivity); api.event("PROTECTION_STATE", "Estado de proteção alterado pelo usuário no aplicativo."); if (store.token.isBlank()) "Estado salvo localmente. Cadastre o aparelho para sincronizar." else runCatching { api.flush() }.fold({ "Estado salvo e sincronizado." }, { "Estado salvo localmente; sincronização pendente." }) } }
                                }, modifier = Modifier.fillMaxWidth()) { Text(if (store.state == ProtectionState.ACTIVE) "Desativar proteção" else if (store.state in setOf(ProtectionState.EMERGENCY, ProtectionState.LOST, ProtectionState.RECOVERING)) "Recuperar com autenticação nativa" else "Ativar proteção") }
                                Text("Não impede desligamento, não intercepta SOS e não remove o bloqueio de segurança do sistema.", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            if (store.state == ProtectionState.LOST) Section("Aparelho marcado como perdido") { Text(store.lostMessage); Text("Mensagem exibida apenas no aplicativo. Tela de bloqueio nativa preservada.", style = MaterialTheme.typography.bodySmall) }
                            Section("Localização autorizada") {
                                Row { Checkbox(checked = consentLocation, onCheckedChange = { consentLocation = it; store.locationConsent = it; if (!it) { location.cancel(); store.purgePendingLocations(); feedback = "Consentimento retirado. Posições pendentes excluídas; dados já enviados devem ser excluídos no painel." } }); Text("Permitir capturar e sincronizar minha posição ao tocar no botão.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)) }
                                Text(store.lastLocation, style = MaterialTheme.typography.bodySmall, color = Muted)
                                Button(enabled = consentLocation && !busy, onClick = { pendingPermission = "location"; permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, modifier = Modifier.fillMaxWidth()) { Text("Capturar posição atual") }
                                Text("Sem GPS permanente. Ao desligar, o painel mantém a última posição. A rede Buscar/Find Hub é separada e depende do aparelho.", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            Section("Histórico deste Android") { store.logs().take(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted); HorizontalDivider() }; OutlinedButton(onClick = { store.clearLocalHistory(); refresh(); feedback = "Histórico e posições locais excluídos. Dados já sincronizados devem ser excluídos pelo painel." }) { Text("Excluir histórico local") } }
                        }
                        "Voz" -> {
                            Section("Sua frase. Seu acionamento.") {
                                Text("${if (voice.available()) "Reconhecimento local disponível" else "Reconhecimento local indisponível"}. Apenas uma sessão com aplicativo visível. Nenhum áudio é gravado ou enviado pelo Sentinela.", style = MaterialTheme.typography.bodySmall, color = Muted)
                                OutlinedTextField(value = phrases, onValueChange = { phrases = it.take(400) }, label = { Text("Uma frase por linha") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                                Button(onClick = { val items = phrases.lines().map(String::trim).filter { it.length in 3..60 }.distinct().take(8); if (items.isEmpty()) feedback = "Adicione pelo menos uma frase de 3 a 60 caracteres." else { store.phrases = items; phrases = items.joinToString("\n"); feedback = "Frases salvas. Faça testes negativos antes de permitir bloqueio."; refresh() } }, modifier = Modifier.fillMaxWidth()) { Text("Salvar frases") }
                                Row { Switch(checked = phraseMode, onCheckedChange = { phraseMode = it; store.phraseMode = it }); Text("Detectar frase inteira dentro de uma sentença. Desligado: transcrição precisa corresponder exatamente.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp)) }
                                Text("Confiança mínima: ${(minimumConfidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                Slider(value = minimumConfidence, onValueChange = { minimumConfidence = it }, valueRange = .7f..1f)
                                Row { Checkbox(checked = allowVoiceLock, onCheckedChange = { allowVoiceLock = it }); Text("Autorizar bloqueio real nesta sessão se a frase corresponder e a proteção estiver ativa.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)) }
                                Text(voiceStatus, color = if (listening) Green else Muted, style = MaterialTheme.typography.bodyMedium)
                                Button(enabled = !listening && voice.available(), onClick = { pendingPermission = "voice"; permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sessão de voz local") }
                                OutlinedButton(onClick = { voice.stop() }, modifier = Modifier.fillMaxWidth()) { Text("Parar microfone") }
                                if (transcript.isNotEmpty()) Text(transcript, style = MaterialTheme.typography.bodySmall)
                                Text("Sem confiança fornecida pelo motor, o bloqueio automático não é executado. Saindo da tela do app, a sessão termina.", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            Section("Botões e atalhos") {
                                Text("Power 3× e Power + Volume não são interceptados por aplicativos comuns de forma confiável. Use o botão do app ou a ação Bloquear agora da notificação autorizada. O SOS nativo permanece intacto.", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                        "Conexão" -> {
                            Section("Conectar este dispositivo") {
                                Text(if (store.token.isBlank()) "Não cadastrado no servidor." else "Cadastrado · ${store.deviceId.take(16)}…", style = MaterialTheme.typography.bodySmall, color = Green)
                                OutlinedTextField(enabled = store.token.isBlank(), value = server, onValueChange = { server = it }, label = { Text("Origem HTTPS do servidor") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Text("Emulador: http://10.0.2.2:4173. Android USB: adb reverse tcp:4173 tcp:4173 e http://localhost:4173. HTTP limitado ao build debug nestes hosts.", style = MaterialTheme.typography.bodySmall, color = Muted)
                                if (store.token.isBlank()) {
                                    Text("Já criou o dispositivo pelo painel? Cole o token único para parear sem criar outro cadastro.", style = MaterialTheme.typography.bodySmall, color = Muted)
                                    OutlinedTextField(value = pairingToken, onValueChange = { pairingToken = it.trim() }, label = { Text("Token de dispositivo existente") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedButton(enabled = !busy && pairingToken.length >= 20, onClick = { val candidate = pairingToken; pairingToken = ""; runTask { ApiClient(this@MainActivity).pair(server, candidate); "Dispositivo existente validado e pareado. Token cifrado no Android Keystore." } }, modifier = Modifier.fillMaxWidth()) { Text("Parear dispositivo existente") }
                                    HorizontalDivider()
                                    Text("Ou cadastre um novo dispositivo com sua conta verificada.", style = MaterialTheme.typography.bodySmall, color = Muted)
                                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail verificado") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = deviceName, onValueChange = { deviceName = it.take(80) }, label = { Text("Nome deste aparelho") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    Button(enabled = !busy && password.length >= 12 && email.contains('@') && deviceName.isNotBlank(), onClick = {
                                        val secret = password; password = ""
                                        runTask { store.server = ApiAddress.validate(server, BuildConfig.DEBUG); ApiClient(this@MainActivity).connect(email.trim(), secret, deviceName); "Dispositivo cadastrado. Token privado protegido pelo Android Keystore." }
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Entrar e cadastrar este Android") }
                                } else {
                                    Text("Para trocar de servidor ou conta, desconecte este aparelho antes.", style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { confirmForget = true }, modifier = Modifier.fillMaxWidth()) { Text("Desconectar este dispositivo localmente") }
                                }
                                OutlinedButton(onClick = { runCatching { val base = ApiAddress.validate(server, BuildConfig.DEBUG); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$base/panel.html"))) }.onFailure { feedback = it.message.orEmpty() } }, modifier = Modifier.fillMaxWidth()) { Text("Abrir cadastro / painel web") }
                            }
                            Section("Sincronização e ações remotas") {
                                Text(store.syncStatus, style = MaterialTheme.typography.bodySmall, color = Muted)
                                Text("${store.pending().length()} registro(s) aguardando envio.", style = MaterialTheme.typography.bodySmall)
                                Text("Ao ativar, você permite comandos autenticados LOCK, LOST, RECOVER e alarme do seu painel. Serviço visível; consulta a cada 15 s, sem push FCM nesta versão. Android pode encerrar o serviço.", style = MaterialTheme.typography.bodySmall, color = Muted)
                                Button(enabled = store.token.isNotBlank(), onClick = {
                                    if (store.remoteEnabled) { store.remoteEnabled = false; stopService(Intent(this@MainActivity, SyncService::class.java)); refresh() }
                                    else if (Build.VERSION.SDK_INT >= 33) { pendingPermission = "notifications"; permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
                                    else startSync()
                                }, modifier = Modifier.fillMaxWidth()) { Text(if (store.remoteEnabled) "Desativar sincronização remota" else "Autorizar e ativar ações remotas") }
                                OutlinedButton(enabled = store.token.isNotBlank() && !busy, onClick = { runTask { ApiClient(this@MainActivity).flush(); "Fila sincronizada." } }, modifier = Modifier.fillMaxWidth()) { Text("Sincronizar agora") }
                            }
                        }
                    }
                    Text("DESENVOLVIDO POR UNITRIXAPP · 0.2.0 DEV", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(vertical = 20.dp))
                }
                if (confirmLock) AlertDialog(onDismissRequest = { confirmLock = false }, title = { Text("Bloquear este aparelho?") }, text = { Text("O Android exigirá sua credencial nativa para desbloquear. O Sentinela não conhece nem remove seu PIN. Você autoriza este teste agora?") }, confirmButton = { TextButton(onClick = { confirmLock = false; runTask { protector.lock("MANUAL_TRIGGER").message.also { runCatching { ApiClient(this@MainActivity).flush() } } } }) { Text("Autorizar bloqueio agora") } }, dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("Cancelar") } })
                if (confirmForget) AlertDialog(onDismissRequest = { confirmForget = false }, title = { Text("Desconectar localmente?") }, text = { Text("O token será removido deste Android. Revogue também o dispositivo no painel web. O estado de emergência e o bloqueio nativo não serão removidos.") }, confirmButton = { TextButton(onClick = { confirmForget = false; store.clearConnection(); stopService(Intent(this@MainActivity, SyncService::class.java)); refresh() }) { Text("Desconectar") } }, dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancelar") } })
            }
        }
    }
    @Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, color = Ink); content() }
        }
    }
    private fun stateLabel(state: ProtectionState): String = when (state) {
        ProtectionState.DISABLED -> "Proteção desativada"
        ProtectionState.ACTIVE -> "Proteção ativa"
        ProtectionState.EMERGENCY -> "Emergência detectada"
        ProtectionState.LOST -> "Dispositivo perdido"
        ProtectionState.RECOVERING -> "Recuperação em andamento"
        ProtectionState.RESOLVED -> "Emergência encerrada"
    }
}
