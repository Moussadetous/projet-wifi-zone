package com.wifizone.mikhmon

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Petits utilitaires d'interface (écrans construits en code, sans XML). */
internal object Ui {
    const val PRIMARY = 0xFF1565C0.toInt()

    fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    fun title(c: Context, text: String) = TextView(c).apply {
        this.text = text; textSize = 24f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.BLACK)
    }

    fun body(c: Context, text: String = "") = TextView(c).apply {
        this.text = text; textSize = 15f; setTextColor(0xFF444444.toInt())
    }

    fun card(c: Context, text: String, ok: Boolean) = TextView(c).apply {
        this.text = text; textSize = 15f
        setTextColor(if (ok) 0xFF1B5E20.toInt() else 0xFFB71C1C.toInt())
        background = GradientDrawable().apply {
            cornerRadius = c.dp(10).toFloat()
            setColor(if (ok) 0xFFE8F5E9.toInt() else 0xFFFFEBEE.toInt())
        }
        val p = c.dp(14); setPadding(p, p, p, p)
    }

    fun primaryButton(c: Context, text: String, onClick: () -> Unit) = Button(c).apply {
        this.text = text; textSize = 17f; setTextColor(Color.WHITE); isAllCaps = false
        background = GradientDrawable().apply { cornerRadius = c.dp(10).toFloat(); setColor(PRIMARY) }
        setPadding(0, c.dp(14), 0, c.dp(14))
        setOnClickListener { onClick() }
    }

    fun textButton(c: Context, text: String, onClick: () -> Unit) = Button(c).apply {
        this.text = text; isAllCaps = false; setTextColor(PRIMARY); setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { onClick() }
    }

    fun LinearLayout.add(v: View, topMarginDp: Int = 0, width: Int = LinearLayout.LayoutParams.MATCH_PARENT) {
        addView(v, LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(topMarginDp)
        })
    }
}
