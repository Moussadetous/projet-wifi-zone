package com.wifizone.mikhmon

/** Tout ce dont Mikhmon a besoin. Seuls routerUser/routerPass sont saisis par l'utilisateur. */
data class MikhmonSettings(
    val routerIp: String,
    val routerUser: String,
    val routerPass: String,
    val sessionName: String,
    val hotspotName: String,
    val dnsName: String,
    val currency: String,
    val adminUser: String,
    val adminPass: String,
    val routerPort: Int = RouterOs.PORT,
)
