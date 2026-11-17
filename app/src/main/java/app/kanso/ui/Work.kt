package app.kanso.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.kanso.KansoViewModel
import app.kanso.data.Shift
import app.kanso.data.WorkMode
import app.kanso.data.hhmm
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** In Home, sopra i riquadri: Personale | Lavoro in orario di lavoro, oppure il permesso in corso. */
@Composable
fun WorkBar(vm: KansoViewModel) {
    var pauseOpen by remember { mutableStateOf(false) }
    // A inizio turno si passa a Lavoro, a fine turno si torna a Personale.
    LaunchedEffect(vm.atWork) { vm.workTab = vm.atWork }
    AnimatedVisibility(
        vm.workSwitch,
        enter = fadeIn(tween(300)) + expandVertically(Motion.soft()),
        exit = fadeOut(tween(150)) + shrinkVertically(Motion.soft()),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Segmented(listOf("Personale", "Lavoro"), if (vm.workTab) 1 else 0) { vm.workTab = it == 1 }
            }
            if (vm.atWork && !vm.editing) {
                Spacer(Modifier.width(10.dp))
                IconCircle(Icons.Rounded.BeachAccess, "Permesso o ferie") { pauseOpen = true }
            }
        }
    }
    // In permesso o in ferie il controllo sparisce: resta solo un promemoria per riprendere prima.
    val end = vm.shiftEndsAt
    AnimatedVisibility(
        end != null && vm.paused && !vm.editing,
        enter = fadeIn(tween(300)) + expandVertically(Motion.soft()),
        exit = fadeOut(tween(150)) + shrinkVertically(Motion.soft()),
    ) {
        val until = if (vm.prefs.pauseTo >= (end?.let { vm.millisOf(it) } ?: 0)) "a fine turno" else "alle ${vm.clock(vm.prefs.pauseTo)}"
        Text(
            "In pausa dal lavoro fino $until · Riprendi",
            style = MaterialTheme.typography.labelMedium, color = K.Muted,
            modifier = Modifier.padding(top = 14.dp).clip(CircleShape).pressable { vm.cancelPause() }.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
    if (pauseOpen) PauseCard(vm) { pauseOpen = false }
}

/** Ferie per il resto del turno, oppure un permesso da un'ora a un'ora. */
@Composable
private fun PauseCard(vm: KansoViewModel, onDismiss: () -> Unit) {
    val nowMin = remember { vm.minutesOf(vm.now) }
    val endMin = remember { vm.shiftEndsAt?.let { it.hour * 60 + it.minute } ?: (nowMin + 60) }
    var from by remember { mutableStateOf(nowMin) }
    var to by remember { mutableStateOf(endMin) }
    var picking by remember { mutableStateOf<Int?>(null) }
    ModalCard(onDismiss) {
        when (val p = picking) {
            null -> Column {
                Eyebrow("Pausa dal lavoro")
                Spacer(Modifier.height(16.dp))
                PillButton("Ferie oggi", Modifier.fillMaxWidth()) { vm.takeDayOff(); onDismiss() }
                Spacer(Modifier.height(6.dp))
                Text("La scheda Lavoro sparisce fino a fine turno.", style = MaterialTheme.typography.bodyMedium, color = K.Muted)
                Spacer(Modifier.height(18.dp))
                Groove()
                Spacer(Modifier.height(14.dp))
                Text("Permesso", style = MaterialTheme.typography.titleMedium, color = K.Paper)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeChip("Dalle", from, Modifier.weight(1f)) { picking = 0 }
                    TimeChip("Alle", to, Modifier.weight(1f)) { picking = 1 }
                }
                Spacer(Modifier.height(14.dp))
                PillButton("Prendi permesso", Modifier.fillMaxWidth(), filled = false, enabled = from != to) { vm.takeLeave(from, to); onDismiss() }
                if (vm.pauseLater) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Permesso già fissato alle ${vm.clock(vm.prefs.pauseFrom)}–${vm.clock(vm.prefs.pauseTo)} · Annulla",
                        style = MaterialTheme.typography.labelMedium, color = K.Muted,
                        modifier = Modifier.clip(CircleShape).pressable { vm.cancelPause() }.padding(vertical = 8.dp),
                    )
                }
            }
            else -> TimePick(if (p == 0) "Dalle" else "Alle", if (p == 0) from else to, onCancel = { picking = null }) {
                if (p == 0) from = it else to = it
                picking = null
            }
        }
    }
}

@Composable
private fun TimeChip(label: String, minutes: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.skin(androidx.compose.foundation.shape.RoundedCornerShape(18.dp), K.Surface, inset = true)
            .pressable(pressedScale = 0.97f, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = K.Muted)
        Text(hhmm(minutes), style = MaterialTheme.typography.titleLarge, color = K.Paper)
    }
}

