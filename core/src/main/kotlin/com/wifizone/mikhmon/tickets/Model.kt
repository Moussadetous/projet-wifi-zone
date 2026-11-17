package com.wifizone.mikhmon.tickets

import com.wifizone.mikhmon.tickets.gfx.Canvas2D
import com.wifizone.mikhmon.tickets.gfx.Colors
import com.wifizone.mikhmon.tickets.gfx.ImageRef

/** Un ticket Wi-Fi (un utilisateur du hotspot). */
data class Voucher(
    val username: String,
    /** null : code unique (mot de passe = identifiant). */
    val password: String? = null,
    val profile: String = "",
    /** Prix tel qu'affiché (ex. « 500 »), sans devise. */
    val price: String? = null,
    /** Validité lisible (ex. « 24 h », « 7 jours »). */
    val validity: String? = null,
    /** Volume lisible (ex. « 2 Go ») ; null = illimité / non précisé. */
    val data: String? = null,
    /** Débit lisible (ex. « 5 Mb/s »). */
    val speed: String? = null,
    /** Temps de connexion (limit-uptime) lisible (ex. « 3 h »). */
    val time: String? = null,
    val serial: Int? = null,
)

/** Ce qui ne change pas d'un ticket à l'autre : l'identité du vendeur. */
data class Branding(
    val business: String = "WiFi Zone",
    val tagline: String = "",
    /** Adresse de connexion du hotspot (nom DNS), ex. « wifi.zone ». */
    val dns: String = "",
    val contact: String = "",
    val currency: String = "F",
    val logo: ImageRef? = null,
)

/** Réglages choisis par l'utilisateur pour un modèle. */
data class TicketOptions(
    /** Couleur principale (null = celle du modèle). */
    val primary: Int? = null,
    /** Couleur d'accent (null = celle du modèle). */
    val accent: Int? = null,
    val headline: String = "",
    val footer: String = "",
    val badge: String = "",
    /** Icône du motif principal (voir Icons.MOTIFS). */
    val motif: String = "wifi",
    val showQr: Boolean = true,
    val showPrice: Boolean = true,
    val showLogo: Boolean = true,
    val showContact: Boolean = true,
    val showDns: Boolean = true,
    val showSerial: Boolean = true,
    val showIcons: Boolean = true,
    /** Libellés : « Code », « Identifiant »… */
    val codeLabel: String = "",
)

/** Palette d'un modèle. */
data class Palette(
    val bg: Int,
    val surface: Int,
    val primary: Int,
    val accent: Int,
    val text: Int,
    val muted: Int,
    val onPrimary: Int = Colors.onColor(primary),
) {
    /** Applique les couleurs choisies par l'utilisateur en gardant l'équilibre du modèle. */
    fun withOverrides(o: TicketOptions): Palette {
        var p = this
        o.primary?.let { p = p.copy(primary = it, onPrimary = Colors.onColor(it)) }
        o.accent?.let { p = p.copy(accent = it) }
        return p
    }

    companion object {
        fun of(bg: String, surface: String, primary: String, accent: String, text: String, muted: String) =
            Palette(Colors.hex(bg), Colors.hex(surface), Colors.hex(primary), Colors.hex(accent), Colors.hex(text), Colors.hex(muted))
    }
}

/** Tout ce qu'un modèle reçoit pour dessiner UN ticket. */
class TicketContext(
    val c: Canvas2D,
    val v: Voucher,
    val b: Branding,
    val o: TicketOptions,
    val p: Palette,
    /** Contenu du QR code (lien de connexion automatique), null si masqué. */
    val qr: String?,
)

enum class Category(val label: String) {
    CLASSIC("Classiques"), BOLD("Audacieux"), ELEGANT("Élégants"), AFRICA("Motifs africains"), FUN("Festifs"), THERMAL("Thermique")
}

/** Un modèle de ticket. Il dessine dans un repère fixe [w] × [h] (mis à l'échelle à l'impression). */
abstract class TicketTemplate(
    val id: String,
    val name: String,
    val category: Category,
    val premium: Boolean,
    val palette: Palette,
    val w: Float,
    val h: Float,
    val description: String = "",
) {
    abstract fun draw(t: TicketContext)
    val aspect get() = w / h
    /** Largeur à l'impression, en millimètres (340 unités = 85,6 mm, format carte bancaire). */
    open val naturalWidthMm: Float get() = w * 85.6f / 340f
}
