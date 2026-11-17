package com.wifizone.mikhmon

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.wifizone.mikhmon.Ui.add
import com.wifizone.mikhmon.Ui.dp
import kotlin.concurrent.thread

/**
 * Configuration pour débutant : seuls l'utilisateur et le mot de passe du MikroTik sont demandés.
 * L'adresse du routeur vient du Wi-Fi ; le nom, le hotspot et le DNS sont lus sur le routeur.
 */
class SetupActivity : AppCompatActivity() {
    private lateinit var wifiCard: TextView
    private lateinit var user: EditText
    private lateinit var pass: EditText
    private lateinit var ip: EditText
    private lateinit var apiPort: EditText
    private lateinit var currency: EditText
    private lateinit var connect: View
    private lateinit var progress: LinearLayout
    private lateinit var progressText: TextView
    private var ipEditedByUser = false
    private var detectedIp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val old = SettingsStore(this).load()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; val p = dp(24); setPadding(p, dp(32), p, p)
        }
        box.add(Ui.title(this, "Connexion à votre MikroTik"))
        box.add(Ui.body(this, "Entrez seulement l'identifiant et le mot de passe de votre routeur " +
            "(les mêmes que dans Winbox). L'application s'occupe du reste."), 8)
        wifiCard = Ui.card(this, "", true); box.add(wifiCard, 20)

        user = field("Nom d'utilisateur du routeur", old?.routerUser ?: "admin")
        box.add(user, 20)
        pass = field("Mot de passe du routeur", old?.routerPass ?: "", password = true)
        box.add(pass, 8)
        box.add(CheckBox(this).apply {
            text = "Afficher le mot de passe"
            setOnCheckedChangeListener { _, on ->
                pass.inputType = InputType.TYPE_CLASS_TEXT or
                    if (on) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
                pass.setSelection(pass.text.length)
            }
        }, 4)

        connect = Ui.primaryButton(this, "Se connecter") { submit(old) }
        box.add(connect, 16)
        progressText = Ui.body(this)
        progress = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; visibility = View.GONE
            addView(ProgressBar(this@SetupActivity), LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(progressText, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        }
        box.add(progress, 16)

        // Options avancées (repliées) : rarement utiles
        val advanced = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        ip = field("Adresse IP du routeur", old?.routerIp ?: "").also {
            it.setOnKeyListener { _, _, _ -> ipEditedByUser = true; false }
        }
        if (old != null) ipEditedByUser = true
        apiPort = field("Port API", (old?.routerPort ?: RouterOs.PORT).toString(), number = true)
        currency = field("Devise", old?.currency ?: Defaults.currencyFor())
        advanced.add(Ui.body(this, "Adresse IP du routeur"), 8); advanced.add(ip)
        advanced.add(Ui.body(this, "Port API"), 8); advanced.add(apiPort)
        advanced.add(Ui.body(this, "Devise"), 8); advanced.add(currency)
        val toggle = Ui.textButton(this, "Options avancées ▾") {}
        toggle.setOnClickListener {
            val show = advanced.visibility != View.VISIBLE
            advanced.visibility = if (show) View.VISIBLE else View.GONE
            toggle.text = if (show) "Options avancées ▴" else "Options avancées ▾"
        }
        box.add(toggle, 24)
        box.add(advanced)

        setContentView(ScrollView(this).apply { addView(box) })
    }

    override fun onResume() {
        super.onResume()
        refreshWifi()
    }

    private fun field(hint: String, value: String, password: Boolean = false, number: Boolean = false) =
        EditText(this).apply {
            this.hint = hint; setText(value); setSingleLine(); textSize = 17f
            inputType = when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                number -> InputType.TYPE_CLASS_NUMBER
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
        }

    private fun refreshWifi() {
        val w = WifiNetwork.detect(this)
        detectedIp = w.gateway
        if (!ipEditedByUser && w.gateway != null) ip.setText(w.gateway)
        val ok = w.connected && w.gateway != null
        wifiCard.text = if (ok) "✓ Wi-Fi connecté — routeur détecté : ${w.gateway}"
            else "⚠ Le téléphone n'est pas connecté en Wi-Fi.\nConnectez-vous au Wi-Fi de votre MikroTik puis revenez ici."
        wifiCard.setTextColor(if (ok) 0xFF1B5E20.toInt() else 0xFFB71C1C.toInt())
        (wifiCard.background as? android.graphics.drawable.GradientDrawable)
            ?.setColor(if (ok) 0xFFE8F5E9.toInt() else 0xFFFFEBEE.toInt())
    }

    private fun busy(text: String?) {
        progress.visibility = if (text == null) View.GONE else View.VISIBLE
        progressText.text = text ?: ""
        connect.isEnabled = text == null
        connect.alpha = if (text == null) 1f else 0.5f
    }

    private fun submit(old: MikhmonSettings?) {
        refreshWifi()
        val host = ip.text.toString().trim().ifEmpty { detectedIp.orEmpty() }
        val u = user.text.toString().trim()
        val pw = pass.text.toString()
        val port = apiPort.text.toString().toIntOrNull()?.takeIf { it in 1..65535 } ?: RouterOs.PORT
        if (u.isEmpty()) { user.error = "Obligatoire"; return }
        if (host.isEmpty()) {
            alert("Pas de Wi-Fi", "Connectez le téléphone au Wi-Fi de votre MikroTik, puis touchez « Se connecter ».")
            return
        }
        busy("Connexion au routeur $host…")
        thread {
            val result = runCatching {
                RouterProbe.probe(host, u, pw, port) { WifiNetwork.socketFor(this, host) }
            }
            runOnUiThread {
                busy(null)
                result.fold(
                    onSuccess = { info -> save(old, host, port, u, pw, info) },
                    onFailure = { e -> explain(e, old, host, port, u, pw) },
                )
            }
        }
    }

    private fun explain(e: Throwable, old: MikhmonSettings?, host: String, port: Int, u: String, pw: String) {
        val kind = (e as? RouterOsException)?.kind
        val (title, msg) = when (kind) {
            RouterOsException.Kind.BAD_LOGIN -> "Identifiants refusés" to
                "Nom d'utilisateur ou mot de passe incorrect.\nUtilisez les mêmes identifiants que pour Winbox."
            RouterOsException.Kind.API_DISABLED -> "Service API désactivé" to
                "Le routeur $host répond, mais son service API (port $port) est désactivé.\n\n" +
                "Pour l'activer dans Winbox : IP → Services → double-cliquez sur « api » → décochez « Disabled » → OK."
            RouterOsException.Kind.UNREACHABLE -> "Routeur introuvable" to
                "Impossible de joindre le routeur à l'adresse $host.\n\n" +
                "• Vérifiez que le téléphone est connecté au Wi-Fi du MikroTik.\n" +
                "• Si l'adresse n'est pas la bonne, corrigez-la dans « Options avancées »."
            else -> "Erreur de connexion" to (e.message ?: e.toString())
        }
        val d = AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null)
        if (kind != RouterOsException.Kind.BAD_LOGIN) {
            d.setNeutralButton("Continuer quand même") { _, _ -> save(old, host, port, u, pw, RouterInfo("", "", "")) }
        }
        d.show()
    }

    private fun save(old: MikhmonSettings?, host: String, port: Int, u: String, pw: String, info: RouterInfo) {
        val store = SettingsStore(this)
        val session = Defaults.sessionName(info.identity.ifEmpty { old?.sessionName ?: "" })
        store.save(
            MikhmonSettings(
                routerIp = host, routerPort = port, routerUser = u, routerPass = pw,
                sessionName = session,
                hotspotName = info.identity.ifEmpty { info.hotspotName }.ifEmpty { old?.hotspotName ?: session },
                dnsName = info.dnsName.ifEmpty { old?.dnsName ?: "" },
                currency = currency.text.toString().trim(),
                adminUser = old?.adminUser?.takeIf { it.isNotEmpty() } ?: "mikhmon",
                adminPass = old?.adminPass?.takeIf { it.isNotEmpty() } ?: Defaults.randomPassword(),
            )
        )
        MikhmonServer.stop()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun alert(title: String, msg: String) =
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show()
}
