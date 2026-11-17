package com.wifizone.mikhmon.tickets.templates

import com.wifizone.mikhmon.tickets.Category
import com.wifizone.mikhmon.tickets.Kit
import com.wifizone.mikhmon.tickets.Palette
import com.wifizone.mikhmon.tickets.TicketContext
import com.wifizone.mikhmon.tickets.TicketTemplate
import com.wifizone.mikhmon.tickets.gfx.Align
import com.wifizone.mikhmon.tickets.gfx.Canvas2D
import com.wifizone.mikhmon.tickets.gfx.Colors
import com.wifizone.mikhmon.tickets.gfx.FontFace
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.VPath

/** Motifs africains : Kente (tissage), Bogolan (terre et signes), Wax (cercles). */
class AfricaTemplate(id: String, name: String, palette: Palette, private val motif: Motif, private val colors: IntArray) :
    TicketTemplate(id, name, Category.AFRICA, true, palette, 340f, 200f, "Motif ${motif.label} en bandeau") {

    enum class Motif(val label: String) { KENTE("kente"), BOGOLAN("bogolan"), WAX("wax") }

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val body = VPath.round(0f, 0f, w, h, 14f)
        c.save(); c.clip(body)
        c.fill(body, Solid(p.surface))
        val bw = 74f
        c.save(); c.clip(VPath.rect(0f, 0f, bw, h))
        when (motif) {
            Motif.KENTE -> kente(c, 0f, 0f, bw, h)
            Motif.BOGOLAN -> bogolan(c, 0f, 0f, bw, h)
            Motif.WAX -> wax(c, 0f, 0f, bw, h)
        }
        c.restore()
        c.fill(VPath.rect(bw, 0f, 3f, h), Solid(p.primary))
        c.restore()
        c.stroke(VPath.round(0.5f, 0.5f, w - 1f, h - 1f, 14f), Colors.alpha(p.text, 0.12f), Stroke(1f))

        val x0 = bw + 18f; val mw = w - x0 - 16f
        Kit.logoOrMotif(t, x0, 16f, 20f, p.primary)
        Kit.text(c, Kit.headline(t), x0 + 27f, 31f, Kit.style(FontFace.BLACK, 15f), p.text, maxW = mw - 110f)
        if (t.b.tagline.isNotBlank()) Kit.text(c, t.b.tagline, x0 + 27f, 44f, Kit.style(FontFace.MEDIUM, 8f), p.muted, maxW = mw - 110f)
        Kit.price(t)?.let { pr ->
            val st = Kit.fit(c, pr, Kit.style(FontFace.BLACK, 15f), 86f)
            val pw = c.measure(pr, st) + 22f
            c.fill(VPath.round(w - 16f - pw, 14f, pw, 30f, 15f), Solid(p.primary))
            c.text(pr, w - 16f - pw / 2f, 29f + st.cap / 2f, st, Solid(p.onPrimary), Align.CENTER)
        }
        val qr = t.qr
        val cw = if (qr != null) mw - 92f else mw
        val pass = t.v.password
        val lab = Kit.style(FontFace.BOLD, 7f, 0.25f)
        if (pass == null) {
            Kit.text(c, Kit.codeLabel(t), x0, 72f, lab, p.muted)
            Kit.text(c, t.v.username, x0, 104f, Kit.style(FontFace.MONO_HEAVY, 28f, 0.05f), p.text, maxW = cw)
        } else {
            Kit.text(c, "UTILISATEUR", x0, 68f, lab, p.muted)
            Kit.text(c, t.v.username, x0, 88f, Kit.style(FontFace.MONO_HEAVY, 17f, 0.04f), p.text, maxW = cw)
            Kit.text(c, "MOT DE PASSE", x0, 104f, lab, p.muted)
            Kit.text(c, pass, x0, 124f, Kit.style(FontFace.MONO_HEAVY, 17f, 0.04f), p.primary, maxW = cw)
        }
        Kit.chips(c, Kit.infos(t), x0 - 2f, 150f, mw + 4f, 19f, Kit.style(FontFace.SEMIBOLD, 8.6f),
            Colors.alpha(p.primary, 0.12f), p.text, p.primary, icons = t.o.showIcons)
        val foot = listOfNotNull(Kit.dns(t)?.let { "Connexion : $it" }, Kit.contact(t), Kit.serial(t)).joinToString("  ·  ")
        Kit.text(c, foot, x0, 187f, Kit.style(FontFace.MEDIUM, 7.5f), p.muted, maxW = mw)
        if (qr != null) Kit.qr(c, qr, w - 16f - 80f, 58f, 80f, p.text, Colors.WHITE, 6f)
    }

    private fun kente(c: Canvas2D, x: Float, y: Float, w: Float, h: Float) {
        val cols = 2; val rows = 8
        val cw = w / cols; val rh = h / rows
        for (r in 0 until rows) for (k in 0 until cols) {
            val bx = x + k * cw; val by = y + r * rh
            val main = colors[(r + k) % colors.size]
            c.fill(VPath.rect(bx, by, cw, rh), Solid(main))
            if ((r + k) % 2 == 0) {
                // fines rayures horizontales
                val s = VPath()
                var yy = by + 3f
                while (yy < by + rh - 2f) { s.rect(bx, yy, cw, 1.4f); yy += 4.2f }
                c.fill(s, Solid(colors[(r + k + 2) % colors.size]))
            } else {
                // losange central et pointes
                val mx = bx + cw / 2f; val my = by + rh / 2f
                c.fill(VPath().polygon(mx, by + 3f, bx + cw - 4f, my, mx, by + rh - 3f, bx + 4f, my), Solid(colors[(r + k + 1) % colors.size]))
                c.fill(VPath().polygon(mx, by + 8f, bx + cw - 10f, my, mx, by + rh - 8f, bx + 10f, my), Solid(colors[(r + k + 3) % colors.size]))
            }
        }
        // fils verticaux
        c.fill(VPath.rect(x + cw - 1f, y, 2f, h), Solid(Colors.alpha(Colors.BLACK, 0.25f)))
    }

    private fun bogolan(c: Canvas2D, x: Float, y: Float, w: Float, h: Float) {
        c.fill(VPath.rect(x, y, w, h), Solid(colors[0]))
        val ink = colors[1]
        val cell = 25f
        var r = 0
        var yy = y
        while (yy < y + h) {
            var k = 0
            var xx = x
            while (xx < x + w) {
                val cx = xx + cell / 2f; val cy = yy + cell / 2f
                when ((r * 3 + k) % 4) {
                    0 -> { // zigzag
                        val z = VPath().moveTo(xx + 3f, cy + 4f)
                        var i = 0
                        var zx = xx + 3f
                        while (zx < xx + cell - 3f) { zx += 4.5f; z.lineTo(zx, if (i % 2 == 0) cy - 4f else cy + 4f); i++ }
                        c.stroke(z, ink, Stroke(1.6f, round = false))
                    }
                    1 -> { // points
                        val d = VPath()
                        for (a in 0..2) for (b in 0..2) d.circle(xx + 6f + a * 6.5f, yy + 6f + b * 6.5f, 1.3f)
                        c.fill(d, Solid(ink))
                    }
                    2 -> { // croix
                        c.fill(VPath().rect(cx - 1.3f, yy + 5f, 2.6f, cell - 10f).rect(xx + 5f, cy - 1.3f, cell - 10f, 2.6f), Solid(ink))
                    }
                    else -> { // cercle et point
                        c.stroke(VPath.circle(cx, cy, 7f), ink, Stroke(1.6f))
                        c.fill(VPath.circle(cx, cy, 2.2f), Solid(ink))
                    }
                }
                xx += cell; k++
            }
            c.fill(VPath.rect(x, yy + cell - 0.8f, w, 1.6f), Solid(Colors.alpha(ink, 0.6f)))
            yy += cell; r++
        }
    }

    private fun wax(c: Canvas2D, x: Float, y: Float, w: Float, h: Float) {
        c.fill(VPath.rect(x, y, w, h), Solid(colors[0]))
        val step = 30f
        var r = 0
        var yy = y + 12f
        while (yy < y + h + 15f) {
            var xx = x + if (r % 2 == 0) 10f else 25f
            while (xx < x + w + 15f) {
                c.fill(VPath.circle(xx, yy, 13f), Solid(colors[1]))
                c.fill(VPath.circle(xx, yy, 9.5f), Solid(colors[2]))
                c.fill(VPath.circle(xx, yy, 6f), Solid(colors[3 % colors.size]))
                c.fill(VPath.circle(xx, yy, 2.6f), Solid(colors[1]))
                xx += step
            }
            yy += step * 0.86f; r++
        }
    }
}
