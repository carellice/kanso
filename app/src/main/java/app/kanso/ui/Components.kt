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
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = K.Muted)

@Composable
fun PageHeader(eyebrow: String, title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(6.dp))
            // Metro: il titolo del pivot, minuscolo e sottile; se è lungo esce dal bordo invece di andare a capo.
            if (K.metro) Text(title.lowercase(), style = MaterialTheme.typography.headlineLarge, color = K.Paper, maxLines = 1, overflow = TextOverflow.Clip)
            else Text(title, style = MaterialTheme.typography.headlineLarge, color = K.Paper)
        }
        trailing()
    }
}

@Composable
fun KCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .skin(RoundedCornerShape(28.dp), stitched = 28.dp)
            .padding(22.dp),
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
    val bg = if (filled) (if (K.metro) K.Shu else K.Paper) else if (K.nothing) Color.Transparent else K.SurfaceHigh
    val fg = if (filled && !K.metro) K.Ink else K.Paper
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
                    !K.skeuo -> Modifier.clip(CircleShape).background(bg)
                    filled -> Modifier.brass()
                    else -> Modifier.skin(CircleShape, bg, elevation = 3.dp)
                },
            )
            .then(if (enabled) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 15.dp),
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
fun IconCircle(icon: ImageVector, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        // Metro: il cerchio col bordo bianco dei pulsanti della app bar.
        modifier.size(44.dp).then(if (K.metro) Modifier.border(2.dp, K.Paper, CircleShape) else Modifier.skin(CircleShape, elevation = 3.dp)).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(20.dp), tint = K.Paper) }
}

@Composable
fun AppLabel(text: String, modifier: Modifier = Modifier) =
    Text(
        // Nothing: i nomi delle app come etichette di uno strumento, monospazio maiuscolo.
        if (K.nothing) text.uppercase() else text, modifier, style = MaterialTheme.typography.labelMedium, color = K.Paper.copy(alpha = 0.82f),
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
