package app.kanso.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.AlarmClock
import app.kanso.KansoViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomePage(vm: KansoViewModel) {
    // Il contenuto segue il dito con resistenza; oltre la soglia si apre ricerca (su) o notifiche (giù).
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = pull.value
                val p = (pull.value.absoluteValue / (threshold * 3)).coerceIn(0f, 1f)
                alpha = 1f - p * 0.6f
                scaleX = 1f - p * 0.05f; scaleY = scaleX
            }
            .pointerInput(vm.editing) {
                if (vm.editing) return@pointerInput
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        val before = pull.value
                        val next = before + dy * 0.45f
                        if ((before.absoluteValue < threshold) != (next.absoluteValue < threshold)) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch { pull.snapTo(next) }
                    },
                    onDragEnd = {
                        when {
                            pull.value < -threshold -> vm.searchOpen = true
                            pull.value > threshold -> vm.pullDown()
                        }
                        scope.launch { pull.animateTo(0f, spring(0.75f, 260f)) }
                    },
                    onDragCancel = { scope.launch { pull.animateTo(0f, spring(0.75f, 260f)) } },
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .pointerInput(vm.editing) {
                if (!vm.editing) detectTapGestures(onLongPress = { vm.guarded { vm.editing = true } })
            },
    ) {
        AnimatedContent(
            if (vm.editing) TopBar.EDIT else if (vm.prefs.showClock) TopBar.CLOCK else TopBar.NONE,
            transitionSpec = { (fadeIn(tween(300)) + slideInVertically { -it / 2 }) togetherWith (fadeOut(tween(200)) + slideOutVertically { -it / 2 }) },
            label = "editBar",
            modifier = Modifier.padding(top = 20.dp).heightIn(min = 48.dp),
        ) { bar ->
            if (bar == TopBar.CLOCK) Clock { vm.start(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
            else if (bar == TopBar.EDIT) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Tune, "Impostazioni") { vm.settingsOpen = true }
                    Spacer(Modifier.weight(1f))
                    Text("Trascina per riordinare", style = MaterialTheme.typography.bodyMedium, color = K.Muted)
                    Spacer(Modifier.weight(1f))
                    PillButton("Fine", Modifier.height(44.dp)) { vm.editing = false }
                }
            } else Spacer(Modifier.fillMaxWidth())
        }

        AnimatedVisibility(
            !vm.editing,
            enter = fadeIn(tween(300)) + expandVertically(Motion.soft()),
            exit = fadeOut(tween(150)) + shrinkVertically(Motion.soft()),
        ) { AgendaPeek(vm, Modifier.padding(top = 14.dp)) }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { TileGrid(vm) }

        SearchBar(Modifier.padding(bottom = 40.dp)) { vm.searchOpen = true }
    }
}

private enum class TopBar { NONE, CLOCK, EDIT }

/** Orologio facoltativo: ore e data, aggiornati allo scoccare di ogni minuto. */
@Composable
private fun Clock(onClick: () -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(Date(now)) }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp).pressable(pressedScale = 0.97f, onClick = onClick)) {
        Text(time, style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp, lineHeight = 88.sp), color = K.Paper)
        Eyebrow(date)
    }
}

@Composable
private fun TileGrid(vm: KansoViewModel) {
    val state = rememberLazyGridState()
    // Copia locale durante il trascinamento: si salva solo al rilascio.
    val order = remember { mutableStateListOf<String>() }
    val tiles = vm.tiles
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(tiles) { if (dragging == null) { order.clear(); order.addAll(tiles) } }

    fun itemAt(pos: Offset): LazyGridItemInfo? = state.layoutInfo.visibleItemsInfo.firstOrNull {
        pos.x >= it.offset.x && pos.x <= it.offset.x + it.size.width && pos.y >= it.offset.y && pos.y <= it.offset.y + it.size.height
    }?.takeIf { it.key is String && it.key != ADD_KEY }

    LazyVerticalGrid(
        columns = GridCells.Fixed(vm.prefs.columns),
        state = state,
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
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
                    onDragEnd = { dragging = null; dragOffset = Offset.Zero; vm.prefs.saveTiles(order.toList()) },
                    onDragCancel = { dragging = null; dragOffset = Offset.Zero },
                )
            },
    ) {
        itemsIndexed(order, key = { _, pkg -> pkg }) { index, pkg ->
            val app = vm.byPkg[pkg]
            val isDragged = dragging == pkg
            val lift by animateFloatAsState(if (isDragged) 1.08f else 1f, Motion.press(), label = "lift")
            Box(
                Modifier
                    .then(if (isDragged) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                    .zIndex(if (isDragged) 1f else 0f)
                    .offset { if (isDragged) IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) else IntOffset.Zero }
                    .graphicsLayer { scaleX = lift; scaleY = lift },
            ) {
                Tile(
                    vm, pkg, app?.label ?: "", index, editing = vm.editing,
                    onClick = { if (!vm.editing) app?.let { vm.launch(it.pkg) } },
                    onLongClick = { if (!vm.editing) vm.guarded { vm.editing = true } },
                    onRemove = { vm.removeTile(pkg) },
                )
            }
        }
        if (vm.editing) item(key = ADD_KEY) {
            Box(Modifier.animateItem()) { AddTile { vm.pickerOpen = true } }
        }
    }
}

private const val ADD_KEY = "__add__"

@Composable
private fun Tile(
    vm: KansoViewModel, pkg: String, label: String, index: Int, editing: Boolean,
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

    Box(
        Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 40.dp.toPx()
                rotationZ = if (editing) angle else 0f
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(K.Surface)
                .border(1.dp, K.Line, shape)
                .pressable(onLongClick = onLongClick, onClick = onClick)
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(vm.appsRepo, pkg, if (vm.prefs.columns >= 4) 34.dp else 46.dp)
            Spacer(Modifier.height(10.dp))
            AppLabel(label)
        }
        if (editing) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset((-4).dp, (-4).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(K.SurfaceHigher)
                    .border(1.dp, K.Line, CircleShape)
                    .pressable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Remove, "Rimuovi", Modifier.size(16.dp), tint = K.Paper) }
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(30)
    Box(
        Modifier.aspectRatio(1f).clip(shape).border(1.dp, K.Paper.copy(alpha = 0.22f), shape).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, "Aggiungi app", Modifier.size(28.dp), tint = K.Muted) }
}

@Composable
fun SearchBar(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(CircleShape)
            .background(K.Surface)
            .border(1.dp, K.Line, CircleShape)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = K.Muted)
        Spacer(Modifier.size(12.dp))
        Text("Cerca app, persone, luoghi, web", style = MaterialTheme.typography.bodyLarge, color = K.Muted)
    }
}
