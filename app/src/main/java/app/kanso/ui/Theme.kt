package app.kanso.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import app.kanso.data.Style

/** Inchiostro sumi, carta washi e un solo accento vermiglio (shu). */
object K {
    /** Stile scelto nelle impostazioni: cambia colori, materiali e forme di tutta l'app. */
    var style: () -> Style = { Style.KANSO }
    /** Skeuomorfismo: cuoio cucito, ottone e rilievi al posto dell'inchiostro piatto. */
    val skeuo get() = style() == Style.SKEUO
    /** Modern UI (Windows Phone): nero pieno, riquadri quadrati nel colore d'accento, testo sottile. */
    val metro get() = style() == Style.METRO
    /** Nothing: nero OLED, testo bianco, etichette monospazio maiuscole, griglia di punti e un solo rosso segnale. */
    val nothing get() = style() == Style.NOTHING
    /** Dueffe: fondo quasi nero, superfici calde a gradiente, testo avorio e un solo accento oro. */
    val dueffe get() = style() == Style.DUEFFE

    private fun pick(kanso: Long, skeuo: Long, metro: Long, nothing: Long, dueffe: Long) =
        Color(when (style()) { Style.KANSO -> kanso; Style.SKEUO -> skeuo; Style.METRO -> metro; Style.NOTHING -> nothing; Style.DUEFFE -> dueffe })

    val Ink get() = pick(0xFF0E0D0B, 0xFF1F150E, 0xFF000000, 0xFF000000, 0xFF050505)
    val Surface get() = pick(0xFF171512, 0xFF3D2A1C, 0xFF1A1A1A, 0xFF111111, 0xFF11100D)
    val SurfaceHigh get() = pick(0xFF201D19, 0xFF4A3423, 0xFF262626, 0xFF1A1A1A, 0xFF1A1711)
    val SurfaceHigher get() = pick(0xFF2A2621, 0xFF5A402B, 0xFF333333, 0xFF333333, 0xFF242016)
    val Paper get() = pick(0xFFF1E9DA, 0xFFF6E9D0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFAF0)
    val Muted get() = pick(0xFF9A9183, 0xFFC4AD8C, 0xFFA6A6A6, 0xFF999999, 0xFFB8AA8D)
    val Faint get() = pick(0xFF5E574D, 0xFF907859, 0xFF6B6B6B, 0xFF666666, 0xFF6E6553)
    val Line get() = pick(0x1FF1E9DA, 0x2EF6E9D0, 0x26FFFFFF, 0xFF222222, 0x29FFDC80)
    /** Metro: il cobalto dell'accento di Windows Phone. Nothing: il rosso segnale, solo per ciò che è urgente. Dueffe: l'oro. */
    val Shu get() = pick(0xFFD9623B, 0xFFE2AE52, 0xFF1BA1E2, 0xFFD71921, 0xFFFFD84D)
    val Matcha get() = pick(0xFFA9B78F, 0xFF9FC27A, 0xFF8CBF26, 0xFF4A9E5C, 0xFFA8F0B0)
    /** Errori: l'accento, tranne in Dueffe dove l'oro non può dire "sbagliato". */
    val Alert get() = if (dueffe) Color(0xFFFF9B85) else Shu
}

/** Token Dueffe senza equivalente negli altri stili. */
object DF {
    val PrimaryStrong = Color(0xFFFFB800)
    val Secondary = Color(0xFFFFF0AD)
    val OnPrimary = Color(0xFF161006)
    val LineStrong = Color(0x57FFD84D)
}

object Motion {
    val Calm = CubicBezierEasing(.22f, .8f, .2f, 1f)
    val Standard = CubicBezierEasing(.4f, 0f, .2f, 1f)
    /** Nothing: niente molle né rimbalzi, solo un ease-out breve e meccanico. */
    private val Click = CubicBezierEasing(.25f, .1f, .25f, 1f)
    /** Dueffe: le curve del design system. */
    val Reveal = CubicBezierEasing(.22f, .78f, .16f, 1f)
    val Letter = CubicBezierEasing(.16f, .86f, .24f, 1f)
    fun <T> soft(): FiniteAnimationSpec<T> = if (K.nothing) tween(300, easing = Click) else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
    /** Dueffe: la molla che rimbalza al rilascio (smorzamento 0,42, rigidità 380). */
    fun <T> press(): FiniteAnimationSpec<T> = when {
        K.nothing -> tween(150, easing = Click)
        K.dueffe -> spring(dampingRatio = 0.42f, stiffness = 380f)
        else -> spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
    }
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

/** Metro: un solo carattere, sottile e grande nei titoli, come Segoe WP. */
private val metroTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(300), fontSize = 60.sp, lineHeight = 64.sp, letterSpacing = (-0.02).em),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(300), fontSize = 46.sp, lineHeight = 52.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(300), fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(300), fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight(600), fontSize = 12.sp, letterSpacing = 0.04.em),
)

@OptIn(ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(R.font.grotesk, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Nothing: Doto (punti a matrice) per i numeri eroe, Space Grotesk per il testo, Space Mono per etichette e dati. */
@OptIn(ExperimentalTextApi::class)
val Doto = FontFamily(Font(R.font.doto, FontWeight(600), variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.Setting("ROND", 100f))))
val Grotesk = FontFamily(grotesk(300), grotesk(400), grotesk(500))
val Mono = FontFamily(Font(R.font.mono))

private val nothingTypography = Typography(
    displayLarge = TextStyle(fontFamily = Doto, fontWeight = FontWeight(600), fontSize = 64.sp, lineHeight = 66.sp, letterSpacing = (-0.02).em),
    headlineLarge = TextStyle(fontFamily = Doto, fontWeight = FontWeight(600), fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.03).em),
    headlineMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight(400), fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight(400), fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight(500), fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.01.em),
    labelLarge = TextStyle(fontFamily = Mono, fontSize = 13.sp, letterSpacing = 0.06.em),
    labelMedium = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.06.em),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.1.em),
)

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int) = Font(R.font.bricolage, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Dueffe: Bricolage Grotesque per titoli e display, Inter per testo ed etichette. */
val Bricolage = FontFamily(bricolage(600), bricolage(700), bricolage(800))

private val dueffeTypography = Typography(
    displayLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(800), fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = (-0.035).em),
    headlineLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(800), fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = (-0.03).em),
    headlineMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(700), fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em),
    titleLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(600), fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight(600), fontSize = 15.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight(600), fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight(500), fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.04.em),
)

/** Lettere in rilievo che proiettano un'ombra sul cuoio. */
private val Emboss = Shadow(Color(0xB0000000), Offset(0f, 2f), 3f)

@Composable
fun KansoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = K.Ink, surface = K.Surface, surfaceContainer = K.Surface, surfaceContainerHigh = K.SurfaceHigh,
            onBackground = K.Paper, onSurface = K.Paper, onSurfaceVariant = K.Muted,
            primary = K.Paper, onPrimary = K.Ink, secondary = K.Shu, outline = K.Line, error = K.Alert,
        ),
        typography = when { K.metro -> metroTypography; K.nothing -> nothingTypography; K.dueffe -> dueffeTypography; else -> typography(K.skeuo) },
        content = content,
    )
}
