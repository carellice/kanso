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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

    // Un launcher non si chiude mai: Indietro chiude le sovrapposizioni o torna alla Home.
    BackHandler {
        when {
            vm.unlock != null -> vm.cancelUnlock()
            vm.friction != null -> vm.cancelFriction()
            vm.pickerOpen -> vm.pickerOpen = false
            vm.settingsOpen -> vm.settingsOpen = false
            vm.searchOpen -> vm.searchOpen = false
            vm.editing -> vm.editing = false
            pager.currentPage != HOME -> scope.launch { pager.animateScrollToPage(HOME) }
        }
    }

    val covered = vm.searchOpen || vm.settingsOpen || vm.friction != null || vm.unlock != null || vm.pickerOpen
    val blur by animateDpAsState(if (covered) 18.dp else 0.dp, tween(420, easing = Motion.Calm), label = "blur")
    val dim by animateFloatAsState(if (covered) 0.92f else 1f, tween(420, easing = Motion.Calm), label = "dim")

    Box(Modifier.fillMaxSize().background(K.Ink)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = dim.coerceAtLeast(0.96f); scaleY = dim.coerceAtLeast(0.96f); alpha = dim }
                .blur(blur),
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
                        LIBRARY -> LibraryPage(vm)
                    }
                }
            }
            // Sfumatura in basso: il contenuto che scorre svanisce sotto i pallini.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp)
                    .background(Brush.verticalGradient(listOf(K.Ink.copy(alpha = 0f), K.Ink.copy(alpha = 0.92f)))),
            )
            PageDots(pager, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp))
        }

        AnimatedVisibility(
            vm.searchOpen,
            enter = fadeIn(tween(260)) + slideInVertically(tween(420, easing = Motion.Calm)) { it / 10 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(260)) { it / 14 },
        ) { SearchOverlay(vm) }

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

@Composable
private fun PageDots(pager: PagerState, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val d = ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(
                Modifier
                    .height(5.dp)
                    .width(lerp(18f, 5f, d).dp)
                    .clip(CircleShape)
                    .background(K.Paper.copy(alpha = lerp(0.9f, 0.25f, d))),
            )
        }
    }
}
