package com.wifizone.mikhmon.tickets

import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Dessine chaque modèle (toujours, pour vérifier qu'il ne plante pas) et écrit les images si PREVIEW_DIR est défini. */
class PreviewTest {
    private val fonts = File("../app/src/main/assets/fonts")
    private val out = System.getenv("PREVIEW_DIR")?.let { File(it).apply { mkdirs() } }

    private val samples = listOf(
        Voucher("K7M4Q2", null, "1jour", "500", "24 h", "2 Go", "5 Mb/s", serial = 17),
        Voucher("wz-48213", "7731", "semaine", "2 500", "7 jours", null, "10 Mb/s", serial = 3),
    )
    private val brand = Branding("Wifi Zone Ouaga", "Internet rapide, partout", "wifi.zone", "+226 70 12 34 56", "F")

    private fun render(tpl: TicketTemplate, v: Voucher, scale: Float, opts: TicketOptions = TicketOptions()): BufferedImage {
        val img = BufferedImage((tpl.w * scale).toInt(), (tpl.h * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.color = java.awt.Color.WHITE; g.fillRect(0, 0, img.width, img.height)
        val c = Java2DCanvas(g, fonts)
        c.scale(scale)
        val qr = if (opts.showQr) "http://${brand.dns}/login?username=${v.username}&password=${v.password ?: v.username}" else null
        tpl.draw(TicketContext(c, v, brand, opts, tpl.palette.withOverrides(opts), qr))
        g.dispose()
        return img
    }

    @Test fun everyTemplateDraws() {
        for (tpl in Catalog.all) for ((i, v) in samples.withIndex()) {
            val img = render(tpl, v, if (out != null) 3f else 1f, TicketOptions(showQr = i == 0))
            assertTrue(img.width > 0)
            if (out != null) ImageIO.write(img, "png", File(out, "${tpl.id}-${i + 1}.png"))
        }
    }

    @Test fun catalogIdsAreUnique() {
        val ids = Catalog.all.map { it.id }
        assertTrue(ids.size == ids.toSet().size)
        assertTrue(ids.all { Regex("^[a-z0-9][a-z0-9._-]{0,59}$").matches(it) })
    }
}

class SheetAndVoucherTest {
    @Test fun a4PlanFitsTickets() {
        val tpl = Catalog.byId("tpl-carte-or")!!
        val plan = Sheet.plan(tpl, 25, PrintSettings(PaperFormat.A4))
        org.junit.Assert.assertEquals(2, plan.columns)
        org.junit.Assert.assertEquals(5, plan.rows)
        org.junit.Assert.assertEquals(3, plan.pages)
        for (s in plan.slots) {
            org.junit.Assert.assertTrue(s.x >= 0 && s.x + s.w <= plan.pageW + 0.01f)
            org.junit.Assert.assertTrue(s.y >= 0 && s.y + s.h <= plan.pageH + 0.01f)
        }
        val mini = Sheet.plan(Catalog.byId("std-mini")!!, 100, PrintSettings(PaperFormat.A4))
        org.junit.Assert.assertTrue("mini : ${mini.perPage} par page", mini.perPage >= 40)
        val roll = Sheet.plan(Catalog.byId("tpl-thermique-pro")!!, 3, PrintSettings(PaperFormat.ROLL58))
        org.junit.Assert.assertEquals(3, roll.pages)
    }

    @Test fun generatedCodesAreUniqueAndUnambiguous() {
        val existing = setOf("AAAA")
        val list = Vouchers.generate(Vouchers.Spec(300, length = 5), existing)
        org.junit.Assert.assertEquals(300, list.size)
        org.junit.Assert.assertEquals(300, list.map { it.first }.toSet().size)
        org.junit.Assert.assertTrue(list.none { it.first.any { ch -> ch in "0O1lI" } })
        val up = Vouchers.generate(Vouchers.Spec(5, userPass = true, prefix = "wz-"), emptySet())
        org.junit.Assert.assertTrue(up.all { it.first.startsWith("wz-") && it.second!!.length == 4 })
    }

    @Test fun mikhmonFormats() {
        val c = Vouchers.mikhmonComment(false, "Promo été!", java.util.GregorianCalendar(2026, 10, 17).time)
        org.junit.Assert.assertTrue(c, Regex("^vc-\\d{3}-11\\.17\\.26-Promo t$").matches(c))
        org.junit.Assert.assertTrue(Vouchers.isMikhmonBatch(c))
        val info = Vouchers.parseOnLogin(":put (\",rem,1000,1d,1500,,Disable,\"); {:local comment [ /ip hotspot user get")!!
        org.junit.Assert.assertEquals("1000", info.price)
        org.junit.Assert.assertEquals("1d", info.validity)
        org.junit.Assert.assertEquals("1500", info.sellingPrice)
        org.junit.Assert.assertEquals("1 jour", Vouchers.duration("1d"))
        org.junit.Assert.assertEquals("7 jours", Vouchers.duration("1w"))
        org.junit.Assert.assertEquals("2 h 30", Vouchers.duration("2h30m"))
        org.junit.Assert.assertEquals("30 min", Vouchers.duration("00:30:00"))
        org.junit.Assert.assertEquals("2 Go", Vouchers.bytes(2L * 1024 * 1024 * 1024))
        org.junit.Assert.assertEquals("500 Mo", Vouchers.bytes(500L * 1024 * 1024))
        org.junit.Assert.assertEquals("5 Mb/s", Vouchers.rate("2M/5M"))
        org.junit.Assert.assertEquals("1 000", Vouchers.price("1000"))
    }
}

class SheetPreviewTest {
    /** Page A4 complète (image) pour vérifier la mise en page et les traits de coupe. */
    @Test fun a4Page() {
        val out = System.getenv("PREVIEW_DIR")?.let { File(it) } ?: return
        val brand = Branding("Wifi Zone Ouaga", "", "wifi.zone", "+226 70 12 34 56", "F")
        for (id in listOf("tpl-carte-saphir", "std-mini", "tpl-billet-rouge")) {
            val tpl = Catalog.byId(id)!!
            val vs = Vouchers.generate(Vouchers.Spec(80), emptySet()).mapIndexed { i, (u, p) -> Voucher(u, p, "", "500", "24 h", "2 Go", null, serial = i + 1) }
            val st = PrintSettings(PaperFormat.A4)
            val plan = Sheet.plan(tpl, vs.size, st)
            val scale = 1.4f
            val img = BufferedImage((plan.pageW * scale).toInt(), (plan.pageH * scale).toInt(), BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val c = Java2DCanvas(g, File("../app/src/main/assets/fonts"))
            c.scale(scale)
            Sheet.drawPage(c, plan, 0, tpl, vs, brand, TicketOptions(), st)
            g.dispose()
            ImageIO.write(img, "png", File(out, "page-a4-$id.png"))
            println("$id : " + Sheet.summary(plan, vs.size))
        }
    }
}
