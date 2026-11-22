package app.kanso.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kanso.KansoViewModel
import app.kanso.data.NotificationCollector
import app.kanso.data.Slot
import app.kanso.data.normalize

/** Icona dello slot disegnata per lo stile in uso; è una maschera bianca che si colora col tema. */
@Composable
private fun SlotIcon(slot: Slot, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val style = K.style().name.lowercase()
    // Nomi fissi (classic_<stile>_<slot>), protetti dallo shrink in res/raw/keep_classic.xml.
    @android.annotation.SuppressLint("DiscouragedApi")
    val id = remember(style, slot) { context.resources.getIdentifier("classic_${style}_${slot.key}", "drawable", context.packageName) }
    if (id == 0) return
    Image(
        painterResource(id), slot.title, modifier.size(size),
        // Skeuo e Dueffe: l'ottone e l'oro dell'accento; gli altri stili il colore del testo.
        colorFilter = ColorFilter.tint(if (K.skeuo || K.dueffe) K.Shu else K.Paper),
    )
}

/** Numero di notifiche nel pannello: tondo sull'angolo del riquadro o accanto all'app. */
@Composable
private fun Badge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp).clip(CircleShape)
            // Metro: il riquadro è già nel colore d'accento, il bollino è bianco come quello per togliere.
            .background(if (K.metro) K.Paper else K.Shu).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else "$count", style = MaterialTheme.typography.labelSmall,
            color = if (K.metro) K.Ink else if (K.light()) Color.White else K.Ink,
        )
    }
}

private fun KansoViewModel.unread(slot: Slot): Int = slotApps(slot).sumOf { NotificationCollector.unread[it] ?: 0 }

/** La Home della modalità classica: dodici funzioni fisse, 3×4 in verticale e 6×2 in orizzontale. */
@Composable
fun ClassicGrid(vm: KansoViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        val gap = if (K.metro) 8.dp else 14.dp
        val cols = if (maxWidth > maxHeight * 1.5f) 6 else 3
        val rows = Slot.entries.size / cols
        val tile = minOf((maxWidth - gap * (cols - 1)) / cols, (maxHeight - gap * (rows - 1)) / rows)
        val compact = tile < 84.dp
        val iconSize = minOf(56.dp, tile * if (compact) 0.42f else 0.48f)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Slot.entries.chunked(cols).forEachIndexed { r, line ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    line.forEachIndexed { c, slot -> SlotTile(vm, slot, r * cols + c, tile, iconSize, compact) }
                }
            }
        }
    }
}

@Composable
private fun SlotTile(vm: KansoViewModel, slot: Slot, index: Int, tile: Dp, iconSize: Dp, compact: Boolean) {
    // Entrata a cascata, come i riquadri della Home normale.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(40L * index); enter.animateTo(1f, tween(700, easing = Motion.Calm)) }
    val from = rememberLaunchFrom()
    val empty = vm.slotApps(slot).isEmpty()
    val shape = RoundedCornerShape(30)
    Box(
        Modifier.size(tile).then(from.modifier)
            .graphicsLayer {
                alpha = enter.value * if (empty) 0.45f else 1f
                translationY = (1f - enter.value) * 40.dp.toPx()
            },
    ) {
        Column(
            Modifier.fillMaxSize()
                .then(if (K.metro) Modifier.skin(shape, K.Shu) else Modifier.skin(shape))
                .pressable(
                    onLongClick = { if (!vm.editing) vm.guarded { vm.slotPicker = slot } },
                    // In modifica (Home già sbloccata) un tocco sceglie le app dello slot.
                    onClick = { if (vm.editing) vm.slotPicker = slot else vm.openSlot(slot, from.options()) },
                )
                .padding(if (compact) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SlotIcon(slot, iconSize)
            Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
            AppLabel(slot.title, compact = compact)
        }
        Badge(vm.unread(slot), Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp))
    }
}

