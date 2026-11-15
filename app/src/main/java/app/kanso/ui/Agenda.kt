package app.kanso.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kanso.AGENDA_DAYS
import app.kanso.KansoViewModel
import app.kanso.data.AgendaEvent
import app.kanso.data.dayKey
import app.kanso.data.eventUri
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PEEK = 3
private const val PEEK_DAYS = 7

/**
 * L'agenda in piccolo, sotto l'orologio: una card di altezza fissa con una pagina per giorno (prossimi 7 giorni),
 * da scorrere in orizzontale. Con Cloudflare configurato ogni pagina è il riassunto AI del giorno; senza, gli impegni.
 * Un tocco apre l'agenda intera.
 */
@Composable
fun AgendaPeek(vm: KansoViewModel, modifier: Modifier = Modifier) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshAgenda() }
    val now = System.currentTimeMillis()
    val today = dayKey(now, false)
    val tomorrow = dayKey(now + 86_400_000L, false)
    val until = dayKey(now + PEEK_DAYS * 86_400_000L, false)
    val open = { if (vm.hasCalendarAccess) vm.agendaOpen = true else ask.launch(Manifest.permission.READ_CALENDAR) }
    val days = remember(vm.agenda, today) { vm.agenda.filter { it.day < until }.groupBy { it.day }.toList() }
    val lines = remember(vm.agendaSummary) { summaryLines(vm.agendaSummary) }
    val ai = vm.prefs.cfAccount.isNotBlank() && vm.prefs.cfToken.isNotBlank()
    val shape = RoundedCornerShape(24.dp)
    // La card ha la stessa altezza per tutte le pagine: quella della pagina più lunga. I pallini restano in fondo,
    // così si scorre sempre nello stesso punto.
    val density = LocalDensity.current
    var tallest by remember(days, lines, ai) { mutableIntStateOf(0) }
    val cardHeight = maxOf(168.dp, with(density) { tallest.toDp() } + 25.dp)
    Column(modifier.fillMaxWidth()) {
    Box(
        Modifier.fillMaxWidth().height(cardHeight).animateContentSize(Motion.soft()).clip(shape).background(K.Surface).border(1.dp, K.Line, shape),
    ) {
        if (!vm.hasCalendarAccess || days.isEmpty()) {
            Text(
                if (!vm.hasCalendarAccess) "Tocca per vedere i tuoi impegni" else "Nessun impegno nei prossimi $PEEK_DAYS giorni. Spazio libero, per te.",
                style = MaterialTheme.typography.bodyMedium, color = K.Muted,
                modifier = Modifier.fillMaxSize().pressable(pressedScale = 0.98f, onClick = open).padding(20.dp),
            )
            return@Box
        }
        val pager = rememberPagerState { days.size }
        Column(Modifier.fillMaxSize()) {
            // Tutte le pagine composte subito: così si misurano tutte e si conosce la più lunga.
            HorizontalPager(
                pager, Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top, beyondViewportPageCount = days.size,
            ) { page ->
                val (day, events) = days[page]
                val title = dayTitle(day, today, tomorrow)
                // La frase del riassunto si abbina alla pagina dal giorno con cui inizia (Oggi, Domani, Giovedì…).
                val line = lines.firstOrNull { (label, _) -> label != null && plain(label.substringBefore(' ')) == plain(title.substringBefore(' ')) }?.second
                Column(
                    Modifier.fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { if (it.height > tallest) tallest = it.height }
                        .pressable(pressedScale = 0.98f, onClick = open).padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ai) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(13.dp), tint = K.Shu)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (day == today) K.Shu else K.Muted, modifier = Modifier.weight(1f))
                        Text(if (events.size == 1) "1 impegno" else "${events.size} impegni", style = MaterialTheme.typography.labelSmall, color = K.Faint)
                        if (ai) RegenerateButton(vm)
                    }
                    // Con l'AI la pagina è il riassunto; finché non arriva (o se fallisce) restano gli impegni.
                    // Il riassunto si legge sempre intero: la card cresce e i riquadri della Home si rimpiccioliscono.
                    if (ai && line != null) Text(
                        highlightTimes(line), style = MaterialTheme.typography.bodyMedium, color = K.Paper.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    ) else {
                        events.take(PEEK).forEach { e ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(3.dp).height(16.dp).clip(CircleShape).background(Color(e.color).copy(alpha = 0.9f)))
                                Spacer(Modifier.width(10.dp))
                                val time = if (e.allDay) "tutto il giorno" else timeFormat.format(Date(e.begin))
                                Text(time, style = MaterialTheme.typography.labelMedium, color = if (isLive(e)) K.Shu else K.Muted)
                                Spacer(Modifier.width(10.dp))
                                Text(e.title, style = MaterialTheme.typography.bodyMedium, color = K.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val more = events.size - PEEK
                        if (more > 0) Text(
                            if (more == 1) "e un altro impegno" else "e altri $more impegni",
                            style = MaterialTheme.typography.labelMedium, color = K.Faint, modifier = Modifier.padding(start = 13.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(5.dp), horizontalArrangement = Arrangement.Center) {
                if (days.size > 1) repeat(days.size) { i ->
                    val current = i == pager.currentPage
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(5.dp).width(if (current) 14.dp else 5.dp).animateContentSize(Motion.soft())
                            .clip(CircleShape).background(if (current) K.Paper.copy(alpha = 0.8f) else K.Faint),
                    )
                }
            }
        }
    }
    NavigateButton(vm)
    }
}

