package com.wifizone.mikhmon.tickets

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.wifizone.mikhmon.tickets.gfx.Align
import com.wifizone.mikhmon.tickets.gfx.Canvas2D
import com.wifizone.mikhmon.tickets.gfx.Colors
import com.wifizone.mikhmon.tickets.gfx.Fill
import com.wifizone.mikhmon.tickets.gfx.FontFace
import com.wifizone.mikhmon.tickets.gfx.Icon
import com.wifizone.mikhmon.tickets.gfx.Icons
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.TextStyle
import com.wifizone.mikhmon.tickets.gfx.VPath
import java.util.concurrent.ConcurrentHashMap

/** Outils de mise en page partagés par tous les modèles. */
object Kit {
    fun style(face: FontFace, size: Float, tracking: Float = 0f) = TextStyle(face, size, tracking)

    /** Réduit la taille du texte jusqu'à ce qu'il tienne dans [maxW]. */
    fun fit(c: Canvas2D, s: String, style: TextStyle, maxW: Float, minScale: Float = 0.45f): TextStyle {
        if (s.isEmpty() || maxW <= 0f) return style
        val w = c.measure(s, style)
        if (w <= maxW) return style
        return style.sized((style.size * maxW / w).coerceAtLeast(style.size * minScale))
    }

    /** Texte sur sa ligne de base, réduit si besoin pour tenir dans [maxW]. Renvoie le style utilisé. */
    fun text(c: Canvas2D, s: String, x: Float, y: Float, style: TextStyle, color: Int, align: Align = Align.LEFT, maxW: Float = 0f): TextStyle {
        if (s.isBlank()) return style
        val st = if (maxW > 0f) fit(c, s, style, maxW) else style
        c.text(s, x, y, st, Solid(color), align)
        return st
    }

    fun textFill(c: Canvas2D, s: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: Align = Align.LEFT, maxW: Float = 0f): TextStyle {
        if (s.isBlank()) return style
        val st = if (maxW > 0f) fit(c, s, style, maxW) else style
        c.text(s, x, y, st, fill, align)
        return st
    }

    /** Texte centré verticalement sur [cy] (d'après la hauteur des capitales). */
    fun textMid(c: Canvas2D, s: String, x: Float, cy: Float, style: TextStyle, color: Int, align: Align = Align.LEFT, maxW: Float = 0f): TextStyle {
        val st = if (maxW > 0f) fit(c, s, style, maxW) else style
        c.text(s, x, cy + st.cap / 2f, st, Solid(color), align)
        return st
    }

    fun price(t: TicketContext): String? {
        val p = t.v.price?.trim().orEmpty()
        if (!t.o.showPrice || p.isEmpty() || p == "0") return null
        val cur = t.b.currency.trim()
        return if (cur.isEmpty()) p else "$p $cur"
    }

    fun headline(t: TicketContext, fallback: String = "WI-FI"): String =
        t.o.headline.ifBlank { t.b.business }.ifBlank { fallback }

    fun codeLabel(t: TicketContext): String =
        t.o.codeLabel.ifBlank { if (t.v.password == null) "CODE" else "UTILISATEUR" }

    /** Informations du forfait, avec leur icône (validité, volume, débit, durée). */
    fun infos(t: TicketContext): List<Pair<Icon, String>> {
        val out = ArrayList<Pair<Icon, String>>()
        t.v.validity?.takeIf { it.isNotBlank() }?.let { out += Icons.HOURGLASS to it }
        t.v.time?.takeIf { it.isNotBlank() }?.let { out += Icons.CLOCK to it }
        val d = t.v.data?.takeIf { it.isNotBlank() }
        out += if (d != null) Icons.DATA to d else Icons.INFINITY to "Illimité"
        t.v.speed?.takeIf { it.isNotBlank() }?.let { out += Icons.GAUGE to it }
        return out
    }

    fun motif(t: TicketContext): Icon = Icons.byId(t.o.motif) ?: Icons.WIFI

    /** Pastille icône + texte. Renvoie sa largeur. */
    fun chip(c: Canvas2D, x: Float, y: Float, h: Float, icon: Icon?, label: String, style: TextStyle, bg: Int?, fg: Int, iconColor: Int = fg, radius: Float = h / 2f): Float {
        val pad = h * 0.36f
        val isz = h * 0.58f
        val tw = c.measure(label, style)
        val w = pad + (if (icon != null) isz + h * 0.18f else 0f) + tw + pad
        if (bg != null) c.fill(VPath.round(x, y, w, h, radius), Solid(bg))
        var cx = x + pad
        if (icon != null) { Icons.draw(c, icon, cx, y + (h - isz) / 2f, isz, iconColor); cx += isz + h * 0.18f }
        c.text(label, cx, y + h / 2f + style.cap / 2f, style, Solid(fg))
        return w
    }

    fun chipWidth(c: Canvas2D, h: Float, hasIcon: Boolean, label: String, style: TextStyle): Float {
        val pad = h * 0.36f
        return pad + (if (hasIcon) h * 0.58f + h * 0.18f else 0f) + c.measure(label, style) + pad
    }

