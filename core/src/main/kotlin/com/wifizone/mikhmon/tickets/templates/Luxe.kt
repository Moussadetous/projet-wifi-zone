package com.wifizone.mikhmon.tickets.templates

import com.wifizone.mikhmon.tickets.Category
import com.wifizone.mikhmon.tickets.Kit
import com.wifizone.mikhmon.tickets.Palette
import com.wifizone.mikhmon.tickets.TicketContext
import com.wifizone.mikhmon.tickets.TicketTemplate
import com.wifizone.mikhmon.tickets.gfx.Align
import com.wifizone.mikhmon.tickets.gfx.Colors
import com.wifizone.mikhmon.tickets.gfx.FontFace
import com.wifizone.mikhmon.tickets.gfx.Icons
import com.wifizone.mikhmon.tickets.gfx.Linear
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.VPath

/** « Luxe » : fond profond, filets dorés, typographie à empattements. */
class LuxeTemplate(id: String, name: String, palette: Palette) :
    TicketTemplate(id, name, Category.ELEGANT, true, palette, 340f, 200f, "Filets dorés et lettres classiques") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val body = VPath.round(0f, 0f, w, h, 10f)
        c.fill(body, Linear(0f, 0f, w, h, intArrayOf(Colors.lighten(p.bg, 0.06f), p.bg, Colors.darken(p.bg, 0.2f))))
        val gold = Linear(0f, 0f, w, h, intArrayOf(Colors.lighten(p.accent, 0.35f), p.accent, Colors.darken(p.accent, 0.25f), Colors.lighten(p.accent, 0.2f)))
        val goldC = p.accent
        // double filet
        c.stroke(VPath.round(8f, 8f, w - 16f, h - 16f, 6f), goldC, Stroke(1.2f))
        c.stroke(VPath.round(12f, 12f, w - 24f, h - 24f, 4f), Colors.alpha(goldC, 0.55f), Stroke(0.6f))
        // ornements d'angle
        for ((x, y) in listOf(12f to 12f, w - 12f to 12f, 12f to h - 12f, w - 12f to h - 12f)) {
            c.fill(VPath().polygon(x, y - 4f, x + 4f, y, x, y + 4f, x - 4f, y), Solid(goldC))
        }
        val cx = w / 2f
        Kit.logoOrMotif(t, cx - 11f, 22f, 22f, goldC)
        Kit.textFill(c, Kit.headline(t).uppercase(), cx, 62f, Kit.style(FontFace.SERIF_BOLD, 17f, 0.12f), gold, Align.CENTER, maxW = w - 70f)
        if (t.b.tagline.isNotBlank()) Kit.text(c, t.b.tagline, cx, 75f, Kit.style(FontFace.SERIF, 8.5f, 0.05f), Colors.alpha(p.text, 0.7f), Align.CENTER, maxW = w - 80f)
        // séparateur
        Kit.line(c, cx - 70f, 84f, cx - 8f, 84f, Colors.alpha(goldC, 0.7f), 0.7f)
        Kit.line(c, cx + 8f, 84f, cx + 70f, 84f, Colors.alpha(goldC, 0.7f), 0.7f)
        c.fill(VPath().polygon(cx, 80f, cx + 4f, 84f, cx, 88f, cx - 4f, 84f), Solid(goldC))

        val qr = t.qr
        val pass = t.v.password
        val lab = Kit.style(FontFace.SERIF, 7.5f, 0.3f)
        val left = if (qr != null) 30f else cx
        val al = if (qr != null) Align.LEFT else Align.CENTER
        val cw = if (qr != null) 190f else w - 70f
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), left, 106f - Kit.codeLift(t, 28f), lab, Colors.alpha(p.text, 0.65f), al)
            Kit.codeText(t, t.v.username, left, 136f, 27f, 0.1f, p.text, al, maxW = cw)
        } else {
            Kit.codeText(t, "UTILISATEUR · ${t.v.username}", left, 112f, 13f, 0.04f, p.text, al, maxW = cw)
            Kit.codeText(t, "MOT DE PASSE · $pass", left, 132f, 13f, 0.04f, p.text, al, maxW = cw)
        }
        val infos = Kit.infos(t).joinToString("  •  ") { it.second }
        val price = Kit.price(t)
        Kit.text(c, listOfNotNull(price, infos).joinToString("  •  "), left, 158f, Kit.style(FontFace.SERIF_BOLD, 10f, 0.04f), goldC, al, maxW = cw)
        val foot = listOfNotNull(Kit.dns(t), Kit.contact(t), Kit.serial(t)).joinToString("  ·  ")
        Kit.text(c, foot, left, 176f, Kit.style(FontFace.SERIF, 7.5f, 0.04f), Colors.alpha(p.text, 0.6f), al, maxW = cw)
        if (qr != null) {
            c.stroke(VPath.round(w - 112f, 98f, 84f, 84f, 4f), goldC, Stroke(0.8f))
            Kit.qr(c, qr, w - 108f, 102f, 76f, Colors.hex("#111111"), Colors.hex("#FDFBF5"), 2f)
        }
        Icons.draw(c, Icons.SPARKLE, w - 30f, 20f, 10f, Colors.alpha(goldC, 0.8f))
    }
}