/** Da 2 ore prima di un impegno con un luogo (e finché è in corso): un tocco apre la navigazione fin lì. */
@Composable
private fun NavigateButton(vm: KansoViewModel) {
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(60_000); value = System.currentTimeMillis() } }
    val next = remember(vm.agenda, now / 60_000) {
        vm.agenda.firstOrNull { !it.allDay && it.location.isNotBlank() && now < it.end && it.begin - now <= 2 * 3_600_000L }
    }
    AnimatedVisibility(
        next != null,
        enter = fadeIn(tween(300)) + expandVertically(Motion.soft()),
        exit = fadeOut(tween(150)) + shrinkVertically(Motion.soft()),
    ) {
        val e = next ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(20.dp)
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().clip(shape).background(K.Shu.copy(alpha = 0.14f))
                .border(1.dp, K.Shu.copy(alpha = 0.35f), shape)
                .pressable(pressedScale = 0.98f) { vm.navigate(e.location) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Directions, null, Modifier.size(22.dp), tint = K.Shu)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Naviga verso ${e.location}", style = MaterialTheme.typography.titleSmall, color = K.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val at = if (e.begin <= now) "in corso" else "alle ${timeFormat.format(Date(e.begin))}"
                Text("${e.title} · $at", style = MaterialTheme.typography.labelMedium, color = K.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Rifà subito il riassunto AI dell'agenda; mentre lavora, gira. */
@Composable
private fun RegenerateButton(vm: KansoViewModel) {
    val spin = rememberInfiniteTransition(label = "regen")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Icon(
        Icons.Rounded.Refresh, "Rigenera il riassunto",
        Modifier.padding(start = 8.dp).size(26.dp).clip(CircleShape)
            .pressable { if (!vm.agendaSummarizing) vm.regenerateAgenda() }
            .padding(5.dp)
            .graphicsLayer { rotationZ = if (vm.agendaSummarizing) angle else 0f },
        tint = K.Muted,
    )
}

/** Minuscolo e senza accenti: «Giovedi» e «giovedì» sono lo stesso giorno. */
private fun plain(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

/** Le righe del riassunto AI: etichetta del giorno (prima dei due punti, se breve e non un orario) e frase. */
private fun summaryLines(text: String?): List<Pair<String?, String>> =
    text.orEmpty().lines().map { it.trim().trimStart('-', '•', '*', ' ') }.filter { it.isNotBlank() }.map { line ->
        val label = line.substringBefore(':', "").trim().takeIf { it.length in 1..20 && !it.last().isDigit() }
        label to (if (label != null) line.substringAfter(':').trim() else line)
    }

private val TimeRegex = Regex("""\b\d{1,2}[:.]\d{2}\b""")

/** Orari in grassetto e a pieno colore, il resto più tenue. */
private fun highlightTimes(text: String) = buildAnnotatedString {
    var last = 0
    TimeRegex.findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = K.Paper)) { append(m.value) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** Tutti gli impegni dei prossimi giorni, raggruppati per giorno. */
@Composable
fun AgendaOverlay(vm: KansoViewModel) {
    val today = dayKey(System.currentTimeMillis(), false)
    val tomorrow = dayKey(System.currentTimeMillis() + 86_400_000L, false)
    val days = remember(vm.agenda) { vm.agenda.groupBy { it.day }.toList() }
    Column(Modifier.fillMaxSize().background(K.Ink).statusBarsPadding()) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Prossimi $AGENDA_DAYS giorni")
                Text("Agenda", style = MaterialTheme.typography.headlineLarge, color = K.Paper)
            }
            Text(
                "Chiudi", style = MaterialTheme.typography.labelLarge, color = K.Paper,
                modifier = Modifier.clip(CircleShape).pressable { vm.agendaOpen = false }.padding(12.dp),
            )
        }
        LazyColumn(
            Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (days.isEmpty()) item {
                KCard {
                    Column {
                        Text("Nessun impegno nei prossimi $AGENDA_DAYS giorni.", style = MaterialTheme.typography.titleLarge, color = K.Paper)
                        Spacer(Modifier.height(4.dp))
                        Text("Spazio libero, per te.", style = MaterialTheme.typography.bodyMedium, color = K.Muted)
                    }
                }
            }
            items(days, key = { it.first }) { (day, events) ->
                val start = vm.agenda.indexOf(events.first())
                KCard(Modifier.animateItem()) {
                    Column {
                        Text(dayTitle(day, today, tomorrow), style = MaterialTheme.typography.titleLarge, color = if (day == today) K.Paper else K.Paper.copy(alpha = 0.75f))
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            events.forEachIndexed { i, e -> AgendaRow(vm, e, start + i) }
                        }
                    }
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.ITALIAN)

private fun isLive(e: AgendaEvent): Boolean {
    val now = System.currentTimeMillis()
    return !e.allDay && e.begin <= now && now < e.end
}

private fun dayTitle(day: Int, today: Int, tomorrow: Int): String = when (day) {
    today -> "Oggi"
    tomorrow -> "Domani"
    else -> {
        val c = Calendar.getInstance().apply { set(day / 10000, day / 100 % 100 - 1, day % 100) }
        SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(c.time).replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun AgendaRow(vm: KansoViewModel, e: AgendaEvent, index: Int) {
    val a = remember(e.id, e.begin) { Animatable(0f) }
    LaunchedEffect(e.id, e.begin) { delay(50L * index.coerceAtMost(10)); a.animateTo(1f, tween(600, easing = Motion.Calm)) }
    val live = isLive(e)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = a.value; translationX = (1 - a.value) * 18.dp.toPx() }
            .clip(RoundedCornerShape(16.dp))
            .pressable(pressedScale = 0.98f) { vm.start(Intent(Intent.ACTION_VIEW, eventUri(e.id))) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(56.dp)) {
            if (e.allDay) Text("tutto il\ngiorno", style = MaterialTheme.typography.labelMedium, color = K.Muted)
            else {
                Text(timeFormat.format(Date(e.begin)), style = MaterialTheme.typography.titleMedium, color = if (live) K.Shu else K.Paper)
                Text(timeFormat.format(Date(e.end)), style = MaterialTheme.typography.labelMedium, color = K.Muted)
            }
        }
        Box(Modifier.width(3.dp).height(36.dp).clip(CircleShape).background(Color(e.color).copy(alpha = 0.9f)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.titleMedium, color = K.Paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when {
                live -> Text("in corso", style = MaterialTheme.typography.labelMedium, color = K.Shu)
                e.location.isNotBlank() -> Text(e.location, style = MaterialTheme.typography.labelMedium, color = K.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Il riassunto delle notifiche che scende dall'alto, con lo swipe verso il basso in Home. */
@Composable
fun NotesOverlay(vm: KansoViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(K.Ink.copy(alpha = 0.9f))
            .pointerInput(Unit) { detectTapGestures { vm.notesOpen = false } }
            // Su richiude il pannello, come la tendina di sistema; un altro swipe giù apre le notifiche di Android.
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -24.dp.toPx()) vm.notesOpen = false
                        else if (total > 40.dp.toPx()) { vm.notesOpen = false; vm.expandNotifications() }
                    },
                ) { _, dy -> total += dy }
            },
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(vm)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Notifiche di Android", filled = false) { vm.notesOpen = false; vm.expandNotifications() }
                PillButton("Chiudi", filled = false) { vm.notesOpen = false }
            }
        }
    }
}
