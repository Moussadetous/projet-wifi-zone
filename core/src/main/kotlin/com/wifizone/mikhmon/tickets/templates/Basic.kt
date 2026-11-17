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

/** Modèles gratuits : « Standard » (comme Mikhmon, en plus net) et « Mini » (très dense, économe). */
class StandardTemplate : TicketTemplate("std-standard", "Standard", Category.CLASSIC, false,
    Palette.of("#FFF", "#FFFFFF", "#2563EB", "#2563EB", "#111827", "#6B7280"), 300f, 168f, "Le ticket classique, gratuit") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val box = VPath.rect(0.8f, 0.8f, w - 1.6f, h - 1.6f)
        c.fill(box, Solid(p.surface))
        c.stroke(box, p.text, Stroke(1.4f, round = false))
        c.fill(VPath.rect(0.8f, 0.8f, w - 1.6f, 32f), Solid(p.primary))
        Kit.text(c, Kit.headline(t), 12f, 22f, Kit.style(FontFace.BOLD, 13f), p.onPrimary, maxW = 180f)
        Kit.price(t)?.let { Kit.text(c, it, w - 12f, 22f, Kit.style(FontFace.BLACK, 14f), p.onPrimary, Align.RIGHT, maxW = 100f) }
        val qr = t.qr
        val cw = if (qr != null) w - 110f else w - 24f
        val pass = t.v.password
        if (pass == null) {
            Kit.text(c, "Code", 12f, 54f, Kit.style(FontFace.MEDIUM, 9f), p.muted)
            Kit.codeText(t, t.v.username, 12f, 84f, 26f, 0.04f, p.text, maxW = cw)
        } else {
            Kit.text(c, "Utilisateur", 12f, 52f, Kit.style(FontFace.MEDIUM, 9f), p.muted)
            Kit.codeText(t, t.v.username, 12f, 70f, 16f, 0f, p.text, maxW = cw)
            Kit.text(c, "Mot de passe", 12f, 86f, Kit.style(FontFace.MEDIUM, 9f), p.muted)
            Kit.codeText(t, pass, 12f, 104f, 16f, 0f, p.text, maxW = cw)
        }
        val info = Kit.infos(t).joinToString("   ") { it.second }
        Kit.text(c, info, 12f, 128f, Kit.style(FontFace.SEMIBOLD, 10f), p.text, maxW = cw)
        val foot = listOfNotNull(Kit.dns(t)?.let { "Connexion : $it" }, Kit.contact(t), Kit.serial(t)).joinToString("  ·  ")
        Kit.text(c, foot, 12f, 156f, Kit.style(FontFace.REGULAR, 8f), p.muted, maxW = w - 24f)
        if (qr != null) Kit.qr(c, qr, w - 12f - 84f, 44f, 84f, p.text, null)
    }
}

class MiniTemplate : TicketTemplate("std-mini", "Mini", Category.CLASSIC, false,
    Palette.of("#FFF", "#FFFFFF", "#111827", "#111827", "#111827", "#6B7280"), 200f, 104f, "Très petit format : 44 tickets par page A4") {

    override val naturalWidthMm get() = 44f

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        c.stroke(VPath.rect(0.6f, 0.6f, w - 1.2f, h - 1.2f), Colors.alpha(p.text, 0.6f), Stroke(0.9f, floatArrayOf(3f, 2f), round = false))
        Kit.text(c, Kit.headline(t), 9f, 17f, Kit.style(FontFace.BOLD, 9.5f), p.text, maxW = 120f)
        Kit.price(t)?.let { Kit.text(c, it, w - 9f, 17f, Kit.style(FontFace.BLACK, 10f), p.text, Align.RIGHT, maxW = 70f) }
        val pass = t.v.password
        if (pass == null) Kit.codeText(t, t.v.username, w / 2f, 58f, 24f, 0.05f, p.text, Align.CENTER, maxW = w - 18f)
        else {
            Kit.codeText(t, "U : ${t.v.username}", 9f, 44f, 13f, 0f, p.text, maxW = w - 18f)
            Kit.codeText(t, "P : $pass", 9f, 62f, 13f, 0f, p.text, maxW = w - 18f)
        }
        val info = Kit.infos(t).joinToString("  ·  ") { it.second }
        Kit.text(c, info, w / 2f, 80f, Kit.style(FontFace.SEMIBOLD, 8f), p.text, Align.CENTER, maxW = w - 18f)
        Kit.dns(t)?.let { Kit.text(c, it, w / 2f, 95f, Kit.style(FontFace.REGULAR, 7f), p.muted, Align.CENTER, maxW = w - 18f) }
    }
}
