package app.kanso.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.kanso.R

/** Inchiostro sumi, carta washi e un solo accento vermiglio (shu). */
object K {
    /** Stile skeuomorfico: cuoio cucito, ottone e rilievi al posto dell'inchiostro piatto. */
    var style: () -> Boolean = { false }
    val skeuo get() = style()

    val Ink get() = if (skeuo) Color(0xFF1F150E) else Color(0xFF0E0D0B)
    val Surface get() = if (skeuo) Color(0xFF3D2A1C) else Color(0xFF171512)
    val SurfaceHigh get() = if (skeuo) Color(0xFF4A3423) else Color(0xFF201D19)
    val SurfaceHigher get() = if (skeuo) Color(0xFF5A402B) else Color(0xFF2A2621)
    val Paper get() = if (skeuo) Color(0xFFF6E9D0) else Color(0xFFF1E9DA)
    val Muted get() = if (skeuo) Color(0xFFC4AD8C) else Color(0xFF9A9183)
    val Faint get() = if (skeuo) Color(0xFF907859) else Color(0xFF5E574D)
    val Line get() = if (skeuo) Color(0x2EF6E9D0) else Color(0x1FF1E9DA)
    val Shu get() = if (skeuo) Color(0xFFE2AE52) else Color(0xFFD9623B)
    val Matcha get() = if (skeuo) Color(0xFF9FC27A) else Color(0xFFA9B78F)
}

object Motion {
    val Calm = CubicBezierEasing(.22f, .8f, .2f, 1f)
    val Standard = CubicBezierEasing(.4f, 0f, .2f, 1f)
    fun <T> soft() = spring<T>(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
    fun <T> press() = spring<T>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
}

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(R.font.inter, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Serif = FontFamily(Font(R.font.serif))
val Sans = FontFamily(inter(300), inter(400), inter(500), inter(600))

private fun typography(skeuo: Boolean) = Typography(
    displayLarge = TextStyle(shadow = if (skeuo) Emboss else null, fontFamily = Serif, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-0.01).em),
    headlineLarge = TextStyle(shadow = if (skeuo) Emboss else null, fontFamily = Serif, fontSize = 38.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(shadow = if (skeuo) Emboss else null, fontFamily = Serif, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(shadow = if (skeuo) Emboss else null, fontFamily = Serif, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(shadow = if (skeuo) Emboss else null, fontFamily = Sans, fontWeight = FontWeight(500), fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(shadow = if (skeuo) Emboss else null, fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(shadow = if (skeuo) Emboss else null, fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(shadow = if (skeuo) Emboss else null, fontFamily = Sans, fontWeight = FontWeight(500), fontSize = 15.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(shadow = if (skeuo) Emboss else null, fontFamily = Sans, fontWeight = FontWeight(500), fontSize = 12.sp, letterSpacing = 0.02.em),
    labelSmall = TextStyle(shadow = if (skeuo) Emboss else null, fontFamily = Sans, fontWeight = FontWeight(600), fontSize = 11.sp, letterSpacing = 0.16.em),
)

/** Lettere in rilievo che proiettano un'ombra sul cuoio. */
private val Emboss = Shadow(Color(0xB0000000), Offset(0f, 2f), 3f)

@Composable
fun KansoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = K.Ink, surface = K.Surface, surfaceContainer = K.Surface, surfaceContainerHigh = K.SurfaceHigh,
            onBackground = K.Paper, onSurface = K.Paper, onSurfaceVariant = K.Muted,
            primary = K.Paper, onPrimary = K.Ink, secondary = K.Shu, outline = K.Line, error = K.Shu,
        ),
        typography = typography(K.skeuo),
        content = content,
    )
}
