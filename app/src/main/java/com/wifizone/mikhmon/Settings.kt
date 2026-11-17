package com.wifizone.mikhmon

import android.content.Context

/** Paramètres saisis par l'utilisateur, stockés en privé dans l'application. */
data class MikhmonSettings(
    val adminUser: String,
    val adminPass: String,
    val routerName: String,
    val routerIp: String,
    val routerApiPort: Int,
    val routerUser: String,
    val routerPass: String,
    val hotspotName: String,
    val dnsName: String,
    val currency: String,
    val port: Int,
)

class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("mikhmon", Context.MODE_PRIVATE)

    fun load(): MikhmonSettings? {
        if (!p.getBoolean("configured", false)) return null
        return MikhmonSettings(
            adminUser = p.getString("adminUser", "")!!,
            adminPass = p.getString("adminPass", "")!!,
            routerName = p.getString("routerName", "MikroTik")!!,
            routerIp = p.getString("routerIp", "")!!,
            routerApiPort = p.getInt("routerApiPort", 8728),
            routerUser = p.getString("routerUser", "")!!,
            routerPass = p.getString("routerPass", "")!!,
            hotspotName = p.getString("hotspotName", "")!!,
            dnsName = p.getString("dnsName", "")!!,
            currency = p.getString("currency", "")!!,
            port = p.getInt("port", 0),
        )
    }

    fun save(s: MikhmonSettings) {
        p.edit()
            .putBoolean("configured", true)
            .putString("adminUser", s.adminUser).putString("adminPass", s.adminPass)
            .putString("routerName", s.routerName).putString("routerIp", s.routerIp)
            .putInt("routerApiPort", s.routerApiPort)
            .putString("routerUser", s.routerUser).putString("routerPass", s.routerPass)
            .putString("hotspotName", s.hotspotName).putString("dnsName", s.dnsName)
            .putString("currency", s.currency).putInt("port", s.port)
            .apply()
    }

    fun reset() = p.edit().clear().apply()
}
