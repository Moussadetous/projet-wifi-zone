package com.wifizone.mikhmon.tickets

import com.wifizone.mikhmon.tickets.gfx.Canvas2D
import com.wifizone.mikhmon.tickets.gfx.Colors
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.VPath
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Formats de papier. Les dimensions sont en millimètres ; un rouleau n'a pas de hauteur fixe. */
enum class PaperFormat(val label: String, val wMm: Float, val hMm: Float, val roll: Boolean = false, val printableMm: Float = 0f) {
    A4("A4", 210f, 297f),
    A5("A5", 148f, 210f),
    LETTER("Lettre US", 215.9f, 279.4f),
    ROLL58("Rouleau 58 mm", 58f, 0f, roll = true, printableMm = 48f),
    ROLL80("Rouleau 80 mm", 80f, 0f, roll = true, printableMm = 72f),
}

data class PrintSettings(
    val format: PaperFormat = PaperFormat.A4,
    /**
     * Tickets par feuille, choisis par l'utilisateur (8, 24, 100…) : la grille est calculée pour que les tickets
     * soient aussi grands que possible. 0 = automatique (taille réelle du modèle).
     */
    val perPage: Int = 0,
    /** Tickets par ligne imposés (feuilles) ; 0 = calculé. */
    val columns: Int = 0,
    val marginMm: Float = 6f,
    val gapMm: Float = 3f,
    val cutMarks: Boolean = true,
)

/** Emplacement d'un ticket, en points PDF (1/72 de pouce). */
data class Slot(val page: Int, val x: Float, val y: Float, val w: Float, val h: Float)

class SheetPlan(
    val pageW: Float, val pageH: Float, val pages: Int, val slots: List<Slot>, val columns: Int, val rows: Int,
    /** Tickets par page (au plus columns × rows). */
    val perPage: Int = columns * rows,
) {
    /** Taille d'un ticket imprimé, en millimètres. */
    val ticketWmm get() = (slots.firstOrNull()?.w ?: 0f) / Sheet.PT_PER_MM
    val ticketHmm get() = (slots.firstOrNull()?.h ?: 0f) / Sheet.PT_PER_MM
    /** Indices des tickets de la page [page]. */
    fun ticketsOn(page: Int): IntRange {
        val from = page * perPage
        return from until minOf(slots.size, from + perPage)
    }
}

object Sheet {
    const val PT_PER_MM = 72f / 25.4f
    /** Taille « naturelle » d'un modèle : 340 unités = 85,6 mm (format carte bancaire). */
    const val MM_PER_UNIT = 85.6f / 340f

    fun naturalWidthMm(t: TicketTemplate) = t.naturalWidthMm

    fun autoColumns(t: TicketTemplate, s: PrintSettings): Int {
        if (s.format.roll) return 1
        val usable = s.format.wMm - 2 * s.marginMm
        return floor((usable + s.gapMm) / (naturalWidthMm(t) + s.gapMm)).toInt().coerceIn(1, 6)
    }

    fun plan(t: TicketTemplate, count: Int, s: PrintSettings): SheetPlan {
        val pt = PT_PER_MM
        if (s.format.roll) {
            // Un ticket par page, à la largeur imprimable du rouleau : chaque ticket se découpe proprement.
            val pageW = s.format.wMm * pt
            val tw = s.format.printableMm * pt
            val th = tw / t.aspect
            val pageH = th + 8f * pt
            val slots = (0 until count).map { Slot(it, (pageW - tw) / 2f, 4f * pt, tw, th) }
            return SheetPlan(pageW, pageH, count, slots, 1, 1)
        }
        val pageW = s.format.wMm * pt
        val pageH = s.format.hMm * pt
        val m = s.marginMm * pt
        if (s.perPage > 0) return dense(t, count, s, pageW, pageH, m)
        val g = s.gapMm * pt
        val cols = (if (s.columns > 0) s.columns else autoColumns(t, s)).coerceIn(1, 8)
        // Jamais plus grand que la taille réelle du modèle (ex. carte bancaire 85,6 mm) : plus de tickets par page.
        val tw = minOf((pageW - 2 * m - (cols - 1) * g) / cols, naturalWidthMm(t) * pt)
        val th = tw / t.aspect
        val rows = max(1, floor((pageH - 2 * m + g) / (th + g)).toInt())
        // Bloc de tickets centré sur la page.
        val blockW = cols * tw + (cols - 1) * g
        val blockH = rows * th + (rows - 1) * g
        val left = (pageW - blockW) / 2f
        val top = (pageH - blockH) / 2f
        val per = cols * rows
        val pages = if (count == 0) 0 else (count + per - 1) / per
        val slots = (0 until count).map { i ->
            val page = i / per
            val k = i % per
            val r = k / cols; val col = k % cols
            Slot(page, left + col * (tw + g), top + r * (th + g), tw, th)
        }
        return SheetPlan(pageW, pageH, pages, slots, cols, rows)
    }

