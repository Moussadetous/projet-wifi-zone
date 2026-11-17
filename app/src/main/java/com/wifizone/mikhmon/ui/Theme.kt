package com.wifizone.mikhmon.ui

import android.content.res.AssetManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Brand {
    val Indigo = Color(0xFF4338CA)
    val Blue = Color(0xFF2563EB)
    val Violet = Color(0xFF7C3AED)
    val Pink = Color(0xFFDB2777)
    val Green = Color(0xFF16A34A)
    val Orange = Color(0xFFEA580C)
    val Red = Color(0xFFDC2626)
    val Teal = Color(0xFF0D9488)
    val Amber = Color(0xFFD97706)
    val Bg = Color(0xFFF3F5FA)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF64748B)
    val Line = Color(0xFFE6E9F0)
    val Hero = Brush.linearGradient(listOf(Color(0xFF3B5BFD), Color(0xFF6D3AED), Color(0xFFA62CC9)))
    val Premium = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6)))
}

@OptIn(ExperimentalTextApi::class)
private fun inter(assets: AssetManager) = FontFamily(
    Font("fonts/inter_regular.ttf", assets, FontWeight.Normal),
    Font("fonts/inter_medium.ttf", assets, FontWeight.Medium),
    Font("fonts/inter_semibold.ttf", assets, FontWeight.SemiBold),
    Font("fonts/inter_bold.ttf", assets, FontWeight.Bold),
    Font("fonts/inter_black.ttf", assets, FontWeight.Black),
)

@OptIn(ExperimentalTextApi::class)
fun monoFamily(assets: AssetManager) = FontFamily(
    Font("fonts/jetbrains_mono_bold.ttf", assets, FontWeight.Bold),
    Font("fonts/jetbrains_mono_extrabold.ttf", assets, FontWeight.ExtraBold),
)

@Composable
fun MikhmonTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val family = remember { inter(ctx.assets) }
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = family)
    val typo = Typography(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f().copy(fontWeight = FontWeight.Black),
        headlineMedium = base.headlineMedium.f().copy(fontWeight = FontWeight.Black, letterSpacing = (-0.4).sp),
        headlineSmall = base.headlineSmall.f().copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.f().copy(fontWeight = FontWeight.Bold), titleMedium = base.titleMedium.f().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.f().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f().copy(fontWeight = FontWeight.SemiBold), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand.Indigo, onPrimary = Color.White, primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Brand.Indigo,
            secondary = Brand.Violet, background = Brand.Bg, surface = Brand.Card, onSurface = Brand.Ink, onBackground = Brand.Ink,
            surfaceVariant = Color(0xFFEEF1F7), onSurfaceVariant = Brand.Muted, outline = Brand.Line, error = Brand.Red,
            surfaceContainer = Color(0xFFF7F8FC), surfaceContainerHigh = Color.White, surfaceContainerLow = Color(0xFFF7F8FC),
        ),
        typography = typo,
        content = content,
    )
}
