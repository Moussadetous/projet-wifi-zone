package com.wifizone.mikhmon.tickets.gfx

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Dessin vectoriel indépendant de la plateforme : le même code produit l'aperçu (écran), le PDF (Android
 * PdfDocument) et les images de contrôle des tests (Java2D). Repère : y vers le bas, angles en degrés,
 * sens horaire (comme Android).
 */

/** Polices embarquées dans l'application (licences libres, voir assets/fonts/LICENCES.txt). */
enum class FontFace(val file: String, val capHeight: Float) {
    REGULAR("inter_regular.ttf", 0.727f),
    MEDIUM("inter_medium.ttf", 0.727f),
    SEMIBOLD("inter_semibold.ttf", 0.727f),
    BOLD("inter_bold.ttf", 0.727f),
    BLACK("inter_black.ttf", 0.727f),
    BOLD_ITALIC("inter_bold_italic.ttf", 0.727f),
    BLACK_ITALIC("inter_black_italic.ttf", 0.727f),
    MONO("jetbrains_mono_bold.ttf", 0.73f),
    MONO_HEAVY("jetbrains_mono_extrabold.ttf", 0.73f),
    SERIF("caladea_regular.ttf", 0.667f),
    SERIF_BOLD("caladea_bold.ttf", 0.667f),
}

/** Style de texte. [tracking] : espacement des lettres en em (0.1 = 10 % de la taille). */
data class TextStyle(val face: FontFace, val size: Float, val tracking: Float = 0f) {
    fun sized(s: Float) = copy(size = s)
    val cap get() = size * face.capHeight
}

enum class Align { LEFT, CENTER, RIGHT }

sealed interface Fill
data class Solid(val argb: Int) : Fill
class Linear(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val colors: IntArray, val stops: FloatArray? = null) : Fill
class Radial(val cx: Float, val cy: Float, val r: Float, val colors: IntArray, val stops: FloatArray? = null) : Fill

class Stroke(val width: Float, val dash: FloatArray? = null, val round: Boolean = true)

/** Image de la plateforme (logo du client), déjà réduite à la taille utile. */
interface ImageRef { val width: Int; val height: Int }

interface Canvas2D {
    fun save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    fun scale(s: Float)
    fun rotate(deg: Float)
    fun clip(path: VPath)
    fun fill(path: VPath, fill: Fill)
    fun stroke(path: VPath, color: Int, stroke: Stroke)
    /** Texte posé sur sa ligne de base [y]. */
    fun text(s: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: Align = Align.LEFT)
    fun measure(s: String, style: TextStyle): Float
    fun image(img: ImageRef, x: Float, y: Float, w: Float, h: Float)
}

/** Tracé vectoriel (segments, courbes de Bézier quadratiques et cubiques). */
class VPath {
    sealed interface Op
    data class Move(val x: Float, val y: Float) : Op
    data class Line(val x: Float, val y: Float) : Op
    data class Quad(val x1: Float, val y1: Float, val x: Float, val y: Float) : Op
    data class Cubic(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : Op
    object Close : Op

    val ops = ArrayList<Op>()
    var evenOdd = false
    private var cx = 0f
    private var cy = 0f
    private var sx = 0f
    private var sy = 0f

    val currentX get() = cx
    val currentY get() = cy

    fun moveTo(x: Float, y: Float) = apply { ops += Move(x, y); cx = x; cy = y; sx = x; sy = y }
    fun lineTo(x: Float, y: Float) = apply { ops += Line(x, y); cx = x; cy = y }
    fun quadTo(x1: Float, y1: Float, x: Float, y: Float) = apply { ops += Quad(x1, y1, x, y); cx = x; cy = y }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) =
        apply { ops += Cubic(x1, y1, x2, y2, x, y); cx = x; cy = y }
    fun close() = apply { ops += Close; cx = sx; cy = sy }

    /** Arc de cercle (angles en degrés, sens horaire), relié au point courant si [connect]. */
    fun arc(ccx: Float, ccy: Float, r: Float, startDeg: Float, sweepDeg: Float, connect: Boolean = true) = apply {
        val x0 = ccx + r * cos(rad(startDeg))
        val y0 = ccy + r * sin(rad(startDeg))
        if (connect && ops.isNotEmpty() && ops.last() !is Close) lineTo(x0, y0) else moveTo(x0, y0)
        val n = ceil(abs(sweepDeg) / 90f).toInt().coerceAtLeast(1)
        val step = sweepDeg / n
        var a = startDeg
        repeat(n) {
            val a1 = rad(a)
            val a2 = rad(a + step)
            val k = 4f / 3f * tan((a2 - a1) / 4f)
            val p0x = ccx + r * cos(a1); val p0y = ccy + r * sin(a1)
            val p3x = ccx + r * cos(a2); val p3y = ccy + r * sin(a2)
            cubicTo(
                p0x - k * r * sin(a1), p0y + k * r * cos(a1),
                p3x + k * r * sin(a2), p3y - k * r * cos(a2),
                p3x, p3y,
            )
            a += step
        }
    }

