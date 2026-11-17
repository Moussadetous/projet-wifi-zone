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
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.VPath

/** « Coupon » : papier, pointillés à découper, tampon encreur. */
class CouponTemplate(id: String, name: String, palette: Palette) :
    TicketTemplate(id, name, Category.CLASSIC, true, palette, 340f, 182f, "Bon à découper avec tampon") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        c.fill(VPath.round(0f, 0f, w, h, 8f), Solid(p.bg))
        // Papier : léger dégradé (une seule forme, PDF léger).
        c.fill(VPath.round(0f, 0f, w, h, 8f), com.wifizone.mikhmon.tickets.gfx.Linear(0f, 0f, w, h, intArrayOf(Colors.lighten(p.bg, 0.25f), p.bg)))
        // bordure à découper
        c.stroke(VPath.round(7f, 7f, w - 14f, h - 14f, 6f), Colors.alpha(p.text, 0.55f), Stroke(1.2f, floatArrayOf(5f, 4f), round = false))
        c.fill(VPath.rect(18f, 1f, 22f, 12f), Solid(p.bg))
        Icons.draw(c, Icons.SCISSORS, 19f, -3f, 20f, Colors.alpha(p.text, 0.75f))

        // tampon
        val sx = w - 128f; val sy = 18f; val sw = 110f; val sh = 46f
        c.save(); c.translate(sx + sw / 2f, sy + sh / 2f); c.rotate(-6f)
        c.stroke(VPath.round(-sw / 2f, -sh / 2f, sw, sh, 8f), Colors.alpha(p.primary, 0.85f), Stroke(2f))
        c.stroke(VPath.round(-sw / 2f + 4f, -sh / 2f + 4f, sw - 8f, sh - 8f, 5f), Colors.alpha(p.primary, 0.6f), Stroke(0.8f))
        val price = Kit.price(t)
        Kit.text(c, t.o.badge.ifBlank { "COUPON WI-FI" }.uppercase(), 0f, if (price != null) -6f else 4f, Kit.style(FontFace.BLACK, 9.5f, 0.12f), p.primary, Align.CENTER, maxW = sw - 14f)
        if (price != null) Kit.text(c, price, 0f, 14f, Kit.style(FontFace.BLACK, 17f), p.primary, Align.CENTER, maxW = sw - 14f)
        c.restore()

        Kit.logoOrMotif(t, 22f, 22f, 22f, p.text)
        Kit.text(c, Kit.headline(t), 52f, 37f, Kit.style(FontFace.BLACK, 15f), p.text, maxW = sx - 62f)
        if (t.b.tagline.isNotBlank()) Kit.text(c, t.b.tagline, 52f, 50f, Kit.style(FontFace.MEDIUM, 8f), p.muted, maxW = sx - 62f)

        val qr = t.qr
        val cw = if (qr != null) 196f else w - 46f
        // boîte du code
        val by = 68f; val bh = 52f
        c.fill(VPath.round(20f, by, cw, bh, 8f), Solid(p.surface))
        c.stroke(VPath.round(20f, by, cw, bh, 8f), Colors.alpha(p.text, 0.25f), Stroke(1f))
        val pass = t.v.password
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), 30f, by + 14f - Kit.codeLift(t, 28f), Kit.style(FontFace.BOLD, 6.5f, 0.25f), p.muted)
            Kit.codeText(t, t.v.username, 20f + cw / 2f, by + 42f, 25f, 0.08f, p.text, Align.CENTER, maxW = cw - 20f)
        } else {
            Kit.text(c, "UTILISATEUR", 30f, by + 14f - Kit.codeLift(t, 17f), Kit.style(FontFace.BOLD, 6.5f, 0.25f), p.muted)
            Kit.codeText(t, t.v.username, 30f, by + 30f - Kit.codeLift(t, 14f), 14f, 0.04f, p.text, maxW = cw - 20f, maxScale = 1.6f)
            Kit.codeText(t, "MOT DE PASSE  $pass", 30f, by + 45f + Kit.codeLift(t, 12f), 12f, 0.04f, p.primary, maxW = cw - 20f, maxScale = 1.6f)
        }
        Kit.chips(c, Kit.infos(t), 18f, 130f, cw + 4f, 18f, Kit.style(FontFace.SEMIBOLD, 8.2f), null, p.text, p.primary, gap = 1f, icons = t.o.showIcons)
        val foot = listOfNotNull(Kit.dns(t)?.let { "Connexion : $it" }, Kit.contact(t), Kit.serial(t)).joinToString("   ·   ")
        Kit.text(c, foot, 22f, 166f, Kit.style(FontFace.MEDIUM, 7.5f), p.muted, maxW = if (qr != null) cw else w - 46f)
        if (qr != null) Kit.qr(c, qr, w - 20f - 84f, 76f, 84f, p.text, p.surface, 6f)
    }
}