    /** Rangée de pastilles qui tient dans [maxW] (texte réduit, puis pastilles retirées si nécessaire). */
    fun chips(
        c: Canvas2D, items: List<Pair<Icon, String>>, x: Float, y: Float, maxW: Float, h: Float, style: TextStyle,
        bg: Int?, fg: Int, iconColor: Int = fg, gap: Float = h * 0.25f, align: Align = Align.LEFT, icons: Boolean = true,
    ) {
        if (items.isEmpty()) return
        var list = items
        var st = style
        fun total(l: List<Pair<Icon, String>>, s: TextStyle) = l.sumOf { chipWidth(c, h, icons, it.second, s).toDouble() }.toFloat() + gap * (l.size - 1)
        while (total(list, st) > maxW && st.size > style.size * 0.72f) st = st.sized(st.size * 0.94f)
        while (list.size > 1 && total(list, st) > maxW) list = list.dropLast(1)
        val tw = total(list, st)
        var cx = when (align) { Align.LEFT -> x; Align.CENTER -> x + (maxW - tw) / 2f; Align.RIGHT -> x + maxW - tw }
        for ((ic, label) in list) {
            cx += chip(c, cx, y, h, if (icons) ic else null, label, st, bg, fg, iconColor) + gap
        }
    }

    /** Logo du client s'il existe (et qu'il est activé), sinon l'icône du motif. */
    fun logoOrMotif(t: TicketContext, x: Float, y: Float, size: Float, color: Int) {
        val logo = t.b.logo
        if (logo != null && t.o.showLogo) {
            val r = logo.width.toFloat() / logo.height.coerceAtLeast(1)
            val (w, h) = if (r >= 1f) size to size / r else size * r to size
            t.c.image(logo, x + (size - w) / 2f, y + (size - h) / 2f, w, h)
        } else {
            Icons.draw(t.c, motif(t), x, y, size, color)
        }
    }

    fun line(c: Canvas2D, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float, dash: FloatArray? = null) {
        c.stroke(VPath().moveTo(x0, y0).lineTo(x1, y1), color, Stroke(width, dash, round = dash == null))
    }

    // ------------------------------------------------------------------ QR code
    private val qrCache = ConcurrentHashMap<String, Pair<VPath, Int>>()

    /** Tracé du QR code (modules regroupés par lignes), dans un carré de côté [n] modules. */
    fun qrPath(content: String): Pair<VPath, Int> = qrCache.getOrPut(content) {
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 0))
        val p = VPath()
        for (y in 0 until m.height) {
            var x = 0
            while (x < m.width) {
                if (m[x, y]) {
                    var e = x
                    while (e < m.width && m[e, y]) e++
                    p.rect(x.toFloat(), y.toFloat(), (e - x).toFloat(), 1f)
                    x = e
                } else x++
            }
        }
        if (qrCache.size > 600) qrCache.clear()
        p to m.width
    }

    /** QR code dans un carré [size] (avec sa marge blanche si [bg] est donné). */
    fun qr(c: Canvas2D, content: String, x: Float, y: Float, size: Float, color: Int, bg: Int? = Colors.WHITE, radius: Float = size * 0.08f) {
        val (path, n) = qrPath(content)
        val quiet = if (bg != null) size * 0.07f else 0f
        if (bg != null) c.fill(VPath.round(x, y, size, size, radius), Solid(bg))
        val s = (size - 2 * quiet) / n
        c.fill(VPath().add(path, x + quiet, y + quiet, s), Solid(color))
    }

    // ------------------------------------------------------------------ décors
    fun dots(c: Canvas2D, x: Float, y: Float, w: Float, h: Float, step: Float, r: Float, color: Int) {
        val p = VPath()
        var yy = y + step / 2f
        var row = 0
        while (yy < y + h) {
            var xx = x + step / 2f + if (row % 2 == 1) step / 2f else 0f
            while (xx < x + w) { p.circle(xx, yy, r); xx += step }
            yy += step; row++
        }
        c.fill(p, Solid(color))
    }

    fun stripes(c: Canvas2D, x: Float, y: Float, w: Float, h: Float, step: Float, width: Float, color: Int, angle: Float = 45f) {
        val p = VPath()
        val k = kotlin.math.tan(VPath.rad(angle))
        var xx = x - h / k
        while (xx < x + w) {
            p.polygon(xx, y + h, xx + width, y + h, xx + width + h / k, y, xx + h / k, y)
            xx += step
        }
        c.save(); c.clip(VPath.rect(x, y, w, h)); c.fill(p, Solid(color)); c.restore()
    }

    /** Lueur « néon » : traits superposés de plus en plus fins et opaques. */
    fun glow(c: Canvas2D, path: VPath, color: Int, width: Float, spread: Float = 5f) {
        val layers = 5
        for (i in layers downTo 1) {
            val f = i / layers.toFloat()
            c.stroke(path, Colors.alpha(color, 0.10f + 0.06f * (layers - i)), Stroke(width + spread * width * f))
        }
        c.stroke(path, Colors.lighten(color, 0.55f), Stroke(width * 0.55f))
    }

    /** Encoches de découpe (demi-cercles) sur un bord vertical, couleur du papier. */
    fun notch(c: Canvas2D, x: Float, y: Float, r: Float, paper: Int) = c.fill(VPath.circle(x, y, r), Solid(paper))

    /** Petites pastilles en rangée (perforation). */
    fun perforation(c: Canvas2D, x: Float, y0: Float, y1: Float, r: Float, gap: Float, color: Int) {
        val p = VPath()
        var y = y0
        while (y <= y1) { p.circle(x, y, r); y += gap }
        c.fill(p, Solid(color))
    }

    fun serial(t: TicketContext): String? = if (t.o.showSerial) t.v.serial?.let { "N° ${it.toString().padStart(3, '0')}" } else null

    fun dns(t: TicketContext): String? = if (t.o.showDns && t.b.dns.isNotBlank()) t.b.dns.trim() else null

    fun contact(t: TicketContext): String? = if (t.o.showContact && t.b.contact.isNotBlank()) t.b.contact.trim() else null
}
