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

/** « Tech » : grille hexagonale, pistes de circuit, terminal. */
class TechTemplate(id: String, name: String, palette: Palette) :
    TicketTemplate(id, name, Category.BOLD, true, palette, 340f, 200f, "Circuit imprimé et terminal") {

    private fun hex(cx: Float, cy: Float, r: Float): VPath {
        val pts = FloatArray(12)
        for (i in 0 until 6) { val a = VPath.rad(60f * i + 30f); pts[i * 2] = cx + r * cos(a); pts[i * 2 + 1] = cy + r * sin(a) }
        return VPath().polygon(*pts)
    }

    override fun draw(t: TicketContext) {
        val c = t.c; val p = t.p
        val body = VPath.round(0f, 0f, w, h, 12f)
        c.save(); c.clip(body)
        c.fill(body, Linear(0f, 0f, w, h, intArrayOf(Colors.lighten(p.bg, 0.05f), p.bg)))
        // grille hexagonale
        val grid = VPath()
        val r = 11f
        val dx = r * 1.732f; val dy = r * 1.5f
        var row = 0
        var y = 0f
        while (y < h + r) {
            var x = if (row % 2 == 0) 0f else dx / 2f
            while (x < w + r) { grid.add(hex(x, y, r - 0.8f)); x += dx }
            y += dy; row++
        }
        c.stroke(grid, Colors.alpha(p.primary, 0.08f), Stroke(0.7f))
        // pistes de circuit
        val tr = VPath()
            .moveTo(0f, 189f).lineTo(40f, 189f).lineTo(47f, 195f).lineTo(150f, 195f)
            .moveTo(w, 182f).lineTo(w - 40f, 182f).lineTo(w - 52f, 170f)
        c.stroke(tr, Colors.alpha(p.primary, 0.45f), Stroke(1.2f))
        for ((nx, ny) in listOf(150f to 195f, w - 52f to 170f)) {
            c.fill(VPath.circle(nx, ny, 3f), Solid(p.primary)); c.fill(VPath.circle(nx, ny, 1.4f), Solid(p.bg))
        }
        c.restore()
        c.stroke(VPath.round(0.5f, 0.5f, w - 1f, h - 1f, 12f), Colors.alpha(p.primary, 0.35f), Stroke(1f))

        Kit.logoOrMotif(t, 18f, 16f, 22f, p.primary)
        Kit.text(c, Kit.headline(t).uppercase(), 48f, 32f, Kit.style(FontFace.MONO_HEAVY, 13f, 0.06f), p.text, maxW = 170f)
        Kit.price(t)?.let { Kit.text(c, it, w - 18f, 34f, Kit.style(FontFace.MONO_HEAVY, 19f), p.primary, Align.RIGHT, maxW = 110f) }
        val qr = t.qr
        val tw = if (qr != null) 206f else w - 36f
        // terminal
        val ty = 50f; val th = 84f
        c.fill(VPath.round(16f, ty, tw, th, 8f), Solid(Colors.alpha(Colors.BLACK, 0.45f)))
        c.stroke(VPath.round(16f, ty, tw, th, 8f), Colors.alpha(p.primary, 0.5f), Stroke(0.9f))
        for ((i, col) in listOf(Colors.hex("#FF5F57"), Colors.hex("#FEBC2E"), Colors.hex("#28C840")).withIndex()) {
            c.fill(VPath.circle(27f + i * 9f, ty + 10f, 2.6f), Solid(col))
        }
        Kit.text(c, "wifi@${t.b.dns.ifBlank { "hotspot" }}", 16f + tw - 10f, ty + 13f, Kit.style(FontFace.MONO, 6.5f), Colors.alpha(p.text, 0.45f), Align.RIGHT, maxW = tw - 60f)
        val mono = Kit.style(FontFace.MONO, 8f)
        val pass = t.v.password
        if (pass == null) {
            Kit.text(c, "$ login --code", 26f, ty + 32f, mono, Colors.alpha(p.primary, 0.9f))
            val st = Kit.text(c, t.v.username, 26f, ty + 64f, Kit.style(FontFace.MONO_HEAVY, 26f, 0.05f), p.text, maxW = tw - 40f)
            val cx = 26f + c.measure(t.v.username, st) + 5f
            c.fill(VPath.rect(cx, ty + 64f - st.cap, st.size * 0.5f, st.cap), Solid(Colors.alpha(p.primary, 0.85f)))
        } else {
            Kit.text(c, "$ login --user", 26f, ty + 31f, mono, Colors.alpha(p.primary, 0.9f))
            Kit.text(c, t.v.username, 26f, ty + 47f, Kit.style(FontFace.MONO_HEAVY, 15f, 0.03f), p.text, maxW = tw - 40f)
            Kit.text(c, "$ --password", 26f, ty + 62f, mono, Colors.alpha(p.primary, 0.9f))
            Kit.text(c, pass, 26f, ty + 77f, Kit.style(FontFace.MONO_HEAVY, 15f, 0.03f), p.text, maxW = tw - 40f)
        }
        Kit.chips(c, Kit.infos(t), 14f, 142f, tw + 4f, 18f, Kit.style(FontFace.MONO, 8f), Colors.alpha(p.primary, 0.14f), p.text, p.primary, icons = t.o.showIcons)
        val foot = listOfNotNull(Kit.dns(t)?.let { "> $it" }, Kit.contact(t), Kit.serial(t)).joinToString("   ")
        Kit.text(c, foot, 18f, 178f, Kit.style(FontFace.MONO, 7.5f), Colors.alpha(p.text, 0.6f), maxW = w - 120f)
        if (qr != null) {
            val s = 88f; val x = w - 18f - s; val y = 54f
            Kit.qr(c, qr, x, y, s, Colors.hex("#0A0F0A"), Colors.WHITE, 4f)
            val b = VPath()
            val L = 10f; val o = 5f
            b.moveTo(x - o, y - o + L).lineTo(x - o, y - o).lineTo(x - o + L, y - o)
            b.moveTo(x + s + o - L, y - o).lineTo(x + s + o, y - o).lineTo(x + s + o, y - o + L)
            b.moveTo(x - o, y + s + o - L).lineTo(x - o, y + s + o).lineTo(x - o + L, y + s + o)
            b.moveTo(x + s + o - L, y + s + o).lineTo(x + s + o, y + s + o).lineTo(x + s + o, y + s + o - L)
            c.stroke(b, p.primary, Stroke(1.8f, round = false))
        }
    }
}
