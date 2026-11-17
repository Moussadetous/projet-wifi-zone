package com.wifizone.mikhmon.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.wifizone.mikhmon.tickets.Branding
import com.wifizone.mikhmon.tickets.PaperFormat
import com.wifizone.mikhmon.tickets.PrintSettings
import com.wifizone.mikhmon.tickets.TicketOptions
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Réglages des tickets gardés d'une fois sur l'autre : identité du vendeur, options, papier, dernier modèle. */
class DesignStore(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("design", Context.MODE_PRIVATE)
    private val logoFile get() = File(ctx.filesDir, "logo.png")

    var templateId: String
        get() = prefs.getString("template", "std-standard") ?: "std-standard"
        set(v) { prefs.edit().putString("template", v).apply() }

    /** Dernier modèle premium choisi (bouton « Premium » de Mikhmon). */
    var lastPremium: String?
        get() = prefs.getString("lastPremium", null)
        set(v) { prefs.edit().putString("lastPremium", v).apply() }

    fun branding(defaultName: String, dns: String, currency: String): Branding {
        val j = prefs.getString("branding", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        return Branding(
            business = j?.optString("business")?.ifBlank { null } ?: defaultName.ifBlank { "WiFi Zone" },
            tagline = j?.optString("tagline").orEmpty(),
            dns = j?.optString("dns")?.ifBlank { null } ?: dns,
            contact = j?.optString("contact").orEmpty(),
            currency = j?.optString("currency")?.ifBlank { null } ?: currency,
            logo = logo(),
        )
    }

    /** Remplace une devise enregistrée par erreur (ancienne déduction d'après la langue). */
    fun replaceCurrency(old: String, new: String) {
        val j = prefs.getString("branding", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        if (j.optString("currency").equals(old, ignoreCase = true)) prefs.edit().putString("branding", j.put("currency", new).toString()).apply()
    }

    fun saveBranding(b: Branding) {
        prefs.edit().putString("branding", JSONObject().put("business", b.business).put("tagline", b.tagline).put("dns", b.dns)
            .put("contact", b.contact).put("currency", b.currency).toString()).apply()
    }

    fun options(templateId: String): TicketOptions {
        val j = prefs.getString("opt", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return TicketOptions()
        val colors = j.optJSONObject("colors")?.optJSONObject(templateId)
        return TicketOptions(
            primary = colors?.takeIf { it.has("p") }?.optInt("p"),
            accent = colors?.takeIf { it.has("a") }?.optInt("a"),
            headline = j.optString("headline"), footer = j.optString("footer"), badge = j.optString("badge"),
            motif = j.optString("motif").ifBlank { "wifi" },
            showQr = j.optBoolean("qr", true), showPrice = j.optBoolean("price", true), showLogo = j.optBoolean("logo", true),
            showContact = j.optBoolean("contact", true), showDns = j.optBoolean("dns", true), showSerial = j.optBoolean("serial", true),
            showIcons = j.optBoolean("icons", true),
        )
    }

    fun saveOptions(templateId: String, o: TicketOptions) {
        val j = prefs.getString("opt", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val colors = j.optJSONObject("colors") ?: JSONObject()
        val c = JSONObject()
        o.primary?.let { c.put("p", it) }
        o.accent?.let { c.put("a", it) }
        colors.put(templateId, c)
        j.put("colors", colors).put("headline", o.headline).put("footer", o.footer).put("badge", o.badge).put("motif", o.motif)
            .put("qr", o.showQr).put("price", o.showPrice).put("logo", o.showLogo).put("contact", o.showContact)
            .put("dns", o.showDns).put("serial", o.showSerial).put("icons", o.showIcons)
        prefs.edit().putString("opt", j.toString()).apply()
    }

    /** Réglages du papier ; le nombre de tickets par feuille est gardé pour chaque modèle. */
    fun print(templateId: String): PrintSettings {
        val per = prefs.getString("perPage", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        return PrintSettings(
            format = runCatching { PaperFormat.valueOf(prefs.getString("format", "A4")!!) }.getOrDefault(PaperFormat.A4),
            perPage = per?.optInt(templateId, 0) ?: 0,
            cutMarks = prefs.getBoolean("cuts", true),
        )
    }

    fun savePrint(templateId: String, v: PrintSettings) {
        val per = prefs.getString("perPage", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        per.put(templateId, v.perPage)
        prefs.edit().putString("format", v.format.name).putBoolean("cuts", v.cutMarks).putString("perPage", per.toString()).apply()
    }

    fun logo(): BitmapRef? = if (logoFile.exists()) BitmapFactory.decodeFile(logoFile.absolutePath)?.let { BitmapRef(it) } else null

    /** Logo choisi par l'utilisateur : réduit à 320 px (le PDF reste léger). */
    fun setLogo(uri: Uri?) {
        if (uri == null) { logoFile.delete(); return }
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
        val s = 320f / max(src.width, src.height)
        val bmp = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true) else src
        FileOutputStream(logoFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Previews.clear()
    }
}
