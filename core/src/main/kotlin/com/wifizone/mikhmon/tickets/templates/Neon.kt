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
import com.wifizone.mikhmon.tickets.gfx.Radial
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.VPath

/** « Néon » : fond nuit, contours lumineux, code blanc souligné de lumière. */
class NeonTemplate(id: String, name: String, palette: Palette) :
    TicketTemplate(id, name, Category.BOLD, true, palette, 340f, 196f, "Enseigne lumineuse sur fond nuit") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val body = VPath.round(0f, 0f, w, h, 16f)
        c.save(); c.clip(body)
        c.fill(body, Solid(p.bg))
        c.fill(VPath.rect(0f, 0f, w, h), Radial(w * 0.2f, h * 0.1f, 220f, intArrayOf(Colors.alpha(p.primary, 0.20f), Colors.alpha(p.primary, 0f))))
        c.fill(VPath.rect(0f, 0f, w, h), Radial(w * 0.95f, h, 200f, intArrayOf(Colors.alpha(p.accent, 0.18f), Colors.alpha(p.accent, 0f))))
        c.restore()
        // cadre néon
        Kit.glow(c, VPath.round(9f, 9f, w - 18f, h - 18f, 11f), p.primary, 1.6f, 3.5f)

        // motif lumineux
        val ic = Kit.motif(t)
        val logo = t.b.logo
        if (logo != null && t.o.showLogo) Kit.logoOrMotif(t, 24f, 22f, 30f, p.primary)
        else {
            Icons.draw(c, ic, 22f, 20f, 34f, Colors.alpha(p.primary, 0.35f))
            Icons.draw(c, ic, 24f, 22f, 30f, Colors.lighten(p.primary, 0.35f))
        }
        Kit.text(c, Kit.headline(t).uppercase(), 64f, 37f, Kit.style(FontFace.BLACK, 17f, 0.06f), Colors.lighten(p.primary, 0.45f), maxW = 160f)
        if (t.b.tagline.isNotBlank()) Kit.text(c, t.b.tagline, 64f, 50f, Kit.style(FontFace.MEDIUM, 8f), Colors.alpha(Colors.WHITE, 0.55f), maxW = 160f)
        Kit.price(t)?.let { pr ->
            Kit.text(c, pr, w - 24f, 40f, Kit.style(FontFace.BLACK, 24f), Colors.lighten(p.accent, 0.35f), Align.RIGHT, maxW = 100f)
        }

        val qr = t.qr
        val codeW = if (qr != null) 192f else w - 50f
        val pass = t.v.password
        val label = Kit.style(FontFace.BOLD, 7f, 0.25f)
        val white = Colors.WHITE
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), 26f, 82f, label, Colors.alpha(white, 0.55f))
            val st = Kit.text(c, t.v.username, 26f, 112f, Kit.style(FontFace.MONO_HEAVY, 28f, 0.08f), white, maxW = codeW)
            val tw = c.measure(t.v.username, st)
            Kit.glow(c, VPath().moveTo(26f, 121f).lineTo(26f + tw, 121f), p.accent, 1.4f, 2.5f)
        } else {
            Kit.text(c, "UTILISATEUR", 26f, 80f, label, Colors.alpha(white, 0.55f))
            Kit.text(c, t.v.username, 26f, 102f, Kit.style(FontFace.MONO_HEAVY, 19f, 0.05f), white, maxW = codeW)
            Kit.text(c, "MOT DE PASSE", 26f, 118f, label, Colors.alpha(white, 0.55f))
            Kit.text(c, pass, 26f, 138f, Kit.style(FontFace.MONO_HEAVY, 19f, 0.05f), Colors.lighten(p.accent, 0.4f), maxW = codeW)
        }
        Kit.chips(c, Kit.infos(t), 24f, 146f, codeW + 6f, 18f, Kit.style(FontFace.SEMIBOLD, 8.2f),
            Colors.alpha(p.primary, 0.16f), white, Colors.lighten(p.primary, 0.3f), icons = t.o.showIcons)
        val foot = listOfNotNull(Kit.dns(t)?.let { "→ $it" }, Kit.contact(t), Kit.serial(t)).joinToString("   ")
        Kit.text(c, foot, 26f, 180f, Kit.style(FontFace.MEDIUM, 7.5f), Colors.alpha(white, 0.5f), maxW = codeW + 30f)
        if (qr != null) {
            val s = 88f; val x = w - 24f - s; val y = 74f
            Kit.glow(c, VPath.round(x - 4f, y - 4f, s + 8f, s + 8f, 10f), p.accent, 1.2f, 3f)
            Kit.qr(c, qr, x, y, s, Colors.hex("#0B0B14"), Colors.WHITE, 7f)
        }
    }
}
