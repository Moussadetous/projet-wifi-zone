package com.wifizone.mikhmon.tickets.gfx

import kotlin.math.cos
import kotlin.math.sin

/**
 * Icônes vectorielles dessinées sur une grille 24 × 24. Elles sont écrites en tracés (pas en images) :
 * quelques dizaines d'octets chacune dans le PDF, nettes à toutes les tailles.
 */
class Icon(val id: String, val label: String, val fill: VPath?, val stroke: VPath? = null, val strokeWidth: Float = 2f)

object Icons {
    private fun band(p: VPath, cx: Float, cy: Float, ro: Float, ri: Float, start: Float, sweep: Float, round: Boolean = true) {
        p.arc(cx, cy, ro, start, sweep, connect = false)
        p.arc(cx, cy, ri, start + sweep, -sweep, connect = true)
        p.close()
        if (round) {
            val rm = (ro + ri) / 2f; val w = (ro - ri) / 2f
            for (a in floatArrayOf(start, start + sweep)) {
                p.circle(cx + rm * cos(VPath.rad(a)), cy + rm * sin(VPath.rad(a)), w)
            }
        }
    }

    private fun star(cx: Float, cy: Float, ro: Float, ri: Float, n: Int = 5): VPath {
        val pts = FloatArray(n * 4)
        for (i in 0 until n * 2) {
            val r = if (i % 2 == 0) ro else ri
            val a = VPath.rad(-90f + i * 180f / n)
            pts[i * 2] = cx + r * cos(a); pts[i * 2 + 1] = cy + r * sin(a)
        }
        return VPath().polygon(*pts)
    }