/** Uno slot con più app (Messaggi con SMS, WhatsApp, Telegram…): l'elenco, ognuna col suo numero di notifiche. */
@Composable
fun SlotHub(vm: KansoViewModel, slot: Slot) {
    val apps = vm.slotApps(slot)
    Column(Modifier.fillMaxSize().backdrop(cloth = true).statusBarsPadding().sideInsetsPadding()) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SlotIcon(slot, 36.dp)
            Spacer(Modifier.width(14.dp))
            Text(slot.title, style = MaterialTheme.typography.headlineMedium, color = K.Paper, modifier = Modifier.weight(1f))
            Text("Chiudi", style = MaterialTheme.typography.labelLarge, color = K.Paper,
                modifier = Modifier.clip(CircleShape).pressable { vm.hubOpen = null }.padding(12.dp))
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.weight(1f)) {
            items(apps, key = { it }) { pkg ->
                val from = rememberLaunchFrom()
                Row(
                    Modifier.fillMaxWidth().then(from.modifier).clip(RoundedCornerShape(20.dp))
                        .pressable(pressedScale = 0.98f) { vm.hubOpen = null; vm.launch(pkg, from.options()) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(vm.appsRepo, pkg, 44.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(vm.byPkg[pkg]?.label ?: pkg, style = MaterialTheme.typography.titleMedium, color = K.Paper, modifier = Modifier.weight(1f))
                    Badge(NotificationCollector.unread[pkg] ?: 0)
                }
            }
        }
        Text(
            "Scegli le app", style = MaterialTheme.typography.labelLarge, color = K.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding().padding(bottom = 24.dp)
                .clip(CircleShape).pressable { vm.guarded { vm.hubOpen = null; vm.slotPicker = slot } }.padding(12.dp),
        )
    }
}

/** Scelta delle app di uno slot: una sola apre subito, più d'una mostrano l'elenco. */
@Composable
fun SlotPicker(vm: KansoViewModel, slot: Slot) {
    val initial = remember(slot) { vm.slotApps(slot) }
    val chosen = remember(slot) { mutableStateListOf<String>().apply { addAll(initial) } }
    var query by remember { mutableStateOf("") }
    // In cima le app già scelte all'apertura; l'ordine non cambia mentre si tocca, così le righe non saltano sotto il dito.
    val list = remember(vm.apps, query) {
        val q = normalize(query.trim())
        vm.apps.filter { q.isEmpty() || normalize(it.label).contains(q) }
            .sortedBy { if (it.pkg in initial) initial.indexOf(it.pkg) else Int.MAX_VALUE }
    }
    Column(Modifier.fillMaxSize().backdrop(cloth = true).statusBarsPadding().sideInsetsPadding()) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SlotIcon(slot, 36.dp)
            Spacer(Modifier.width(14.dp))
            Text(slot.title, style = MaterialTheme.typography.headlineMedium, color = K.Paper, modifier = Modifier.weight(1f))
            Text("Fine", style = MaterialTheme.typography.labelLarge, color = K.Paper,
                modifier = Modifier.clip(CircleShape).pressable { vm.prefs.saveSlotApps(slot, chosen.toList()); vm.slotPicker = null }.padding(12.dp))
        }
        Text(
            "Con un'app lo slot la apre subito; con più app mostra l'elenco.",
            style = MaterialTheme.typography.bodyMedium, color = K.Muted, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { FieldBox(query, { query = it }, "Filtra le app") }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), modifier = Modifier.weight(1f)) {
            items(list, key = { it.pkg }) { app ->
                val on = app.pkg in chosen
                Row(
                    Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .pressable(pressedScale = 0.98f) { if (on) chosen.remove(app.pkg) else chosen.add(app.pkg) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(vm.appsRepo, app.pkg, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(app.label, style = MaterialTheme.typography.titleMedium, color = K.Paper, modifier = Modifier.weight(1f))
                    if (on) Icon(Icons.Rounded.Check, "Scelta", Modifier.size(22.dp), tint = K.Shu)
                }
            }
        }
        Text(
            "Ripristina le app predefinite", style = MaterialTheme.typography.labelLarge, color = K.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding().padding(bottom = 16.dp)
                .clip(CircleShape).pressable { vm.prefs.saveSlotApps(slot, null); vm.slotPicker = null }.padding(12.dp),
        )
    }
}