    /** Espace entre les tickets : plus la feuille est chargée, plus il est fin (mais jamais nul, pour la découpe). */
    fun gapFor(perPage: Int, wanted: Float): Float = minOf(wanted, when {
        perPage <= 12 -> 3f
        perPage <= 30 -> 2f
        perPage <= 60 -> 1.5f
        else -> 1f
    })

    /**
     * Grille pour [n] tickets par page dans une zone [w] × [h] : le nombre de colonnes qui donne les plus grands
     * tickets (à égalité, celui qui laisse le moins de cases vides). Renvoie colonnes, lignes et largeur d'un ticket.
     */
    fun bestGrid(n: Int, w: Float, h: Float, aspect: Float, gap: Float, fixedColumns: Int = 0): Triple<Int, Int, Float> {
        var best: Triple<Int, Int, Float>? = null
        val range = if (fixedColumns > 0) fixedColumns..fixedColumns else 1..n.coerceAtMost(40)
        for (c in range) {
            val r = (n + c - 1) / c
            val tw = minOf((w - (c - 1) * gap) / c, (h - (r - 1) * gap) / r * aspect)
            if (tw <= 0f) continue
            val b = best
            if (b == null || tw > b.third * 1.0001f || (tw > b.third * 0.9999f && c * r - n < b.first * b.second - n)) best = Triple(c, r, tw)
        }
        return best ?: Triple(1, n, 1f)
    }

    /** Feuille au nombre de tickets choisi : la grille s'adapte, les tickets sont mis à l'échelle pour tenir. */
    private fun dense(t: TicketTemplate, count: Int, s: PrintSettings, pageW: Float, pageH: Float, m: Float): SheetPlan {
        val pt = PT_PER_MM
        val n = s.perPage.coerceIn(1, 400)
        val g = gapFor(n, s.gapMm) * pt
        val (cols, rows, tw) = bestGrid(n, pageW - 2 * m, pageH - 2 * m, t.aspect, g, s.columns)
        val th = tw / t.aspect
        val blockW = cols * tw + (cols - 1) * g
        val blockH = rows * th + (rows - 1) * g
        val left = (pageW - blockW) / 2f
        val top = (pageH - blockH) / 2f
        val pages = if (count == 0) 0 else (count + n - 1) / n
        val slots = (0 until count).map { i ->
            val k = i % n
            val r = k / cols; val col = k % cols
            Slot(i / n, left + col * (tw + g), top + r * (th + g), tw, th)
        }
        return SheetPlan(pageW, pageH, pages, slots, cols, rows, n)
    }

    /** Tickets par page à taille réelle (choix « Auto »). */
    fun naturalPerPage(t: TicketTemplate, s: PrintSettings): Int = plan(t, 1, s.copy(perPage = 0)).let { it.columns * it.rows }

    /** Choix proposés à l'utilisateur pour le nombre de tickets par feuille. */
    fun densityChoices(t: TicketTemplate, s: PrintSettings): List<Int> =
        (listOf(4, 6, 8, 10, 12, 16, 20, 24, 30, 40, 50, 60, 80, 100, 120, 150) + naturalPerPage(t, s)).distinct().sorted()

