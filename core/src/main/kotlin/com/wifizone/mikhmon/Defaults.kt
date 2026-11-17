package com.wifizone.mikhmon

import java.security.SecureRandom
import java.util.Currency
import java.util.Locale

object Defaults {
    fun currencyFor(locale: Locale = Locale.getDefault()): String {
        val code = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull() ?: return ""
        return if (code == "XOF" || code == "XAF") "FCFA" else code
    }

    fun randomPassword(len: Int = 16): String {
        val chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val r = SecureRandom()
        return (1..len).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }

    /** Nom de session Mikhmon : il sert de clé dans config.php et dans les URL. */
    fun sessionName(identity: String): String =
        identity.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').take(30).ifEmpty { "MikroTik" }

    /** Passerelle supposée quand le DHCP ne la donne pas : x.y.z.1 */
    fun guessGateway(ownIpv4: String?): String? {
        val parts = ownIpv4?.split('.') ?: return null
        if (parts.size != 4) return null
        return parts.take(3).joinToString(".") + ".1"
    }
}
