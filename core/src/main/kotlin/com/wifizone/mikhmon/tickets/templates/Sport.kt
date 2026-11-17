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
import com.wifizone.mikhmon.tickets.gfx.VPath

/** « Sport » : diagonale, italiques, lignes de vitesse. */
class SportTemplate(id: String, name: String, palette: Palette) :
    TicketTemplate(id, name, Category.BOLD, true, palette, 340f, 190f, "Diagonale et vitesse") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val body = VPath.round(0f, 0f, w, h, 12f)
        c.save(); c.clip(body)
        c.fill(body, Solid(p.bg))
        c.fill(VPath().polygon(0f, 0f, 210f, 0f, 150f, h, 0f, h), Linear(0f, 0f, 210f, h, intArrayOf(p.primary, Colors.darken(p.primary, 0.2f))))
        c.fill(VPath().polygon(214f, 0f, 228f, 0f, 168f, h, 154f, h), Solid(p.accent))
        // lignes de vitesse
        for (i in 0 until 6) {
            val y = 18f + i * 9f
            c.fill(VPath.round(234f + i * 7f, y, 60f - i * 6f, 2.6f, 1.3f), Solid(Colors.alpha(p.accent, 0.25f + i * 0.08f)))
        }
        c.restore()
        val on = p.onPrimary
        Kit.logoOrMotif(t, 16f, 14f, 22f, on)
        Kit.text(c, Kit.headline(t).uppercase(), 44f, 31f, Kit.style(FontFace.BLACK_ITALIC, 15f, 0.02f), on, maxW = 150f)
        Kit.text(c, t.o.badge.ifBlank { "PASS INTERNET" }.uppercase(), 18f, 52f, Kit.style(FontFace.BOLD_ITALIC, 8f, 0.2f), Colors.alpha(on, 0.8f), maxW = 160f)
        val pass = t.v.password
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), 18f, 86f, Kit.style(FontFace.BOLD, 7f, 0.25f), Colors.alpha(on, 0.75f))
            Kit.text(c, t.v.username, 17f, 116f, Kit.style(FontFace.MONO_HEAVY, 27f, 0.04f), on, maxW = 150f)
        } else {
            Kit.text(c, "UTILISATEUR", 18f, 80f, Kit.style(FontFace.BOLD, 7f, 0.25f), Colors.alpha(on, 0.75f))
            Kit.text(c, t.v.username, 17f, 98f, Kit.style(FontFace.MONO_HEAVY, 16f, 0.03f), on, maxW = 140f)
            Kit.text(c, "MOT DE PASSE", 18f, 114f, Kit.style(FontFace.BOLD, 7f, 0.25f), Colors.alpha(on, 0.75f))
            Kit.text(c, pass, 17f, 132f, Kit.style(FontFace.MONO_HEAVY, 16f, 0.03f), on, maxW = 130f)
        }
        Kit.chips(c, Kit.infos(t).take(2), 16f, 146f, 138f, 18f, Kit.style(FontFace.BOLD, 8f), Colors.alpha(on, 0.18f), on, icons = t.o.showIcons)
        val foot = listOfNotNull(Kit.dns(t), Kit.contact(t)).joinToString("  ·  ")
        Kit.text(c, foot, 18f, 180f, Kit.style(FontFace.SEMIBOLD, 7f), Colors.alpha(on, 0.8f), maxW = 140f)
        // côté sombre : prix + QR
        val fg = Colors.onColor(p.bg)
        Kit.price(t)?.let { Kit.text(c, it, w - 18f, 98f, Kit.style(FontFace.BLACK_ITALIC, 24f), fg, Align.RIGHT, maxW = 120f) }
        Kit.serial(t)?.let { Kit.text(c, it, w - 18f, 112f, Kit.style(FontFace.MONO, 7.5f, 0.1f), Colors.alpha(fg, 0.6f), Align.RIGHT) }
        val qr = t.qr
        if (qr != null) Kit.qr(c, qr, w - 18f - 62f, 118f, 62f, Colors.hex("#111111"), Colors.WHITE, 5f)
        else t.v.speed?.let { Kit.text(c, it, w - 18f, 150f, Kit.style(FontFace.BLACK_ITALIC, 16f), p.accent, Align.RIGHT, maxW = 110f) }
    }
}
