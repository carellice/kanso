package app.kanso.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.AlarmClock
import app.kanso.KansoViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.width
import app.kanso.data.Chrono
import app.kanso.data.ChronoInfo
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Timer

@Composable
fun HomePage(vm: KansoViewModel) {
    // Il contenuto segue il dito con resistenza; oltre la soglia si apre ricerca (su) o notifiche (giù).
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    val flingSpeed = with(LocalDensity.current) { 400.dp.toPx() }
    val tracker = remember { VelocityTracker() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasTorch = remember { torchCamera(context) != null }
    val chiarella = vm.prefs.showChiarella && CHIARELLA in vm.byPkg
    val money = vm.prefs.showMoney && MONEY in vm.byPkg
    val gemini = vm.prefs.showGemini && GEMINI in vm.byPkg
    val toggles = vm.prefs.showTorch && hasTorch || vm.prefs.showRinger || vm.prefs.showRotation || chiarella || money || gemini
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Modalità classica: niente Libreria, lo spazio privato si apre tenendo premuto in alto (orologio o fascia vuota).
    var privateOpen by remember { mutableStateOf(false) }
    val openPrivate = if (vm.prefs.classic && vm.privateUser != null) ({ privateOpen = true }) else null
    val home = Modifier
        .fillMaxSize()
        // Il gesto si legge prima dello spostamento: così il dito non "insegue" il contenuto che si muove.
        .pointerInput(vm.editing) {
            if (vm.editing) return@pointerInput
            detectVerticalDragGestures(
                onDragStart = { tracker.resetTracking() },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val before = pull.value
                    val next = before + dy * 0.55f
                    if ((before.absoluteValue < threshold) != (next.absoluteValue < threshold)) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch { pull.snapTo(next) }
                },
                onDragEnd = {
                    // Basta superare la soglia oppure un colpo deciso nella stessa direzione.
                    val v = tracker.calculateVelocity().y
                    when {
                        pull.value < -threshold || (v < -flingSpeed && pull.value < 0) -> vm.searchOpen = true
                        pull.value > threshold || (v > flingSpeed && pull.value > 0) -> vm.pullDown()
                    }
                    scope.launch { pull.animateTo(0f, spring(0.75f, 260f)) }
                },
                onDragCancel = { scope.launch { pull.animateTo(0f, spring(0.75f, 260f)) } },
            )
        }
        .graphicsLayer {
            translationY = pull.value
            val p = (pull.value.absoluteValue / (threshold * 3)).coerceIn(0f, 1f)
            alpha = 1f - p * 0.6f
            scaleX = 1f - p * 0.05f; scaleY = scaleX
        }
        // In orizzontale il foro della fotocamera sta di lato: le barre e il ritaglio insieme, senza sommarli.
        .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
        .padding(horizontal = 24.dp)
        .pointerInput(vm.editing, vm.prefs.doubleTapLock) {
            if (!vm.editing) detectTapGestures(
                onLongPress = { vm.guarded { vm.editing = true } },
                onDoubleTap = if (vm.prefs.doubleTapLock) { _ -> vm.lockScreen() } else null,
            )
        }
    val topBar = @Composable {
        AnimatedContent(
            if (vm.editing) TopBar.EDIT else if (vm.prefs.showClock || Chrono.active.isNotEmpty() || toggles) TopBar.CLOCK else TopBar.NONE,
            transitionSpec = { (fadeIn(tween(300)) + slideInVertically { -it / 2 }) togetherWith (fadeOut(tween(200)) + slideOutVertically { -it / 2 }) },
            label = "editBar",
            modifier = Modifier.padding(top = if (landscape) 8.dp else 20.dp).heightIn(min = 48.dp),
        ) { bar ->
            if (bar == TopBar.CLOCK) Clock(vm, toggles, landscape, chiarella, money, gemini, openPrivate)
            else if (bar == TopBar.EDIT) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Tune, "Impostazioni") { vm.settingsOpen = true }
                    Spacer(Modifier.weight(1f))
                    Text(if (vm.prefs.classic && !vm.showingWork) "Tocca un riquadro per le sue app" else "Trascina per riordinare", style = MaterialTheme.typography.bodyMedium, color = K.Muted)
                    Spacer(Modifier.weight(1f))
                    PillButton("Fine", Modifier.height(44.dp)) { vm.editing = false }
                }
            } else Spacer(
                Modifier.fillMaxWidth().height(48.dp)
                    .then(if (openPrivate != null) Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { openPrivate() }) } else Modifier),
            )
        }
    }
    val agenda = @Composable { m: Modifier ->
        AnimatedVisibility(
            !vm.editing, m,
            enter = fadeIn(tween(300)) + expandVertically(Motion.soft()),
            exit = fadeOut(tween(150)) + shrinkVertically(Motion.soft()),
        ) { AgendaPeek(vm, Modifier.padding(top = 14.dp)) }
    }
    // Personale e Lavoro scorrono di lato, nella direzione del controllo.
    val tiles = @Composable { m: Modifier ->
        AnimatedContent(
            vm.showingWork,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (fadeIn(tween(320)) + androidx.compose.animation.slideInHorizontally(tween(420, easing = Motion.Calm)) { it / 3 * dir }) togetherWith
                    (fadeOut(tween(200)) + androidx.compose.animation.slideOutHorizontally(tween(320, easing = Motion.Calm)) { -it / 3 * dir })
            },
            label = "workTab",
            modifier = m.fillMaxWidth(),
        ) { work ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (vm.prefs.classic && !work) ClassicGrid(vm) else TileGrid(vm, work) }
        }
    }
    // In orizzontale due colonne: a sinistra orologio, agenda e ricerca; a destra i riquadri, alti quanto lo schermo.
    if (landscape) Row(home, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            // La ricerca si misura per prima e resta in fondo: sugli schermi bassi è l'agenda ad accorciarsi.
            Column(Modifier.weight(1f, fill = false)) {
                topBar()
                agenda(Modifier)
            }
            SearchBar(Modifier.padding(top = 12.dp, bottom = 16.dp)) { vm.searchOpen = true }
        }
        // In fondo, al centro, ci sono i pallini delle pagine: i riquadri si fermano poco sopra.
        Column(Modifier.weight(0.58f).fillMaxHeight().padding(bottom = 24.dp)) {
            WorkBar(vm)
            tiles(Modifier.weight(1f))
        }
    } else Column(home) {
        topBar()
        agenda(Modifier)
        WorkBar(vm)
        tiles(Modifier.weight(1f))
        SearchBar(Modifier.padding(bottom = 40.dp)) { vm.searchOpen = true }
    }
    if (privateOpen && openPrivate != null) ModalCard({ privateOpen = false }) { PrivateSpaceContent(vm) { privateOpen = false } }
}

