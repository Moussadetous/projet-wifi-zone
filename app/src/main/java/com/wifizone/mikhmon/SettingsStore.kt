package com.wifizone.mikhmon

import android.content.Context

/** Configuration persistante (préférences privées de l'application). */
class SettingsStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("mikhmon", Context.MODE_PRIVATE)

    fun load(): MikhmonSettings? {
        if (!p.getBoolean("configured", false)) return null
        fun s(k: String) = p.getString(k, "") ?: ""
        return MikhmonSettings(
            routerIp = s("routerIp"), routerUser = s("routerUser"), routerPass = s("routerPass"),
            sessionName = s("sessionName"), hotspotName = s("hotspotName"), dnsName = s("dnsName"),
            currency = s("currency"), adminUser = s("adminUser"), adminPass = s("adminPass"),
            routerPort = p.getInt("routerPort", RouterOs.PORT),
        )
    }

    fun save(s: MikhmonSettings) {
        p.edit()
            .putBoolean("configured", true).putBoolean("configDirty", true)
            .putString("routerIp", s.routerIp).putInt("routerPort", s.routerPort)
            .putString("routerUser", s.routerUser).putString("routerPass", s.routerPass)
            .putString("sessionName", s.sessionName).putString("hotspotName", s.hotspotName)
            .putString("dnsName", s.dnsName).putString("currency", s.currency)
            .putString("adminUser", s.adminUser).putString("adminPass", s.adminPass)
            .apply()
    }

    /** config.php doit être réécrit (nouvelle configuration ou nouveau port du pont). */
    var configDirty: Boolean
        get() = p.getBoolean("configDirty", true)
        set(v) { p.edit().putBoolean("configDirty", v).apply() }

    var webPort: Int
        get() = p.getInt("webPort", 0)
        set(v) { p.edit().putInt("webPort", v).apply() }

    var bridgePort: Int
        get() = p.getInt("bridgePort", 0)
        set(v) { p.edit().putInt("bridgePort", v).apply() }

    fun reset() = p.edit().clear().apply()
}
