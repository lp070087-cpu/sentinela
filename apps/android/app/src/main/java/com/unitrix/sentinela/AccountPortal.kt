package com.unitrix.sentinela

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Sessão de conta somente em memória; separada do token nativo do dispositivo. */
internal class AccountApi(private val context: Context) {
    private var cookie = ""
    private var csrf = ""
    var signedIn = false
        private set

    private fun request(origin: String, path: String, verb: String = "GET", body: JSONObject? = null, secured: Boolean = true): JSONObject {
        check(!SecurityStore(context).demoActive) { "O DEMO não pode acessar a conta real." }
        val base = ApiAddress.validate(origin, BuildConfig.DEBUG)
        require(path.startsWith("/api/")) { "Rota de conta inválida." }
        if (secured) check(signedIn && cookie.isNotBlank() && csrf.isNotBlank()) { "Entre na sua conta novamente." }
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = verb
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Origin", base)
            if (secured) {
                connection.setRequestProperty("Cookie", cookie)
                if (verb != "GET") connection.setRequestProperty("X-CSRF-Token", csrf)
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val result = runCatching { JSONObject(stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}") }.getOrDefault(JSONObject())
            if (code !in 200..299) {
                if (secured && code == 401) clear()
                throw IllegalStateException(result.optString("error", "Erro HTTP " + code))
            }
            if (path == "/api/auth/login") {
                val receivedCookie = connection.getHeaderField("Set-Cookie")?.substringBefore(';').orEmpty()
                val receivedCsrf = result.optString("csrfToken")
                check(receivedCookie.startsWith("sentinela_session=") && receivedCsrf.isNotBlank()) { "Não foi possível validar a sessão." }
                cookie = receivedCookie
                csrf = receivedCsrf
                signedIn = true
            }
            return result
        } finally {
            connection.disconnect()
        }
    }

    fun clear() { cookie = ""; csrf = ""; signedIn = false }
    fun login(base: String, email: String, password: String): JSONObject {
        clear()
        val result = request(base, "/api/auth/login", "POST", JSONObject().put("email", email).put("password", password), false)
        SecurityStore(context).server = ApiAddress.validate(base, BuildConfig.DEBUG)
        return result
    }
    fun register(base: String, name: String, email: String, password: String): JSONObject =
        request(base, "/api/auth/register", "POST", JSONObject().put("name", name).put("email", email).put("password", password), false)
    fun requestReset(base: String, email: String): JSONObject =
        request(base, "/api/auth/request-reset", "POST", JSONObject().put("email", email), false)
    fun reset(base: String, token: String, password: String): JSONObject =
        request(base, "/api/auth/reset", "POST", JSONObject().put("token", token).put("password", password), false)
    fun verify(base: String, token: String): JSONObject =
        request(base, "/api/auth/verify-email", "POST", JSONObject().put("token", token), false)
    fun logout(base: String) {
        if (signedIn) runCatching { request(base, "/api/auth/logout", "POST", JSONObject()) }
        clear()
    }
    fun data(base: String): JSONObject = JSONObject()
        .put("profile", request(base, "/api/profile"))
        .put("devices", request(base, "/api/devices").optJSONArray("devices") ?: JSONArray())
        .put("contacts", request(base, "/api/contacts").optJSONArray("contacts") ?: JSONArray())
        .put("notices", request(base, "/api/notifications").optJSONArray("notifications") ?: JSONArray())
        .put("plan", request(base, "/api/subscription").optJSONObject("subscription") ?: JSONObject())
    fun saveProfile(base: String, name: String, phone: String) = request(base, "/api/profile", "PATCH", JSONObject().put("name", name).put("secondaryPhone", phone))
    fun addContact(base: String, name: String, phone: String) = request(base, "/api/contacts", "POST", JSONObject().put("name", name).put("phone", phone).put("allowAlerts", false))
    fun rename(base: String, id: String, name: String) = request(base, "/api/devices/" + id, "PATCH", JSONObject().put("name", name))
    fun readAll(base: String) = request(base, "/api/notifications/read-all", "POST", JSONObject())
}

private val PortalGreen = Color(0xFF426E60)
private val PortalInk = Color(0xFF182A30)
private val PortalMuted = Color(0xFF68767A)

@Composable
private fun PortalCard(title: String, body: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PortalInk)
            body()
        }
    }
}

