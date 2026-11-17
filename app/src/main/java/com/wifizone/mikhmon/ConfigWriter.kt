package com.wifizone.mikhmon

import java.util.Base64
import java.net.URLEncoder

/**
 * Génère include/config.php au format Mikhmon v3.
 * Tout ce qui dépend du format de Mikhmon est isolé ici.
 */
object ConfigWriter {
    private const val KEY = "128"

    /** Équivalent exact de la fonction encrypt() de Mikhmon v3. */
    fun encrypt(s: String): String {
        val out = ByteArray(s.length)
        val bytes = s.toByteArray(Charsets.UTF_8)
        for (i in bytes.indices) {
            // PHP: substr($key, ($i % strlen($key)) - 1, 1) ; index négatif = depuis la fin
            var idx = (i % KEY.length) - 1
            if (idx < 0) idx += KEY.length
            out[i] = ((bytes[i].toInt() and 0xFF) + KEY[idx].code).toByte()
        }
        val b64 = Base64.getEncoder().encodeToString(out)
        return URLEncoder.encode(b64, "UTF-8")
    }

    private fun q(s: String) = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"

    fun render(s: MikhmonSettings): String {
        val n = s.routerName
        val rows = listOf(
            "1" to "$n!${s.routerIp}:${s.routerApiPort}",
            null to "$n@|@${s.routerUser}",
            null to "$n#|#${encrypt(s.routerPass)}",
            null to "$n%${s.hotspotName}",
            null to "$n^${s.dnsName}",
            null to "$n&${s.currency}",
            null to "$n*10",
            null to "$n(1",
            null to "$n)",
            null to "$n=10",
            null to "$n@!@disable",
        )
        val body = rows.joinToString(",") { (k, v) -> if (k != null) "'$k'=>${q(v)}" else q(v) }
        return """<?php
${'$'}data['mikhmon'] = array('1'=>${q("mikhmon<|<" + s.adminUser)},${q("mikhmon>|>" + s.adminPass)});
${'$'}data[${q(n)}] = array($body);
"""
    }
}
