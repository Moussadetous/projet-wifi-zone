package com.wifizone.mikhmon

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale
import java.util.TimeZone

/** Pays et devise de l'utilisateur, d'après le téléphone lui-même et non d'après sa langue. */
object Region {
    /**
     * Pays (code ISO à 2 lettres) : réseau mobile, puis carte SIM, puis fuseau horaire, puis langue.
     * Aucune autorisation n'est nécessaire.
     */
    fun country(ctx: Context): String? {
        val tm = runCatching { ctx.getSystemService(TelephonyManager::class.java) }.getOrNull()
        val phone = listOf(
            runCatching { tm?.networkCountryIso }.getOrNull(),
            runCatching { tm?.simCountryIso }.getOrNull(),
        ).map { it.orEmpty().trim().uppercase(Locale.ROOT) }.firstOrNull { it.length == 2 }
        return phone
            ?: Defaults.countryFromTimeZone(TimeZone.getDefault().id)
            ?: Locale.getDefault().country.uppercase(Locale.ROOT).takeIf { it.length == 2 }
    }

    fun currency(ctx: Context): String = Defaults.currencyFor(country(ctx))

    /**
     * Les versions précédentes déduisaient la devise de la langue (« Français (France) » donnait EUR, même au Niger).
     * Une seule fois : si la devise enregistrée est celle qu'aurait donnée la langue et que le pays dit autre chose,
     * on corrige les réglages, le studio et config.php de Mikhmon (réécrit au prochain démarrage du serveur).
     */
    fun migrateCurrency(ctx: Context) {
        val prefs = ctx.getSharedPreferences("region", Context.MODE_PRIVATE)
        if (prefs.getBoolean("currencyChecked", false)) return
        val store = Services.settings
        val s = store.load()
        val right = currency(ctx)
        if (s != null && right.isNotEmpty()) {
            val fromLanguage = Defaults.currencyFor(locale = Locale.getDefault())
            if (s.currency.equals(fromLanguage, ignoreCase = true) && !s.currency.equals(right, ignoreCase = true)) {
                store.save(s.copy(currency = right))
                Services.design.replaceCurrency(s.currency, right)
            }
        }
        prefs.edit().putBoolean("currencyChecked", true).apply()
    }
}
