package com.wifizone.mikhmon.print

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.wifizone.mikhmon.tickets.Branding
import com.wifizone.mikhmon.tickets.PrintSettings
import com.wifizone.mikhmon.tickets.Sheet
import com.wifizone.mikhmon.tickets.TicketOptions
import com.wifizone.mikhmon.tickets.TicketTemplate
import com.wifizone.mikhmon.tickets.Voucher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Export PDF natif (sans navigateur) : tracés vectoriels, polices embarquées en sous-ensemble, fichier léger. */
object PdfExporter {
    class Result(val uri: Uri, val name: String, val pages: Int, val bytes: Long, val location: String)

    /** Android 8 et 9 : il faut l'autorisation de stockage pour écrire dans Téléchargements. */
    fun needsStoragePermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun fileName(t: TicketTemplate, count: Int): String {
        val d = SimpleDateFormat("yyyy-MM-dd_HH'h'mm", Locale.FRANCE).format(Date())
        val slug = t.name.lowercase(Locale.FRANCE).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return "tickets_${count}_${slug}_$d.pdf"
    }

    /** Pages à produire : toutes, ou celles choisies (indices à partir de 0). */
    private fun pagesOf(plan: com.wifizone.mikhmon.tickets.SheetPlan, pages: Collection<Int>?): List<Int> =
        pages?.filter { it in 0 until plan.pages }?.distinct()?.sorted()?.ifEmpty { null } ?: (0 until plan.pages).toList()

    /** Nombre de tickets réellement imprimés sur les pages choisies. */
    fun ticketCount(t: TicketTemplate, count: Int, s: PrintSettings, pages: Collection<Int>?): Int {
        val plan = Sheet.plan(t, count, s)
        return pagesOf(plan, pages).sumOf { plan.ticketsOn(it).count() }
    }

    fun build(ctx: Context, t: TicketTemplate, vouchers: List<Voucher>, b: Branding, o: TicketOptions, s: PrintSettings, pages: Collection<Int>? = null): PdfDocument {
        val plan = Sheet.plan(t, vouchers.size, s)
        val doc = PdfDocument()
        for ((n, page) in pagesOf(plan, pages).withIndex()) {
            val info = PdfDocument.PageInfo.Builder(plan.pageW.roundToInt(), plan.pageH.roundToInt(), n + 1).create()
            val p = doc.startPage(info)
            Sheet.drawPage(AndroidCanvas2D(p.canvas, ctx), plan, page, t, vouchers, b, o, s)
            doc.finishPage(p)
        }
        return doc
    }

