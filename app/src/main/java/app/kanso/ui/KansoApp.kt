package app.kanso.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.kanso.KansoViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

const val AWARENESS = 0
const val HOME = 1
const val LIBRARY = 2

@Composable
fun KansoApp(vm: KansoViewModel) {
    val pager = rememberPagerState(initialPage = HOME) { 3 }
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm.homeSignal) { if (vm.homeSignal > 0) pager.animateScrollToPage(HOME) }
    // Le impostazioni si aprono anche dalla pagina a sinistra: la modifica dei riquadri riporta alla Home.
    LaunchedEffect(vm.editing) { if (vm.editing) pager.animateScrollToPage(HOME) }

    // Tornando da un'app la Home riaffiora: si riassesta da un poco più vicino e si schiarisce.
    val arrival = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(vm.returnSignal) {
        if (vm.returnSignal == 0) return@LaunchedEffect
        arrival.snapTo(0f)
        arrival.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 220f))
    }

    // Aprendo un'app la Home si allontana verso lo schermo e svanisce, mentre l'app cresce dall'icona.
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(view) { vm.rootView = java.lang.ref.WeakReference(view) }
    val depart = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(vm.departSignal) { if (vm.departSignal > 0) depart.animateTo(1f, tween(380, easing = Motion.Calm)) }
    LaunchedEffect(vm.returnSignal) { depart.snapTo(0f) }

    // Un launcher non si chiude mai: Indietro chiude le sovrapposizioni o torna alla Home.
    BackHandler {
        when {
            vm.unlock != null -> vm.cancelUnlock()
            vm.friction != null -> vm.cancelFriction()
            vm.pickerOpen -> vm.pickerOpen = false
            vm.settingsOpen -> vm.settingsOpen = false
            vm.agendaOpen -> vm.agendaOpen = false
            vm.notesOpen -> vm.notesOpen = false
            vm.searchOpen -> vm.searchOpen = false
            vm.editing -> vm.editing = false
            pager.currentPage != HOME -> scope.launch { pager.animateScrollToPage(HOME) }
        }
    }

    val covered = vm.searchOpen || vm.agendaOpen || vm.notesOpen || vm.settingsOpen || vm.friction != null || vm.unlock != null || vm.pickerOpen
    val blur by animateDpAsState(if (covered) 18.dp else 0.dp, tween(420, easing = Motion.Calm), label = "blur")
    val dim by animateFloatAsState(if (covered) 0.92f else 1f, tween(420, easing = Motion.Calm), label = "dim")

    Box(Modifier.fillMaxSize().background(K.Ink)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val back = lerp(1.08f, 1f, arrival.value) * lerp(1f, 1.12f, depart.value)
                    scaleX = dim.coerceAtLeast(0.96f) * back; scaleY = scaleX
                    alpha = dim * lerp(0.3f, 1f, arrival.value).coerceIn(0f, 1f) * (1f - depart.value * 0.85f)
                }
                .blur(blur)
                .backdrop(),
        ) {
            Glow(pager)
            HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                userScrollEnabled = !vm.editing,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Box(Modifier.fillMaxSize().pageTransition(pager, page)) {
                    when (page) {
                        AWARENESS -> AwarenessPage(vm)
                        HOME -> HomePage(vm)
                        LIBRARY -> LibraryPage(vm, active = pager.settledPage == LIBRARY)
                    }
                }
            }
            // Sfumatura in basso: il contenuto che scorre svanisce sotto i pallini.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp)
                    .background(Brush.verticalGradient(listOf(K.Ink.copy(alpha = 0f), K.Ink.copy(alpha = if (K.skeuo) 0.5f else 0.92f)))),
            )
            PageDots(pager, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp))
        }

        AnimatedVisibility(
            vm.searchOpen,
            enter = fadeIn(tween(260)) + slideInVertically(tween(420, easing = Motion.Calm)) { it / 10 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(260)) { it / 14 },
        ) { SearchOverlay(vm) }

        AnimatedVisibility(
            vm.agendaOpen,
            enter = fadeIn(tween(260)) + scaleIn(tween(460, easing = Motion.Calm), initialScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 0.2f)),
            exit = fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 0.95f, transformOrigin = TransformOrigin(0.5f, 0.2f)),
        ) { AgendaOverlay(vm) }

        // Scende dall'alto come la tendina di sistema.
        AnimatedVisibility(
            vm.notesOpen,
            enter = fadeIn(tween(260)) + slideInVertically(tween(480, easing = Motion.Calm)) { -it / 3 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(260)) { -it / 4 },
        ) { NotesOverlay(vm) }

        AnimatedVisibility(
            vm.settingsOpen,
            enter = fadeIn(tween(240)) + slideInVertically(tween(480, easing = Motion.Calm)) { it / 6 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(280)) { it / 8 },
        ) { SettingsSheet(vm) }

        AnimatedVisibility(
            vm.pickerOpen,
            enter = fadeIn(tween(240)) + slideInVertically(tween(480, easing = Motion.Calm)) { it / 6 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(280)) { it / 8 },
        ) { AppPicker(vm) }

        AnimatedVisibility(
            vm.friction != null,
            enter = fadeIn(tween(320)) + scaleIn(tween(520, easing = Motion.Calm), initialScale = 0.94f),
            exit = fadeOut(tween(220)) + scaleOut(tween(260), targetScale = 0.97f),
        ) { vm.friction?.let { FrictionPrompt(vm, it) } }

        AnimatedVisibility(
            vm.unlock != null,
            enter = fadeIn(tween(260)) + scaleIn(tween(460, easing = Motion.Calm), initialScale = 0.96f),
            exit = fadeOut(tween(200)),
        ) { UnlockPrompt(vm) }

        // Al primo avvio la presentazione copre tutto; chiudendosi si dissolve sulla Home.
        AnimatedVisibility(
            !vm.prefs.onboarded,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(520, easing = Motion.Calm)) + scaleOut(tween(620, easing = Motion.Calm), targetScale = 1.06f),
        ) { Onboarding(vm) }
    }
}