    val WIFI = Icon("wifi", "Wi-Fi", VPath().also { p ->
        band(p, 12f, 20f, 16.5f, 13.4f, -135f, 90f)
        band(p, 12f, 20f, 10.8f, 7.7f, -135f, 90f)
        p.circle(12f, 19.2f, 2.5f)
    })
    val CLOCK = Icon("clock", "Durée", VPath().apply {
        evenOdd = true
        circle(12f, 12f, 10f); circle(12f, 12f, 8f)
        rect(11f, 6f, 2f, 6.5f); rect(13f, 11f, 4f, 2f)
    })
    val HOURGLASS = Icon("hourglass", "Validité", VPath()
        .rect(5f, 2f, 14f, 2f).rect(5f, 20f, 14f, 2f)
        .polygon(7f, 4f, 17f, 4f, 13.2f, 12f, 17f, 20f, 7f, 20f, 10.8f, 12f))
    val CALENDAR = Icon("calendar", "Jours", VPath().apply {
        evenOdd = true
        roundRect(3f, 4f, 18f, 17f, 2.5f); rect(5f, 9f, 14f, 10f)
        rect(7f, 1.6f, 2f, 2.4f); rect(15f, 1.6f, 2f, 2.4f)
        rect(7f, 11f, 3f, 3f); rect(14f, 11f, 3f, 3f); rect(7f, 15f, 3f, 2.5f)
    })
    val DATA = Icon("data", "Données", VPath()
        .polygon(3f, 9f, 7.5f, 3f, 12f, 9f).rect(6f, 8.5f, 3f, 12.5f)
        .polygon(12f, 15f, 16.5f, 21f, 21f, 15f).rect(15f, 3f, 3f, 12.5f))
    val GAUGE = Icon("gauge", "Débit", VPath().also { p ->
        band(p, 12f, 13f, 10f, 7.6f, 135f, 270f)
        val d = VPath.rad(-60f)
        val tipX = 12f + 6.6f * cos(d); val tipY = 13f + 6.6f * sin(d)
        val px = -sin(d) * 1.5f; val py = cos(d) * 1.5f
        p.polygon(tipX, tipY, 12f + px, 13f + py, 12f - px, 13f - py)
        p.circle(12f, 13f, 2.3f)
    })
    val BOLT = Icon("bolt", "Rapide", VPath().polygon(13.5f, 2f, 4f, 14f, 11f, 14f, 10f, 22f, 20f, 9.5f, 13f, 9.5f))
    val INFINITY = Icon("infinity", "Illimité", null, VPath()
        .moveTo(12f, 12f).cubicTo(9f, 7f, 3f, 7f, 3f, 12f).cubicTo(3f, 17f, 9f, 17f, 12f, 12f)
        .cubicTo(15f, 7f, 21f, 7f, 21f, 12f).cubicTo(21f, 17f, 15f, 17f, 12f, 12f), 2.5f)
    val USER = Icon("user", "Utilisateur", VPath()
        .circle(12f, 7.5f, 4.6f)
        .moveTo(3f, 21.5f).cubicTo(3f, 15.8f, 7f, 13.6f, 12f, 13.6f).cubicTo(17f, 13.6f, 21f, 15.8f, 21f, 21.5f).close())
    val KEY = Icon("key", "Code", null, VPath()
        .circle(7.5f, 12f, 4.2f).moveTo(11.7f, 12f).lineTo(21f, 12f).moveTo(17f, 12f).lineTo(17f, 15.5f)
        .moveTo(20.3f, 12f).lineTo(20.3f, 14.5f), 2.3f)
    val LOCK = Icon("lock", "Mot de passe", VPath().also { p ->
        p.evenOdd = true
        band(p, 12f, 10f, 6.2f, 4f, 180f, 180f, round = false)
        p.roundRect(4f, 10f, 16f, 12f, 2.5f)
        p.circle(12f, 15.2f, 1.8f); p.rect(11.2f, 17f, 1.6f, 2.6f)
    })
    val PHONE = Icon("phone", "Téléphone", VPath()
        .moveTo(6.6f, 10.8f).cubicTo(8f, 13.6f, 10.4f, 16f, 13.2f, 17.4f).lineTo(15.4f, 15.2f)
        .cubicTo(15.7f, 14.9f, 16.1f, 14.8f, 16.4f, 15f).cubicTo(17.5f, 15.4f, 18.7f, 15.6f, 20f, 15.6f)
        .cubicTo(20.6f, 15.6f, 21f, 16f, 21f, 16.6f).lineTo(21f, 20f).cubicTo(21f, 20.6f, 20.6f, 21f, 20f, 21f)
        .cubicTo(10.6f, 21f, 3f, 13.4f, 3f, 4f).cubicTo(3f, 3.4f, 3.4f, 3f, 4f, 3f).lineTo(7.5f, 3f)
        .cubicTo(8.1f, 3f, 8.5f, 3.4f, 8.5f, 4f).cubicTo(8.5f, 5.3f, 8.7f, 6.5f, 9.1f, 7.6f)
        .cubicTo(9.2f, 7.9f, 9.1f, 8.3f, 8.9f, 8.6f).close())
    val CHAT = Icon("chat", "Message", VPath().apply {
        evenOdd = true
        roundRect(2f, 3f, 20f, 14f, 4.5f); polygon(6f, 16.9f, 6f, 21.5f, 11.5f, 16.9f)
        circle(7.8f, 10f, 1.5f); circle(12f, 10f, 1.5f); circle(16.2f, 10f, 1.5f)
    })
    val GLOBE = Icon("globe", "Connexion", null, VPath()
        .circle(12f, 12f, 9.6f).ellipse(12f, 12f, 4.2f, 9.6f)
        .moveTo(2.4f, 12f).lineTo(21.6f, 12f).moveTo(4f, 7f).lineTo(20f, 7f).moveTo(4f, 17f).lineTo(20f, 17f), 1.7f)
    val STAR = Icon("star", "Premium", star(12f, 12.6f, 10.5f, 4.4f))
    val CROWN = Icon("crown", "VIP", VPath()
        .polygon(2.5f, 7.5f, 7.5f, 12f, 12f, 3.5f, 16.5f, 12f, 21.5f, 7.5f, 19.2f, 18.6f, 4.8f, 18.6f)
        .roundRect(4.8f, 19.8f, 14.4f, 2.4f, 1f))
    val TICKET = Icon("ticket", "Ticket", VPath().apply {
        evenOdd = true
        moveTo(2f, 6f); lineTo(22f, 6f); lineTo(22f, 9.6f)
        arc(22f, 12f, 2.4f, 270f, -180f); lineTo(22f, 18f); lineTo(2f, 18f); lineTo(2f, 14.4f)
        arc(2f, 12f, 2.4f, 90f, -180f); close()
        rect(14.4f, 7.6f, 1.3f, 2.2f); rect(14.4f, 10.9f, 1.3f, 2.2f); rect(14.4f, 14.2f, 1.3f, 2.2f)
    })
    val SIGNAL = Icon("signal", "Réseau", VPath()
        .roundRect(3f, 16f, 3.2f, 5f, 0.8f).roundRect(8f, 12f, 3.2f, 9f, 0.8f)
        .roundRect(13f, 8f, 3.2f, 13f, 0.8f).roundRect(18f, 3f, 3.2f, 18f, 0.8f))
    val HEART = Icon("heart", "Merci", VPath()
        .moveTo(12f, 21f).cubicTo(5f, 15.5f, 2f, 12f, 2f, 8f).cubicTo(2f, 5f, 4.5f, 3f, 7.2f, 3f)
        .cubicTo(9.2f, 3f, 11f, 4.2f, 12f, 6f).cubicTo(13f, 4.2f, 14.8f, 3f, 16.8f, 3f)
        .cubicTo(19.5f, 3f, 22f, 5f, 22f, 8f).cubicTo(22f, 12f, 19f, 15.5f, 12f, 21f).close())
    val PLANE = Icon("plane", "Voyage", VPath().polygon(
        21f, 16f, 21f, 14f, 13f, 9f, 13f, 3.5f, 12f, 2f, 11f, 3.5f, 11f, 9f, 3f, 14f, 3f, 16f, 11f, 13.5f,
        11f, 19f, 9f, 20.5f, 9f, 22f, 12f, 21f, 15f, 22f, 15f, 20.5f, 13f, 19f, 13f, 13.5f))
    val SCISSORS = Icon("scissors", "Découper", null, VPath()
        .circle(6f, 6.2f, 2.9f).circle(6f, 17.8f, 2.9f)
        .moveTo(8.4f, 7.7f).lineTo(20.5f, 17.2f).moveTo(8.4f, 16.3f).lineTo(20.5f, 6.8f), 1.9f)
    val SHIELD = Icon("shield", "Sécurisé", VPath().apply {
        evenOdd = true
        moveTo(12f, 2f); lineTo(20f, 5f); lineTo(20f, 11f); cubicTo(20f, 16f, 16.5f, 20f, 12f, 22f)
        cubicTo(7.5f, 20f, 4f, 16f, 4f, 11f); lineTo(4f, 5f); close()
        polygon(10.5f, 15.6f, 7f, 12.1f, 8.4f, 10.7f, 10.5f, 12.8f, 15.6f, 7.7f, 17f, 9.1f)
    })
    val DOWNLOAD = Icon("download", "Téléchargement", VPath()
        .polygon(10.5f, 3f, 13.5f, 3f, 13.5f, 10.5f, 17.2f, 10.5f, 12f, 16.4f, 6.8f, 10.5f, 10.5f, 10.5f)
        .polygon(3f, 14.5f, 5.2f, 14.5f, 5.2f, 18.8f, 18.8f, 18.8f, 18.8f, 14.5f, 21f, 14.5f, 21f, 21f, 3f, 21f))
    val PRINT = Icon("print", "Imprimer", VPath().apply {
        evenOdd = true
        rect(6.6f, 2.6f, 10.8f, 4.2f)          // feuille qui entre
        roundRect(2f, 8f, 20f, 8.6f, 2.2f)      // corps de l'imprimante
        rect(6.6f, 12.6f, 10.8f, 1.5f)          // fente (évidée)
        circle(18.2f, 10.8f, 1f)                // voyant (évidé)
        rect(6.6f, 17.8f, 10.8f, 3.8f)          // feuille imprimée
    })
    val GRID = Icon("grid", "Grille", VPath()
        .roundRect(3f, 3f, 5f, 5f, 1.2f).roundRect(9.5f, 3f, 5f, 5f, 1.2f).roundRect(16f, 3f, 5f, 5f, 1.2f)
        .roundRect(3f, 9.5f, 5f, 5f, 1.2f).roundRect(9.5f, 9.5f, 5f, 5f, 1.2f).roundRect(16f, 9.5f, 5f, 5f, 1.2f)
        .roundRect(3f, 16f, 5f, 5f, 1.2f).roundRect(9.5f, 16f, 5f, 5f, 1.2f).roundRect(16f, 16f, 5f, 5f, 1.2f))
    val PLAY = Icon("play", "Vidéo", VPath().apply {
        evenOdd = true
        circle(12f, 12f, 10f); polygon(9.8f, 7.6f, 16.8f, 12f, 9.8f, 16.4f)
    })
    val COFFEE = Icon("coffee", "Café", VPath()
        .moveTo(3.5f, 8f).lineTo(16f, 8f).lineTo(16f, 14.5f).cubicTo(16f, 17.8f, 13.8f, 20f, 10.6f, 20f)
        .lineTo(8.9f, 20f).cubicTo(5.7f, 20f, 3.5f, 17.8f, 3.5f, 14.5f).close()
        .roundRect(2.5f, 21f, 15f, 1.6f, 0.8f),
        VPath().arc(16f, 12.2f, 2.8f, -90f, 180f, connect = false)
            .moveTo(7.5f, 2.2f).cubicTo(6.6f, 3.4f, 8.4f, 4.4f, 7.5f, 5.8f)
            .moveTo(11.5f, 2.2f).cubicTo(10.6f, 3.4f, 12.4f, 4.4f, 11.5f, 5.8f), 1.7f)
    val HOME = Icon("home", "Maison", VPath().polygon(
        12f, 2.8f, 21.5f, 11f, 18.6f, 11f, 18.6f, 21f, 14.2f, 21f, 14.2f, 15f, 9.8f, 15f, 9.8f, 21f, 5.4f, 21f, 5.4f, 11f, 2.5f, 11f))
    val FLAME = Icon("flame", "Promo", VPath()
        .moveTo(12f, 2f).cubicTo(13f, 6f, 18.5f, 8.3f, 18.5f, 14.2f).cubicTo(18.5f, 18.5f, 15.6f, 22f, 12f, 22f)
        .cubicTo(8.4f, 22f, 5.5f, 19.4f, 5.5f, 15.4f).cubicTo(5.5f, 12.4f, 7.6f, 10.4f, 8.8f, 8.8f)
        .cubicTo(9f, 10.9f, 9.9f, 12f, 11f, 12.6f).cubicTo(11f, 8.6f, 11.5f, 5f, 12f, 2f).close())
    val GIFT = Icon("gift", "Cadeau", VPath().apply {
        evenOdd = true
        roundRect(3f, 8f, 18f, 4f, 1f); roundRect(4.6f, 12.6f, 14.8f, 9.4f, 1f)
        rect(11f, 8f, 2f, 4f); rect(11f, 12.6f, 2f, 9.4f)
    }, VPath().moveTo(12f, 7.6f).cubicTo(9.5f, 2.6f, 5f, 4.4f, 8.2f, 7.6f)
        .moveTo(12f, 7.6f).cubicTo(14.5f, 2.6f, 19f, 4.4f, 15.8f, 7.6f), 1.8f)
    val SPARKLE = Icon("sparkle", "Éclat", VPath()
        .moveTo(12f, 2f).cubicTo(12.8f, 7.5f, 15f, 10f, 21f, 11f).cubicTo(15f, 12f, 12.8f, 14.5f, 12f, 21f)
        .cubicTo(11.2f, 14.5f, 9f, 12f, 3f, 11f).cubicTo(9f, 10f, 11.2f, 7.5f, 12f, 2f).close())
    val CHECK = Icon("check", "Valide", null, VPath().moveTo(4.5f, 12.5f).lineTo(9.5f, 17.5f).lineTo(19.5f, 6.5f), 2.8f)

