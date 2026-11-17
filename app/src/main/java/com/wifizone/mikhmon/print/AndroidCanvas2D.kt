package com.wifizone.mikhmon.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.wifizone.mikhmon.tickets.gfx.Align
import com.wifizone.mikhmon.tickets.gfx.Canvas2D
import com.wifizone.mikhmon.tickets.gfx.Fill
import com.wifizone.mikhmon.tickets.gfx.FontFace
import com.wifizone.mikhmon.tickets.gfx.ImageRef
import com.wifizone.mikhmon.tickets.gfx.Linear
import com.wifizone.mikhmon.tickets.gfx.Radial
import com.wifizone.mikhmon.tickets.gfx.Solid
import com.wifizone.mikhmon.tickets.gfx.Stroke
import com.wifizone.mikhmon.tickets.gfx.TextStyle
import com.wifizone.mikhmon.tickets.gfx.VPath

/** Polices embarquées (assets/fonts), chargées une seule fois. */
object Fonts {
    private val cache = HashMap<FontFace, Typeface>()
    fun get(ctx: Context, f: FontFace): Typeface = synchronized(cache) {
        cache.getOrPut(f) { runCatching { Typeface.createFromAsset(ctx.assets, "fonts/${f.file}") }.getOrDefault(Typeface.DEFAULT) }
    }
}

class BitmapRef(val bitmap: Bitmap) : ImageRef {
    override val width get() = bitmap.width
    override val height get() = bitmap.height
}

/** Dessin des tickets sur un Canvas Android : écran (aperçu) et PDF (PdfDocument). */
class AndroidCanvas2D(private val c: Canvas, private val ctx: Context) : Canvas2D {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { isLinearText = true }
    private val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun path(p: VPath): Path {
        val out = Path()
        out.fillType = if (p.evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
        for (op in p.ops) when (op) {
            is VPath.Move -> out.moveTo(op.x, op.y)
            is VPath.Line -> out.lineTo(op.x, op.y)
            is VPath.Quad -> out.quadTo(op.x1, op.y1, op.x, op.y)
            is VPath.Cubic -> out.cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
            VPath.Close -> out.close()
        }
        return out
    }

    private fun stops(n: Int, s: FloatArray?): FloatArray? = s ?: if (n > 2) FloatArray(n) { it / (n - 1).toFloat() } else null

    private fun applyFill(p: Paint, f: Fill) {
        when (f) {
            is Solid -> { p.shader = null; p.color = f.argb }
            is Linear -> { p.color = 0xFF000000.toInt(); p.shader = LinearGradient(f.x0, f.y0, f.x1, f.y1, f.colors, stops(f.colors.size, f.stops), Shader.TileMode.CLAMP) }
            is Radial -> { p.color = 0xFF000000.toInt(); p.shader = RadialGradient(f.cx, f.cy, f.r.coerceAtLeast(0.01f), f.colors, stops(f.colors.size, f.stops), Shader.TileMode.CLAMP) }
        }
    }

    private fun applyText(st: TextStyle) {
        textPaint.typeface = Fonts.get(ctx, st.face)
        textPaint.textSize = st.size
        textPaint.letterSpacing = st.tracking
    }

    override fun save() { c.save() }
    override fun restore() { c.restore() }
    override fun translate(dx: Float, dy: Float) = c.translate(dx, dy)
    override fun scale(s: Float) = c.scale(s, s)
    override fun rotate(deg: Float) = c.rotate(deg)
    override fun clip(path: VPath) { c.clipPath(path(path)) }
    override fun fill(path: VPath, fill: Fill) { applyFill(fillPaint, fill); c.drawPath(path(path), fillPaint) }
    override fun stroke(path: VPath, color: Int, stroke: Stroke) {
        strokePaint.color = color
        strokePaint.strokeWidth = stroke.width
        strokePaint.strokeCap = if (stroke.round) Paint.Cap.ROUND else Paint.Cap.BUTT
        strokePaint.strokeJoin = if (stroke.round) Paint.Join.ROUND else Paint.Join.MITER
        strokePaint.pathEffect = stroke.dash?.let { DashPathEffect(it, 0f) }
        c.drawPath(path(path), strokePaint)
    }
    override fun text(s: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: Align) {
        if (s.isEmpty()) return
        applyText(style)
        applyFill(textPaint, fill)
        val w = textPaint.measureText(s)
        val x0 = when (align) { Align.LEFT -> x; Align.CENTER -> x - w / 2f; Align.RIGHT -> x - w }
        c.drawText(s, x0, y, textPaint)
    }
    override fun measure(s: String, style: TextStyle): Float {
        if (s.isEmpty()) return 0f
        applyText(style)
        return textPaint.measureText(s)
    }
    override fun image(img: ImageRef, x: Float, y: Float, w: Float, h: Float) {
        val b = (img as? BitmapRef)?.bitmap ?: return
        c.drawBitmap(b, null, RectF(x, y, x + w, y + h), imgPaint)
    }
}
