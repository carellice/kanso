package app.kanso.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kanso.data.AppsRepository

val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun AppIcon(repo: AppsRepository, pkg: String, size: Dp, monochrome: Boolean = false, modifier: Modifier = Modifier) {
    val icon by produceState<ImageBitmap?>(repo.cachedIcon(pkg), pkg) { value = repo.icon(pkg) }
    val alpha by animateFloatAsState(if (icon != null) 1f else 0f, tween(260), label = "icon")
    Box(modifier.size(size)) {
        icon?.let {
            // Skeuomorfismo: l'icona è un oggetto appoggiato sul cuoio e proietta la sua ombra.
            if (K.skeuo) Image(
                it, null, Modifier.size(size).offset(y = size * 0.05f).graphicsLayer { this.alpha = alpha * 0.45f }.blur(size * 0.05f),
                colorFilter = ColorFilter.tint(Color.Black),
            )
            Image(
                it, null, Modifier.size(size).graphicsLayer { this.alpha = alpha },
                colorFilter = if (monochrome) grayscale else null,
            )
        }
    }
}

/** Clic con un leggero affondamento a molla, come un tasto vero. Metro: l'effetto tilt di Windows Phone, che inclina verso il dito. */
@Composable
fun Modifier.pressable(onLongClick: (() -> Unit)? = null, pressedScale: Float = 0.94f, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.press(), label = "press")
    val tilt by animateFloatAsState(if (pressed && K.metro) 1f else 0f, tween(if (pressed) 90 else 220), label = "tilt")
    val haptic = LocalHapticFeedback.current
    return this
        .graphicsLayer {
            scaleX = scale; scaleY = scale
            if (tilt > 0f) { rotationX = tilt * 9f; cameraDistance = 12f * density }
        }
        .combinedClickable(
            interactionSource = source, indication = null,
            onLongClick = onLongClick?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            onClick = onClick,
        )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = if (K.dueffe) K.Shu else K.Muted)

@Composable
fun PageHeader(eyebrow: String, title: String, trailing: @Composable () -> Unit = {}) {
    // In orizzontale l'altezza è preziosa: meno aria sopra e sotto il titolo.
    val landscape = isLandscape()
    Row(Modifier.fillMaxWidth().padding(top = if (landscape) 8.dp else 28.dp, bottom = if (landscape) 12.dp else 20.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(6.dp))
            // Metro: il titolo del pivot, minuscolo e sottile; se è lungo esce dal bordo invece di andare a capo.
            if (K.metro) Text(title.lowercase(), style = MaterialTheme.typography.headlineLarge, color = K.Paper, maxLines = 1, overflow = TextOverflow.Clip)
            else if (K.dueffe) GoldUnderlined(title)
            else Text(title, style = MaterialTheme.typography.headlineLarge, color = K.Paper)
        }
        trailing()
    }
}

/** Dueffe: il titolo con la sottolineatura disegnata a mano in oro forte, che si traccia all'entrata. */
@Composable
private fun GoldUnderlined(title: String) {
    val draw = remember(title) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(title) { draw.animateTo(1f, tween(720, delayMillis = 260, easing = Motion.Reveal)) }
    Text(
        title, style = MaterialTheme.typography.headlineLarge, color = K.Paper,
        modifier = Modifier.padding(bottom = 6.dp).drawBehind {
            val w = 2.6.dp.toPx()
            val y = size.height + 3.dp.toPx()
            val end = size.width * 0.62f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, y)
                quadraticTo(end * 0.5f, y + 4.dp.toPx(), end, y - 2.dp.toPx())
            }
            val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
            val part = androidx.compose.ui.graphics.Path()
            measure.getSegment(0f, measure.length * draw.value, part, true)
            drawPath(part, DF.PrimaryStrong, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        },
    )
}

