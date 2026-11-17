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
import com.wifizone.mikhmon.tickets.gfx.VPath

/** Imprimantes thermiques (rouleaux 58 et 80 mm) : noir pur, gros caractères, aucune trame. */
class ThermalTemplate(id: String, name: String, private val withQr: Boolean) :
    TicketTemplate(id, name, Category.THERMAL, true, Palette.of("#FFF", "#FFF", "#000", "#000", "#000", "#000"),
        240f, if (withQr) 318f else 206f, if (withQr) "Reçu thermique avec QR code" else "Reçu thermique compact") {

    override fun draw(t: TicketContext) {
        val c = t.c
        val k = Colors.BLACK
        val cx = w / 2f
        Kit.logoOrMotif(t, cx - 13f, 6f, 26f, k)
        Kit.text(c, Kit.headline(t).uppercase(), cx, 48f, Kit.style(FontFace.BLACK, 17f, 0.04f), k, Align.CENTER, maxW = w - 16f)
        if (t.b.tagline.isNotBlank()) Kit.text(c, t.b.tagline, cx, 61f, Kit.style(FontFace.SEMIBOLD, 9f), k, Align.CENTER, maxW = w - 16f)
        Kit.line(c, 6f, 70f, w - 6f, 70f, k, 1.4f, floatArrayOf(4f, 3f))
        var y = 70f
        Kit.price(t)?.let {
            y += 28f
            Kit.text(c, it, cx, y, Kit.style(FontFace.BLACK, 24f), k, Align.CENTER, maxW = w - 16f)
        }
        val pass = t.v.password
        if (pass == null) {
            y += 16f
            Kit.text(c, Kit.codeLabel(t), cx, y - Kit.codeLift(t, 28f), Kit.style(FontFace.BOLD, 8f, 0.3f), k, Align.CENTER)
            y += 32f
            // code en blanc sur bandeau noir
            val base = Kit.style(FontFace.MONO_HEAVY, 28f, 0.06f)
            val st = Kit.fit(c, t.v.username, Kit.codeStyle(t, 28f, 0.06f), w - 30f, minScale = 0.3f)
            val yc = y + (st.cap - base.cap) / 2f
            c.fill(VPath.round(8f, yc - st.cap - 9f, w - 16f, st.cap + 18f, 6f), Solid(k))
            c.text(t.v.username, cx, yc, st, Solid(Colors.WHITE), Align.CENTER)
        } else {
            y += 22f
            Kit.codeText(t, "UTILISATEUR : ${t.v.username}", cx, y, 14f, 0f, k, Align.CENTER, maxW = w - 16f)
            y += 22f
            Kit.codeText(t, "MOT DE PASSE : $pass", cx, y, 14f, 0f, k, Align.CENTER, maxW = w - 16f)
        }
        y += 24f
        val infos = Kit.infos(t)
        val line = infos.joinToString("   ") { it.second }
        if (t.o.showIcons && infos.size <= 3) {
            val st = Kit.style(FontFace.BOLD, 10.5f)
            val tot = infos.sumOf { (13f + 4f + c.measure(it.second, st)).toDouble() }.toFloat() + 10f * (infos.size - 1)
            var x = cx - tot / 2f
            for ((ic, label) in infos) {
                Icons.draw(c, ic, x, y - 10.5f, 13f, k)
                c.text(label, x + 17f, y, st, Solid(k))
                x += 17f + c.measure(label, st) + 10f
            }
        } else Kit.text(c, line, cx, y, Kit.style(FontFace.BOLD, 10.5f), k, Align.CENTER, maxW = w - 16f)
        val qr = t.qr
        if (withQr && qr != null) {
            y += 10f
            Kit.qr(c, qr, cx - 45f, y, 90f, k, null)
            y += 96f
        }
        y += 12f
        Kit.dns(t)?.let { Kit.text(c, "Connexion : $it", cx, y, Kit.style(FontFace.BOLD, 9f), k, Align.CENTER, maxW = w - 16f); y += 12f }
        val foot = listOfNotNull(Kit.contact(t), Kit.serial(t), t.o.footer.ifBlank { null }).joinToString("  ·  ")
        Kit.text(c, foot, cx, y, Kit.style(FontFace.SEMIBOLD, 8.5f), k, Align.CENTER, maxW = w - 16f)
        Kit.line(c, 6f, h - 3f, w - 6f, h - 3f, k, 1.4f, floatArrayOf(4f, 3f))
    }
}