@Composable
internal fun AccountPortal(api: AccountApi, initialServer: String, openProtection: () -> Unit) {
    var base by remember { mutableStateOf(initialServer) }
    var mode by remember { mutableStateOf("Entrar") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var freshPassword by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var data by remember { mutableStateOf(JSONObject()) }
    var tab by remember { mutableStateOf("Resumo") }
    var displayName by remember { mutableStateOf("") }
    var secondaryPhone by remember { mutableStateOf("") }
    var newContact by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var editingDevice by remember { mutableStateOf("") }
    var editingName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun load(result: JSONObject) {
        data = result
        connected = true
        val profile = result.optJSONObject("profile") ?: JSONObject()
        displayName = profile.optJSONObject("user")?.optString("name").orEmpty()
        secondaryPhone = profile.optString("secondaryPhone").takeUnless { it == "null" }.orEmpty()
    }
    fun perform(task: () -> JSONObject, done: (JSONObject) -> Unit = {}) {
        if (busy) return
        busy = true
        message = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { task() } }
            busy = false
            result.onSuccess(done).onFailure {
                message = it.message ?: "A operação não foi concluída."
                if (!api.signedIn) { connected = false; data = JSONObject() }
            }
        }
    }
    val profile = data.optJSONObject("profile")?.optJSONObject("user") ?: JSONObject()
    val devices = data.optJSONArray("devices") ?: JSONArray()
    val contacts = data.optJSONArray("contacts") ?: JSONArray()
    val notices = data.optJSONArray("notices") ?: JSONArray()

    Text("Sua conta Sentinela", style = MaterialTheme.typography.headlineSmall, color = PortalInk)
    Text("Controle privado de seus dispositivos. A demonstração é isolada e não acessa esta conta.", style = MaterialTheme.typography.bodySmall, color = PortalMuted)
    if (message.isNotBlank()) Surface(color = Color(0xFFE4F0E9), shape = RoundedCornerShape(10.dp)) {
        Text(message, color = PortalInk, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

    if (!connected) {
        PortalCard("Acesso e recuperação") {
            OutlinedTextField(value = base, onValueChange = { base = it.take(250) }, label = { Text("Servidor HTTPS") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Para emulador use http://10.0.2.2:4173. O backend de desenvolvimento precisa estar ativo.", color = PortalMuted, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Entrar", "Criar conta", "Recuperar", "Verificar e-mail").forEach { option ->
                    FilterChip(selected = mode == option, onClick = { mode = option }, label = { Text(option) })
                }
            }
            if (mode == "Criar conta") OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Seu nome") }, modifier = Modifier.fillMaxWidth())
            if (mode != "Verificar e-mail") OutlinedTextField(value = email, onValueChange = { email = it.take(254) }, label = { Text("Seu e-mail") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (mode == "Entrar" || mode == "Criar conta") OutlinedTextField(value = password, onValueChange = { password = it.take(128) }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            if (mode == "Recuperar" || mode == "Verificar e-mail") OutlinedTextField(value = token, onValueChange = { token = it.trim().take(200) }, label = { Text("Token recebido") }, modifier = Modifier.fillMaxWidth())
            if (mode == "Recuperar") OutlinedTextField(value = freshPassword, onValueChange = { freshPassword = it.take(128) }, label = { Text("Nova senha (mínimo 12 caracteres)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            when (mode) {
                "Entrar" -> Button(enabled = !busy && email.contains('@') && password.isNotEmpty(), onClick = {
                    val secret = password
                    perform({ api.login(base, email.trim(), secret); api.data(base) }) {
                        load(it); password = ""; message = "Acesso iniciado. Sua sessão fica somente na memória do aplicativo."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Entrar") }
                "Criar conta" -> Button(enabled = !busy && name.isNotBlank() && email.contains('@') && password.length >= 12, onClick = {
                    val secret = password
                    perform({ api.register(base, name.trim(), email.trim(), secret) }) {
                        password = ""; mode = "Verificar e-mail"; message = "Cadastro criado. Confirme seu e-mail com o token entregue pelo servidor."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Criar minha conta") }
                "Verificar e-mail" -> Button(enabled = !busy && token.isNotBlank(), onClick = {
                    perform({ api.verify(base, token) }) { token = ""; mode = "Entrar"; message = "E-mail confirmado. Faça login." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Confirmar e-mail") }
                "Recuperar" -> {
                    OutlinedButton(enabled = !busy && email.contains('@'), onClick = {
                        perform({ api.requestReset(base, email.trim()) }) { message = "Se a conta existir, a recuperação foi solicitada. Verifique o meio de entrega configurado." }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Solicitar recuperação") }
                    Button(enabled = !busy && token.isNotBlank() && freshPassword.length >= 12, onClick = {
                        val secret = freshPassword
                        perform({ api.reset(base, token, secret) }) { freshPassword = ""; token = ""; mode = "Entrar"; message = "Senha redefinida. Entre novamente." }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Redefinir senha") }
                }
            }
            Text("Sem provedores de e-mail configurados, tokens de desenvolvimento são consultados no painel local. Não existe conta DEMO com acesso às APIs reais.", color = PortalMuted, style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    PortalCard("Sessão autenticada") {
        Text(profile.optString("name"), style = MaterialTheme.typography.titleLarge, color = PortalInk)
        Text(profile.optString("email"), color = PortalMuted)
        Text(if (profile.optBoolean("emailVerified")) "E-mail confirmado" else "E-mail ainda não confirmado", color = PortalGreen)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(enabled = !busy, onClick = { perform({ api.data(base) }) { load(it) } }) { Text("Atualizar") }
            TextButton(enabled = !busy, onClick = {
                perform({ api.logout(base); JSONObject() }) { connected = false; data = JSONObject(); password = ""; mode = "Entrar"; message = "Sessão encerrada. O pareamento Android permanece independente." }
            }) { Text("Sair") }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Resumo", "Perfil", "Contatos", "Dispositivos", "Avisos", "Plano").forEach { item ->
            FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item) })
        }
    }
    when (tab) {
        "Resumo" -> PortalCard("Seus recursos") {
            Text("Dispositivos: " + devices.length(), color = PortalInk)
            Text("Contatos: " + contacts.length(), color = PortalInk)
            Text("Notificações: " + notices.length(), color = PortalInk)
            Button(onClick = openProtection, modifier = Modifier.fillMaxWidth()) { Text("Abrir proteção do Android") }
            Text("Bloqueio, microfone e localização continuam exigindo autorização e testes nativos.", color = PortalMuted, style = MaterialTheme.typography.bodySmall)
        }
        "Perfil" -> PortalCard("Dados pessoais") {
            OutlinedTextField(value = displayName, onValueChange = { displayName = it.take(80) }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = secondaryPhone, onValueChange = { secondaryPhone = it.take(30) }, label = { Text("Telefone secundário (opcional)") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && displayName.isNotBlank(), onClick = {
                perform({ api.saveProfile(base, displayName.trim(), secondaryPhone.trim()); api.data(base) }) { load(it); message = "Perfil salvo no servidor." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Salvar perfil") }
        }
        "Contatos" -> {
            PortalCard("Novo contato de confiança") {
                OutlinedTextField(value = newContact, onValueChange = { newContact = it.take(80) }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = newPhone, onValueChange = { newPhone = it.take(30) }, label = { Text("Telefone") }, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && newContact.isNotBlank() && newPhone.length >= 7, onClick = {
                    perform({ api.addContact(base, newContact.trim(), newPhone.trim()); api.data(base) }) {
                        load(it); newContact = ""; newPhone = ""; message = "Contato salvo sem alertas automáticos. Verifique o contato e seu consentimento no painel."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Cadastrar contato") }
                Text("Apenas contatos verificados e autorizados podem receber alertas reais.", style = MaterialTheme.typography.bodySmall, color = PortalMuted)
            }
            for (i in 0 until contacts.length()) {
                val c = contacts.optJSONObject(i) ?: continue
                PortalCard(c.optString("name")) {
                    Text(c.optString("phone"), color = PortalInk)
                    Text(if (c.optBoolean("verified")) "Contato verificado" else "Aguardando verificação", color = PortalMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        "Dispositivos" -> {
            if (devices.length() == 0) PortalCard("Sem aparelhos") { Text("Cadastre seu Android na área Conexão e atualize.", color = PortalMuted) }
            for (i in 0 until devices.length()) {
                val d = devices.optJSONObject(i) ?: continue
                val id = d.optString("id")
                PortalCard(d.optString("name", "Meu Android")) {
                    Text("Estado: " + d.optString("state"), color = PortalInk)
                    Text("Plataforma: " + d.optString("platform"), color = PortalMuted)
                    if (editingDevice == id) {
                        OutlinedTextField(value = editingName, onValueChange = { editingName = it.take(80) }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                        Button(enabled = !busy && editingName.isNotBlank(), onClick = {
                            perform({ api.rename(base, id, editingName.trim()); api.data(base) }) {
                                load(it); editingDevice = ""; message = "Nome atualizado."
                            }
                        }) { Text("Salvar nome") }
                    } else OutlinedButton(onClick = { editingDevice = id; editingName = d.optString("name") }) { Text("Renomear") }
                }
            }
        }
        "Avisos" -> {
            PortalCard("Notificações da conta") {
                OutlinedButton(enabled = !busy && notices.length() > 0, onClick = {
                    perform({ api.readAll(base); api.data(base) }) { load(it); message = "Notificações marcadas como lidas." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Marcar todas como lidas") }
            }
            if (notices.length() == 0) PortalCard("Tudo em dia") { Text("Não há notificações registradas.", color = PortalMuted) }
            for (i in 0 until notices.length()) {
                val n = notices.optJSONObject(i) ?: continue
                PortalCard(n.optString("title", "Notificação")) { Text(n.optString("body"), color = PortalMuted) }
            }
        }
        "Plano" -> PortalCard("Assinatura") {
            val plan = data.optJSONObject("plan") ?: JSONObject()
            Text("Plano atual: " + plan.optString("plan", "Essencial"), color = PortalInk)
            Text("Status: " + plan.optString("status", "DEVELOPMENT"), color = PortalMuted)
            Text("Cobranças reais ainda não estão configuradas. Consulte opções demonstrativas no painel web.", style = MaterialTheme.typography.bodySmall, color = PortalMuted)
        }
    }
}
