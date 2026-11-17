package com.wifizone.mikhmon.tickets

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
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.Paint
import java.awt.RadialGradientPaint
import java.awt.Shape
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.io.File

/** Rendu Java2D (tests et aperçus sur ordinateur), avec les mêmes polices que l'application. */
class Java2DCanvas(private val g: Graphics2D, private val fontDir: File) : Canvas2D {
    class Img(val img: BufferedImage) : ImageRef { override val width get() = img.width; override val height get() = img.height }

    private val stack = ArrayDeque<Pair<AffineTransform, Shape?>>()

    companion object {
        private val fonts = HashMap<FontFace, Font>()
    }

    private fun base(face: FontFace): Font = synchronized(fonts) {
        fonts.getOrPut(face) { Font.createFont(Font.TRUETYPE_FONT, File(fontDir, face.file)) }
    }

    private fun font(st: TextStyle): Font {
        val attrs = HashMap<TextAttribute, Any>()
        attrs[TextAttribute.SIZE] = st.size
        if (st.tracking != 0f) attrs[TextAttribute.TRACKING] = st.tracking
        attrs[TextAttribute.KERNING] = TextAttribute.KERNING_ON
        return base(st.face).deriveFont(attrs)
    }

    private fun shape(p: VPath): Shape {
        val s = Path2D.Float(if (p.evenOdd) Path2D.WIND_EVEN_ODD else Path2D.WIND_NON_ZERO)
        for (op in p.ops) when (op) {
            is VPath.Move -> s.moveTo(op.x, op.y)
            is VPath.Line -> s.lineTo(op.x, op.y)
            is VPath.Quad -> s.quadTo(op.x1, op.y1, op.x, op.y)
            is VPath.Cubic -> s.curveTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
            VPath.Close -> s.closePath()
        }
        return s
    }

    private fun stops(n: Int, s: FloatArray?): FloatArray {
        val out = s?.copyOf() ?: FloatArray(n) { it / (n - 1).toFloat() }
        for (i in 1 until out.size) if (out[i] <= out[i - 1]) out[i] = (out[i - 1] + 0.0001f).coerceAtMost(1f)
        return out
    }

    private fun paint(f: Fill): Paint = when (f) {
        is Solid -> Color(f.argb, true)
        is Linear -> if (f.x0 == f.x1 && f.y0 == f.y1) Color(f.colors[0], true)
            else LinearGradientPaint(Point2D.Float(f.x0, f.y0), Point2D.Float(f.x1, f.y1), stops(f.colors.size, f.stops), f.colors.map { Color(it, true) }.toTypedArray())
        is Radial -> RadialGradientPaint(Point2D.Float(f.cx, f.cy), f.r, stops(f.colors.size, f.stops), f.colors.map { Color(it, true) }.toTypedArray())
    }

    override fun save() { stack.addLast(g.transform to g.clip) }
    override fun restore() { val (t, c) = stack.removeLast(); g.transform = t; g.clip = c }
    override fun translate(dx: Float, dy: Float) = g.translate(dx.toDouble(), dy.toDouble())
    override fun scale(s: Float) = g.scale(s.toDouble(), s.toDouble())
    override fun rotate(deg: Float) = g.rotate(Math.toRadians(deg.toDouble()))
    override fun clip(path: VPath) = g.clip(shape(path))
    override fun fill(path: VPath, fill: Fill) { g.paint = paint(fill); g.fill(shape(path)) }
    override fun stroke(path: VPath, color: Int, stroke: Stroke) {
        g.paint = Color(color, true)
        g.stroke = BasicStroke(stroke.width, if (stroke.round) BasicStroke.CAP_ROUND else BasicStroke.CAP_BUTT,
            if (stroke.round) BasicStroke.JOIN_ROUND else BasicStroke.JOIN_MITER, 10f, stroke.dash, 0f)
        g.draw(shape(path))
    }
    override fun text(s: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: Align) {
        if (s.isEmpty()) return
        val f = font(style)
        val w = measure(s, style)
        val x0 = when (align) { Align.LEFT -> x; Align.CENTER -> x - w / 2f; Align.RIGHT -> x - w }
        g.paint = paint(fill)
        g.font = f
        g.drawString(s, x0, y)
    }
    override fun measure(s: String, style: TextStyle): Float =
        if (s.isEmpty()) 0f else TextLayout(s, font(style), g.fontRenderContext).advance
    override fun image(img: ImageRef, x: Float, y: Float, w: Float, h: Float) {
        val i = (img as Img).img
        g.drawImage(i, x.toInt(), y.toInt(), w.toInt(), h.toInt(), null)
    }
}
