package com.wifizone.mikhmon.tickets.templates

import com.wifizone.mikhmon.tickets.Category
import com.wifizone.mikhmon.tickets.Kit
import com.wifizone.mikhmon.tickets.Palette
import com.wifizone.mikhmon.tickets.TicketContext
import com.wifizone.mikhmon.tickets.TicketTemplate
import com.wifizone.mikhmon.tickets.gfx.Align
import com.wifizone.mikhmon.tickets.gfx.Canvas2D
import com.wifizone.mikhmon.tickets.gfx.FontFace
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.VPath

/**
 * Les trois formats d'impression de Mikhmon (voucher/default.php et default-small.php) : « Default », « QR » et
 * « Small ». Même disposition que dans Mikhmon (cadre noir, nom du hotspot et numéro, code encadré, forfait et
 * prix, adresse de connexion), mais dessinés en vectoriel : nets à toutes les tailles et gratuits.
 * Repère : 1,5 unité par pixel CSS de Mikhmon (220 px de large pour Default et QR, 160 px pour Small).
 */
abstract class MikhmonFormat(id: String, name: String, w: Float, h: Float, description: String) :
    TicketTemplate(id, name, Category.MIKHMON, false, Palette.of("#FFF", "#FFFFFF", "#000000", "#000000", "#000000", "#333333"), w, h, description) {

    protected val border = 2.6f
    protected val thin = 1.4f

    /** Ligne « validité  durée  volume  prix », comme Mikhmon. */
    protected fun plan(t: TicketContext): String =
        listOfNotNull(t.v.validity?.takeIf { it.isNotBlank() }, t.v.time?.takeIf { it.isNotBlank() }, t.v.data?.takeIf { it.isNotBlank() }, Kit.price(t))
            .joinToString("  ")

    /** Cadre extérieur et en-tête : logo, nom du hotspot, numéro « [n] ». Renvoie le bas de l'en-tête. */
    protected fun frame(t: TicketContext, headerH: Float, nameSize: Float, logo: Boolean): Float {
        val c = t.c; val p = t.p
        val box = VPath.rect(border / 2f, border / 2f, w - border, h - border)
        c.fill(box, Solid(p.surface))
        c.stroke(box, p.primary, Stroke(border, round = false))
        var x = 9f
        val lg = t.b.logo
        if (logo && lg != null && t.o.showLogo) {
            val size = headerH - 12f
            Kit.logoOrMotif(t, x, (headerH - size) / 2f + 1f, size, p.text)
            x += size + 7f
        }
        val num = if (t.o.showSerial) t.v.serial?.let { "[$it]" } else null
        val numW = num?.let { c.measure(it, Kit.style(FontFace.BOLD, nameSize * 0.9f)) + 12f } ?: 0f
        val base = headerH / 2f + Kit.style(FontFace.BOLD, nameSize).cap / 2f + 1f
        Kit.text(c, Kit.headline(t, "Hotspot"), x, base, Kit.style(FontFace.BOLD, nameSize), p.text, maxW = w - x - 9f - numW)
        num?.let { Kit.text(c, it, w - 9f, base, Kit.style(FontFace.BOLD, nameSize * 0.9f), p.text, Align.RIGHT) }
        Kit.line(c, border, headerH, w - border, headerH, p.primary, thin)
        return headerH
    }

    /** Case encadrée avec son texte centré (le code, l'identifiant ou le mot de passe). */
    protected fun cell(c: Canvas2D, p: Palette, s: String, x: Float, y: Float, cw: Float, ch: Float, size: Float, scale: Float = 1f) {
        c.stroke(VPath.rect(x, y, cw, ch), p.primary, Stroke(thin, round = false))
        Kit.textMid(c, s, x + cw / 2f, y + ch / 2f, Kit.style(FontFace.BOLD, size * scale), p.text, Align.CENTER, maxW = cw - 8f)
    }

    protected fun label(c: Canvas2D, p: Palette, s: String, x: Float, y: Float, size: Float, maxW: Float) =
        Kit.text(c, s, x, y, Kit.style(FontFace.REGULAR, size), p.muted, Align.CENTER, maxW = maxW)

    /** Zone du code, centrée dans [x0, x1] ; [stacked] : identifiant et mot de passe l'un sous l'autre. */
    protected fun codes(t: TicketContext, x0: Float, x1: Float, top: Float, stacked: Boolean, labelSize: Float, codeSize: Float, cellH: Float) {
        val c = t.c; val p = t.p
        val k = Kit.codeScale(t)
        val cw = x1 - x0
        val pass = t.v.password
        if (pass == null) {
            label(c, p, "Code du ticket", x0 + cw / 2f, top + labelSize, labelSize, cw)
            cell(c, p, t.v.username, x0, top + labelSize + 6f, cw, cellH, codeSize, k)
        } else if (stacked) {
            label(c, p, "Utilisateur", x0 + cw / 2f, top + labelSize, labelSize, cw)
            cell(c, p, t.v.username, x0, top + labelSize + 4f, cw, cellH * 0.8f, codeSize * 0.8f, k)
            val y2 = top + labelSize + 4f + cellH * 0.8f + 6f
            label(c, p, "Mot de passe", x0 + cw / 2f, y2 + labelSize, labelSize, cw)
            cell(c, p, pass, x0, y2 + labelSize + 4f, cw, cellH * 0.8f, codeSize * 0.8f, k)
        } else {
            val half = (cw - 6f) / 2f
            label(c, p, "Utilisateur", x0 + half / 2f, top + labelSize, labelSize, half)
            label(c, p, "Mot de passe", x1 - half / 2f, top + labelSize, labelSize, half)
            cell(c, p, t.v.username, x0, top + labelSize + 6f, half, cellH, codeSize * 0.82f, k)
            cell(c, p, pass, x1 - half, top + labelSize + 6f, half, cellH, codeSize * 0.82f, k)
        }
    }

    protected fun footer(t: TicketContext, lineY: Float, planY: Float, loginY: Float) {
        val c = t.c; val p = t.p
        Kit.line(c, border + 6f, lineY, w - border - 6f, lineY, p.primary, thin)
        Kit.text(c, plan(t), w / 2f, planY, Kit.style(FontFace.BOLD, 21f), p.text, Align.CENTER, maxW = w - 20f)
        val login = Kit.dns(t)?.let { "Login : http://" + it.removePrefix("http://").removePrefix("https://") }
            ?: Kit.contact(t)
        login?.let { Kit.text(c, it, w / 2f, loginY, Kit.style(FontFace.BOLD, 15f), p.text, Align.CENTER, maxW = w - 20f) }
    }
}

