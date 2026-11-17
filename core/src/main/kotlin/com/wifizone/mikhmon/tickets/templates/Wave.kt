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

/** « Vague » : vagues superposées en bas, pastille de prix ronde. */
class WaveTemplate(id: String, name: String, palette: Palette) :
    TicketTemplate(id, name, Category.FUN, true, palette, 340f, 196f, "Vagues colorées et pastille de prix") {

    private fun wave(y: Float, amp: Float, phase: Float): VPath {
        val p = VPath().moveTo(0f, y)
        val seg = w / 3f
        var x = 0f
        var up = phase > 0f
        while (x < w) {
            val nx = x + seg
            p.cubicTo(x + seg * 0.35f, y + if (up) -amp else amp, nx - seg * 0.35f, y + if (up) -amp else amp, nx, y)
            x = nx; up = !up
        }
        return p.lineTo(w, h).lineTo(0f, h).close()
    }

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val body = VPath.round(0f, 0f, w, h, 18f)
        c.save(); c.clip(body)
        c.fill(body, Solid(p.surface))
        c.fill(wave(150f, 14f, 1f), Solid(Colors.alpha(p.accent, 0.35f)))
        c.fill(wave(160f, 12f, -1f), Linear(0f, 150f, w, h, intArrayOf(Colors.alpha(p.primary, 0.75f), Colors.alpha(p.accent, 0.75f))))
        c.fill(wave(172f, 9f, 1f), Linear(0f, 160f, w, h, intArrayOf(p.primary, Colors.darken(p.primary, 0.2f))))
        c.restore()
        c.stroke(VPath.round(0.5f, 0.5f, w - 1f, h - 1f, 18f), Colors.alpha(p.text, 0.10f), Stroke(1f))

        Kit.logoOrMotif(t, 18f, 16f, 22f, p.primary)
        Kit.text(c, Kit.headline(t), 47f, 32f, Kit.style(FontFace.BLACK, 16f), p.text, maxW = 180f)
        if (t.b.tagline.isNotBlank()) Kit.text(c, t.b.tagline, 47f, 45f, Kit.style(FontFace.MEDIUM, 8f), p.muted, maxW = 180f)
        // pastille de prix
        Kit.price(t)?.let { pr ->
            val cx = w - 44f; val cy = 40f
            c.fill(VPath.circle(cx, cy + 2f, 30f), Solid(Colors.alpha(p.primary, 0.18f)))
            c.fill(VPath.circle(cx, cy, 30f), Linear(cx - 30f, cy - 30f, cx + 30f, cy + 30f, intArrayOf(p.accent, p.primary)))
            val parts = pr.split(" ")
            val amount = parts.dropLast(1).joinToString(" ").ifBlank { pr }
            val cur = if (parts.size > 1) parts.last() else ""
            Kit.text(c, amount, cx, cy + 4f, Kit.style(FontFace.BLACK, 17f), p.onPrimary, Align.CENTER, maxW = 50f)
            if (cur.isNotEmpty()) Kit.text(c, cur, cx, cy + 16f, Kit.style(FontFace.BOLD, 8f, 0.1f), Colors.alpha(p.onPrimary, 0.85f), Align.CENTER)
        }
        val qr = t.qr
        val cw = if (qr != null) 200f else w - 40f
        val pass = t.v.password
        val lab = Kit.style(FontFace.BOLD, 7f, 0.25f)
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), 20f, 76f - Kit.codeLift(t, 28f), lab, p.muted)
            Kit.codeText(t, t.v.username, 19f, 106f, 28f, 0.05f, p.text, maxW = cw)
        } else {
            Kit.text(c, "UTILISATEUR", 20f, 72f - Kit.codeLift(t, 17f), lab, p.muted)
            Kit.codeText(t, t.v.username, 19f, 91f, 17f, 0.04f, p.text, maxW = cw)
            Kit.text(c, "MOT DE PASSE", 20f, 106f, lab, p.muted)
            Kit.codeText(t, pass, 19f, 125f, 17f, 0.04f, p.primary, maxW = cw)
        }
        Kit.chips(c, Kit.infos(t), 17f, 133f, cw + 4f, 18f, Kit.style(FontFace.SEMIBOLD, 8.2f),
            Colors.alpha(p.primary, 0.10f), p.text, p.primary, icons = t.o.showIcons)
        val foot = listOfNotNull(Kit.dns(t)?.let { "Connexion : $it" }, Kit.contact(t), Kit.serial(t)).joinToString("   ·   ")
        Kit.text(c, foot, w / 2f, 187f, Kit.style(FontFace.SEMIBOLD, 7.8f), Colors.alpha(p.onPrimary, 0.95f), Align.CENTER, maxW = w - 40f)
        if (qr != null) Kit.qr(c, qr, w - 24f - 86f, 76f, 86f, p.text, Colors.WHITE, 8f)
    }
}