    /** Icônes proposées pour le motif principal des tickets. */
    val ALL = listOf(WIFI, CLOCK, HOURGLASS, CALENDAR, DATA, GAUGE, BOLT, INFINITY, USER, KEY, LOCK, PHONE, CHAT, GLOBE,
        STAR, CROWN, TICKET, SIGNAL, HEART, PLANE, SCISSORS, SHIELD, DOWNLOAD, PRINT, GRID, PLAY, COFFEE, HOME, FLAME, GIFT, SPARKLE, CHECK)
    val MOTIFS = listOf(WIFI, BOLT, STAR, CROWN, FLAME, HEART, GLOBE, SIGNAL, COFFEE, HOME, GIFT, SPARKLE, PLAY, SHIELD, PLANE, TICKET)

    fun byId(id: String?) = ALL.firstOrNull { it.id == id }

    /** Dessine [icon] dans un carré de côté [size] dont le coin haut gauche est (x, y). */
    fun draw(c: Canvas2D, icon: Icon, x: Float, y: Float, size: Float, color: Int) {
        val s = size / 24f
        icon.fill?.let { c.fill(VPath().add(it, x, y, s), Solid(color)) }
        icon.stroke?.let { c.stroke(VPath().add(it, x, y, s), color, Stroke(icon.strokeWidth * s, round = true)) }
    }
}