/** Le pagine vicine si allontanano appena e sfumano, con un leggero effetto di profondità. */
private fun Modifier.pageTransition(pager: PagerState, page: Int) = graphicsLayer {
    // d > 0: la pagina è a destra di quella visibile. La spingiamo un po' più in là, così entra con calma.
    val d = page - (pager.currentPage + pager.currentPageOffsetFraction)
    val offset = d.absoluteValue.coerceIn(0f, 1f)
    val scale = lerp(1f, 0.92f, offset)
    scaleX = scale; scaleY = scale
    alpha = lerp(1f, 0f, offset)
    translationX = size.width * 0.12f * d.coerceIn(-1f, 1f)
}

/** Un alone caldo che segue lo scorrimento tra le pagine. */
@Composable
private fun Glow(pager: PagerState) {
    if (K.metro || K.nothing) return // Metro e Nothing: nero pieno, nessun alone.
    if (K.dueffe) return Aurora(pager)
    Box(
        Modifier.fillMaxSize().drawBehind {
            val pos = pager.currentPage + pager.currentPageOffsetFraction // 0..2
            val x = size.width * (0.85f - pos * 0.35f)
            drawCircle(
                Brush.radialGradient(
                    listOf(K.Shu.copy(alpha = 0.10f), K.Shu.copy(alpha = 0f)),
                    center = Offset(x, size.height * 0.12f), radius = size.width * 0.9f,
                ),
                radius = size.width * 0.9f, center = Offset(x, size.height * 0.12f),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(K.Paper.copy(alpha = 0.04f), K.Paper.copy(alpha = 0f)),
                    center = Offset(size.width - x, size.height * 0.95f), radius = size.width,
                ),
                radius = size.width, center = Offset(size.width - x, size.height * 0.95f),
            )
        },
    )
}

/** Dueffe: nebulose oro e ambra che derivano piano, e pulviscolo avorio che sale e scintilla. Segue lo scorrimento. */
@Composable
private fun Aurora(pager: PagerState) {
    val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(32_000, easing = androidx.compose.animation.core.LinearEasing)), label = "drift",
    )
    val dust = remember { val r = kotlin.random.Random(9); List(34) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.2f) } }
    Box(
        Modifier.fillMaxSize().drawBehind {
            val a = t * 2f * Math.PI.toFloat()
            val pos = pager.currentPage + pager.currentPageOffsetFraction
            val shift = -pos * size.width * 0.12f
            fun nebula(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) {
                val c = Offset(cx + shift, cy)
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), c, r), r, c)
            }
            nebula(Color(0xFFFFD84D), 0.18f, size.width * (0.25f + 0.08f * kotlin.math.sin(a)), size.height * (0.12f + 0.04f * kotlin.math.cos(a)), size.width * 0.85f)
            nebula(Color(0xFFFFB800), 0.14f, size.width * (0.95f + 0.07f * kotlin.math.cos(a)), size.height * (0.55f + 0.05f * kotlin.math.sin(a)), size.width * 0.75f)
            nebula(Color(0xFFFFF0AD), 0.10f, size.width * (0.3f + 0.1f * kotlin.math.sin(a + 2f)), size.height * (0.95f + 0.03f * kotlin.math.cos(a)), size.width * 0.9f)
            dust.forEach { (x, y, phase, r) ->
                val rise = ((y - t * 3f) % 1f + 1f) % 1f
                val twinkle = (kotlin.math.sin((t * 18f + phase) * 2f * Math.PI.toFloat()) + 1f) / 2f
                drawCircle(Color(0xFFFFFAF0).copy(alpha = 0.10f + 0.35f * twinkle), r.dp.toPx(), Offset(x * size.width + shift * 0.5f, rise * size.height))
            }
        },
    )
}

@Composable
private fun PageDots(pager: PagerState, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val d = ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            if (K.skeuo) Box(
                // Fori incisi nel cuoio; quello della pagina attuale si accende d'ottone.
                Modifier.height(7.dp).width(lerp(20f, 7f, d).dp)
                    .skin(CircleShape, androidx.compose.ui.graphics.lerp(K.Shu, K.Ink, d), inset = d > 0.5f, elevation = 2.dp),
            ) else if (K.nothing) Box(
                // Nothing: punti della matrice; quello della pagina attuale si accende di bianco.
                Modifier.size(6.dp).clip(CircleShape).background(androidx.compose.ui.graphics.lerp(K.Paper, K.SurfaceHigher, d)),
            ) else if (K.dueffe) Box(
                // Dueffe: la pagina attuale è una pillola d'oro, le altre punti avorio tenui.
                Modifier.height(6.dp).width(lerp(22f, 6f, d).dp).clip(CircleShape)
                    .background(androidx.compose.ui.graphics.lerp(K.Shu, K.Paper.copy(alpha = 0.22f), d)),
            ) else if (K.metro) Box(
                // Metro: tasselli quadrati, quello della pagina attuale nel colore d'accento.
                Modifier.size(lerp(9f, 6f, d).dp).background(androidx.compose.ui.graphics.lerp(K.Shu, K.Paper.copy(alpha = 0.3f), d)),
            ) else Box(
                Modifier
                    .height(5.dp)
                    .width(lerp(18f, 5f, d).dp)
                    .clip(CircleShape)
                    .background(K.Paper.copy(alpha = lerp(0.9f, 0.25f, d))),
            )
        }
    }
}
