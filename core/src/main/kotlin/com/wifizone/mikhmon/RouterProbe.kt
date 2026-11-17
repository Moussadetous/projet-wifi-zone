package com.wifizone.mikhmon

import java.net.Socket

data class RouterInfo(val identity: String, val hotspotName: String, val dnsName: String)

/** Se connecte au routeur et lit ce que Mikhmon a besoin de savoir, pour que l'utilisateur n'ait rien à saisir. */
object RouterProbe {
    fun probe(
        host: String, user: String, pass: String,
        port: Int = RouterOs.PORT, socketFactory: () -> Socket = { Socket() },
    ): RouterInfo =
        RouterOs.connect(host, user, pass, port = port, socketFactory = socketFactory).use { r ->
            val identity = runCatching { r.command("/system/identity/print") }.getOrNull()
                ?.firstOrNull()?.get("name").orEmpty()
            val hotspot = runCatching { r.command("/ip/hotspot/print") }.getOrNull()?.firstOrNull()
            val profile = hotspot?.get("profile")
            val dns = if (profile == null) "" else runCatching {
                r.command("/ip/hotspot/profile/print", "?name=$profile")
            }.getOrNull()?.firstOrNull { it["name"] == profile }?.get("dns-name").orEmpty()
            RouterInfo(identity, hotspot?.get("name").orEmpty(), dns)
        }
}