    fun rect(x: Float, y: Float, w: Float, h: Float) = apply { moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h); close() }

    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) = roundRect(x, y, w, h, r, r, r, r)

    fun roundRect(x: Float, y: Float, w: Float, h: Float, tl: Float, tr: Float, br: Float, bl: Float) = apply {
        val m = minOf(w, h) / 2f
        val a = tl.coerceIn(0f, m); val b = tr.coerceIn(0f, m); val c = br.coerceIn(0f, m); val d = bl.coerceIn(0f, m)
        moveTo(x + a, y)
        lineTo(x + w - b, y)
        if (b > 0) arc(x + w - b, y + b, b, -90f, 90f)
        lineTo(x + w, y + h - c)
        if (c > 0) arc(x + w - c, y + h - c, c, 0f, 90f)
        lineTo(x + d, y + h)
        if (d > 0) arc(x + d, y + h - d, d, 90f, 90f)
        lineTo(x, y + a)
        if (a > 0) arc(x + a, y + a, a, 180f, 90f)
        close()
    }

    fun circle(ccx: Float, ccy: Float, r: Float) = apply { arc(ccx, ccy, r, 0f, 360f, connect = false); close() }

    fun ellipse(ccx: Float, ccy: Float, rx: Float, ry: Float) = apply {
        val k = 0.5522848f
        moveTo(ccx + rx, ccy)
        cubicTo(ccx + rx, ccy + ry * k, ccx + rx * k, ccy + ry, ccx, ccy + ry)
        cubicTo(ccx - rx * k, ccy + ry, ccx - rx, ccy + ry * k, ccx - rx, ccy)
        cubicTo(ccx - rx, ccy - ry * k, ccx - rx * k, ccy - ry, ccx, ccy - ry)
        cubicTo(ccx + rx * k, ccy - ry, ccx + rx, ccy - ry * k, ccx + rx, ccy)
        close()
    }

    fun polygon(vararg pts: Float) = apply {
        moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
        close()
    }

    /** Ajoute [other] transformé (échelle uniforme puis décalage). */
    fun add(other: VPath, dx: Float = 0f, dy: Float = 0f, s: Float = 1f) = apply {
        for (op in other.ops) when (op) {
            is Move -> moveTo(dx + op.x * s, dy + op.y * s)
            is Line -> lineTo(dx + op.x * s, dy + op.y * s)
            is Quad -> quadTo(dx + op.x1 * s, dy + op.y1 * s, dx + op.x * s, dy + op.y * s)
            is Cubic -> cubicTo(dx + op.x1 * s, dy + op.y1 * s, dx + op.x2 * s, dy + op.y2 * s, dx + op.x * s, dy + op.y * s)
            Close -> close()
        }
        if (other.evenOdd) evenOdd = true
    }

    val isEmpty get() = ops.isEmpty()

    companion object {
        fun rad(d: Float) = (d * Math.PI / 180.0).toFloat()
        fun rect(x: Float, y: Float, w: Float, h: Float) = VPath().rect(x, y, w, h)
        fun round(x: Float, y: Float, w: Float, h: Float, r: Float) = VPath().roundRect(x, y, w, h, r)
        fun circle(x: Float, y: Float, r: Float) = VPath().circle(x, y, r)
    }
}

/** Couleurs : ARGB 32 bits. */
object Colors {
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun hex(s: String): Int {
        val h = s.removePrefix("#")
        val v = h.toLong(16).toInt()
        return if (h.length <= 6) (0xFF shl 24) or v else v
    }
    fun alpha(c: Int, a: Float) = (((a.coerceIn(0f, 1f) * 255).toInt()) shl 24) or (c and 0xFFFFFF)
    fun a(c: Int) = (c ushr 24) and 0xFF
    fun r(c: Int) = (c shr 16) and 0xFF
    fun g(c: Int) = (c shr 8) and 0xFF
    fun b(c: Int) = c and 0xFF
    fun mix(c1: Int, c2: Int, t: Float): Int {
        fun m(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return argb(m(a(c1), a(c2)), m(r(c1), r(c2)), m(g(c1), g(c2)), m(b(c1), b(c2)))
    }
    fun lighten(c: Int, t: Float) = mix(c, (c and 0xFF000000.toInt()) or 0xFFFFFF, t)
    fun darken(c: Int, t: Float) = mix(c, c and 0xFF000000.toInt(), t)
    /** Luminance relative (0 = noir, 1 = blanc). */
    fun luminance(c: Int): Float {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return (0.2126 * ch(r(c)) + 0.7152 * ch(g(c)) + 0.0722 * ch(b(c))).toFloat()
    }
    /** Texte lisible (noir ou blanc) sur le fond [c]. */
    fun onColor(c: Int) = if (luminance(c) > 0.42f) 0xFF111318.toInt() else 0xFFFFFFFF.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
}
