package com.wifizone.mikhmon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wifizone.mikhmon.Defaults
import com.wifizone.mikhmon.MikhmonServer
import com.wifizone.mikhmon.MikhmonSettings
import com.wifizone.mikhmon.RouterInfo
import com.wifizone.mikhmon.RouterOs
import com.wifizone.mikhmon.RouterOsException
import com.wifizone.mikhmon.RouterProbe
import com.wifizone.mikhmon.Services
import com.wifizone.mikhmon.WifiNetwork
import com.wifizone.mikhmon.tickets.gfx.Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Connexion au routeur : seuls l'identifiant et le mot de passe sont demandés, le reste est détecté. */
@Composable
fun SetupScreen(onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val old = remember { Services.settings.load() }
    var user by remember { mutableStateOf(old?.routerUser ?: "admin") }
    var pass by remember { mutableStateOf(old?.routerPass ?: "") }
    var showPass by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf(old?.routerIp ?: "") }
    var ipEdited by remember { mutableStateOf(old != null) }
    var port by remember { mutableStateOf((old?.routerPort ?: RouterOs.PORT).toString()) }
    var currency by remember { mutableStateOf(old?.currency?.ifBlank { null } ?: com.wifizone.mikhmon.Region.currency(ctx)) }
    var advanced by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var wifi by remember { mutableStateOf(WifiNetwork.detect(ctx)) }
    var problem by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshWifi() {
        wifi = WifiNetwork.detect(ctx)
        if (!ipEdited) wifi.gateway?.let { ip = it }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refreshWifi() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun save(host: String, p: Int, info: RouterInfo) {
        val session = Defaults.sessionName(info.identity.ifEmpty { old?.sessionName ?: "" })
        if (old == null || old.routerIp != host || old.routerPort != p) com.wifizone.mikhmon.remote.RemoteAccess.forgetRouter()
        Services.settings.save(MikhmonSettings(
            routerIp = host, routerPort = p, routerUser = user.trim(), routerPass = pass,
            sessionName = session,
            hotspotName = info.identity.ifEmpty { info.hotspotName }.ifEmpty { old?.hotspotName ?: session },
            dnsName = info.dnsName.ifEmpty { old?.dnsName ?: "" },
            currency = currency.trim(),
            adminUser = old?.adminUser?.takeIf { it.isNotEmpty() } ?: "mikhmon",
            adminPass = old?.adminPass?.takeIf { it.isNotEmpty() } ?: Defaults.randomPassword(),
        ))
        MikhmonServer.stop()
        onDone()
    }

    fun submit() {
        refreshWifi()
        val host = ip.trim().ifEmpty { wifi.gateway.orEmpty() }
        val p = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: RouterOs.PORT
        if (user.isBlank()) { problem = Triple("Identifiant manquant", "Indiquez le nom d'utilisateur du routeur (souvent « admin »).", false); return }
        if (host.isEmpty()) { problem = Triple("Pas de Wi-Fi", "Connectez le téléphone au Wi-Fi de votre MikroTik, puis touchez « Se connecter ».", false); return }
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { RouterProbe.probe(host, user.trim(), pass, p) { WifiNetwork.socketFor(ctx, host) } } }
            busy = false
            r.onSuccess { save(host, p, it) }.onFailure { e ->
                val kind = (e as? RouterOsException)?.kind
                problem = when (kind) {
                    RouterOsException.Kind.BAD_LOGIN -> Triple("Identifiants refusés", "Nom d'utilisateur ou mot de passe incorrect.\nUtilisez les mêmes identifiants que pour Winbox.", false)
                    RouterOsException.Kind.API_DISABLED -> Triple("Service API désactivé",
                        "Le routeur $host répond, mais son service API (port $p) est désactivé.\n\nDans Winbox : IP → Services → double-cliquez sur « api » → décochez « Disabled » → OK.", true)
                    RouterOsException.Kind.UNREACHABLE -> Triple("Routeur introuvable",
                        "Impossible de joindre le routeur à l'adresse $host.\n\n• Vérifiez que le téléphone est connecté au Wi-Fi du MikroTik.\n• Si l'adresse n'est pas la bonne, corrigez-la dans « Options avancées ».", true)
                    else -> Triple("Erreur de connexion", e.message ?: e.toString(), true)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Brand.Bg).verticalScroll(rememberScrollState()).padding(22.dp)) {
        Spacer(Modifier.height(18.dp))
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Brand.Hero), contentAlignment = Alignment.Center) {
            VIcon(Icons.WIFI, Color.White, size = 34.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Connexion à votre MikroTik", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("Entrez seulement l'identifiant et le mot de passe de votre routeur (les mêmes que dans Winbox). L'application s'occupe du reste.",
            style = MaterialTheme.typography.bodyLarge, color = Brand.Muted)
        Spacer(Modifier.height(18.dp))
        val ok = wifi.connected && wifi.gateway != null
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (ok) Brand.Green.copy(alpha = 0.10f) else Brand.Red.copy(alpha = 0.08f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            VIcon(if (ok) Icons.CHECK else Icons.WIFI, if (ok) Brand.Green else Brand.Red, size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(if (ok) "Wi-Fi connecté — routeur détecté : ${wifi.gateway}" else "Le téléphone n'est pas connecté en Wi-Fi. Connectez-vous au Wi-Fi de votre MikroTik puis revenez ici.",
                color = if (ok) Color(0xFF166534) else Brand.Red, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(user, { user = it }, label = { Text("Nom d'utilisateur du routeur") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pass, { pass = it }, label = { Text("Mot de passe du routeur") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { TextButton({ showPass = !showPass }) { Text(if (showPass) "Masquer" else "Afficher") } })
        Spacer(Modifier.height(18.dp))
        BigButton("Se connecter", loading = busy, onClick = { submit() })
        if (onCancel != null) TextButton(onCancel, Modifier.fillMaxWidth()) { Text("Annuler") }
        Spacer(Modifier.height(12.dp))
        Text(if (advanced) "Options avancées ▴" else "Options avancées ▾", color = Brand.Indigo, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp)).clickable { advanced = !advanced }.padding(8.dp))
        if (advanced) {
            OutlinedTextField(ip, { ip = it; ipEdited = true }, label = { Text("Adresse IP du routeur") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port API") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(currency, { currency = it }, label = { Text("Devise") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(30.dp))
    }

    problem?.let { (title, msg, canContinue) ->
        AlertDialog(
            onDismissRequest = { problem = null },
            title = { Text(title) }, text = { Text(msg) },
            confirmButton = { TextButton({ problem = null }) { Text("OK") } },
            dismissButton = if (canContinue) ({ TextButton({ problem = null; save(ip.trim().ifEmpty { wifi.gateway.orEmpty() }, port.toIntOrNull() ?: RouterOs.PORT, RouterInfo("", "", "")) }) { Text("Continuer quand même") } }) else null,
        )
    }
}
