package app.kanso.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/*
 * Materiali dello stile skeuomorfico. Tutto è disegnato in codice: grana del cuoio, trama del lino,
 * rilievi, incassi e cuciture. Con lo stile Kanso ogni funzione ricade sul disegno piatto di sempre.
 */

private fun noise(size: Int, seed: Int, pixel: (Random) -> Int): Bitmap {
    val r = Random(seed)
    return Bitmap.createBitmap(IntArray(size * size) { pixel(r) }, size, size, Bitmap.Config.ARGB_8888)
}

private fun tiled(bitmap: Bitmap, scale: Float) = object : ShaderBrush() {
    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        .apply { setLocalMatrix(Matrix().apply { setScale(scale, scale) }) }
    override fun createShader(size: Size) = shader
}

/** Punti chiari e scuri casuali: ingranditi e sfumati diventano i pori della pelle. */
private val pores by lazy {
    noise(64, 7) { r ->
        val a = r.nextInt(0, 46)
        if (r.nextBoolean()) a shl 24 else (a shl 24) or 0xF6E9D0
    }
}
private val grain by lazy {
    noise(96, 11) { r -> (r.nextInt(0, 30) shl 24) or (if (r.nextBoolean()) 0 else 0xFFFFFF) }
}

/** Fili orizzontali e verticali appena irregolari: la tela di lino delle sovrapposizioni. */
private val linen by lazy {
    val n = 96
    val r = Random(5)
    val rows = IntArray(n) { r.nextInt(0, 40) }
    val cols = IntArray(n) { r.nextInt(0, 40) }
    Bitmap.createBitmap(IntArray(n * n) { i ->
        val a = (rows[i / n] + cols[i % n]) / 2 + r.nextInt(0, 10)
        (a shl 24) or (if ((i / n + i % n) % 2 == 0) 0 else 0xF6E9D0)
    }, n, n, Bitmap.Config.ARGB_8888)
}

private val poresBrush by lazy { tiled(pores, 3.5f) }
private val grainBrush by lazy { tiled(grain, 1f) }
private val linenBrush by lazy { tiled(linen, 1.5f) }

private fun DrawScope.leather() {
    drawRect(poresBrush)
    drawRect(grainBrush)
}

private fun DrawScope.stitch(inset: Dp, corner: Dp, color: Color) {
    val i = inset.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.5.dp.toPx()))
    val radius = CornerRadius((corner.toPx() - i).coerceAtLeast(0f))
    val w = 1.6.dp.toPx()
    val area = Size(size.width - i * 2, size.height - i * 2)
    // Prima l'incavo scuro del foro, poi il filo chiaro appena sopra.
    drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(i, i + 1.dp.toPx()), area, radius, Stroke(w, pathEffect = dash))
    drawRoundRect(color, Offset(i, i), area, radius, Stroke(w, pathEffect = dash))
}

/** Sfondo pieno: cuoio con vignettatura e cucitura sul bordo, oppure lino per le schede sovrapposte. */
fun Modifier.backdrop(cloth: Boolean = false, stitched: Boolean = !cloth): Modifier =
    if (!K.skeuo) background(K.Ink)
    else drawBehind {
        if (cloth) {
            drawRect(Color(0xFF2A221B))
            drawRect(linenBrush)
        } else {
            drawRect(Brush.radialGradient(listOf(Color(0xFF5A3D27), Color(0xFF2A1C11)), Offset(size.width / 2, size.height * 0.35f), size.maxDimension * 0.75f))
            leather()
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center, size.maxDimension * 0.72f))
        if (stitched) stitch(9.dp, 34.dp, K.Shu.copy(alpha = 0.55f))
    }

/**
 * Superficie di un controllo. Kanso: colore pieno e filo sottile. Skeuomorfismo: in rilievo con luce dall'alto,
 * bordo smussato e ombra portata; oppure incassata, come un campo inciso nel cuoio.
 */
fun Modifier.skin(
    shape: Shape,
    color: Color = K.Surface,
    inset: Boolean = false,
    border: Color = K.Line,
    elevation: Dp = 5.dp,
    stitched: Dp? = null,
): Modifier = when {
    !K.skeuo -> clip(shape).background(color).border(1.dp, border, shape)
    inset -> clip(shape)
        .background(Brush.verticalGradient(listOf(lerp(color, Color.Black, 0.55f), lerp(color, Color.Black, 0.3f))))
        .drawWithContent {
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), endY = 8.dp.toPx()))
            drawContent()
        }
        .border(1.dp, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.White.copy(alpha = 0.14f))), shape)
    else -> shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.12f), lerp(color, Color.Black, 0.22f))))
        .drawBehind {
            // Sui rilievi la grana è più fine: i pori pieni sbiadirebbero il colore del cuoio.
            drawRect(poresBrush, alpha = 0.35f)
            drawRect(grainBrush)
            // Lucentezza: la metà alta riflette la luce della stanza.
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.Transparent), endY = size.height * 0.5f))
            stitched?.let { stitch(7.dp, it, K.Shu.copy(alpha = 0.6f)) }
        }
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.5f))), shape)
}

/** Bottone d'ottone lucido, per le azioni principali. */
fun Modifier.brass(shape: Shape = CircleShape): Modifier =
    shadow(5.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFFFBEAC0), Color(0xFFE0B465), Color(0xFFB98532), Color(0xFFD7A851))))
        .drawBehind {
            drawRect(grainBrush)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f))),
                Offset(size.width * 0.04f, 1.dp.toPx()), Size(size.width * 0.92f, size.height * 0.48f), CornerRadius(size.height / 2),
            )
        }
        .border(1.dp, Brush.verticalGradient(listOf(Color(0xFFFFF6DC), Color(0xFF7A5420))), shape)

/** Pomello della levetta: metallo spazzolato con riflesso. */
fun Modifier.knob(): Modifier =
    shadow(3.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(CircleShape)
        .background(Brush.verticalGradient(listOf(Color(0xFFFFFBF2), Color(0xFFE3D5BC), Color(0xFFA9967A))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.45f))), CircleShape)

/** Separatore: filo sottile, oppure solco inciso (ombra sopra, luce sotto). */
@Composable
fun Groove(modifier: Modifier = Modifier) =
    Box(
        modifier.fillMaxWidth().padding(vertical = 4.dp).height(if (K.skeuo) 2.dp else 1.dp).drawBehind {
            if (!K.skeuo) drawRect(K.Line)
            else {
                drawRect(Color.Black.copy(alpha = 0.5f), size = Size(size.width, size.height / 2))
                drawRect(Color.White.copy(alpha = 0.08f), Offset(0f, size.height / 2), Size(size.width, size.height / 2))
            }
        },
    )
