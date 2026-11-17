package com.wifizone.mikhmon.tickets.templates

import com.wifizone.mikhmon.tickets.Category
import com.wifizone.mikhmon.tickets.Kit
import com.wifizone.mikhmon.tickets.Palette
import com.wifizone.mikhmon.tickets.TicketContext
import com.wifizone.mikhmon.tickets.TicketTemplate
import com.wifizone.mikhmon.tickets.gfx.Align
import com.wifizone.mikhmon.tickets.gfx.Colors
import com.wifizone.mikhmon.tickets.gfx.FontFace
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.VPath

/** « Minimal » : blanc, typographie forte, une seule couleur d'accent. Économe en encre. */
class MinimalTemplate(id: String, name: String, palette: Palette) :
    TicketTemplate(id, name, Category.CLASSIC, true, palette, 320f, 176f, "Sobre et élégant, économe en encre") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val body = VPath.round(0.6f, 0.6f, w - 1.2f, h - 1.2f, 12f)
        c.fill(body, Solid(p.surface))
        c.stroke(body, Colors.alpha(p.text, 0.16f), Stroke(1.2f))
        c.fill(VPath.round(16f, 18f, 4f, 30f, 2f), Solid(p.primary))
        Kit.text(c, Kit.headline(t), 28f, 32f, Kit.style(FontFace.BLACK, 15f), p.text, maxW = 170f)
        val sub = t.b.tagline.ifBlank { Kit.dns(t)?.let { "Connexion : $it" } ?: "" }
        Kit.text(c, sub, 28f, 46f, Kit.style(FontFace.MEDIUM, 8f), p.muted, maxW = 170f)
        Kit.price(t)?.let { Kit.text(c, it, w - 18f, 38f, Kit.style(FontFace.BLACK, 22f), p.primary, Align.RIGHT, maxW = 110f) }
        Kit.line(c, 16f, 60f, w - 16f, 60f, Colors.alpha(p.text, 0.08f), 1f)

        val qr = t.qr
        val cw = if (qr != null) w - 132f else w - 34f
        val pass = t.v.password
        val lab = Kit.style(FontFace.BOLD, 6.8f, 0.28f)
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), 18f, 82f - Kit.codeLift(t, 28f), lab, p.muted)
            Kit.codeText(t, t.v.username, 16f, 114f, 32f, 0.04f, p.text, maxW = cw)
        } else {
            Kit.text(c, "UTILISATEUR", 18f, 80f - Kit.codeLift(t, 17f), lab, p.muted)
            Kit.codeText(t, t.v.username, 17f, 100f, 18f, 0.03f, p.text, maxW = cw)
            Kit.text(c, "MOT DE PASSE", 18f, 116f, lab, p.muted)
            Kit.codeText(t, pass, 17f, 136f, 18f, 0.03f, p.primary, maxW = cw)
        }
        Kit.chips(c, Kit.infos(t), 14f, 146f, cw + 6f, 18f, Kit.style(FontFace.SEMIBOLD, 8.2f), null, p.text, p.primary, gap = 2f, icons = t.o.showIcons)
        if (qr != null) {
            Kit.qr(c, qr, w - 110f, 72f, 92f, p.text, null)
        }
        val foot = listOfNotNull(Kit.contact(t), Kit.serial(t)).joinToString("   ")
        Kit.text(c, foot, w - 18f, 51f, Kit.style(FontFace.MEDIUM, 7f), p.muted, Align.RIGHT, maxW = 120f)
    }
}
