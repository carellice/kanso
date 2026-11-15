package app.kanso.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
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

private const val PEEK = 2

/** L'agenda in piccolo, sotto l'orologio: i prossimi impegni. Un tocco la apre a tutto schermo. */
@Composable
fun AgendaPeek(vm: KansoViewModel, modifier: Modifier = Modifier) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshAgenda() }
    val today = dayKey(System.currentTimeMillis(), false)
    val tomorrow = dayKey(System.currentTimeMillis() + 86_400_000L, false)
    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize(Motion.soft())
            .clip(RoundedCornerShape(20.dp))
            .pressable(pressedScale = 0.98f) {
                if (vm.hasCalendarAccess) vm.agendaOpen = true else ask.launch(Manifest.permission.READ_CALENDAR)
            }
            .padding(vertical = 6.dp),
    ) {
        when {
            !vm.hasCalendarAccess -> Text("Tocca per vedere i tuoi impegni", style = MaterialTheme.typography.bodyMedium, color = K.Muted)
            vm.agenda.isEmpty() -> Text("Nessun impegno in vista. Spazio libero, per te.", style = MaterialTheme.typography.bodyMedium, color = K.Muted)
            vm.agendaSummary != null -> Text(
                vm.agendaSummary!!, style = MaterialTheme.typography.bodyMedium, color = K.Paper,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
            else -> {
                vm.agenda.take(PEEK).forEach { e ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(16.dp).clip(CircleShape).background(Color(e.color).copy(alpha = 0.9f)))
                        Spacer(Modifier.width(10.dp))
                        val day = if (e.day == today) "" else dayTitle(e.day, today, tomorrow).substringBefore(' ') + " "
                        val time = if (e.allDay) "tutto il giorno" else timeFormat.format(Date(e.begin))
                        Text("$day$time", style = MaterialTheme.typography.labelMedium, color = if (isLive(e)) K.Shu else K.Muted)
                        Spacer(Modifier.width(10.dp))
                        Text(e.title, style = MaterialTheme.typography.bodyMedium, color = K.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val more = vm.agenda.size - PEEK
                if (more > 0) Text(
                    if (more == 1) "e un altro impegno" else "e altri $more impegni",
                    style = MaterialTheme.typography.labelMedium, color = K.Faint, modifier = Modifier.padding(start = 13.dp, top = 2.dp),
                )
            }
        }
    }
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
            // Uno swipe verso l'alto richiude il pannello, come la tendina di sistema.
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -24) vm.notesOpen = false } },
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
