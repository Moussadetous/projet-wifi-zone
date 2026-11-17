package com.wifizone.mikhmon.tickets.templates

import com.wifizone.mikhmon.tickets.Category
import com.wifizone.mikhmon.tickets.Kit
import com.wifizone.mikhmon.tickets.Palette
import com.wifizone.mikhmon.tickets.TicketContext
import com.wifizone.mikhmon.tickets.TicketTemplate
import com.wifizone.mikhmon.tickets.gfx.Align
import com.wifizone.mikhmon.tickets.gfx.Colors
import com.wifizone.mikhmon.tickets.gfx.FontFace
import com.wifizone.mikhmon.tickets.gfx.Linear
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.VPath

/** « Billet » : billet de concert avec talon détachable, perforation et encoches. */
class EventTemplate(id: String, name: String, palette: Palette) :
    TicketTemplate(id, name, Category.FUN, true, palette, 380f, 168f, "Billet de spectacle avec talon détachable") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val stubX = 272f
        val body = VPath.round(0f, 0f, w, h, 12f)
        c.save(); c.clip(body)
        c.fill(body, Solid(p.surface))
        // bande colorée verticale
        c.fill(VPath.rect(0f, 0f, 46f, h), Linear(0f, 0f, 0f, h, intArrayOf(p.primary, Colors.darken(p.primary, 0.25f))))
        Kit.stripes(c, 0f, 0f, 46f, h, 7f, 2.2f, Colors.alpha(Colors.WHITE, 0.10f), 50f)
        // talon
        c.fill(VPath.rect(stubX, 0f, w - stubX, h), Linear(stubX, 0f, w, h, intArrayOf(Colors.mix(p.surface, p.primary, 0.08f), Colors.mix(p.surface, p.primary, 0.16f))))
        c.restore()

        // texte vertical dans la bande
        c.save(); c.translate(29f, h / 2f); c.rotate(-90f)
        Kit.text(c, t.o.badge.ifBlank { "WI-FI PASS" }.uppercase(), 0f, 0f, Kit.style(FontFace.BLACK, 15f, 0.22f), p.onPrimary, Align.CENTER, maxW = h - 24f)
        c.restore()

        // partie principale
        val x0 = 62f; val mw = stubX - x0 - 16f
        Kit.logoOrMotif(t, x0, 16f, 20f, p.primary)
        Kit.text(c, Kit.headline(t).uppercase(), x0 + 28f, 31f, Kit.style(FontFace.BLACK, 15f, 0.04f), p.text, maxW = mw - 28f)
        if (t.b.tagline.isNotBlank()) Kit.text(c, t.b.tagline, x0 + 28f, 43f, Kit.style(FontFace.MEDIUM, 8f), p.muted, maxW = mw - 28f)
        val pass = t.v.password
        val label = Kit.style(FontFace.BOLD, 7f, 0.22f)
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), x0, 66f, label, p.muted)
            Kit.text(c, t.v.username, x0, 98f, Kit.style(FontFace.MONO_HEAVY, 30f, 0.06f), p.primary, maxW = mw)
        } else {
            Kit.text(c, "UTILISATEUR", x0, 64f, label, p.muted)
            Kit.text(c, t.v.username, x0, 86f, Kit.style(FontFace.MONO_HEAVY, 19f, 0.04f), p.primary, maxW = mw * 0.5f)
            Kit.text(c, "MOT DE PASSE", x0 + mw * 0.53f, 64f, label, p.muted)
            Kit.text(c, pass, x0 + mw * 0.53f, 86f, Kit.style(FontFace.MONO_HEAVY, 19f, 0.04f), p.text, maxW = mw * 0.47f)
        }
        Kit.chips(c, Kit.infos(t), x0 - 2f, 112f, mw + 4f, 20f, Kit.style(FontFace.SEMIBOLD, 8.6f),
            Colors.alpha(p.primary, 0.10f), p.text, p.primary, icons = t.o.showIcons)
        val foot = listOfNotNull(Kit.dns(t)?.let { "Connexion : $it" }, Kit.contact(t)).joinToString("  ·  ")
        Kit.text(c, foot, x0, 152f, Kit.style(FontFace.MEDIUM, 7.5f), p.muted, maxW = mw)

        // talon : prix + QR
        val sw = w - stubX
        val cx = stubX + sw / 2f
        val qr = t.qr
        Kit.price(t)?.let { pr ->
            Kit.text(c, "PRIX", cx, 24f, Kit.style(FontFace.BOLD, 6.5f, 0.3f), p.muted, Align.CENTER)
            Kit.text(c, pr, cx, 46f, Kit.style(FontFace.BLACK, 21f), p.text, Align.CENTER, maxW = sw - 16f)
        }
        if (qr != null) Kit.qr(c, qr, cx - 36f, 62f, 72f, p.text, Colors.WHITE, 6f)
        else Kit.logoOrMotif(t, cx - 26f, 70f, 52f, p.primary)
        Kit.serial(t)?.let { Kit.text(c, it, cx, 154f, Kit.style(FontFace.MONO, 8f, 0.1f), p.muted, Align.CENTER) }

        // perforation et encoches
        Kit.line(c, stubX, 12f, stubX, h - 12f, Colors.alpha(p.muted, 0.6f), 1.1f, floatArrayOf(3.5f, 3.5f))
        Kit.notch(c, stubX, 0f, 9f, Colors.WHITE)
        Kit.notch(c, stubX, h, 9f, Colors.WHITE)
        c.stroke(VPath.round(0.5f, 0.5f, w - 1f, h - 1f, 12f), Colors.alpha(p.text, 0.10f), Stroke(1f))
    }
}
