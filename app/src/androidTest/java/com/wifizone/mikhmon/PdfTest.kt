package com.wifizone.mikhmon

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wifizone.mikhmon.print.PdfExporter
import com.wifizone.mikhmon.tickets.Branding
import com.wifizone.mikhmon.tickets.Catalog
import com.wifizone.mikhmon.tickets.PaperFormat
import com.wifizone.mikhmon.tickets.PrintSettings
import com.wifizone.mikhmon.tickets.TicketOptions
import com.wifizone.mikhmon.tickets.Voucher
import com.wifizone.mikhmon.tickets.Vouchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Export PDF natif : nombre de pages, poids du fichier, et enregistrement dans Téléchargements. */
@RunWith(AndroidJUnit4::class)
class PdfTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val brand = Branding("Wifi Zone Ouaga", "Internet rapide", "wifi.zone", "+226 70 12 34 56", "F")
    private val vouchers = Vouchers.generate(Vouchers.Spec(25), emptySet()).mapIndexed { i, (u, p) ->
        Voucher(u, p, "1jour", "500", "24 h", "2 Go", "5 Mb/s", serial = i + 1)
    }

    @Test fun everyTemplateExportsALightPdf(): Unit = runBlocking {
        val report = StringBuilder()
        for (t in Catalog.all) {
            val s = PrintSettings(if (t.id.startsWith("tpl-thermique")) PaperFormat.ROLL58 else PaperFormat.A4)
            val f = PdfExporter.exportToCache(ctx, t, vouchers, brand, TicketOptions(), s)
            val pages = PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
            report.append("${t.id} : $pages page(s), ${f.length() / 1024} Ko\n")
            assertTrue("${t.id} : PDF vide", f.length() > 1000)
            assertTrue("${t.id} : PDF trop lourd (${f.length() / 1024} Ko)", f.length() < 2_500_000)
            f.delete()
        }
        Log.i("PdfTest", "\n$report")
        println(report)
        Unit
    }

    @Test fun a4CardsLayout(): Unit = runBlocking {
        val t = Catalog.byId("tpl-carte-or")!!
        val f = PdfExporter.exportToCache(ctx, t, vouchers, brand, TicketOptions(), PrintSettings(PaperFormat.A4))
        val r = PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY))
        assertEquals("10 cartes par page A4 : 25 tickets = 3 pages", 3, r.pageCount)
        r.openPage(0).use { p -> assertEquals(595, p.width); assertEquals(842, p.height) }
        r.close()
        f.delete()
        Unit
    }

    @Test fun savedInDownloads(): Unit = runBlocking {
        val r = PdfExporter.export(ctx, Catalog.byId("std-standard")!!, vouchers.take(5), brand, TicketOptions(), PrintSettings())
        assertTrue(r.bytes > 1000)
        ctx.contentResolver.openInputStream(r.uri)!!.use { assertEquals("%PDF", String(it.readNBytes(4))) }
        ctx.contentResolver.delete(r.uri, null, null)
        Unit
    }
}
