package com.wifizone.mikhmon

import java.security.SecureRandom
import java.util.Currency
import java.util.Locale

object Defaults {
    /** Zone franc CFA : UEMOA (XOF) et CEMAC (XAF). Les tickets y affichent « FCFA ». */
    val FCFA_COUNTRIES = setOf("BJ", "BF", "CI", "GW", "ML", "NE", "SN", "TG", "CM", "CF", "TD", "CG", "GQ", "GA")

    /** Devise d'un pays (code ISO à 2 lettres), ou null si le pays est inconnu. */
    fun currencyForCountry(country: String?): String? {
        val cc = country?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 && it.all(Char::isLetter) } ?: return null
        if (cc in FCFA_COUNTRIES) return "FCFA"
        val code = runCatching { Currency.getInstance(Locale("", cc)).currencyCode }.getOrNull() ?: return null
        return if (code == "XOF" || code == "XAF") "FCFA" else code
    }

    /**
     * Devise par défaut : d'abord le pays où se trouve le téléphone (réseau mobile, carte SIM ou fuseau horaire,
     * fournis par [country]), et seulement ensuite la langue du téléphone. Un téléphone réglé en « Français (France) »
     * au Niger ou au Burkina affiche ainsi des prix en FCFA, pas en euros.
     */
    fun currencyFor(country: String? = null, locale: Locale = Locale.getDefault()): String =
        currencyForCountry(country) ?: currencyForCountry(locale.country) ?: ""

    /** Pays déduit du fuseau horaire (téléphone sans carte SIM). */
    fun countryFromTimeZone(id: String?): String? = TZ_COUNTRY[id?.trim()]

    private val TZ_COUNTRY = mapOf(
        "Africa/Niamey" to "NE", "Africa/Ouagadougou" to "BF", "Africa/Bamako" to "ML", "Africa/Dakar" to "SN",
        "Africa/Abidjan" to "CI", "Africa/Lome" to "TG", "Africa/Porto-Novo" to "BJ", "Africa/Bissau" to "GW",
        "Africa/Douala" to "CM", "Africa/Libreville" to "GA", "Africa/Brazzaville" to "CG", "Africa/Ndjamena" to "TD",
        "Africa/Bangui" to "CF", "Africa/Malabo" to "GQ", "Africa/Conakry" to "GN", "Africa/Nouakchott" to "MR",
        "Africa/Lagos" to "NG", "Africa/Accra" to "GH", "Africa/Kinshasa" to "CD", "Africa/Lubumbashi" to "CD",
        "Africa/Casablanca" to "MA", "Africa/Algiers" to "DZ", "Africa/Tunis" to "TN", "Africa/Kigali" to "RW",
        "Africa/Bujumbura" to "BI", "Indian/Comoro" to "KM", "Indian/Antananarivo" to "MG", "Africa/Freetown" to "SL",
        "Africa/Monrovia" to "LR", "Africa/Banjul" to "GM", "Africa/Cairo" to "EG", "Africa/Nairobi" to "KE",
    )

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