    /** Lien de connexion automatique au hotspot (comme les QR codes de Mikhmon). */
    fun loginUrl(dns: String, v: Voucher): String? {
        val host = dns.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (host.isEmpty()) return null
        return "http://$host/login?username=${enc(v.username)}&password=${enc(v.password ?: v.username)}"
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /** Dessine un ticket dans son emplacement (mise à l'échelle uniforme du repère du modèle). */
    fun drawTicket(c: Canvas2D, t: TicketTemplate, v: Voucher, b: Branding, o: TicketOptions, x: Float, y: Float, w: Float) {
        c.save()
        c.translate(x, y)
        c.scale(w / t.w)
        c.clip(VPath.rect(-1f, -1f, t.w + 2f, t.h + 2f))
        t.draw(TicketContext(c, v, b, o, t.palette.withOverrides(o), if (o.showQr) loginUrl(b.dns, v) else null))
        c.restore()
    }

    /** Dessine une page entière : ses tickets et, si demandé, les traits de coupe. */
    fun drawPage(c: Canvas2D, plan: SheetPlan, page: Int, t: TicketTemplate, vouchers: List<Voucher>, b: Branding, o: TicketOptions, s: PrintSettings) {
        val mine = plan.ticketsOn(page).map { IndexedValue(it, plan.slots[it]) }
        c.fill(VPath.rect(0f, 0f, plan.pageW, plan.pageH), Solid(Colors.WHITE))
        for ((i, slot) in mine) drawTicket(c, t, vouchers[i], b, o, slot.x, slot.y, slot.w)
        if (s.cutMarks && !s.format.roll && mine.isNotEmpty()) {
            val marks = VPath()
            val len = 2.2f * PT_PER_MM
            val off = 0.6f * PT_PER_MM
            val xs = mine.flatMap { listOf(it.value.x, it.value.x + it.value.w) }.distinct()
            val ys = mine.flatMap { listOf(it.value.y, it.value.y + it.value.h) }.distinct()
            val minY = ys.minOrNull() ?: 0f; val maxY = ys.maxOrNull() ?: 0f
            val minX = xs.minOrNull() ?: 0f; val maxX = xs.maxOrNull() ?: 0f
            for (x in xs) { marks.moveTo(x, minY - off - len).lineTo(x, minY - off); marks.moveTo(x, maxY + off).lineTo(x, maxY + off + len) }
            for (y in ys) { marks.moveTo(minX - off - len, y).lineTo(minX - off, y); marks.moveTo(maxX + off, y).lineTo(maxX + off + len, y) }
            c.stroke(marks, Colors.alpha(Colors.BLACK, 0.45f), Stroke(0.4f, round = false))
        }
    }

    fun summary(plan: SheetPlan, count: Int): String =
        if (plan.slots.isEmpty()) "Aucun ticket"
        else if (plan.columns == 1 && plan.rows == 1) "$count ticket(s), un par page"
        else "$count ticket(s) · ${plan.perPage} par page (${plan.columns} × ${plan.rows}) · ${plan.pages} page(s)"

    /** Taille d'un ticket, ex. « 58 × 31 mm ». */
    fun sizeLabel(plan: SheetPlan): String = "${plan.ticketWmm.roundToInt()} × ${plan.ticketHmm.roundToInt()} mm"

    /**
     * Lit une sélection de pages saisie par l'utilisateur (« 1-3, 5 ») ; pages numérotées à partir de 1.
     * Renvoie des indices à partir de 0, triés, limités au document ; null si la saisie est vide ou invalide.
     */
    fun parsePages(text: String, pages: Int): List<Int>? {
        val out = sortedSetOf<Int>()
        val parts = text.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        for (p in parts) {
            val r = p.split('-', '–', '—').map { it.trim() }
            val a = r[0].toIntOrNull() ?: return null
            val b = if (r.size > 1) (r[1].ifEmpty { "$pages" }).toIntOrNull() ?: return null else a
            if (r.size > 2) return null
            for (i in minOf(a, b)..maxOf(a, b)) if (i in 1..pages) out += i - 1
        }
        return out.toList().ifEmpty { null }
    }

    /** Écrit une sélection de pages (indices à partir de 0) sous forme lisible : « 1-3, 5 ». */
    fun formatPages(sel: Collection<Int>): String {
        val l = sel.distinct().sorted()
        if (l.isEmpty()) return ""
        val out = ArrayList<String>()
        var start = l[0]; var prev = l[0]
        for (i in l.drop(1) + Int.MIN_VALUE) {
            if (i == prev + 1) { prev = i; continue }
            out += if (start == prev) "${start + 1}" else "${start + 1}-${prev + 1}"
            start = i; prev = i
        }
        return out.joinToString(", ")
    }

    fun mmLabel(pt: Float) = "${(pt / PT_PER_MM).roundToInt()} mm"
}
