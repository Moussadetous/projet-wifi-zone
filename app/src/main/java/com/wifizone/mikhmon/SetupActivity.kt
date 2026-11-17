package com.wifizone.mikhmon

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/** Formulaire de première configuration. */
class SetupActivity : AppCompatActivity() {
    private val fields = linkedMapOf<String, EditText>()

    private fun field(box: LinearLayout, key: String, label: String, def: String = "", pass: Boolean = false, num: Boolean = false) {
        box.addView(TextView(this).apply { text = label; setPadding(0, 24, 0, 0) })
        val e = EditText(this).apply {
            setText(def); setSingleLine()
            inputType = when {
                pass -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                num -> InputType.TYPE_CLASS_NUMBER
                else -> InputType.TYPE_CLASS_TEXT
            }
        }
        fields[key] = e; box.addView(e)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val old = SettingsStore(this).load()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
        box.addView(TextView(this).apply { text = "Configuration Mikhmon"; textSize = 22f })

        box.addView(TextView(this).apply { text = "\nCompte administrateur Mikhmon"; textSize = 16f })
        field(box, "adminUser", "Utilisateur", old?.adminUser ?: "mikhmon")
        field(box, "adminPass", "Mot de passe", old?.adminPass ?: "", pass = true)

        box.addView(TextView(this).apply { text = "\nRouteur MikroTik"; textSize = 16f })
        field(box, "routerName", "Nom de la session", old?.routerName ?: "MikroTik")
        field(box, "routerIp", "Adresse IP du routeur", old?.routerIp ?: "192.168.88.1")
        field(box, "routerApiPort", "Port API", (old?.routerApiPort ?: 8728).toString(), num = true)
        field(box, "routerUser", "Utilisateur du routeur", old?.routerUser ?: "admin")
        field(box, "routerPass", "Mot de passe du routeur", old?.routerPass ?: "", pass = true)

        box.addView(TextView(this).apply { text = "\nHotspot (facultatif)"; textSize = 16f })
        field(box, "hotspotName", "Nom du hotspot", old?.hotspotName ?: "")
        field(box, "dnsName", "Nom DNS", old?.dnsName ?: "")
        field(box, "currency", "Devise", old?.currency ?: "")

        box.addView(Button(this).apply { text = "Valider et démarrer"; setOnClickListener { submit(old) } })
        setContentView(ScrollView(this).apply { addView(box) })
    }

    private fun v(k: String) = fields[k]!!.text.toString().trim()

    private fun submit(old: MikhmonSettings?) {
        val ip = v("routerIp")
        val api = v("routerApiPort").toIntOrNull()
        val ipOk = Regex("""^(\d{1,3}\.){3}\d{1,3}$|^[A-Za-z0-9.-]+$""").matches(ip)
        val problem = when {
            v("adminUser").isEmpty() || v("adminPass").isEmpty() -> "Identifiants administrateur requis."
            v("routerName").isEmpty() || !Regex("[A-Za-z0-9_-]+").matches(v("routerName")) -> "Nom de session : lettres, chiffres, - et _ uniquement."
            !ipOk -> "Adresse du routeur invalide."
            api == null || api !in 1..65535 -> "Port API invalide."
            v("routerUser").isEmpty() -> "Utilisateur du routeur requis."
            else -> null
        }
        if (problem != null) { Toast.makeText(this, problem, Toast.LENGTH_LONG).show(); return }
        val s = MikhmonSettings(
            v("adminUser"), v("adminPass"), v("routerName"), ip, api!!, v("routerUser"), v("routerPass"),
            v("hotspotName"), v("dnsName"), v("currency"),
            port = PortPicker.pick(old?.port ?: 0),
        )
        SettingsStore(this).save(s)
        MikhmonService.stop(this) // relance avec la nouvelle config
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