/** Scelta di un orario con i campi ore e minuti. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePick(title: String, minutes: Int, onCancel: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(minutes / 60 % 24, minutes % 60, is24Hour = true)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Eyebrow(title, Modifier.align(Alignment.Start))
        Spacer(Modifier.height(16.dp))
        TimeInput(state)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Annulla", Modifier.weight(1f), filled = false, onClick = onCancel)
            PillButton("Fatto", Modifier.weight(1f)) { onPick(state.hour * 60 + state.minute) }
        }
    }
}

/** Impostazioni: orario fisso o turni della settimana. Le app si scelgono dalla Home. */
@Composable
fun WorkSettings(vm: KansoViewModel) {
    val p = vm.prefs
    // Cosa si sta scegliendo: l'inizio o la fine dell'orario fisso, oppure il turno di un giorno.
    var picking by remember { mutableStateOf<String?>(null) }
    var shiftDay by remember { mutableStateOf<LocalDate?>(null) }
    Text(
        "In orario di lavoro la Home mostra Personale | Lavoro, e le app di lavoro si aprono senza attrito.",
        style = MaterialTheme.typography.bodyMedium, color = K.Muted,
    )
    Spacer(Modifier.height(14.dp))
    Segmented(WorkMode.entries.map { it.title }, p.workMode.ordinal) { p.saveWorkMode(WorkMode.entries[it]) }
    AnimatedVisibility(p.workMode == WorkMode.FIXED) {
        Column {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("L", "M", "M", "G", "V", "S", "D").forEachIndexed { i, d ->
                    val on = p.workDays and (1 shl i) != 0
                    Box(
                        Modifier.size(38.dp).clip(CircleShape)
                            .then(if (on) Modifier.background(if (K.metro || K.nothing) K.Shu else K.Paper) else Modifier.border(1.dp, K.SurfaceHigher, CircleShape))
                            .pressable { p.saveWorkDays(p.workDays xor (1 shl i)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d, style = MaterialTheme.typography.labelLarge, color = if (on && !K.metro && !K.nothing) K.Ink else K.Paper)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeChip("Inizio", p.workFixed.start, Modifier.weight(1f)) { picking = "start" }
                TimeChip("Fine", p.workFixed.end, Modifier.weight(1f)) { picking = "end" }
            }
        }
    }
    AnimatedVisibility(p.workMode == WorkMode.SHIFTS) {
        var week by remember { mutableLongStateOf(0) }
        val monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(week)
        Column {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.ChevronLeft, "Settimana prima") { week-- }
                Text(
                    when (week) { 0L -> "Questa settimana"; 1L -> "Prossima settimana"; else -> "Dal ${monday.dayOfMonth} ${monday.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)}" },
                    style = MaterialTheme.typography.titleMedium, color = K.Paper,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconCircle(Icons.Rounded.ChevronRight, "Settimana dopo") { week++ }
            }
            Spacer(Modifier.height(8.dp))
            for (i in 0L..6L) {
                val day = monday.plusDays(i)
                val shift = p.shifts[day.toEpochDay()]
                Row(
                    Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .pressable(pressedScale = 0.98f) { shiftDay = day }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() } + " ${day.dayOfMonth}",
                        style = MaterialTheme.typography.titleMedium, color = if (day == LocalDate.now()) K.Shu else K.Paper, modifier = Modifier.weight(1f),
                    )
                    Text(shift?.toString() ?: "Riposo", style = MaterialTheme.typography.bodyLarge, color = if (shift == null) K.Faint else K.Paper)
                }
            }
            Text("Imposta i turni ogni settimana: i giorni senza turno sono di riposo.", style = MaterialTheme.typography.labelMedium, color = K.Faint)
        }
    }
    AnimatedVisibility(p.workMode != WorkMode.NONE) {
        Column {
            Spacer(Modifier.height(8.dp))
            Row_("Scegli le app di lavoro", "${p.workTiles.size} app · si aggiungono dalla Home, nella scheda Lavoro") {
                vm.settingsOpen = false; vm.editing = true; vm.workTab = true
            }
        }
    }

    picking?.let { which ->
        ModalCard({ picking = null }) {
            TimePick(if (which == "start") "Inizio" else "Fine", if (which == "start") p.workFixed.start else p.workFixed.end, onCancel = { picking = null }) {
                p.saveWorkFixed(if (which == "start") p.workFixed.copy(start = it) else p.workFixed.copy(end = it))
                picking = null
            }
        }
    }
    shiftDay?.let { day -> ShiftCard(vm, day) { shiftDay = null } }
}

/** Il turno di un giorno: inizio e fine, oppure riposo. Un turno può finire il giorno dopo. */
@Composable
private fun ShiftCard(vm: KansoViewModel, day: LocalDate, onDismiss: () -> Unit) {
    val p = vm.prefs
    // Si parte dal turno già scelto, o dall'ultimo turno messo nei giorni prima.
    val start = remember {
        p.shifts[day.toEpochDay()] ?: (1L..7L).firstNotNullOfOrNull { p.shifts[day.toEpochDay() - it] } ?: Shift(9 * 60, 17 * 60)
    }
    var shift by remember { mutableStateOf(start) }
    var picking by remember { mutableStateOf<Int?>(null) }
    ModalCard(onDismiss) {
        when (val k = picking) {
            null -> Column {
                Eyebrow(day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN) + " ${day.dayOfMonth} " + day.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeChip("Inizio", shift.start, Modifier.weight(1f)) { picking = 0 }
                    TimeChip("Fine", shift.end, Modifier.weight(1f)) { picking = 1 }
                }
                if (shift.end <= shift.start) {
                    Spacer(Modifier.height(8.dp))
                    Text("Finisce il giorno dopo.", style = MaterialTheme.typography.labelMedium, color = K.Muted)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Riposo", Modifier.weight(1f), filled = false) { p.saveShift(day.toEpochDay(), null); onDismiss() }
                    PillButton("Salva", Modifier.weight(1f)) { p.saveShift(day.toEpochDay(), shift); onDismiss() }
                }
            }
            else -> TimePick(if (k == 0) "Inizio" else "Fine", if (k == 0) shift.start else shift.end, onCancel = { picking = null }) {
                shift = if (k == 0) shift.copy(start = it) else shift.copy(end = it)
                picking = null
            }
        }
    }
}
