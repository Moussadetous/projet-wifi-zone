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
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.VPath

/** « Embarquement » : carte d'embarquement, du Wi-Fi vers Internet. */
class BoardingTemplate(id: String, name: String, palette: Palette) :
    TicketTemplate(id, name, Category.CLASSIC, true, palette, 400f, 172f, "Carte d'embarquement : départ Wi-Fi, arrivée Internet") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val body = VPath.round(0f, 0f, w, h, 14f)
        c.save(); c.clip(body)
        c.fill(body, Solid(p.surface))
        c.fill(VPath.rect(0f, 0f, w, 40f), Linear(0f, 0f, w, 0f, intArrayOf(p.primary, Colors.mix(p.primary, p.accent, 0.45f))))
        c.restore()
        val on = p.onPrimary
        Kit.logoOrMotif(t, 14f, 9f, 22f, on)
        Kit.text(c, Kit.headline(t).uppercase(), 44f, 26f, Kit.style(FontFace.BLACK, 13f, 0.08f), on, maxW = 230f)
        Kit.text(c, t.o.badge.ifBlank { "EMBARQUEMENT" }.uppercase(), 345f, 24f, Kit.style(FontFace.BOLD, 7f, 0.22f), Colors.alpha(on, 0.9f), Align.CENTER, maxW = 96f)

        // trajet : WIFI ✈ NET
        val mx = 290f
        Kit.text(c, "DÉPART", 16f, 58f, Kit.style(FontFace.BOLD, 6.5f, 0.25f), p.muted)
        Kit.text(c, "WIFI", 16f, 82f, Kit.style(FontFace.BLACK, 24f, 0.02f), p.text)
        Kit.text(c, "ARRIVÉE", mx - 18f, 58f, Kit.style(FontFace.BOLD, 6.5f, 0.25f), p.muted, Align.RIGHT)
        Kit.text(c, "NET", mx - 18f, 82f, Kit.style(FontFace.BLACK, 24f, 0.02f), p.text, Align.RIGHT)
        val ax = 92f; val bx = mx - 82f; val ay = 73f
        Kit.line(c, ax, ay, bx, ay, Colors.alpha(p.muted, 0.7f), 1.1f, floatArrayOf(2.5f, 3f))
        c.save(); c.translate((ax + bx) / 2f, ay); c.rotate(90f)
        Icons.draw(c, Icons.PLANE, -11f, -11f, 22f, p.primary)
        c.restore()

        // champs
        val pass = t.v.password
        val lab = Kit.style(FontFace.BOLD, 6.5f, 0.25f)
        val f1 = 16f; val fy = 100f
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), f1, fy - minOf(Kit.codeLift(t, 22f), 3f), lab, p.muted)
            Kit.codeText(t, t.v.username, f1, fy + 24f, 22f, 0.06f, p.primary, maxW = 150f, maxScale = 1.4f, down = 0f)
        } else {
            Kit.text(c, "UTILISATEUR", f1, fy - Kit.codeLift(t, 17f), lab, p.muted)
            Kit.codeText(t, t.v.username, f1, fy + 20f, 16f, 0.04f, p.primary, maxW = 120f, maxScale = 1.4f)
            Kit.text(c, "MOT DE PASSE", f1 + 128f, fy - Kit.codeLift(t, 17f), lab, p.muted)
            Kit.codeText(t, pass, f1 + 128f, fy + 20f, 16f, 0.04f, p.text, maxW = 100f, maxScale = 1.4f)
        }
        val fields = listOfNotNull(
            t.v.validity?.let { "VALIDITÉ" to it },
            ("VOLUME" to (t.v.data ?: "Illimité")),
            t.v.speed?.let { "DÉBIT" to it },
        )
        var fx = 16f
        for ((k, v) in fields.take(3)) {
            Kit.text(c, k, fx, 136f, lab, p.muted)
            Kit.text(c, v, fx, 150f, Kit.style(FontFace.BOLD, 11f), p.text, maxW = 80f)
            fx += 88f
        }
        // talon
        Kit.line(c, mx, 46f, mx, h - 8f, Colors.alpha(p.muted, 0.5f), 1f, floatArrayOf(3f, 3f))
        Kit.notch(c, mx, 40f, 7f, Colors.WHITE)
        Kit.notch(c, mx, h, 7f, Colors.WHITE)
        val sx = mx + 14f; val sw = w - sx - 14f
        Kit.price(t)?.let {
            Kit.text(c, "TARIF", sx, 58f, lab, p.muted)
            Kit.text(c, it, sx, 80f, Kit.style(FontFace.BLACK, 18f), p.text, maxW = sw)
        }
        val qr = t.qr
        if (qr != null) Kit.qr(c, qr, sx, 90f, 72f, p.text, Colors.WHITE, 5f)
        else Kit.logoOrMotif(t, sx + sw / 2f - 24f, 96f, 48f, p.primary)
        val foot = listOfNotNull(Kit.dns(t)?.let { "Connexion : $it" }, Kit.contact(t), Kit.serial(t)).joinToString("   ·   ")
        Kit.text(c, foot, 16f, 165f, Kit.style(FontFace.MEDIUM, 7f), p.muted, maxW = mx - 32f)
        c.stroke(VPath.round(0.5f, 0.5f, w - 1f, h - 1f, 14f), Colors.alpha(p.text, 0.10f), Stroke(1f))
    }
}
