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
import kotlin.math.cos
import kotlin.math.sin

/** « Fête » : confettis, ruban en coin et sceau étoilé du prix. */
class RibbonTemplate(id: String, name: String, palette: Palette, private val confetti: IntArray) :
    TicketTemplate(id, name, Category.FUN, true, palette, 330f, 196f, "Confettis, ruban et sceau du prix") {

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val body = VPath.round(0f, 0f, w, h, 16f)
        c.save(); c.clip(body)
        c.fill(body, Solid(p.surface))
        // confettis (toujours les mêmes pour un modèle donné)
        var seed = id.hashCode().toLong() and 0x7fffffff
        fun rnd(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 10000) / 10000f }
        repeat(160) {
            val x = rnd() * w; val y = rnd() * h
            // confettis seulement sur les bords : le texte reste parfaitement lisible
            val edge = y < 12f || y > h - 8f || x > w - 14f || (x < 14f && y > 90f)
            if (!edge) return@repeat
            val col = Colors.alpha(confetti[(rnd() * confetti.size).toInt() % confetti.size], 0.85f)
            if (rnd() > 0.5f) c.fill(VPath.circle(x, y, 1.6f + rnd() * 1.8f), Solid(col))
            else { c.save(); c.translate(x, y); c.rotate(rnd() * 180f); c.fill(VPath.round(-4f, -1.5f, 8f, 3f, 1f), Solid(col)); c.restore() }
        }
        // ruban en coin
        c.save(); c.translate(0f, 0f)
        val rb = VPath().polygon(0f, 52f, 52f, 0f, 84f, 0f, 0f, 84f)
        c.fill(rb, Linear(0f, 0f, 84f, 84f, intArrayOf(p.primary, Colors.darken(p.primary, 0.25f))))
        c.restore()
        c.save(); c.translate(28f, 28f); c.rotate(-45f)
        Kit.text(c, t.o.badge.ifBlank { "WI-FI" }.uppercase(), 0f, 4f, Kit.style(FontFace.BLACK, 9f, 0.18f), p.onPrimary, Align.CENTER, maxW = 60f)
        c.restore()
        c.restore()
        c.stroke(VPath.round(0.5f, 0.5f, w - 1f, h - 1f, 16f), Colors.alpha(p.text, 0.10f), Stroke(1f))

        Kit.text(c, Kit.headline(t), 70f, 34f, Kit.style(FontFace.BLACK, 17f), p.text, maxW = 150f)
        if (t.b.tagline.isNotBlank()) Kit.text(c, t.b.tagline, 70f, 47f, Kit.style(FontFace.MEDIUM, 8f), p.muted, maxW = 150f)
        // sceau du prix
        val price = Kit.price(t)
        val qr = t.qr
        val sealX = w - 52f; val sealY = 52f
        if (price != null) {
            val n = 18; val pts = FloatArray(n * 4)
            for (i in 0 until n * 2) {
                val rr = if (i % 2 == 0) 38f else 33f
                val a = VPath.rad(i * 180f / n)
                pts[i * 2] = sealX + rr * cos(a); pts[i * 2 + 1] = sealY + rr * sin(a)
            }
            c.fill(VPath().polygon(*pts), Linear(sealX - 38f, sealY - 38f, sealX + 38f, sealY + 38f, intArrayOf(p.accent, Colors.darken(p.accent, 0.2f))))
            c.stroke(VPath.circle(sealX, sealY, 27f), Colors.alpha(Colors.onColor(p.accent), 0.5f), Stroke(0.8f, floatArrayOf(2f, 2f)))
            val parts = price.split(" ")
            val amount = parts.dropLast(1).joinToString(" ").ifBlank { price }
            val on = Colors.onColor(p.accent)
            Kit.text(c, amount, sealX, sealY + 3f, Kit.style(FontFace.BLACK, 16f), on, Align.CENTER, maxW = 46f)
            if (parts.size > 1) Kit.text(c, parts.last(), sealX, sealY + 15f, Kit.style(FontFace.BOLD, 7.5f, 0.1f), on, Align.CENTER)
        }
        val cw = if (qr != null) 196f else w - 46f
        val pass = t.v.password
        val lab = Kit.style(FontFace.BOLD, 7f, 0.25f)
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), 24f, 82f, lab, p.muted)
            Kit.text(c, t.v.username, 23f, 112f, Kit.style(FontFace.MONO_HEAVY, 28f, 0.05f), p.primary, maxW = cw)
        } else {
            Kit.text(c, "UTILISATEUR", 24f, 78f, lab, p.muted)
            Kit.text(c, t.v.username, 23f, 97f, Kit.style(FontFace.MONO_HEAVY, 17f, 0.04f), p.primary, maxW = cw)
            Kit.text(c, "MOT DE PASSE", 24f, 113f, lab, p.muted)
            Kit.text(c, pass, 23f, 132f, Kit.style(FontFace.MONO_HEAVY, 17f, 0.04f), p.text, maxW = cw)
        }
        Kit.chips(c, Kit.infos(t), 21f, 142f, cw + 4f, 18f, Kit.style(FontFace.SEMIBOLD, 8.2f),
            Colors.alpha(p.primary, 0.10f), p.text, p.primary, icons = t.o.showIcons)
        val foot = listOfNotNull(Kit.dns(t)?.let { "Connexion : $it" }, Kit.contact(t), Kit.serial(t)).joinToString("  ·  ")
        Kit.text(c, foot, 24f, 182f, Kit.style(FontFace.MEDIUM, 7.5f), p.muted, maxW = w - 48f)
        if (qr != null) Kit.qr(c, qr, w - 20f - 80f, 98f, 80f, p.text, Colors.WHITE, 6f)
    }
}
