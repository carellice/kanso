package app.kanso.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.kanso.KansoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private class Slide(val eyebrow: String, val title: String, val body: String, val art: @Composable (Boolean) -> Unit)

private val slides = listOf(
    Slide("簡素 · Kanso", "Togli il superfluo.", "Un launcher calmo che mette davanti solo quello che serve, e lascia il resto un passo più in là.") { Enso(it) },
    Slide("Tre schermate", "Nessun cassetto di app.", "A sinistra la consapevolezza, al centro la tua Home, a destra la Libreria ordinata per categorie.") { ThreePages(it) },
    Slide("Attrito consapevole", "Un respiro prima di aprire.", "Le app fuori dalla Home chiedono un momento. Social, video e giochi aspettano un po' di più.") { Breath(it) },
    Slide("Ricerca universale", "Tutto da un solo posto.", "App, contatti, indirizzi e siti. Scorri verso il basso in Home per il riassunto delle notifiche.") { SearchArt(it) },
)

/** Presentazione del primo avvio: quattro idee e poi i permessi, tutto saltabile. */
@Composable
fun Onboarding(vm: KansoViewModel) {
    val total = slides.size + 1
    val pager = rememberPagerState { total }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == total - 1

    BackHandler(pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }

    Box(Modifier.fillMaxSize().backdrop()) {
        OnboardingGlow(pager)
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val d = page - (pager.currentPage + pager.currentPageOffsetFraction)
            Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp).padding(top = 72.dp, bottom = 130.dp),
            ) {
                if (page < slides.size) {
                    val s = slides[page]
                    // L'illustrazione scorre più lenta del testo: un poco di profondità.
                    Box(
                        Modifier.fillMaxWidth().weight(1f).graphicsLayer {
                            translationX = size.width * 0.35f * d; alpha = 1f - d.absoluteValue.coerceIn(0f, 1f)
                        },
                        contentAlignment = Alignment.Center,
                    ) { s.art(pager.settledPage == page) }
                    Column(Modifier.graphicsLayer { translationX = size.width * 0.15f * d; alpha = 1f - d.absoluteValue.coerceIn(0f, 1f) }) {
                        Eyebrow(s.eyebrow)
                        Spacer(Modifier.height(10.dp))
                        Text(s.title, style = MaterialTheme.typography.headlineLarge, color = K.Paper)
                        Spacer(Modifier.height(14.dp))
                        Text(s.body, style = MaterialTheme.typography.bodyLarge, color = K.Muted)
                    }
                } else SetupPage(vm, Modifier.graphicsLayer { alpha = 1f - d.absoluteValue.coerceIn(0f, 1f) })
            }
        }

        if (!last) {
            Text(
                "Salta", style = MaterialTheme.typography.labelLarge, color = K.Muted,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).clip(CircleShape)
                    .pressable { scope.launch { pager.animateScrollToPage(total - 1) } }.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(total) { i ->
                    val d = ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    Box(
                        Modifier.height(5.dp).width(lerp(22f, 5f, d).dp).clip(CircleShape)
                            // Dueffe: la pagina attuale in oro.
                            .background(if (K.dueffe) androidx.compose.ui.graphics.lerp(K.Shu, K.Paper.copy(alpha = 0.22f), d) else K.Paper.copy(alpha = lerp(0.9f, 0.25f, d))),
                    )
                }
            }
            PillButton(if (last) "Inizia" else "Avanti") {
                if (last) vm.prefs.saveOnboarded() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
        }
    }
}

@Composable
private fun SetupPage(vm: KansoViewModel, modifier: Modifier) {
    val isHome = rememberIsHome(vm)
    val requestHome = rememberHomeRequest(vm)
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshAgenda() }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Eyebrow("Ultimo passo")
        Spacer(Modifier.height(10.dp))
        Text("Rendila tua.", style = MaterialTheme.typography.headlineLarge, color = K.Paper)
        Spacer(Modifier.height(14.dp))
        Text(
            "Ogni permesso serve a una cosa sola e i dati restano sul telefono. Puoi farlo anche dopo, dalle impostazioni.",
            style = MaterialTheme.typography.bodyLarge, color = K.Muted,
        )
        Spacer(Modifier.height(28.dp))
        KCard {
            Column {
                Row_("Kanso come Home", "Il launcher predefinito", done = isHome, onClick = requestHome)
                Row_("Dati di utilizzo", "Tempo schermo e sblocchi", done = vm.hasUsageAccess) {
                    vm.start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                Row_("Calendario", "L'agenda dei prossimi 7 giorni", done = vm.hasCalendarAccess) {
                    calendar.launch(Manifest.permission.READ_CALENDAR)
                }
                Row_("Lettura notifiche", "Riassunto, timer e cronometro", done = vm.hasNotificationAccess) {
                    vm.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
        }
    }
}

/** L'alone vermiglio scivola con le pagine, come nella Home. */
@Composable
private fun OnboardingGlow(pager: PagerState) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            val pos = (pager.currentPage + pager.currentPageOffsetFraction) / (pager.pageCount - 1)
            val c = Offset(size.width * lerp(0.2f, 0.8f, pos), size.height * 0.3f)
            drawCircle(Brush.radialGradient(listOf(K.Shu.copy(alpha = 0.13f), K.Shu.copy(alpha = 0f)), c, size.width), size.width, c)
        },
    )
}