@Composable
fun KCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .skin(RoundedCornerShape(if (K.dueffe) 26.dp else 28.dp), color = if (K.dueffe) K.SurfaceHigh else K.Surface, stitched = 28.dp)
            .padding(if (K.dueffe) 18.dp else 22.dp),
    ) { content() }
}

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val bg = if (filled) (if (K.metro) K.Shu else K.Paper) else if (K.nothing) Color.Transparent else if (K.dueffe) K.Surface else K.SurfaceHigh
    val fg = when { K.dueffe && filled -> DF.OnPrimary; filled && !K.metro -> K.Ink; else -> K.Paper }
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(300), label = "pill")
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .then(
                when {
                    // Metro: bottone rettangolare, pieno d'accento oppure vuoto col bordo bianco.
                    K.metro -> if (filled) Modifier.background(bg) else Modifier.border(2.dp, K.Paper)
                    // Nothing: primario pillola bianca, secondario solo il contorno.
                    K.nothing -> if (filled) Modifier.clip(CircleShape).background(bg) else Modifier.border(1.dp, K.SurfaceHigher, CircleShape)
                    // Dueffe: tasto raggio 18, oro pieno oppure superficie con la linea forte.
                    K.dueffe -> RoundedCornerShape(18.dp).let { s -> if (filled) Modifier.gold(s) else Modifier.clip(s).background(bg).border(1.dp, DF.LineStrong, s) }
                    !K.skeuo -> Modifier.clip(CircleShape).background(bg)
                    filled -> Modifier.brass()
                    else -> Modifier.skin(CircleShape, bg, elevation = 3.dp)
                },
            )
            .then(if (enabled) Modifier.pressable(pressedScale = if (K.dueffe) 0.95f else 0.94f, onClick = onClick) else Modifier)
            .padding(horizontal = if (K.dueffe) 18.dp else 22.dp, vertical = if (K.dueffe) 14.dp else 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = fg); Spacer(Modifier.size(8.dp)) }
        // Sull'ottone le lettere sono incise: ombra chiara sotto invece che scura.
        val style = MaterialTheme.typography.labelLarge
        Text(
            when { K.metro -> text.lowercase(); K.nothing -> text.uppercase(); else -> text }, style = if (K.skeuo && filled) style.copy(shadow = androidx.compose.ui.graphics.Shadow(Color.White.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(0f, 1.5f), 0f)) else style,
            color = fg, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun IconCircle(icon: ImageVector, description: String, modifier: Modifier = Modifier, active: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        // Metro: il cerchio col bordo bianco dei pulsanti della app bar.
        modifier.size(44.dp).then(if (K.metro) Modifier.border(2.dp, K.Paper, CircleShape) else Modifier.skin(CircleShape, elevation = 3.dp)).pressable(onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(20.dp), tint = if (active) K.Shu else K.Paper) }
}

@Composable
fun AppLabel(text: String, modifier: Modifier = Modifier, compact: Boolean = false) =
    Text(
        // Nothing: i nomi delle app come etichette di uno strumento, monospazio maiuscolo.
        if (K.nothing) text.uppercase() else text, modifier,
        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, color = K.Paper.copy(alpha = 0.82f),
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
    )

/** Ricorda dove sta un'icona, così l'app che apre cresce proprio da lì. */
class LaunchFrom(private val view: android.view.View) {
    private var bounds = androidx.compose.ui.geometry.Rect.Zero
    val modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }
    fun options(): android.os.Bundle? = if (bounds.isEmpty) null else android.app.ActivityOptions.makeScaleUpAnimation(
        view, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt(),
    ).toBundle()
}

@Composable
fun rememberLaunchFrom(): LaunchFrom {
    val view = androidx.compose.ui.platform.LocalView.current
    return remember(view) { LaunchFrom(view) }
}

/** Kanso girato in orizzontale. */
@Composable
fun isLandscape() = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

/** In orizzontale barra di navigazione e foro della fotocamera stanno di lato: il contenuto si scosta. In verticale non fa nulla. */
@Composable
fun Modifier.sideInsetsPadding() = windowInsetsPadding(
    WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
)

/** In verticale una sotto l'altra; in orizzontale su due colonne, la prima metà a sinistra e il resto a destra. */
@Composable
fun TwoColumns(items: List<@Composable () -> Unit>, spacing: Dp = 14.dp) {
    if (!isLandscape()) Column(verticalArrangement = Arrangement.spacedBy(spacing)) { items.forEach { it() } }
    else Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        val half = (items.size + 1) / 2
        listOf(items.take(half), items.drop(half)).forEach { part ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing)) { part.forEach { it() } }
        }
    }
}
