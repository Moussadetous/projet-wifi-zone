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
import com.wifizone.mikhmon.tickets.gfx.Radial
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.VPath

/** « Aurore » : dégradé en nappes de couleur et panneau de verre dépoli. */
class AuroraTemplate(id: String, name: String, palette: Palette, private val third: Int) :
    TicketTemplate(id, name, Category.ELEGANT, true, palette, 340f, 200f, "Dégradé lumineux et panneau de verre") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val body = VPath.round(0f, 0f, w, h, 20f)
        c.save(); c.clip(body)
        c.fill(body, Linear(0f, 0f, w, h, intArrayOf(p.primary, Colors.mix(p.primary, p.accent, 0.5f), p.accent)))
        c.fill(VPath.rect(0f, 0f, w, h), Radial(w * 0.85f, h * 0.05f, 170f, intArrayOf(Colors.alpha(third, 0.85f), Colors.alpha(third, 0f))))
        c.fill(VPath.rect(0f, 0f, w, h), Radial(w * 0.08f, h * 1.0f, 190f, intArrayOf(Colors.alpha(Colors.lighten(p.accent, 0.2f), 0.75f), Colors.alpha(p.accent, 0f))))
        c.fill(VPath.rect(0f, 0f, w, h), Radial(w * 0.5f, h * 0.55f, 120f, intArrayOf(Colors.alpha(Colors.WHITE, 0.16f), Colors.alpha(Colors.WHITE, 0f))))
        c.restore()
        val on = Colors.WHITE
        Kit.logoOrMotif(t, 18f, 16f, 24f, on)
        Kit.text(c, Kit.headline(t), 50f, 33f, Kit.style(FontFace.BLACK, 16f), on, maxW = 170f)
        Kit.price(t)?.let { Kit.text(c, it, w - 18f, 35f, Kit.style(FontFace.BLACK, 22f), on, Align.RIGHT, maxW = 110f) }

        // panneau de verre
        val qr = t.qr
        val px = 14f; val py = 52f; val pw = w - 28f; val ph = 106f
        val panel = VPath.round(px, py, pw, ph, 16f)
        c.fill(panel, Linear(px, py, px + pw, py + ph, intArrayOf(Colors.alpha(Colors.WHITE, 0.30f), Colors.alpha(Colors.WHITE, 0.14f))))
        c.stroke(panel, Colors.alpha(Colors.WHITE, 0.45f), Stroke(1f))
        val cw = if (qr != null) pw - 110f else pw - 28f
        val pass = t.v.password
        val lab = Kit.style(FontFace.BOLD, 7f, 0.25f)
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), px + 16f, py + 26f - Kit.codeLift(t, 28f), lab, Colors.alpha(on, 0.8f))
            Kit.codeText(t, t.v.username, px + 16f, py + 60f, 30f, 0.06f, on, maxW = cw)
        } else {
            Kit.text(c, "UTILISATEUR", px + 16f, py + 19f - Kit.codeLift(t, 17f), lab, Colors.alpha(on, 0.8f))
            Kit.codeText(t, t.v.username, px + 16f, py + 37f, 17f, 0.04f, on, maxW = cw)
            Kit.text(c, "MOT DE PASSE", px + 16f, py + 51f, lab, Colors.alpha(on, 0.8f))
            Kit.codeText(t, pass, px + 16f, py + 69f, 17f, 0.04f, on, maxW = cw)
        }
        Kit.chips(c, Kit.infos(t), px + 13f, py + ph - 26f, cw + 6f, 18f, Kit.style(FontFace.SEMIBOLD, 8.2f),
            Colors.alpha(Colors.WHITE, 0.22f), on, icons = t.o.showIcons)
        if (qr != null) Kit.qr(c, qr, px + pw - 94f, py + 12f, 82f, Colors.hex("#141420"), Colors.WHITE, 10f)
        val foot = listOfNotNull(Kit.dns(t)?.let { "Connexion : $it" }, Kit.contact(t), Kit.serial(t)).joinToString("   ·   ")
        Kit.text(c, foot, w / 2f, 184f, Kit.style(FontFace.SEMIBOLD, 8f), Colors.alpha(on, 0.85f), Align.CENTER, maxW = w - 36f)
    }
}