/** L'ensō, il cerchio zen tracciato con un solo gesto di pennello. */
@Composable
private fun Enso(active: Boolean) {
    val draw = remember { Animatable(0f) }
    LaunchedEffect(active) { if (active) { draw.snapTo(0f); draw.animateTo(1f, tween(1800, easing = Motion.Calm)) } }
    val breathe by rememberInfiniteTransition(label = "enso").animateFloat(
        1f, 1.04f, infiniteRepeatable(tween(3200, easing = Motion.Standard), RepeatMode.Reverse), label = "ensoScale",
    )
    Canvas(Modifier.size(220.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }) {
        val w = size.minDimension * 0.07f
        drawArc(K.Shu, -100f, 320f * draw.value, false, style = Stroke(w, cap = StrokeCap.Round))
        drawArc(K.Shu.copy(alpha = 0.35f), -96f, 300f * draw.value, false, style = Stroke(w * 0.35f, cap = StrokeCap.Round),
            topLeft = Offset(w * 0.4f, w * 0.2f), size = size.copy(size.width - w * 0.8f, size.height - w * 0.4f))
    }
}

/** Tre telefoni stilizzati: la luce passa dall'uno all'altro. */
@Composable
private fun ThreePages(active: Boolean) {
    var lit by remember { mutableIntStateOf(1) }
    LaunchedEffect(active) { while (active) { delay(1600); lit = (lit + 1) % 3 } }
    val labels = listOf("Consapevolezza", "Home", "Libreria")
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            val on = i == lit
            val scale by androidx.compose.animation.core.animateFloatAsState(if (on) 1.08f else 0.92f, Motion.soft(), label = "page$i")
            val glow by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, tween(500), label = "glow$i")
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                Box(
                    Modifier.width(78.dp).height(150.dp).skin(RoundedCornerShape(18.dp), border = if (on) K.Shu.copy(alpha = 0.6f) else K.Line).padding(10.dp),
                ) { MiniPage(i, glow) }
                Spacer(Modifier.height(10.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) K.Paper else K.Faint)
            }
        }
    }
}

@Composable
private fun MiniPage(kind: Int, glow: Float) {
    val ink = K.Paper.copy(alpha = lerp(0.18f, 0.5f, glow))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (kind) {
            0 -> {
                Box(Modifier.width(36.dp).height(6.dp).clip(CircleShape).background(ink))
                Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(K.SurfaceHigher))
                Row(Modifier.height(36.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    listOf(0.3f, 0.6f, 0.9f, 0.5f, 0.7f, 0.4f).forEach { h ->
                        Box(Modifier.width(6.dp).fillMaxHeight(h).clip(CircleShape).background(K.Shu.copy(alpha = lerp(0.3f, 0.9f, glow))))
                    }
                }
            }
            1 -> {
                Spacer(Modifier.height(24.dp))
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { Box(Modifier.size(14.dp).clip(RoundedCornerShape(5.dp)).background(ink)) }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(K.SurfaceHigher))
            }
            else -> repeat(5) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(ink))
                    Box(Modifier.width((18 + r * 7 % 20).dp).height(5.dp).clip(CircleShape).background(K.Faint))
                }
            }
        }
    }
}

/** Un cerchio che si gonfia e si sgonfia: quattro secondi dentro, quattro fuori. */
@Composable
private fun Breath(active: Boolean) {
    val t by rememberInfiniteTransition(label = "breath").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4000, easing = Motion.Standard), RepeatMode.Reverse), label = "breathT",
    )
    val inhale = remember { mutableStateOf(true) }
    LaunchedEffect(active) { while (active) { inhale.value = true; delay(4000); inhale.value = false; delay(4000) } }
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(240.dp)) {
            val r = size.minDimension / 2 * lerp(0.5f, 1f, t)
            drawCircle(Brush.radialGradient(listOf(K.Shu.copy(alpha = 0.28f), K.Shu.copy(alpha = 0f)), center, r * 1.3f), r * 1.3f)
            drawCircle(K.Shu.copy(alpha = 0.85f), r * 0.82f, style = Stroke(2.dp.toPx()))
            drawCircle(K.Paper.copy(alpha = 0.08f), r * 0.6f)
        }
        AnimatedContent(inhale.value, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) }, label = "breathText") {
            Text(if (it) "Inspira" else "Espira", style = MaterialTheme.typography.titleLarge, color = K.Paper)
        }
    }
}

/** Una barra di ricerca che si scrive da sola. */
@Composable
private fun SearchArt(active: Boolean) {
    val words = listOf("Mappe" to "App", "Giulia" to "Contatto", "Via Roma 12" to "Indirizzo", "wikipedia.org" to "Sito")
    var index by remember { mutableIntStateOf(0) }
    var typed by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        while (active) {
            val w = words[index].first
            for (n in 1..w.length) { typed = n; delay(90) }
            delay(1300)
            index = (index + 1) % words.size; typed = 0
            delay(250)
        }
    }
    val (word, kind) = words[index]
    val caret by rememberInfiniteTransition(label = "caret").animateFloat(
        1f, 0f, infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse), label = "caretA",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).skin(CircleShape, K.SurfaceHigh, inset = true).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = K.Muted)
            Spacer(Modifier.width(12.dp))
            Text(word.take(typed), style = MaterialTheme.typography.bodyLarge, color = K.Paper)
            Box(Modifier.width(2.dp).height(20.dp).graphicsLayer { alpha = caret }.background(K.Shu))
        }
        val shown = typed == word.length
        val a by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, tween(350, easing = Motion.Calm), label = "result")
        Row(
            Modifier.fillMaxWidth().graphicsLayer { alpha = a; translationY = (1f - a) * 24f }
                .skin(RoundedCornerShape(20.dp)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(K.Shu.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Text(word.first().uppercase(), style = MaterialTheme.typography.titleMedium, color = K.Shu)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(word, style = MaterialTheme.typography.titleMedium, color = K.Paper)
                Text(kind, style = MaterialTheme.typography.bodyMedium, color = K.Muted)
            }
        }
        Text(
            "↓  Scorri in Home per il riassunto", style = MaterialTheme.typography.labelMedium, color = K.Faint,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}
