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
import com.wifizone.mikhmon.tickets.gfx.Radial
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.VPath

/** « Carte bancaire » : format carte (85,6 × 54 mm), puce dorée, code en relief. */
class CardTemplate(id: String, name: String, palette: Palette, private val chipGold: Boolean = true) :
    TicketTemplate(id, name, Category.ELEGANT, true, palette, 340f, 214f, "Format carte bancaire, puce et code en relief") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val card = VPath.round(0f, 0f, w, h, 18f)
        c.save(); c.clip(card)
        c.fill(card, Linear(0f, 0f, w, h, intArrayOf(Colors.lighten(p.primary, 0.08f), p.primary, Colors.darken(p.primary, 0.45f)), floatArrayOf(0f, 0.45f, 1f)))
        // reflets
        c.fill(VPath.circle(w * 0.95f, h * 1.05f, 150f), Solid(Colors.alpha(p.accent, 0.13f)))
        c.fill(VPath.circle(w * 1.08f, h * 0.55f, 105f), Solid(Colors.alpha(Colors.WHITE, 0.06f)))
        c.fill(VPath.rect(0f, 0f, w, h), Radial(40f, 10f, 230f, intArrayOf(Colors.alpha(Colors.WHITE, 0.20f), Colors.alpha(Colors.WHITE, 0f))))
        Kit.stripes(c, 0f, 0f, w, h, 9f, 0.6f, Colors.alpha(Colors.WHITE, 0.035f), 60f)
        c.restore()

        val fg = p.onPrimary
        val soft = Colors.alpha(fg, 0.72f)
        // en-tête : logo et nom du vendeur
        Kit.logoOrMotif(t, 20f, 18f, 26f, fg)
        Kit.text(c, Kit.headline(t), 54f, 31f, Kit.style(FontFace.BOLD, 14f), fg, maxW = 150f)
        if (t.b.tagline.isNotBlank()) Kit.text(c, t.b.tagline, 54f, 44f, Kit.style(FontFace.MEDIUM, 8.5f), soft, maxW = 150f)
        // prix
        Kit.price(t)?.let { pr ->
            Kit.text(c, pr, w - 20f, 38f, Kit.style(FontFace.BLACK, 26f), fg, Align.RIGHT, maxW = 120f)
        }
        t.v.validity?.let { Kit.text(c, it.uppercase(), w - 20f, 52f, Kit.style(FontFace.SEMIBOLD, 8.5f, 0.14f), soft, Align.RIGHT, maxW = 110f) }

        // puce
        val gx = 22f; val gy = 72f
        val gold = if (chipGold) intArrayOf(Colors.hex("#F7E3A1"), Colors.hex("#D4A84B"), Colors.hex("#A97A24")) else intArrayOf(Colors.hex("#F2F4F7"), Colors.hex("#BFC6CF"), Colors.hex("#8A939E"))
        c.fill(VPath.round(gx, gy, 44f, 34f, 6f), Linear(gx, gy, gx + 44f, gy + 34f, gold))
        val lines = Colors.alpha(Colors.darken(gold[2], 0.3f), 0.55f)
        Kit.line(c, gx + 15f, gy, gx + 15f, gy + 34f, lines, 0.8f)
        Kit.line(c, gx + 29f, gy, gx + 29f, gy + 34f, lines, 0.8f)
        Kit.line(c, gx, gy + 17f, gx + 15f, gy + 17f, lines, 0.8f)
        Kit.line(c, gx + 29f, gy + 11f, gx + 44f, gy + 11f, lines, 0.8f)
        Kit.line(c, gx + 29f, gy + 23f, gx + 44f, gy + 23f, lines, 0.8f)
        c.save(); c.translate(gx + 52f, gy + 4f); c.rotate(90f)
        Icons.draw(c, Icons.WIFI, 0f, -26f, 26f, Colors.alpha(fg, 0.85f))
        c.restore()

        val qr = t.qr
        val codeW = if (qr != null) 196f else w - 44f
        // code
        val pass = t.v.password
        val label = Kit.style(FontFace.SEMIBOLD, 7.5f, 0.2f)
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), 22f, 128f - Kit.codeLift(t, 28f), label, soft)
            embossed(t, t.v.username, 22f, 158f, 30f, codeW)
        } else {
            Kit.text(c, "UTILISATEUR", 22f, 126f - Kit.codeLift(t, 17f), label, soft)
            embossed(t, t.v.username, 22f, 148f, 20f, codeW * 0.52f)
            Kit.text(c, "MOT DE PASSE", 22f + codeW * 0.55f, 126f - Kit.codeLift(t, 17f), label, soft)
            embossed(t, pass, 22f + codeW * 0.55f, 148f, 20f, codeW * 0.45f)
        }
        // forfait
        Kit.chips(c, Kit.infos(t), 20f, 172f, codeW + 4f, 20f, Kit.style(FontFace.SEMIBOLD, 9f), Colors.alpha(fg, 0.16f), fg, icons = t.o.showIcons)
        val foot = listOfNotNull(Kit.dns(t)?.let { "Connexion : $it" }, Kit.contact(t), Kit.serial(t)).joinToString("   ·   ")
        Kit.text(c, foot, 22f, 204f, Kit.style(FontFace.MEDIUM, 7.5f), Colors.alpha(fg, 0.62f), maxW = codeW + 20f)
        if (qr != null) {
            Kit.qr(c, qr, w - 20f - 88f, 112f, 88f, Colors.hex("#111318"), Colors.WHITE, 10f)
        }
        c.stroke(VPath.round(0.5f, 0.5f, w - 1f, h - 1f, 18f), Colors.alpha(Colors.WHITE, 0.18f), Stroke(1f))
    }

    /** Code en relief : ombre sombre décalée puis texte clair. */
    private fun embossed(t: TicketContext, s: String, x: Float, y0: Float, size: Float, maxW: Float) {
        // Taille du code réglable : le code reste centré sur sa place.
        val base = Kit.style(FontFace.MONO_HEAVY, size, 0.08f)
        val st = Kit.fit(t.c, s, Kit.codeStyle(t, size, 0.08f), maxW, minScale = 0.3f)
        val y = y0 + (st.cap - base.cap) / 2f
        t.c.text(s, x + 1.2f, y + 1.4f, st, Solid(Colors.alpha(Colors.BLACK, 0.35f)))
        t.c.text(s, x, y, st, Linear(x, y - st.cap, x, y, intArrayOf(Colors.WHITE, Colors.lighten(t.p.accent, 0.35f))))
    }
}