    suspend fun export(ctx: Context, t: TicketTemplate, vouchers: List<Voucher>, b: Branding, o: TicketOptions, s: PrintSettings, only: Collection<Int>? = null): Result =
        withContext(Dispatchers.IO) {
            val doc = build(ctx, t, vouchers, b, o, s, only)
            val pages = doc.pages.size
            val name = fileName(t, ticketCount(t, vouchers.size, s, only))
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Mikhmon")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = ctx.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Impossible de créer le fichier dans Téléchargements")
                    resolver.openOutputStream(uri)?.use { doc.writeTo(it) } ?: throw IllegalStateException("Écriture impossible")
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    val size = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                    Result(uri, name, pages, size, "Téléchargements/Mikhmon")
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Mikhmon").apply { mkdirs() }
                    val file = File(dir, name)
                    FileOutputStream(file).use { doc.writeTo(it) }
                    android.media.MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf("application/pdf"), null)
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                    Result(uri, name, pages, file.length(), "Téléchargements/Mikhmon")
                }
            } finally {
                doc.close()
            }
        }

    /** Fichier PDF temporaire (impression, tests), dans le cache de l'application. */
    suspend fun exportToCache(ctx: Context, t: TicketTemplate, vouchers: List<Voucher>, b: Branding, o: TicketOptions, s: PrintSettings, only: Collection<Int>? = null): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "pdf").apply { mkdirs() }
            dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000L }?.forEach { it.delete() }
            val doc = build(ctx, t, vouchers, b, o, s, only)
            val file = File(dir, fileName(t, ticketCount(t, vouchers.size, s, only)))
            try { FileOutputStream(file).use { doc.writeTo(it) } } finally { doc.close() }
            file
        }

    /**
     * Impression par le système Android (comme Chrome) : imprimantes Wi-Fi ou Bluetooth installées,
     * ou « Enregistrer au format PDF ». Le PDF est préparé d'abord, le système n'a plus qu'à l'envoyer.
     */
    suspend fun print(activity: android.app.Activity, t: TicketTemplate, vouchers: List<Voucher>, b: Branding, o: TicketOptions, s: PrintSettings, only: Collection<Int>? = null) {
        val file = exportToCache(activity, t, vouchers, b, o, s, only)
        val plan = Sheet.plan(t, vouchers.size, s)
        val count = pagesOf(plan, only).size
        val media = when (s.format) {
            com.wifizone.mikhmon.tickets.PaperFormat.A4 -> android.print.PrintAttributes.MediaSize.ISO_A4
            com.wifizone.mikhmon.tickets.PaperFormat.A5 -> android.print.PrintAttributes.MediaSize.ISO_A5
            com.wifizone.mikhmon.tickets.PaperFormat.LETTER -> android.print.PrintAttributes.MediaSize.NA_LETTER
            else -> null
        }
        val attrs = android.print.PrintAttributes.Builder().apply { media?.let { setMediaSize(it) } }
            .setColorMode(android.print.PrintAttributes.COLOR_MODE_COLOR).build()
        val pm = activity.getSystemService(android.print.PrintManager::class.java)
        pm.print(file.nameWithoutExtension, PdfFileAdapter(file, count), attrs)
    }
}

/** Transmet un PDF déjà prêt au service d'impression d'Android. */
private class PdfFileAdapter(private val file: File, private val pages: Int) : android.print.PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: android.print.PrintAttributes?, newAttributes: android.print.PrintAttributes,
        cancellationSignal: android.os.CancellationSignal?, callback: LayoutResultCallback, extras: android.os.Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
        val info = android.print.PrintDocumentInfo.Builder(file.name)
            .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build()
        callback.onLayoutFinished(info, oldAttributes == null || oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out android.print.PageRange>?, destination: android.os.ParcelFileDescriptor,
        cancellationSignal: android.os.CancellationSignal?, callback: WriteResultCallback,
    ) {
        try {
            file.inputStream().use { input -> java.io.FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
            if (cancellationSignal?.isCanceled == true) callback.onWriteCancelled()
            else callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}

/** Aperçus des tickets (images), avec un petit cache. */
object Previews {
    private val cache = object : android.util.LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun ticket(ctx: Context, t: TicketTemplate, v: Voucher, b: Branding, o: TicketOptions, widthPx: Int, key: String? = null): Bitmap {
        key?.let { k -> cache.get(k)?.let { return it } }
        val w = widthPx.coerceAtLeast(16)
        val h = (w / t.aspect).roundToInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        Sheet.drawTicket(AndroidCanvas2D(c, ctx), t, v, b, o, 0f, 0f, w.toFloat())
        key?.let { cache.put(it, bmp) }
        return bmp
    }

    /** Image d'une page entière (aperçu de la feuille telle qu'elle sera imprimée). */
    fun page(ctx: Context, t: TicketTemplate, vouchers: List<Voucher>, b: Branding, o: TicketOptions, s: PrintSettings, widthPx: Int, page: Int = 0, key: String? = null): Bitmap? {
        if (vouchers.isEmpty()) return null
        key?.let { k -> cache.get(k)?.let { return it } }
        val plan = Sheet.plan(t, vouchers.size, s)
        if (page !in 0 until plan.pages) return null
        val scale = widthPx / plan.pageW
        val bmp = Bitmap.createBitmap(widthPx, (plan.pageH * scale).roundToInt().coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        Sheet.drawPage(AndroidCanvas2D(c, ctx), plan, page, t, vouchers, b, o, s)
        key?.let { cache.put(it, bmp) }
        return bmp
    }

    fun clear() = cache.evictAll()
}
