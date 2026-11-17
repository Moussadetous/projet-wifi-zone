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

/** « Polaroid » : format portrait, grande illustration et légende. */
class PolaroidTemplate(id: String, name: String, palette: Palette, private val sky: IntArray) :
    TicketTemplate(id, name, Category.FUN, true, palette, 210f, 280f, "Portrait façon photo instantanée") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val frame = VPath.round(0f, 0f, w, h, 8f)
        c.fill(frame, Solid(p.surface))
        c.stroke(frame, Colors.alpha(p.text, 0.12f), Stroke(1f))
        // photo
        val px = 12f; val py = 12f; val pw = w - 24f; val ph = 128f
        val photo = VPath.round(px, py, pw, ph, 4f)
        c.save(); c.clip(photo)
        c.fill(photo, Linear(0f, py, 0f, py + ph, sky))
        c.fill(VPath.rect(px, py, pw, ph), Radial(px + pw * 0.75f, py + ph * 0.3f, 70f, intArrayOf(Colors.alpha(Colors.WHITE, 0.55f), Colors.alpha(Colors.WHITE, 0f))))
        // collines
        c.fill(VPath().moveTo(px, py + ph * 0.78f).cubicTo(px + pw * 0.3f, py + ph * 0.6f, px + pw * 0.55f, py + ph * 0.9f, px + pw, py + ph * 0.7f)
            .lineTo(px + pw, py + ph).lineTo(px, py + ph).close(), Solid(Colors.alpha(Colors.darken(sky.last(), 0.35f), 0.7f)))
        c.restore()
        val logo = t.b.logo
        if (logo != null && t.o.showLogo) Kit.logoOrMotif(t, w / 2f - 30f, py + 26f, 60f, Colors.WHITE)
        else {
            Icons.draw(c, Kit.motif(t), w / 2f - 31f, py + 23f, 62f, Colors.alpha(Colors.BLACK, 0.15f))
            Icons.draw(c, Kit.motif(t), w / 2f - 30f, py + 22f, 60f, Colors.WHITE)
        }
        Kit.text(c, Kit.headline(t).uppercase(), w / 2f, py + ph - 14f, Kit.style(FontFace.BLACK, 11f, 0.12f), Colors.WHITE, Align.CENTER, maxW = pw - 20f)
        Kit.price(t)?.let { pr ->
            val st = Kit.style(FontFace.BLACK, 11f)
            val bw = c.measure(pr, st) + 16f
            c.fill(VPath.round(px + pw - bw - 6f, py + 6f, bw, 20f, 10f), Solid(p.primary))
            c.text(pr, px + pw - bw / 2f - 6f, py + 16f + st.cap / 2f, st, Solid(p.onPrimary), Align.CENTER)
        }
        // légende
        val pass = t.v.password
        val qr = t.qr
        val lab = Kit.style(FontFace.BOLD, 6.5f, 0.25f)
        val cw = if (qr != null) pw - 66f else pw
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), px, 160f - Kit.codeLift(t, 28f), lab, p.muted)
            Kit.codeText(t, t.v.username, px - 1f, 186f, 22f, 0.04f, p.text, maxW = cw)
        } else {
            Kit.text(c, "UTILISATEUR", px, 158f - Kit.codeLift(t, 17f), lab, p.muted)
            Kit.codeText(t, t.v.username, px - 1f, 174f, 14f, 0.03f, p.text, maxW = cw)
            Kit.text(c, "MOT DE PASSE", px, 188f, lab, p.muted)
            Kit.codeText(t, pass, px - 1f, 204f, 14f, 0.03f, p.primary, maxW = cw)
        }
        if (qr != null) Kit.qr(c, qr, px + pw - 60f, 150f, 60f, p.text, Colors.WHITE, 4f)
        val infos = Kit.infos(t)
        var iy = 222f
        for ((ic, label) in infos.take(2)) {
            if (t.o.showIcons) Icons.draw(c, ic, px, iy - 9f, 11f, p.primary)
            Kit.text(c, label, px + (if (t.o.showIcons) 15f else 0f), iy, Kit.style(FontFace.SEMIBOLD, 9f), p.text, maxW = pw / 2f)
            iy += 15f
        }
        if (infos.size > 2) {
            val (ic, label) = infos[2]
            if (t.o.showIcons) Icons.draw(c, ic, px + pw / 2f + 6f, 213f, 11f, p.primary)
            Kit.text(c, label, px + pw / 2f + 21f, 222f, Kit.style(FontFace.SEMIBOLD, 9f), p.text, maxW = pw / 2f - 21f)
        }
        val foot = listOfNotNull(Kit.dns(t), Kit.contact(t), Kit.serial(t)).joinToString("  ·  ")
        Kit.text(c, foot, w / 2f, 268f, Kit.style(FontFace.MEDIUM, 7f), p.muted, Align.CENTER, maxW = pw)
    }
}