private enum class TopBar { NONE, CLOCK, EDIT }

/** Orologio facoltativo: ore e data, aggiornati allo scoccare di ogni minuto. Accanto, timer e cronometri attivi. */
@Composable
private fun Clock(vm: KansoViewModel, toggles: Boolean, landscape: Boolean, chiarella: Boolean, money: Boolean, gemini: Boolean, onLongPress: (() -> Unit)?) {
    val chronos = Chrono.active
    val now by produceState(System.currentTimeMillis(), chronos.isEmpty()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(if (chronos.isEmpty()) 60_000 - value % 60_000 else 1_000 - value % 1_000)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = if (landscape) 8.dp else 24.dp), verticalAlignment = Alignment.Bottom) {
        if (vm.prefs.showClock) {
            val time = remember(now / 60_000) { SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(now)) }
            val date = remember(now / 60_000) { SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(Date(now)) }
            Column(Modifier.weight(1f).pressable(onLongPress, pressedScale = 0.97f) { vm.start(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }) {
                // In orizzontale l'altezza è poca: l'ora si rimpicciolisce per lasciare posto all'agenda.
                val size = if (landscape) 64 else 84
                Text(time, style = MaterialTheme.typography.displayLarge.copy(fontSize = size.sp, lineHeight = (size + 4).sp), color = K.Paper, maxLines = 1, softWrap = false)
                Eyebrow(date)
            }
        }
        val chips = @Composable { chronos.take(2).forEach { ChronoChip(it, now) { vm.launch(it.pkg) } } }
        val buttons = @Composable {
            val content = @Composable {
                if (vm.prefs.showTorch) TorchButton()
                if (vm.prefs.showRinger) RingerButton(vm)
                if (vm.prefs.showRotation) RotationButton(vm)
                if (chiarella) ChiarellaButton(vm)
                if (money) MoneyButton(vm)
                if (gemini) GeminiButton(vm)
            }
            // In verticale accanto all'orologio, in riga rubano larghezza all'ora e la mandano a capo: vanno in colonna.
            if (toggles) {
                // Al massimo tre per colonna: con Chiarella e Money la colonna non si allunga troppo.
                if (vm.prefs.showClock && !landscape) androidx.compose.foundation.layout.FlowColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachColumn = 3,
                ) { content() }
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
        }
        // Accanto all'orologio in colonna, coi pulsanti sotto; da soli, in riga coi pulsanti a destra.
        if (vm.prefs.showClock) Column(
            Modifier.padding(bottom = 6.dp).animateContentSize(Motion.soft()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) { chips(); buttons() }
        else {
            Row(Modifier.animateContentSize(Motion.soft()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
            Spacer(Modifier.weight(1f))
            buttons()
        }
    }
}

@Composable
private fun ChronoChip(c: ChronoInfo, now: Long, onClick: () -> Unit) {
    val ms = if (c.countDown) c.base - now else now - c.base
    // Il timer arrotonda per eccesso, come l'Orologio; scaduto, conta in negativo.
    val s = if (c.countDown && ms > 0) (ms + 999) / 1000 else ms.absoluteValue / 1000
    val text = c.frozen ?: Chrono.format(if (ms < 0) -s * 1000 else s * 1000)
    Row(
        Modifier
            .skin(CircleShape, elevation = 3.dp)
            .pressable(pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (c.countDown) Icons.Rounded.HourglassTop else Icons.Rounded.Timer,
            if (c.countDown) "Timer" else "Cronometro", Modifier.size(18.dp),
            tint = if (c.frozen != null) K.Muted else K.Shu,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (c.frozen != null) K.Muted else K.Paper)
    }
}

@Composable
private fun TileGrid(vm: KansoViewModel, work: Boolean) {
    val state = rememberLazyGridState()
    // Copia locale durante il trascinamento: si salva solo al rilascio.
    val order = remember { mutableStateListOf<String>() }
    val tiles = if (work) vm.workTiles else vm.tiles
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(tiles) { if (dragging == null) { order.clear(); order.addAll(tiles) } }

    fun itemAt(pos: Offset): LazyGridItemInfo? = state.layoutInfo.visibleItemsInfo.firstOrNull {
        pos.x >= it.offset.x && pos.x <= it.offset.x + it.size.width && pos.y >= it.offset.y && pos.y <= it.offset.y + it.size.height
    }?.takeIf { it.key is String && it.key != ADD_KEY }

    if (work && order.isEmpty() && !vm.editing) {
        Text(
            "Nessuna app di lavoro.\nTieni premuto per aggiungerne.", style = MaterialTheme.typography.bodyMedium, color = K.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }

    // Colonne e grandezza si adattano allo spazio: vince la griglia (da 2 a 5 colonne) coi riquadri più grandi che ci stanno tutti.
    BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
    // Metro: riquadri vicini vicini, come la Start di Windows Phone.
    val gap = if (K.metro) 8.dp else 14.dp
    val count = order.size + (if (vm.editing) 1 else 0)
    fun tileFor(c: Int): Dp {
        val rows = (count + c - 1) / c
        val natural = (maxWidth - gap * (c - 1)) / c
        val fit = if (rows == 0) natural else (maxHeight - gap * (rows - 1)) / rows
        return minOf(natural, fit)
    }
    // In orizzontale lo spazio è basso e largo: più colonne per non rimpicciolire i riquadri.
    val cols = (2..if (maxWidth > maxHeight * 1.5f) 8 else 5).maxBy { tileFor(it) } // a parità vince la prima: meno colonne
    val tile = kotlin.math.floor(tileFor(cols).value - 1).dp.coerceAtLeast(56.dp)
    // Il nome si vede sempre; nei riquadri piccoli margini e spazi si stringono per farci stare icona e nome.
    val compact = tile < 84.dp
    val iconSize = minOf(52.dp, tile * if (compact) 0.4f else 0.45f)
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        state = state,
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier
            .width(tile * cols + gap * (cols - 1))
            .pointerInput(vm.editing) {
                if (!vm.editing) return@pointerInput
                detectDragGestures(
                    onDragStart = { pos -> itemAt(pos)?.let { dragging = it.key as String; dragOffset = Offset.Zero } },
                    onDrag = { change, amount ->
                        val key = dragging ?: return@detectDragGestures
                        change.consume()
                        dragOffset += amount
                        val current = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return@detectDragGestures
                        val center = Offset(
                            current.offset.x + current.size.width / 2f + dragOffset.x,
                            current.offset.y + current.size.height / 2f + dragOffset.y,
                        )
                        val target = itemAt(center) ?: return@detectDragGestures
                        if (target.key != key) {
                            val from = order.indexOf(key); val to = order.indexOf(target.key)
                            if (from >= 0 && to >= 0) {
                                order.add(to, order.removeAt(from))
                                // Il riquadro trascinato cambia posto: l'offset si sposta con lui.
                                dragOffset -= Offset((target.offset.x - current.offset.x).toFloat(), (target.offset.y - current.offset.y).toFloat())
                            }
                        }
                    },
                    onDragEnd = { dragging = null; dragOffset = Offset.Zero; vm.saveOrder(order.toList(), work) },
                    onDragCancel = { dragging = null; dragOffset = Offset.Zero },
                )
            },
    ) {
        itemsIndexed(order, key = { _, pkg -> pkg }) { index, pkg ->
            val app = vm.byPkg[pkg]
            val from = rememberLaunchFrom()
            val isDragged = dragging == pkg
            val lift by animateFloatAsState(if (isDragged) 1.08f else 1f, Motion.press(), label = "lift")
            Box(
                Modifier
                    .then(if (isDragged) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                    .zIndex(if (isDragged) 1f else 0f)
                    .offset { if (isDragged) IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) else IntOffset.Zero }
                    .graphicsLayer { scaleX = lift; scaleY = lift }
                    .then(from.modifier),
            ) {
                Tile(
                    vm, pkg, app?.label ?: "", index, editing = vm.editing, iconSize = iconSize, compact = compact,
                    onClick = { if (!vm.editing) app?.let { vm.launch(it.pkg, from.options()) } },
                    onLongClick = { if (!vm.editing) vm.guarded { vm.editing = true } },
                    onRemove = { vm.removeTile(pkg, work) },
                )
            }
        }
        if (vm.editing) item(key = ADD_KEY) {
            Box(Modifier.animateItem()) { AddTile { vm.pickerOpen = true } }
        }
    }
    }
}

private const val ADD_KEY = "__add__"

@Composable
private fun Tile(
    vm: KansoViewModel, pkg: String, label: String, index: Int, editing: Boolean, iconSize: Dp, compact: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onRemove: () -> Unit,
) {
    // Entrata a cascata: ogni riquadro sale e appare con un piccolo ritardo.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60L * index); enter.animateTo(1f, tween(700, easing = Motion.Calm)) }

    val wobble = rememberInfiniteTransition(label = "wobble")
    val angle by wobble.animateFloat(
        -1.4f, 1.4f, infiniteRepeatable(tween(150 + (index % 3) * 22), RepeatMode.Reverse), label = "angle",
    )
    val shape = RoundedCornerShape(30)

    // Metro: live tile. Ogni tanto il riquadro si capovolge, mostra il nome in grande e poi torna com'era.
    val flip = remember { Animatable(0f) }
    val live = K.metro && !compact && !editing
    LaunchedEffect(live) {
        flip.snapTo(0f)
        if (!live) return@LaunchedEffect
        val r = kotlin.random.Random(pkg.hashCode())
        while (true) {
            kotlinx.coroutines.delay(r.nextLong(6_000, 18_000))
            flip.animateTo(180f, tween(650, easing = Motion.Standard))
            kotlinx.coroutines.delay(r.nextLong(2_500, 5_000))
            flip.animateTo(360f, tween(650, easing = Motion.Standard))
            flip.snapTo(0f)
        }
    }

    Box(
        Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 40.dp.toPx()
                rotationZ = if (editing) angle else 0f
                rotationX = flip.value
                cameraDistance = 14f * density
            },
    ) {
        if (K.metro) Box(
            Modifier
                .fillMaxSize()
                .skin(shape, K.Shu)
                .pressable(onLongClick = onLongClick, onClick = onClick)
                .padding(if (compact) 6.dp else 10.dp),
        ) {
            val back = flip.value in 90f..270f
            if (back) Text(
                label.lowercase(), style = MaterialTheme.typography.titleLarge, color = K.Paper, maxLines = 3,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).graphicsLayer { rotationX = 180f },
            ) else {
                AppIcon(vm.appsRepo, pkg, iconSize, modifier = Modifier.align(if (compact) Alignment.TopCenter else Alignment.Center))
                Text(
                    label, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, color = K.Paper, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        } else Column(
            Modifier
                .fillMaxSize()
                .skin(shape)
                .pressable(onLongClick = onLongClick, onClick = onClick)
                .padding(if (compact) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(vm.appsRepo, pkg, iconSize)
            Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
            AppLabel(label, compact = compact)
        }
        if (editing) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset((-4).dp, (-4).dp)
                    .size(26.dp)
                    .then(
                        when {
                            // Metro: il bollino bianco per staccare il riquadro dalla Start.
                            K.metro -> Modifier.clip(CircleShape).background(K.Paper)
                            // Nothing: togliere è distruttivo, quindi il rosso segnale.
                            K.nothing -> Modifier.clip(CircleShape).background(K.Shu)
                            K.skeuo -> Modifier.skin(CircleShape, Color(0xFFB0402C), elevation = 3.dp)
                            else -> Modifier.skin(CircleShape, K.SurfaceHigher)
                        },
                    )
                    .pressable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Remove, "Rimuovi", Modifier.size(16.dp), tint = if (K.metro) K.Ink else K.Paper) }
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(30)
    Box(
        Modifier.aspectRatio(1f)
            // Kanso: solo il contorno. Skeuomorfismo: un alloggiamento vuoto, incassato nel cuoio.
            .then(
                when {
                    K.metro -> Modifier.border(2.dp, K.Faint)
                    K.nothing -> Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, K.SurfaceHigher, RoundedCornerShape(16.dp))
                    K.skeuo -> Modifier.skin(shape, K.Surface, inset = true)
                    else -> Modifier.clip(shape).border(1.dp, K.Paper.copy(alpha = 0.22f), shape)
                },
            )
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, "Aggiungi app", Modifier.size(28.dp), tint = K.Muted) }
}

@Composable
fun SearchBar(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .skin(CircleShape, inset = true)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = K.Muted)
        Spacer(Modifier.size(12.dp))
        Text("Cerca app, persone, luoghi, web", style = MaterialTheme.typography.bodyLarge, color = K.Muted)
    }
}