/** « Default » de Mikhmon : sans QR code (même si l'option est active, comme le bouton Default). */
class MikhmonDefaultTemplate : MikhmonFormat("mk-default", "Mikhmon Default", 330f, 176f,
    "Le format « Default » de Mikhmon : cadre noir, code encadré") {
    override val naturalWidthMm get() = 58f

    override fun draw(t: TicketContext) {
        val top = frame(t, 42f, 20f, logo = true)
        codes(t, 14f, w - 14f, top + 4f, stacked = false, labelSize = 14f, codeSize = 25f, cellH = 36f)
        footer(t, top + 72f, top + 100f, top + 124f)
    }
}

/** « QR » de Mikhmon : le QR code de connexion automatique à droite du code. */
class MikhmonQrTemplate : MikhmonFormat("mk-qr", "Mikhmon QR", 330f, 240f,
    "Le format « QR » de Mikhmon : QR code de connexion à droite") {
    override val naturalWidthMm get() = 58f

    override fun draw(t: TicketContext) {
        val top = frame(t, 42f, 20f, logo = true)
        val qr = t.qr
        if (qr != null) {
            val size = 112f
            val qx = w - 12f - size
            val qy = top + 8f
            codes(t, 14f, qx - 12f, top + (if (t.v.password == null) 22f else 6f), stacked = true, labelSize = 14f, codeSize = 24f, cellH = 38f)
            Kit.qr(t.c, qr, qx, qy, size, t.p.text, null)
            footer(t, qy + size + 8f, qy + size + 38f, qy + size + 64f)
        } else {
            codes(t, 14f, w - 14f, top + 20f, stacked = false, labelSize = 14f, codeSize = 25f, cellH = 44f)
            footer(t, top + 100f, top + 136f, top + 166f)
        }
    }
}

/** « Small » de Mikhmon : petit format, forfait et prix encadrés. */
class MikhmonSmallTemplate : MikhmonFormat("mk-small", "Mikhmon Small", 240f, 132f,
    "Le format « Small » de Mikhmon : petit et économe") {
    override val naturalWidthMm get() = 42f

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val top = frame(t, 32f, 17f, logo = false)
        codes(t, 9f, w - 9f, top + 2f, stacked = false, labelSize = 12f, codeSize = 22f, cellH = 30f)
        val y = top + 52f
        val bh = h - border - 6f - y
        c.stroke(VPath.rect(9f, y, w - 18f, bh), p.primary, Stroke(thin, round = false))
        Kit.textMid(c, plan(t).ifEmpty { Kit.dns(t).orEmpty() }, w / 2f, y + bh / 2f, Kit.style(FontFace.BOLD, 15f), p.text, Align.CENTER, maxW = w - 30f)
    }
}
