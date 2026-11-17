package com.wifizone.mikhmon

import java.util.Base64

/**
 * Génère include/config.php au format exact de Mikhmon v3
 * (voir settings/settings.php et include/readcfg.php de Mikhmon).
 */
object ConfigWriter {
    private const val KEY = "128"

    private fun keyChar(i: Int): Int {
        // PHP : substr($key, ($i % strlen($key)) - 1, 1) ; -1 = dernier caractère
        var idx = (i % KEY.length) - 1
        if (idx < 0) idx += KEY.length
        return KEY[idx].code
    }

    /** Identique à encrypt() de lib/routeros_api.class.php. */
    fun encrypt(s: String): String {
        val b = s.toByteArray(Charsets.UTF_8)
        val out = ByteArray(b.size) { i -> ((b[i].toInt() and 0xFF) + keyChar(i)).toByte() }
        return Base64.getEncoder().encodeToString(out)
    }

    fun decrypt(s: String): String {
        val b = Base64.getDecoder().decode(s)
        val out = ByteArray(b.size) { i -> ((b[i].toInt() and 0xFF) - keyChar(i)).toByte() }
        return String(out, Charsets.UTF_8)
    }

    /** Valeur sûre dans une chaîne PHP entre apostrophes, sans casser les séparateurs de Mikhmon. */
    private fun clean(s: String) = s.replace(Regex("[\\p{Cntrl}'\\\\]"), "").trim()

    /** routerAddress : "ip" ou "ip:port" (le port est géré par notre patch de Mikhmon). */
    fun render(s: MikhmonSettings, routerAddress: String = s.routerIp): String {
        val n = Defaults.sessionName(s.sessionName)
        val fields = listOf(
            "$n!${clean(routerAddress)}",
            "$n@|@${clean(s.routerUser)}",
            "$n#|#${encrypt(s.routerPass)}",
            "$n%${clean(s.hotspotName)}",
            "$n^${clean(s.dnsName)}",
            "$n&${clean(s.currency)}",
            "$n*10",
            "$n(1",
            "$n)",
            "$n=10",
            "$n@!@disable",
        )
        val row = fields.mapIndexed { i, f -> if (i == 0) "'1'=>'$f'" else "'$f'" }.joinToString(",")
        return "<?php \n" +
            "if(substr(\$_SERVER[\"REQUEST_URI\"], -10) == \"config.php\"){header(\"Location:./\");}; \n" +
            "\$data['mikhmon'] = array ('1'=>'mikhmon<|<${clean(s.adminUser)}','mikhmon>|>${encrypt(s.adminPass)}');\n" +
            "\$data['$n'] = array ($row);\n"
    }
}
